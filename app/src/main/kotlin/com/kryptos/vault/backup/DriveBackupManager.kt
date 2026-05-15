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

    private fun getDbFile(userId: String?): File {
        val sanitizedId = userId?.replace(Regex("[^a-zA-Z0-9]"), "_")
        val dbName = if (sanitizedId == null) "kryptos.db" else "kryptos_$sanitizedId.db"
        return context.getDatabasePath(dbName)
    }

    suspend fun findExisting(accessToken: String, name: String = BACKUP_NAME): BackupInfo? = withContext(Dispatchers.IO) {
        android.util.Log.d("DriveBackup", "Searching for: $name")
        val query = "name='$name' and trashed=false"
        val url = URL("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id,modifiedTime)&orderBy=modifiedTime desc")
        
        val response = request(accessToken, "GET", url)
        val arr = JSONObject(response).optJSONArray("files") ?: return@withContext null
        if (arr.length() == 0) null else {
            val o = arr.getJSONObject(0)
            BackupInfo(o.getString("id"), parseIso(o.optString("modifiedTime", "")))
        }
    }

    suspend fun backup(accessToken: String, userId: String?): String = withContext(Dispatchers.IO) {
        val dbFile = getDbFile(userId)
        if (!dbFile.exists()) throw IOException("No vault file found for backup.")

        android.util.Log.i("DriveBackup", "Starting backup...")
        val existing = findExisting(accessToken, BACKUP_NAME)
        val fileId = if (existing != null) {
            updateBytes(accessToken, existing.fileId, dbFile.readBytes(), "application/octet-stream")
            existing.fileId
        } else {
            createBytes(accessToken, BACKUP_NAME, dbFile.readBytes(), "application/octet-stream", "appDataFolder")
        }

        // Key backup
        val passphrase = KeyManager.getDatabasePassphrase(context)
        val keyJson = JSONObject().apply {
            put("v", 1)
            put("passphrase", android.util.Base64.encodeToString(passphrase, android.util.Base64.NO_WRAP))
        }.toString().toByteArray()
        
        val keyExisting = findExisting(accessToken, KEY_NAME)
        if (keyExisting != null) {
            updateBytes(accessToken, keyExisting.fileId, keyJson, "application/json")
        } else {
            createBytes(accessToken, KEY_NAME, keyJson, "application/json", "appDataFolder")
        }

        lastBackupAtMillis = System.currentTimeMillis()
        android.util.Log.i("DriveBackup", "Backup success.")
        fileId
    }

    suspend fun backupToOwnDrive(accessToken: String, userId: String?): String = withContext(Dispatchers.IO) {
        val dbFile = getDbFile(userId)
        if (!dbFile.exists()) throw IOException("No vault file found.")

        val folderId = getOrCreateKryptosFolder(accessToken)
        val query = "name='$BACKUP_NAME' and '$folderId' in parents and trashed=false"
        val url = URL("https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id)")
        val response = request(accessToken, "GET", url)
        val arr = JSONObject(response).optJSONArray("files")
        val existingId = if (arr != null && arr.length() > 0) arr.getJSONObject(0).getString("id") else null

        val fileId = if (existingId != null) {
            updateBytes(accessToken, existingId, dbFile.readBytes(), "application/octet-stream")
            existingId
        } else {
            createBytes(accessToken, BACKUP_NAME, dbFile.readBytes(), "application/octet-stream", folderId)
        }

        lastBackupAtMillis = System.currentTimeMillis()
        fileId
    }

    private suspend fun getOrCreateKryptosFolder(accessToken: String): String {
        val folderName = "KryptosBackups"
        val query = "name='$folderName' and mimeType='application/vnd.google-apps.folder' and trashed=false"
        val url = URL("https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id)")
        val response = request(accessToken, "GET", url)
        val arr = JSONObject(response).optJSONArray("files")
        if (arr != null && arr.length() > 0) return arr.getJSONObject(0).getString("id")

        val metadata = JSONObject().apply {
            put("name", folderName)
            put("mimeType", "application/vnd.google-apps.folder")
        }.toString().toByteArray()
        val createUrl = URL("https://www.googleapis.com/drive/v3/files")
        return JSONObject(request(accessToken, "POST", createUrl, metadata, "application/json")).getString("id")
    }

    private suspend fun createBytes(accessToken: String, name: String, bytes: ByteArray, mime: String, parent: String): String {
        val boundary = "KryptosBoundary${System.currentTimeMillis()}"
        val metadata = JSONObject().apply {
            put("name", name)
            put("parents", org.json.JSONArray().put(parent))
            put("mimeType", mime)
        }.toString()
        
        val body = ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: $mime\r\n\r\n".toByteArray())
            write(bytes)
            write("\r\n--$boundary--\r\n".toByteArray())
        }.toByteArray()

        val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
        return JSONObject(request(accessToken, "POST", url, body, "multipart/related; boundary=$boundary")).getString("id")
    }

    private suspend fun updateBytes(accessToken: String, fileId: String, bytes: ByteArray, mime: String) {
        val url = URL("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
        request(accessToken, "PATCH", url, bytes, mime)
    }

    suspend fun restore(accessToken: String, userId: String?): Boolean = withContext(Dispatchers.IO) {
        val dbFile = getDbFile(userId)
        val dbEntry = findExisting(accessToken, BACKUP_NAME) ?: return@withContext false

        val keyEntry = findExisting(accessToken, KEY_NAME)
        if (keyEntry != null) {
            val url = URL("https://www.googleapis.com/drive/v3/files/${keyEntry.fileId}?alt=media")
            val keyBytes = request(accessToken, "GET", url).toByteArray()
            val obj = JSONObject(String(keyBytes))
            val passphrase = android.util.Base64.decode(obj.getString("passphrase"), android.util.Base64.NO_WRAP)
            KeyManager.setDatabasePassphrase(context, passphrase)
        }

        val downloadUrl = URL("https://www.googleapis.com/drive/v3/files/${dbEntry.fileId}?alt=media")
        val dbBytes = request(accessToken, "GET", downloadUrl).toByteArray()
        
        File(dbFile.parentFile, "${dbFile.name}-wal").delete()
        File(dbFile.parentFile, "${dbFile.name}-shm").delete()
        dbFile.writeBytes(dbBytes)
        true
    }

    private suspend fun request(accessToken: String, method: String, url: URL, body: ByteArray? = null, contentType: String? = null): String = withContext(Dispatchers.IO) {
        android.util.Log.d("DriveBackup", "Request: $method $url")
        var lastErr: Exception? = null
        repeat(3) { attempt ->
            try {
                val conn = (url.openConnection() as HttpsURLConnection).apply {
                    requestMethod = method
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    if (contentType != null) setRequestProperty("Content-Type", contentType)
                    connectTimeout = 20000
                    readTimeout = 30000
                    if (body != null) {
                        doOutput = true
                        outputStream.use { it.write(body) }
                    }
                }
                val code = conn.responseCode
                val res = if (code in 200..299) conn.inputStream.use { it.readBytes() } 
                          else conn.errorStream?.use { it.readBytes() } ?: "Error $code".toByteArray()
                
                if (code in 200..299) return@withContext String(res)
                throw IOException("Drive API $code: ${String(res)}")
            } catch (e: Exception) {
                lastErr = e
                android.util.Log.w("DriveBackup", "Attempt ${attempt+1} failed: ${e.message}")
                kotlinx.coroutines.delay(2000L * (attempt + 1))
            }
        }
        throw lastErr ?: IOException("Network error")
    }

    private fun parseIso(s: String): Long = runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrDefault(0L)

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val BACKUP_NAME = "kryptos.db"
        private const val KEY_NAME = "kryptos.key"
        private const val KEY_LAST_BACKUP = "last_backup"
    }
}
