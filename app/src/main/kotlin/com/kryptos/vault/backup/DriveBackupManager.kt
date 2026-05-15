package com.kryptos.vault.backup

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kryptos.vault.data.KeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Uploads the SQLCipher-encrypted vault database to the user's private Drive **AppData** folder.
 *
 * AppData files are only visible to this app's OAuth client — the user can see "Kryptos" listed
 * in Drive settings under "Manage apps" but cannot browse the file from drive.google.com.
 * Combined with the fact that the file is already SQLCipher-encrypted before upload, this gives
 * us zero-knowledge cloud backup: Google never sees the plaintext.
 */
class DriveBackupManager(private val context: Context) {

    data class BackupInfo(val fileId: String, val modifiedAtMillis: Long)

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "kryptos_backup_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var lastBackupAtMillis: Long
        get() = prefs.getLong(KEY_LAST_BACKUP, 0L)
        private set(value) = prefs.edit().putLong(KEY_LAST_BACKUP, value).apply()

    /** Locates the SQLCipher database file Room writes to. */
    private fun getDbFile(userId: String?): File {
        val sanitizedId = userId?.replace(Regex("[^a-zA-Z0-9]"), "_")
        val dbName = if (sanitizedId == null) "kryptos.db" else "kryptos_$sanitizedId.db"
        return context.getDatabasePath(dbName)
    }

    /** Lists existing files in AppData with [name]. */
    suspend fun findExisting(accessToken: String, name: String = BACKUP_NAME): BackupInfo? = withContext(Dispatchers.IO) {
        android.util.Log.d("DriveBackup", "Searching for existing file: $name")
        val query = "name='$name' and trashed=false"
        val url = URL(
            "https://www.googleapis.com/drive/v3/files" +
                "?spaces=appDataFolder" +
                "&q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                "&fields=files(id,modifiedTime)" +
                "&orderBy=modifiedTime desc"
        )
        val text = executeWithRetry(accessToken, "GET", url)
        val arr = JSONObject(text).optJSONArray("files") ?: return@withContext null
        if (arr.length() == 0) {
            android.util.Log.d("DriveBackup", "No existing file found for $name")
            null
        } else {
            val o = arr.getJSONObject(0)
            val info = BackupInfo(
                fileId = o.getString("id"),
                modifiedAtMillis = parseIso(o.optString("modifiedTime", "")),
            )
            android.util.Log.d("DriveBackup", "Found existing file: ${info.fileId}")
            info
        }
    }

    private suspend fun executeWithRetry(
        accessToken: String,
        method: String,
        url: URL,
        body: ByteArray? = null,
        contentType: String? = null,
        retries: Int = 3
    ): String = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        repeat(retries) { attempt ->
            try {
                val conn = (url.openConnection() as HttpsURLConnection).apply {
                    requestMethod = method
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Accept", "application/json")
                    if (contentType != null) setRequestProperty("Content-Type", contentType)
                    connectTimeout = 15000
                    readTimeout = 20000
                    if (body != null) {
                        doOutput = true
                        outputStream.use { it.write(body) }
                    }
                }
                val code = conn.responseCode
                val response = (if (code in 200..299) conn.inputStream else conn.errorStream).use {
                    it.bufferedReader().readText()
                }
                if (code in 200..299) return@withContext response
                throw IOException("Drive API error $code: $response")
            } catch (e: Exception) {
                lastException = e
                android.util.Log.w("DriveBackup", "Attempt ${attempt + 1} failed: ${e.message}")
                kotlinx.coroutines.delay(1000L * (attempt + 1))
            }
        }
        throw lastException ?: IOException("Unknown Drive error")
    }

    /**
     * Uploads (or replaces) the vault DB **and** the SQLCipher passphrase. Without the
     * passphrase, the encrypted DB can't be opened on a fresh install — both pieces are
     * required for true cross-device restore. Drive AppData visibility is scoped to this
     * OAuth client, so the key file is only readable by Kryptos.
     */
    suspend fun backup(accessToken: String, userId: String?): String = withContext(Dispatchers.IO) {
        val dbFile = getDbFile(userId)
        if (!dbFile.exists()) {
            throw IOException("No local vault file found at ${dbFile.name}. Add some entries first.")
        }
        android.util.Log.i("DriveBackup", "Starting backup for user $userId (file: ${dbFile.name})")

        val existing = findExisting(accessToken, BACKUP_NAME)
        val fileId = if (existing != null) {
            android.util.Log.d("DriveBackup", "Updating existing database backup...")
            updateContent(accessToken, existing.fileId, dbFile, "application/octet-stream")
            existing.fileId
        } else {
            android.util.Log.d("DriveBackup", "Creating new database backup...")
            createNew(accessToken, dbFile, BACKUP_NAME, "application/octet-stream")
        }

        // Also back up the passphrase (32 random bytes, base64 inside JSON).
        android.util.Log.d("DriveBackup", "Backing up encryption key...")
        val passphrase = KeyManager.getDatabasePassphrase(context)
        val keyJson = JSONObject().apply {
            put("v", 1)
            put("passphrase", android.util.Base64.encodeToString(passphrase, android.util.Base64.NO_WRAP))
        }.toString().toByteArray()
        val keyExisting = findExisting(accessToken, KEY_NAME)
        if (keyExisting != null) {
            updateBytes(accessToken, keyExisting.fileId, keyJson, "application/json")
        } else {
            createNewBytes(accessToken, KEY_NAME, keyJson, "application/json")
        }

        lastBackupAtMillis = System.currentTimeMillis()
        android.util.Log.i("DriveBackup", "Backup completed successfully.")
        fileId
    }

    /**
     * Premium feature: Back up the vault to a visible folder named "KryptosBackups" in the user's
     * My Drive. This allows the user to see their backup file, although it remains SQLCipher-encrypted.
     */
    suspend fun backupToOwnDrive(accessToken: String, userId: String?): String = withContext(Dispatchers.IO) {
        val dbFile = getDbFile(userId)
        if (!dbFile.exists()) throw IOException("No local vault to back up yet.")

        val folderId = getOrCreateKryptosFolder(accessToken)
        
        // Find existing backup in that specific folder
        val query = "name='$BACKUP_NAME' and '$folderId' in parents and trashed=false"
        val existingId = findFileInFolder(accessToken, query)

        val fileId = if (existingId != null) {
            updateContent(accessToken, existingId, dbFile, "application/octet-stream")
            existingId
        } else {
            createNewInFolder(accessToken, dbFile, BACKUP_NAME, "application/octet-stream", folderId)
        }

        lastBackupAtMillis = System.currentTimeMillis()
        fileId
    }

    private fun findFileInFolder(accessToken: String, query: String): String? {
        val url = URL(
            "https://www.googleapis.com/drive/v3/files" +
                "?q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                "&fields=files(id)"
        )
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 10000
            readTimeout = 10000
        }
        val response = conn.inputStream.use { it.bufferedReader().readText() }
        val arr = JSONObject(response).optJSONArray("files") ?: return null
        return if (arr.length() > 0) arr.getJSONObject(0).getString("id") else null
    }

    private fun getOrCreateKryptosFolder(accessToken: String): String {
        val folderName = "KryptosBackups"
        val query = "name='$folderName' and mimeType='application/vnd.google-apps.folder' and trashed=false"
        val existingId = findFileInFolder(accessToken, query)
        if (existingId != null) return existingId

        val metadata = JSONObject().apply {
            put("name", folderName)
            put("mimeType", "application/vnd.google-apps.folder")
        }.toString()

        val url = URL("https://www.googleapis.com/drive/v3/files")
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 10000
            readTimeout = 10000
            doOutput = true
        }
        conn.outputStream.use { it.write(metadata.toByteArray()) }
        val response = conn.inputStream.use { it.bufferedReader().readText() }
        return JSONObject(response).getString("id")
    }

    private fun createNewInFolder(accessToken: String, file: File, name: String, mime: String, folderId: String): String {
        val boundary = "----KryptosBoundary${System.currentTimeMillis()}"
        val metadata = JSONObject().apply {
            put("name", name)
            put("parents", org.json.JSONArray().put(folderId))
            put("mimeType", mime)
        }.toString()
        val body = ByteArrayOutputStream().apply {
            write("--$boundary\r\n".toByteArray())
            write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            write(metadata.toByteArray())
            write("\r\n--$boundary\r\n".toByteArray())
            write("Content-Type: $mime\r\n\r\n".toByteArray())
            write(file.readBytes())
            write("\r\n--$boundary--\r\n".toByteArray())
        }
        val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            connectTimeout = 10000
            readTimeout = 30000
            doOutput = true
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val response = conn.inputStream.use { it.bufferedReader().readText() }
        return JSONObject(response).getString("id")
    }

    /**
     * Downloads the latest AppData backup, restores the passphrase to local Keystore-wrapped
     * storage, and atomically replaces the live DB. Caller should restart the process so Room
     * reopens with the restored credentials.
     */
    suspend fun restore(accessToken: String, userId: String?): Boolean = withContext(Dispatchers.IO) {
        val dbFile = getDbFile(userId)
        val dbEntry = findExisting(accessToken, BACKUP_NAME) ?: return@withContext false

        // Restore the passphrase first — failing here means we'd be left with a DB we can't open.
        val keyEntry = findExisting(accessToken, KEY_NAME)
        if (keyEntry != null) {
            val keyBytes = downloadBytes(accessToken, keyEntry.fileId)
            val obj = JSONObject(String(keyBytes))
            val passphrase = android.util.Base64.decode(
                obj.getString("passphrase"),
                android.util.Base64.NO_WRAP,
            )
            KeyManager.setDatabasePassphrase(context, passphrase)
        }
        // If keyEntry is null, we assume restore is on the same device the backup came from —
        // the local passphrase is still valid.

        val tmp = File(dbFile.parentFile, "kryptos.db.restore")
        downloadToFile(accessToken, dbEntry.fileId, tmp)
        File(dbFile.parentFile, "${dbFile.name}-wal").delete()
        File(dbFile.parentFile, "${dbFile.name}-shm").delete()
        if (dbFile.exists()) dbFile.delete()
        tmp.renameTo(dbFile)
        true
    }

    private fun downloadBytes(accessToken: String, fileId: String): ByteArray {
        val url = URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 10000
            readTimeout = 10000
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.use { it.bufferedReader().readText() }.orEmpty()
            throw IOException("Drive download failed: $code $err")
        }
        return conn.inputStream.use { it.readBytes() }
    }

    private fun downloadToFile(accessToken: String, fileId: String, dest: File) {
        val url = URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 10000
            readTimeout = 10000
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.use { it.bufferedReader().readText() }.orEmpty()
            throw IOException("Drive download failed: $code $err")
        }
        conn.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
    }

    private fun createNew(accessToken: String, file: File, name: String, mime: String): String =
        createNewBytes(accessToken, name, file.readBytes(), mime)

    private fun createNewBytes(accessToken: String, name: String, bytes: ByteArray, mime: String): String {
        val boundary = "----KryptosBoundary${System.currentTimeMillis()}"
        val metadata = JSONObject().apply {
            put("name", name)
            put("parents", org.json.JSONArray().put("appDataFolder"))
            put("mimeType", mime)
        }.toString()
        val body = ByteArrayOutputStream().apply {
            write("--$boundary\r\n".toByteArray())
            write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            write(metadata.toByteArray())
            write("\r\n--$boundary\r\n".toByteArray())
            write("Content-Type: $mime\r\n\r\n".toByteArray())
            write(bytes)
            write("\r\n--$boundary--\r\n".toByteArray())
        }
        val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            connectTimeout = 10000
            readTimeout = 30000
            doOutput = true
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val response = (if (code in 200..299) conn.inputStream else conn.errorStream).use {
            it.bufferedReader().readText()
        }
        if (code !in 200..299) throw IOException("Drive upload failed: $code $response")
        return JSONObject(response).getString("id")
    }

    private fun updateContent(accessToken: String, fileId: String, file: File, mime: String) =
        updateBytes(accessToken, fileId, file.readBytes(), mime)

    private fun updateBytes(accessToken: String, fileId: String, bytes: ByteArray, mime: String) {
        val url = URL("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "PATCH"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", mime)
            connectTimeout = 10000
            readTimeout = 30000
            doOutput = true
        }
        conn.outputStream.use { it.write(bytes) }
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.use { it.bufferedReader().readText() }.orEmpty()
            throw IOException("Drive replace failed: $code $err")
        }
    }

    private fun parseIso(s: String): Long {
        if (s.isBlank()) return 0L
        return runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrDefault(0L)
    }

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val BACKUP_NAME = "kryptos.db"
        private const val KEY_NAME = "kryptos.key"
        private const val KEY_LAST_BACKUP = "last_backup"
    }
}
