package com.kryptos.vault.backup

import android.content.Context
import com.kryptos.vault.data.KeyManager
import com.kryptos.vault.data.SecurePrefs
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
    private data class BackupPair(
        val db: BackupInfo,
        val key: BackupInfo?,
        val metadata: BackupInfo?,
        val source: String,
        val entryCount: Int?,
        val isBundle: Boolean,
    )


    private val prefs by lazy {
        SecurePrefs(context, "kryptos_backup_prefs")
    }


    fun getLastBackupAtMillis(userId: String?): Long {
        val key = if (userId == null) KEY_LAST_BACKUP else "${KEY_LAST_BACKUP}_$userId"
        return prefs.getLong(key, 0L)
    }

    private fun setLastBackupAtMillis(userId: String?, value: Long) {
        val key = if (userId == null) KEY_LAST_BACKUP else "${KEY_LAST_BACKUP}_$userId"
        prefs.putLong(key, value)
    }

    // MARK: - Backup passphrase

    private fun passphrasePrefKey(userId: String?): String {
        val suffix = userId?.replace(Regex("[^a-zA-Z0-9]"), "_") ?: ""
        return "backup_passphrase$suffix"
    }

    fun setBackupPassphrase(userId: String?, passphrase: String) {
        require(passphrase.length >= BackupKeyProtection.MIN_PASSPHRASE_LENGTH) {
            "Backup passphrase must be at least ${BackupKeyProtection.MIN_PASSPHRASE_LENGTH} characters."
        }
        prefs.putString(passphrasePrefKey(userId), passphrase)
    }

    fun removeBackupPassphrase(userId: String?) {
        prefs.remove(passphrasePrefKey(userId))
    }

    fun hasBackupPassphrase(userId: String?): Boolean =
        !prefs.getString(passphrasePrefKey(userId)).isNullOrEmpty()

    private fun backupPassphrase(userId: String?): String? =
        prefs.getString(passphrasePrefKey(userId))

    private fun buildKeyPayload(userId: String?): Pair<ByteArray, String> {
        val passphrase = KeyManager.getDatabasePassphrase(context, userId)
        val wrapPassphrase = backupPassphrase(userId)
        return if (wrapPassphrase != null) {
            BackupKeyProtection.wrap(wrapPassphrase, passphrase) to "application/octet-stream"
        } else {
            val keyJson = JSONObject().apply {
                put("v", 1)
                put("userId", userId ?: JSONObject.NULL)
                put("passphrase", android.util.Base64.encodeToString(passphrase, android.util.Base64.NO_WRAP))
            }.toString().toByteArray()
            keyJson to "application/json"
        }
    }

    private fun getDbFile(userId: String?): File {
        val sanitizedId = userId?.replace(Regex("[^a-zA-Z0-9]"), "_")
        val dbName = if (sanitizedId == null) "kryptos.db" else "kryptos_$sanitizedId.db"
        return context.getDatabasePath(dbName)
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
            throw IOException("No vault file found to back up. Add an entry first.")
        }

        val backupBytes = createBackupBundle(dbFile)
        val backupName = bundleName(userId)
        val existing = DriveApiClient.findExisting(accessToken, backupName)
        val fileId = if (existing != null) {
            DriveApiClient.updateBytes(accessToken, existing.fileId, backupBytes, "application/zip")
            existing.fileId
        } else {
            DriveApiClient.createBytes(accessToken, backupName, backupBytes, "application/zip", "appDataFolder")
        }

        // Key backup
        val (keyPayload, keyMime) = buildKeyPayload(userId)

        val scopedKeyName = keyName(userId)
        val keyExisting = DriveApiClient.findExisting(accessToken, scopedKeyName)
        if (keyExisting != null) {
            DriveApiClient.updateBytes(accessToken, keyExisting.fileId, keyPayload, keyMime)
        } else {
            DriveApiClient.createBytes(accessToken, scopedKeyName, keyPayload, keyMime, "appDataFolder")
        }
        uploadMetadata(accessToken, userId, entryCount, "appDataFolder")

        setLastBackupAtMillis(userId, System.currentTimeMillis())
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

        val folderId = DriveApiClient.getOrCreateKryptosFolder(accessToken)
        
        val backupBytes = createBackupBundle(dbFile)
        val backupName = bundleName(userId)
        val query = "name='$backupName' and '$folderId' in parents and trashed=false"
        val url = URL("https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id)")
        val response = DriveApiClient.request(accessToken, "GET", url)
        val arr = JSONObject(response).optJSONArray("files")
        val existingId = if (arr != null && arr.length() > 0) arr.getJSONObject(0).getString("id") else null

        val fileId = if (existingId != null) {
            DriveApiClient.updateBytes(accessToken, existingId, backupBytes, "application/zip")
            existingId
        } else {
            DriveApiClient.createBytes(accessToken, backupName, backupBytes, "application/zip", folderId)
        }

        // Key backup (Crucial for Pro users to restore on fresh install!)
        val (keyPayload, keyMime) = buildKeyPayload(userId)

        val scopedKeyName = keyName(userId)
        val keyQuery = "name='$scopedKeyName' and '$folderId' in parents and trashed=false"
        val keyUrl = URL("https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(keyQuery, "UTF-8")}&fields=files(id)")
        val keyResp = DriveApiClient.request(accessToken, "GET", keyUrl)
        val keyArr = JSONObject(keyResp).optJSONArray("files")
        val existingKeyId = if (keyArr != null && keyArr.length() > 0) keyArr.getJSONObject(0).getString("id") else null

        if (existingKeyId != null) {
            DriveApiClient.updateBytes(accessToken, existingKeyId, keyPayload, keyMime)
        } else {
            DriveApiClient.createBytes(accessToken, scopedKeyName, keyPayload, keyMime, folderId)
        }
        uploadMetadata(accessToken, userId, entryCount, folderId)

        setLastBackupAtMillis(userId, System.currentTimeMillis())
        fileId
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
            DriveApiClient.findExisting(accessToken, scopedMetaName, "appDataFolder")
        } else {
            DriveApiClient.findExistingInFolder(accessToken, scopedMetaName, parent)
        }

        if (existing != null) {
            DriveApiClient.updateBytes(accessToken, existing.fileId, metadataJson, "application/json")
        } else {
            DriveApiClient.createBytes(accessToken, scopedMetaName, metadataJson, "application/json", parent)
        }
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

        val appDataDb = DriveApiClient.findExisting(accessToken, scopedBundleName, "appDataFolder")
            ?: DriveApiClient.findExisting(accessToken, scopedDatabaseName, "appDataFolder")
            ?: DriveApiClient.findExisting(accessToken, BUNDLE_NAME, "appDataFolder")
            ?: DriveApiClient.findExisting(accessToken, BACKUP_NAME, "appDataFolder")
            ?: DriveApiClient.findNewestDatabaseLike(accessToken, "appDataFolder", userId)
        if (appDataDb != null) {
            val metadata = DriveApiClient.findExisting(accessToken, scopedMetaName, "appDataFolder")
                ?: DriveApiClient.findExisting(accessToken, META_NAME, "appDataFolder")
            candidates += BackupPair(
                db = appDataDb,
                key = DriveApiClient.findExisting(accessToken, scopedKeyName, "appDataFolder")
                    ?: DriveApiClient.findExisting(accessToken, KEY_NAME, "appDataFolder"),
                metadata = metadata,
                source = "hidden Drive AppData",
                entryCount = DriveApiClient.readEntryCount(accessToken, metadata),
                isBundle = appDataDb.name == scopedBundleName || appDataDb.name == BUNDLE_NAME,
            )
        }

        val folderId = DriveApiClient.findKryptosFolder(accessToken)
        if (folderId != null) {
            val visibleDb = DriveApiClient.findExistingInFolder(accessToken, scopedBundleName, folderId)
                ?: DriveApiClient.findExistingInFolder(accessToken, scopedDatabaseName, folderId)
                ?: DriveApiClient.findExistingInFolder(accessToken, BUNDLE_NAME, folderId)
                ?: DriveApiClient.findExistingInFolder(accessToken, BACKUP_NAME, folderId)
                ?: DriveApiClient.findNewestDatabaseLikeInFolder(accessToken, folderId, userId)
            if (visibleDb != null) {
                val metadata = DriveApiClient.findExistingInFolder(accessToken, scopedMetaName, folderId)
                    ?: DriveApiClient.findExistingInFolder(accessToken, META_NAME, folderId)
                candidates += BackupPair(
                    db = visibleDb,
                    key = DriveApiClient.findExistingInFolder(accessToken, scopedKeyName, folderId)
                        ?: DriveApiClient.findExistingInFolder(accessToken, KEY_NAME, folderId),
                    metadata = metadata,
                    source = "visible KryptosBackups folder",
                    entryCount = DriveApiClient.readEntryCount(accessToken, metadata),
                    isBundle = visibleDb.name == scopedBundleName || visibleDb.name == BUNDLE_NAME,
                )
            }
        }

        if (candidates.none { it.source.startsWith("visible") }) {
            val visibleDb = DriveApiClient.findExisting(accessToken, scopedBundleName, "drive")
                ?: DriveApiClient.findExisting(accessToken, scopedDatabaseName, "drive")
                ?: DriveApiClient.findExisting(accessToken, BUNDLE_NAME, "drive")
                ?: DriveApiClient.findExisting(accessToken, BACKUP_NAME, "drive")
                ?: DriveApiClient.findNewestDatabaseLike(accessToken, "drive", userId)
            if (visibleDb != null) {
                val metadata = DriveApiClient.findExisting(accessToken, scopedMetaName, "drive")
                    ?: DriveApiClient.findExisting(accessToken, META_NAME, "drive")
                candidates += BackupPair(
                    db = visibleDb,
                    key = DriveApiClient.findExisting(accessToken, scopedKeyName, "drive")
                        ?: DriveApiClient.findExisting(accessToken, KEY_NAME, "drive"),
                    metadata = metadata,
                    source = "visible Drive search",
                    entryCount = DriveApiClient.readEntryCount(accessToken, metadata),
                    isBundle = visibleDb.name == scopedBundleName || visibleDb.name == BUNDLE_NAME,
                )
            }
        }

        candidates
    }

    suspend fun restore(accessToken: String, userId: String?, providedPassphrase: String? = null): Boolean = withContext(Dispatchers.IO) {
        val dbFile = getDbFile(userId)

        val candidates = findBackupCandidates(accessToken, userId)
            .sortedByDescending { it.db.modifiedAtMillis }
        if (candidates.isEmpty()) {
            throw IOException("Drive access worked, but no Kryptos database file was visible. Try Restore again and approve both Drive prompts, or use Back up from the device that still has your entries.")
        }

        var restored: Pair<BackupPair, ByteArray>? = null
        var restoredPassphrase: ByteArray? = null
        for (candidate in candidates) {
            val passphrase = readPassphrase(accessToken, candidate.key, userId, providedPassphrase)
            val downloadUrl = URL("https://www.googleapis.com/drive/v3/files/${candidate.db.fileId}?alt=media")
            val dbBytes = DriveApiClient.requestBytes(accessToken, "GET", downloadUrl)
            if (dbBytes.isEmpty()) continue

            val entryCount = countEntriesInBackup(dbBytes, passphrase, candidate.isBundle)
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
        
        true
    }

    private suspend fun readPassphrase(accessToken: String, keyEntry: BackupInfo?, userId: String?, providedPassphrase: String?): ByteArray {
        if (keyEntry == null) {
            return KeyManager.getDatabasePassphrase(context, userId)
        }
        return try {
            val url = URL("https://www.googleapis.com/drive/v3/files/${keyEntry.fileId}?alt=media")
            val data = DriveApiClient.requestBytes(accessToken, "GET", url)
            if (BackupKeyProtection.isWrapped(data)) {
                backupPassphrase(userId)?.let { local ->
                    BackupKeyProtection.unwrap(local, data)?.let { return it }
                }
                providedPassphrase?.takeIf { it.isNotEmpty() }?.let { entered ->
                    BackupKeyProtection.unwrap(entered, data)?.let { return it }
                }
                throw if (providedPassphrase != null) {
                    BackupPassphraseIncorrectException()
                } else {
                    BackupPassphraseRequiredException()
                }
            }
            val obj = JSONObject(String(data, Charsets.UTF_8))
            android.util.Base64.decode(obj.getString("passphrase"), android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            if (e is BackupPassphraseRequiredException || e is BackupPassphraseIncorrectException) throw e
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
        } catch (_: Exception) {
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

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val KEY_LAST_BACKUP = "last_backup"
    }
}
