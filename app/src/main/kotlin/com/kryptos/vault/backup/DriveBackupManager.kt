package com.kryptos.vault.backup

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kryptos.vault.data.KeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.net.ssl.HttpsURLConnection

/**
 * Uploads the SQLCipher-encrypted vault database to the user's private Drive **AppData** folder.
 */
class DriveBackupManager(private val context: Context) {

    data class BackupInfo(val fileId: String, val modifiedAtMillis: Long, val name: String = BACKUP_NAME)
    private data class BackupPair(
        val db: BackupInfo,
        val key: BackupInfo?,
        val metadata: BackupInfo?,
        val source: String,
        val entryCount: Int?,
        val isBundle: Boolean,
    )

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

    fun getLastBackupAtMillis(userId: String?): Long {
        val key = if (userId == null) KEY_LAST_BACKUP else "${KEY_LAST_BACKUP}_$userId"
        return prefs.getLong(key, 0L)
    }

    private fun setLastBackupAtMillis(userId: String?, value: Long) {
        val key = if (userId == null) KEY_LAST_BACKUP else "${KEY_LAST_BACKUP}_$userId"
        prefs.edit().putLong(key, value).apply()
    }

    private fun getDbFile(userId: String?): File {
        val sanitizedId = userId?.replace(Regex("[^a-zA-Z0-9]"), "_")
        val dbName = if (sanitizedId == null) "kryptos.db" else "kryptos_$sanitizedId.db"
        return context.getDatabasePath(dbName)
    }

    private fun userSuffix(userId: String?): String? =
        userId?.replace(Regex("[^a-zA-Z0-9]"), "_")

    private fun bundleName(userId: String?) = userSuffix(userId)?.let { "kryptos_$it.backup" } ?: BUNDLE_NAME
    private fun databaseName(userId: String?) = userSuffix(userId)?.let { "kryptos_$it.db" } ?: BACKUP_NAME
    private fun keyName(userId: String?) = userSuffix(userId)?.let { "kryptos_$it.key" } ?: KEY_NAME
    private fun metaName(userId: String?) = userSuffix(userId)?.let { "kryptos_$it.meta.json" } ?: META_NAME

    suspend fun findExisting(accessToken: String, name: String = BACKUP_NAME, space: String = "appDataFolder"): BackupInfo? = withContext(Dispatchers.IO) {
        android.util.Log.d("DriveBackup", "Searching for: $name in $space")
        val query = "name='$name' and trashed=false"
        val url = URL("https://www.googleapis.com/drive/v3/files?spaces=$space&q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id,name,modifiedTime)&orderBy=modifiedTime desc")
        
        val response = try { request(accessToken, "GET", url) } catch (e: Exception) { return@withContext null }
        val arr = JSONObject(response).optJSONArray("files") ?: return@withContext null
        if (arr.length() == 0) null else {
            val o = arr.getJSONObject(0)
            BackupInfo(o.getString("id"), parseIso(o.optString("modifiedTime", "")), o.optString("name", name))
        }
    }

    suspend fun refreshLastBackupDate(accessToken: String, userId: String?) = withContext(Dispatchers.IO) {
        val existing = findLatestBackup(accessToken, userId)?.db
        
        if (existing != null) {
            setLastBackupAtMillis(userId, existing.modifiedAtMillis)
        }
    }

    suspend fun backup(accessToken: String, userId: String?): String = withContext(Dispatchers.IO) {
        val dbFile = getDbFile(userId)
        
        val app = context.applicationContext as? com.kryptos.vault.KryptosApp
        val repo = app?.getRepository(userId)
        val entryCount = repo?.count() ?: 0
        if (entryCount == 0) throw IOException("No vault entries to back up.")
        repo?.checkpoint()

        if (!dbFile.exists()) {
            android.util.Log.e("DriveBackup", "Database file does not exist at: ${dbFile.absolutePath}")
            throw IOException("No vault file found to back up. Add an entry first.")
        }

        val backupBytes = createBackupBundle(dbFile)
        android.util.Log.i("DriveBackup", "Starting backup bundle of ${backupBytes.size} bytes...")
        val backupName = bundleName(userId)
        val existing = findExisting(accessToken, backupName)
        val fileId = if (existing != null) {
            updateBytes(accessToken, existing.fileId, backupBytes, "application/zip")
            existing.fileId
        } else {
            createBytes(accessToken, backupName, backupBytes, "application/zip", "appDataFolder")
        }

        // Key backup
        val passphrase = KeyManager.getDatabasePassphrase(context, userId)
        val keyJson = JSONObject().apply {
            put("v", 1)
            put("userId", userId ?: JSONObject.NULL)
            put("passphrase", android.util.Base64.encodeToString(passphrase, android.util.Base64.NO_WRAP))
        }.toString().toByteArray()
        
        val scopedKeyName = keyName(userId)
        val keyExisting = findExisting(accessToken, scopedKeyName)
        if (keyExisting != null) {
            updateBytes(accessToken, keyExisting.fileId, keyJson, "application/json")
        } else {
            createBytes(accessToken, scopedKeyName, keyJson, "application/json", "appDataFolder")
        }
        uploadMetadata(accessToken, userId, entryCount, "appDataFolder")

        setLastBackupAtMillis(userId, System.currentTimeMillis())
        android.util.Log.i("DriveBackup", "Backup success.")
        fileId
    }

    suspend fun backupToOwnDrive(accessToken: String, userId: String?): String = withContext(Dispatchers.IO) {
        val dbFile = getDbFile(userId)
        val app = context.applicationContext as? com.kryptos.vault.KryptosApp
        val repo = app?.getRepository(userId)
        val entryCount = repo?.count() ?: 0
        if (entryCount == 0) throw IOException("No vault entries to back up.")
        repo?.checkpoint()

        if (!dbFile.exists()) throw IOException("No vault file found.")

        val folderId = getOrCreateKryptosFolder(accessToken)
        
        val backupBytes = createBackupBundle(dbFile)
        val backupName = bundleName(userId)
        val query = "name='$backupName' and '$folderId' in parents and trashed=false"
        val url = URL("https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id)")
        val response = request(accessToken, "GET", url)
        val arr = JSONObject(response).optJSONArray("files")
        val existingId = if (arr != null && arr.length() > 0) arr.getJSONObject(0).getString("id") else null

        val fileId = if (existingId != null) {
            updateBytes(accessToken, existingId, backupBytes, "application/zip")
            existingId
        } else {
            createBytes(accessToken, backupName, backupBytes, "application/zip", folderId)
        }

        // Key backup (Crucial for Pro users to restore on fresh install!)
        val passphrase = KeyManager.getDatabasePassphrase(context, userId)
        val keyJson = JSONObject().apply {
            put("v", 1)
            put("userId", userId ?: JSONObject.NULL)
            put("passphrase", android.util.Base64.encodeToString(passphrase, android.util.Base64.NO_WRAP))
        }.toString().toByteArray()

        val scopedKeyName = keyName(userId)
        val keyQuery = "name='$scopedKeyName' and '$folderId' in parents and trashed=false"
        val keyUrl = URL("https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(keyQuery, "UTF-8")}&fields=files(id)")
        val keyResp = request(accessToken, "GET", keyUrl)
        val keyArr = JSONObject(keyResp).optJSONArray("files")
        val existingKeyId = if (keyArr != null && keyArr.length() > 0) keyArr.getJSONObject(0).getString("id") else null

        if (existingKeyId != null) {
            updateBytes(accessToken, existingKeyId, keyJson, "application/json")
        } else {
            createBytes(accessToken, scopedKeyName, keyJson, "application/json", folderId)
        }
        uploadMetadata(accessToken, userId, entryCount, folderId)

        setLastBackupAtMillis(userId, System.currentTimeMillis())
        fileId
    }

    private suspend fun findKryptosFolder(accessToken: String): String? {
        val query = "name='$FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false"
        val url = URL("https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id)")
        val response = try { request(accessToken, "GET", url) } catch (e: Exception) { return null }
        val arr = JSONObject(response).optJSONArray("files")
        return if (arr != null && arr.length() > 0) arr.getJSONObject(0).getString("id") else null
    }

    private suspend fun getOrCreateKryptosFolder(accessToken: String): String {
        findKryptosFolder(accessToken)?.let { return it }

        val metadata = JSONObject().apply {
            put("name", FOLDER_NAME)
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

    private fun createBackupBundle(dbFile: File): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun addIfExists(file: File, entryName: String) {
                if (!file.exists()) return
                zip.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }

            addIfExists(dbFile, "database")
            addIfExists(File("${dbFile.path}-wal"), "database-wal")
            addIfExists(File("${dbFile.path}-shm"), "database-shm")
            addIfExists(File("${dbFile.path}-journal"), "database-journal")
        }
        return output.toByteArray()
    }

    private suspend fun uploadMetadata(accessToken: String, userId: String?, entryCount: Int, parent: String) {
        val metadataJson = JSONObject().apply {
            put("v", 1)
            put("entryCount", entryCount)
            put("userId", userId ?: JSONObject.NULL)
            put("createdAtMillis", System.currentTimeMillis())
        }.toString().toByteArray()

        val scopedMetaName = metaName(userId)
        val existing = if (parent == "appDataFolder") {
            findExisting(accessToken, scopedMetaName, "appDataFolder")
        } else {
            findExistingInFolder(accessToken, scopedMetaName, parent)
        }

        if (existing != null) {
            updateBytes(accessToken, existing.fileId, metadataJson, "application/json")
        } else {
            createBytes(accessToken, scopedMetaName, metadataJson, "application/json", parent)
        }
    }

    private suspend fun findExistingInFolder(accessToken: String, name: String, folderId: String): BackupInfo? = withContext(Dispatchers.IO) {
        val query = "name='$name' and '$folderId' in parents and trashed=false"
        val url = URL("https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id,name,modifiedTime)&orderBy=modifiedTime desc")
        val response = try { request(accessToken, "GET", url) } catch (e: Exception) { return@withContext null }
        val arr = JSONObject(response).optJSONArray("files") ?: return@withContext null
        if (arr.length() == 0) null else {
            val o = arr.getJSONObject(0)
            BackupInfo(o.getString("id"), parseIso(o.optString("modifiedTime", "")), o.optString("name", name))
        }
    }

    private suspend fun findNewestDatabaseLike(accessToken: String, space: String, userId: String?): BackupInfo? = withContext(Dispatchers.IO) {
        val query = "trashed=false and name contains 'kryptos'"
        val url = URL("https://www.googleapis.com/drive/v3/files?spaces=$space&q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id,name,modifiedTime,mimeType)&orderBy=modifiedTime desc&pageSize=20")
        val response = try { request(accessToken, "GET", url) } catch (e: Exception) { return@withContext null }
        val arr = JSONObject(response).optJSONArray("files") ?: return@withContext null
        newestDatabaseFrom(arr, userId)
    }

    private suspend fun findNewestDatabaseLikeInFolder(accessToken: String, folderId: String, userId: String?): BackupInfo? = withContext(Dispatchers.IO) {
        val query = "trashed=false and '$folderId' in parents and name contains 'kryptos'"
        val url = URL("https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id,name,modifiedTime,mimeType)&orderBy=modifiedTime desc&pageSize=20")
        val response = try { request(accessToken, "GET", url) } catch (e: Exception) { return@withContext null }
        val arr = JSONObject(response).optJSONArray("files") ?: return@withContext null
        newestDatabaseFrom(arr, userId)
    }

    private fun newestDatabaseFrom(arr: org.json.JSONArray, userId: String?): BackupInfo? {
        val userScopedNames = setOf(bundleName(userId), databaseName(userId))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("name", "")
            val isLegacy = userId == null && (name == BUNDLE_NAME || name == BACKUP_NAME)
            if (name in userScopedNames || isLegacy) {
                return BackupInfo(
                    fileId = o.getString("id"),
                    modifiedAtMillis = parseIso(o.optString("modifiedTime", "")),
                    name = name,
                )
            }
        }
        return null
    }

    private suspend fun findLatestBackup(accessToken: String, userId: String?): BackupPair? = withContext(Dispatchers.IO) {
        val candidates = findBackupCandidates(accessToken, userId)
        val nonEmpty = candidates.filter { (it.entryCount ?: 1) > 0 }
        (nonEmpty.ifEmpty { candidates }).maxByOrNull { it.db.modifiedAtMillis }
    }

    private suspend fun findBackupCandidates(accessToken: String, userId: String?): List<BackupPair> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<BackupPair>()
        val scopedBundleName = bundleName(userId)
        val scopedDatabaseName = databaseName(userId)
        val scopedKeyName = keyName(userId)
        val scopedMetaName = metaName(userId)

        val appDataDb = findExisting(accessToken, scopedBundleName, "appDataFolder")
            ?: findExisting(accessToken, scopedDatabaseName, "appDataFolder")
            ?: findExisting(accessToken, BUNDLE_NAME, "appDataFolder")
            ?: findExisting(accessToken, BACKUP_NAME, "appDataFolder")
            ?: findNewestDatabaseLike(accessToken, "appDataFolder", userId)
        if (appDataDb != null) {
            val metadata = findExisting(accessToken, scopedMetaName, "appDataFolder")
                ?: findExisting(accessToken, META_NAME, "appDataFolder")
            candidates += BackupPair(
                db = appDataDb,
                key = findExisting(accessToken, scopedKeyName, "appDataFolder")
                    ?: findExisting(accessToken, KEY_NAME, "appDataFolder"),
                metadata = metadata,
                source = "hidden Drive AppData",
                entryCount = readEntryCount(accessToken, metadata),
                isBundle = appDataDb.name == scopedBundleName || appDataDb.name == BUNDLE_NAME,
            )
        }

        val folderId = findKryptosFolder(accessToken)
        if (folderId != null) {
            val visibleDb = findExistingInFolder(accessToken, scopedBundleName, folderId)
                ?: findExistingInFolder(accessToken, scopedDatabaseName, folderId)
                ?: findExistingInFolder(accessToken, BUNDLE_NAME, folderId)
                ?: findExistingInFolder(accessToken, BACKUP_NAME, folderId)
                ?: findNewestDatabaseLikeInFolder(accessToken, folderId, userId)
            if (visibleDb != null) {
                val metadata = findExistingInFolder(accessToken, scopedMetaName, folderId)
                    ?: findExistingInFolder(accessToken, META_NAME, folderId)
                candidates += BackupPair(
                    db = visibleDb,
                    key = findExistingInFolder(accessToken, scopedKeyName, folderId)
                        ?: findExistingInFolder(accessToken, KEY_NAME, folderId),
                    metadata = metadata,
                    source = "visible KryptosBackups folder",
                    entryCount = readEntryCount(accessToken, metadata),
                    isBundle = visibleDb.name == scopedBundleName || visibleDb.name == BUNDLE_NAME,
                )
            }
        }

        if (candidates.none { it.source.startsWith("visible") }) {
            val visibleDb = findExisting(accessToken, scopedBundleName, "drive")
                ?: findExisting(accessToken, scopedDatabaseName, "drive")
                ?: findExisting(accessToken, BUNDLE_NAME, "drive")
                ?: findExisting(accessToken, BACKUP_NAME, "drive")
                ?: findNewestDatabaseLike(accessToken, "drive", userId)
            if (visibleDb != null) {
                val metadata = findExisting(accessToken, scopedMetaName, "drive")
                    ?: findExisting(accessToken, META_NAME, "drive")
                candidates += BackupPair(
                    db = visibleDb,
                    key = findExisting(accessToken, scopedKeyName, "drive")
                        ?: findExisting(accessToken, KEY_NAME, "drive"),
                    metadata = metadata,
                    source = "visible Drive search",
                    entryCount = readEntryCount(accessToken, metadata),
                    isBundle = visibleDb.name == scopedBundleName || visibleDb.name == BUNDLE_NAME,
                )
            }
        }

        android.util.Log.i(
            "DriveBackup",
            "Found ${candidates.size} backup candidate(s): ${
                candidates.joinToString { "${it.source}(name=${it.db.name}, db=${it.db.fileId}, key=${it.key != null}, meta=${it.metadata != null}, entries=${it.entryCount ?: "unknown"})" }
            }"
        )
        candidates
    }

    private suspend fun readEntryCount(accessToken: String, metadata: BackupInfo?): Int? {
        if (metadata == null) return null
        return try {
            val url = URL("https://www.googleapis.com/drive/v3/files/${metadata.fileId}?alt=media")
            JSONObject(request(accessToken, "GET", url)).optInt("entryCount")
        } catch (e: Exception) {
            null
        }
    }

    suspend fun restore(accessToken: String, userId: String?): Boolean = withContext(Dispatchers.IO) {
        val dbFile = getDbFile(userId)
        android.util.Log.i("DriveBackup", "Restore started for user: $userId")

        val candidates = findBackupCandidates(accessToken, userId)
            .sortedByDescending { it.db.modifiedAtMillis }
        if (candidates.isEmpty()) {
            android.util.Log.e("DriveBackup", "No database backup found anywhere.")
            throw IOException("Drive access worked, but no Kryptos database file was visible. Try Restore again and approve both Drive prompts, or use Back up from the device that still has your entries.")
        }

        var restored: Pair<BackupPair, ByteArray>? = null
        var restoredPassphrase: ByteArray? = null
        for (candidate in candidates) {
            val passphrase = readPassphrase(accessToken, candidate.key, userId)
            android.util.Log.i("DriveBackup", "Checking ${candidate.source} backup modified at ${candidate.db.modifiedAtMillis}.")
            val downloadUrl = URL("https://www.googleapis.com/drive/v3/files/${candidate.db.fileId}?alt=media")
            val dbBytes = requestBytes(accessToken, "GET", downloadUrl)
            if (dbBytes.isEmpty()) continue

            val entryCount = countEntriesInBackup(dbBytes, passphrase, candidate.isBundle)
            android.util.Log.i("DriveBackup", "${candidate.source} backup contains $entryCount entries.")
            if (entryCount > 0) {
                restored = candidate to dbBytes
                restoredPassphrase = passphrase
                break
            }
        }

        val (restoredBackup, dbBytes) = restored
            ?: throw IOException("Found Drive backup files, but they contain no vault entries.")
        restoredPassphrase?.let { KeyManager.setDatabasePassphrase(context, it, userId) }

        dbFile.parentFile?.mkdirs()
        File(dbFile.parentFile, "${dbFile.name}-wal").delete()
        File(dbFile.parentFile, "${dbFile.name}-shm").delete()
        File(dbFile.parentFile, "${dbFile.name}-journal").delete()

        if (restoredBackup.isBundle) {
            restoreBackupBundle(dbBytes, dbFile)
        } else {
            dbFile.writeBytes(dbBytes)
        }
        android.util.Log.i("DriveBackup", "Restore complete from ${restoredBackup.source}. DB size: ${dbBytes.size}")
        
        true
    }

    private suspend fun readPassphrase(accessToken: String, keyEntry: BackupInfo?, userId: String?): ByteArray {
        if (keyEntry == null) {
            android.util.Log.w("DriveBackup", "Restore found database but NO KEY. Attempting with local key.")
            return KeyManager.getDatabasePassphrase(context, userId)
        }
        return try {
            android.util.Log.i("DriveBackup", "Reading key from ${keyEntry.fileId}")
            val url = URL("https://www.googleapis.com/drive/v3/files/${keyEntry.fileId}?alt=media")
            val obj = JSONObject(request(accessToken, "GET", url))
            android.util.Base64.decode(obj.getString("passphrase"), android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("DriveBackup", "Key restoration failed", e)
            throw IOException("Could not restore encryption key. Data is unrecoverable without it.")
        }
    }

    private fun countEntriesInBackup(dbBytes: ByteArray, passphrase: ByteArray, isBundle: Boolean): Int {
        val tempDir = Files.createTempDirectory(context.cacheDir.toPath(), "kryptos-restore-check").toFile()
        val temp = File(tempDir, "check.db")
        return try {
            if (isBundle) restoreBackupBundle(dbBytes, temp) else temp.writeBytes(dbBytes)
            SQLiteDatabase.openDatabase(temp.absolutePath, passphrase, null, SQLiteDatabase.OPEN_READONLY, null, null).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM vault_entries", emptyArray()).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("DriveBackup", "Could not inspect downloaded backup.", e)
            0
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun restoreBackupBundle(bytes: ByteArray, dbFile: File) {
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = when (entry.name) {
                    "database" -> dbFile
                    "database-wal" -> File("${dbFile.path}-wal")
                    "database-shm" -> File("${dbFile.path}-shm")
                    "database-journal" -> File("${dbFile.path}-journal")
                    else -> null
                }
                if (target != null) {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
            }
        }
    }

    private suspend fun request(accessToken: String, method: String, url: URL, body: ByteArray? = null, contentType: String? = null): String {
        return String(requestBytes(accessToken, method, url, body, contentType))
    }

    private suspend fun requestBytes(accessToken: String, method: String, url: URL, body: ByteArray? = null, contentType: String? = null): ByteArray = withContext(Dispatchers.IO) {
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
                
                if (code in 200..299) return@withContext res
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
        private const val BUNDLE_NAME = "kryptos.backup"
        private const val BACKUP_NAME = "kryptos.db"
        private const val KEY_NAME = "kryptos.key"
        private const val META_NAME = "kryptos.meta.json"
        private const val FOLDER_NAME = "KryptosBackups"
        private const val KEY_LAST_BACKUP = "last_backup"
    }
}
