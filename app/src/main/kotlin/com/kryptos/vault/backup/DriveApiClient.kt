package com.kryptos.vault.backup

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val BUNDLE_NAME = "kryptos.backup"
internal const val BACKUP_NAME = "kryptos.db"
internal const val KEY_NAME = "kryptos.key"
internal const val META_NAME = "kryptos.meta.json"
internal const val FOLDER_NAME = "KryptosBackups"

data class BackupInfo(val fileId: String, val modifiedAtMillis: Long, val name: String = BACKUP_NAME)

internal fun userSuffix(userId: String?): String? =
    userId?.takeIf { it.isNotBlank() }?.let { "_${it.replace(Regex("[^a-zA-Z0-9]"), "_")}" }

internal fun bundleName(userId: String?) = userSuffix(userId)?.let { "kryptos$it.backup" } ?: BUNDLE_NAME
internal fun databaseName(userId: String?) = userSuffix(userId)?.let { "kryptos$it.db" } ?: BACKUP_NAME
internal fun keyName(userId: String?) = userSuffix(userId)?.let { "kryptos$it.key" } ?: KEY_NAME
internal fun metaName(userId: String?) = userSuffix(userId)?.let { "kryptos$it.meta.json" } ?: META_NAME

/// Low-level Google Drive REST client (files list/upload/update/folder/download).
object DriveApiClient {
    suspend fun findExisting(accessToken: String, name: String = BACKUP_NAME, space: String = "appDataFolder"): BackupInfo? = withContext(Dispatchers.IO) {
        val query = "name='$name' and trashed=false"
        val url = URL("https://www.googleapis.com/drive/v3/files?spaces=$space&q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id,name,modifiedTime)&orderBy=modifiedTime desc")

        val response = try { request(accessToken, "GET", url) } catch (e: Exception) { return@withContext null }
        val arr = JSONObject(response).optJSONArray("files") ?: return@withContext null
        if (arr.length() == 0) null else {
            val o = arr.getJSONObject(0)
            BackupInfo(o.getString("id"), parseIso(o.optString("modifiedTime", "")), o.optString("name", name))
        }
    }


    suspend fun findKryptosFolder(accessToken: String): String? {
        val query = "name='$FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false"
        val url = URL("https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id)")
        val response = try { request(accessToken, "GET", url) } catch (e: Exception) { return null }
        val arr = JSONObject(response).optJSONArray("files")
        return if (arr != null && arr.length() > 0) arr.getJSONObject(0).getString("id") else null
    }

    suspend fun getOrCreateKryptosFolder(accessToken: String): String {
        findKryptosFolder(accessToken)?.let { return it }

        val metadata = JSONObject().apply {
            put("name", FOLDER_NAME)
            put("mimeType", "application/vnd.google-apps.folder")
        }.toString().toByteArray()
        val createUrl = URL("https://www.googleapis.com/drive/v3/files")
        return JSONObject(request(accessToken, "POST", createUrl, metadata, "application/json")).getString("id")
    }

    suspend fun createBytes(accessToken: String, name: String, bytes: ByteArray, mime: String, parent: String): String {
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

    suspend fun updateBytes(accessToken: String, fileId: String, bytes: ByteArray, mime: String) {
        val url = URL("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
        request(accessToken, "PATCH", url, bytes, mime)
    }

    suspend fun findExistingInFolder(accessToken: String, name: String, folderId: String): BackupInfo? = withContext(Dispatchers.IO) {
        val query = "name='$name' and '$folderId' in parents and trashed=false"
        val url = URL("https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id,name,modifiedTime)&orderBy=modifiedTime desc")
        val response = try { request(accessToken, "GET", url) } catch (e: Exception) { return@withContext null }
        val arr = JSONObject(response).optJSONArray("files") ?: return@withContext null
        if (arr.length() == 0) null else {
            val o = arr.getJSONObject(0)
            BackupInfo(o.getString("id"), parseIso(o.optString("modifiedTime", "")), o.optString("name", name))
        }
    }

    suspend fun findNewestDatabaseLike(accessToken: String, space: String, userId: String?): BackupInfo? = withContext(Dispatchers.IO) {
        val query = "trashed=false and name contains 'kryptos'"
        val url = URL("https://www.googleapis.com/drive/v3/files?spaces=$space&q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id,name,modifiedTime,mimeType)&orderBy=modifiedTime desc&pageSize=20")
        val response = try { request(accessToken, "GET", url) } catch (e: Exception) { return@withContext null }
        val arr = JSONObject(response).optJSONArray("files") ?: return@withContext null
        newestDatabaseFrom(arr, userId)
    }

    suspend fun findNewestDatabaseLikeInFolder(accessToken: String, folderId: String, userId: String?): BackupInfo? = withContext(Dispatchers.IO) {
        val query = "trashed=false and '$folderId' in parents and name contains 'kryptos'"
        val url = URL("https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id,name,modifiedTime,mimeType)&orderBy=modifiedTime desc&pageSize=20")
        val response = try { request(accessToken, "GET", url) } catch (e: Exception) { return@withContext null }
        val arr = JSONObject(response).optJSONArray("files") ?: return@withContext null
        newestDatabaseFrom(arr, userId)
    }

    fun newestDatabaseFrom(arr: org.json.JSONArray, userId: String?): BackupInfo? {
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

    suspend fun readEntryCount(accessToken: String, metadata: BackupInfo?): Int? {
        if (metadata == null) return null
        return try {
            val url = URL("https://www.googleapis.com/drive/v3/files/${metadata.fileId}?alt=media")
            JSONObject(request(accessToken, "GET", url)).optInt("entryCount")
        } catch (e: Exception) {
            null
        }
    }

    suspend fun request(accessToken: String, method: String, url: URL, body: ByteArray? = null, contentType: String? = null): String {
        return String(requestBytes(accessToken, method, url, body, contentType))
    }

    suspend fun requestBytes(accessToken: String, method: String, url: URL, body: ByteArray? = null, contentType: String? = null): ByteArray = withContext(Dispatchers.IO) {
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
                kotlinx.coroutines.delay(2000L * (attempt + 1))
            }
        }
        throw lastErr ?: IOException("Network error")
    }

    fun parseIso(s: String): Long = runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrDefault(0L)
}
