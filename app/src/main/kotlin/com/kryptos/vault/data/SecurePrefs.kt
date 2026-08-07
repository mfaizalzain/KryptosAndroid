@file:Suppress("DEPRECATION") // Legacy bridge for the deprecated EncryptedSharedPreferences stack.

package com.kryptos.vault.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * SharedPreferences wrapper that encrypts string values with an Android Keystore
 * AES-GCM key. Replaces the deprecated [EncryptedSharedPreferences]/[MasterKey]
 * stack with a small, explicit Keystore-backed implementation.
 *
 * Values are stored in `{baseName}_v2`; on first access any legacy
 * EncryptedSharedPreferences file (`{baseName}`) is migrated, verified, and deleted.
 */
class SecurePrefs(context: Context, baseName: String) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("${baseName}_v2", Context.MODE_PRIVATE)
    private val keyAlias = "_kryptos_secure_${baseName}"
    private val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    private val cipher = Cipher.getInstance("AES/GCM/NoPadding")

    init {
        ensureKey()
        migrateLegacy(baseName)
    }

    fun getString(key: String, defaultValue: String? = null): String? {
        val stored = prefs.getString(key, null) ?: return defaultValue
        return decrypt(stored) ?: defaultValue
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        prefs.getBoolean(key, defaultValue)

    fun getLong(key: String, defaultValue: Long = 0L): Long =
        prefs.getLong(key, defaultValue)

    fun getInt(key: String, defaultValue: Int = 0): Int =
        prefs.getInt(key, defaultValue)

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, encrypt(value)).commit()
    }

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).commit()
    }

    fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).commit()
    }

    fun remove(key: String) {
        prefs.edit().remove(key).commit()
    }

    fun clear() {
        prefs.edit().clear().commit()
    }

    private fun ensureKey() {
        if (keyStore.containsAlias(keyAlias)) return
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        generator.generateKey()
    }

    private fun secretKey(): SecretKey =
        (keyStore.getKey(keyAlias, null) as SecretKey)

    private fun encrypt(value: String): String {
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? {
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_SIZE)
            val ciphertext = bytes.copyOfRange(IV_SIZE, bytes.size)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun migrateLegacy(baseName: String) {
        if (prefs.getBoolean(MIGRATED_FLAG, false)) return
        val legacyFile = File(appContext.dataDir, "shared_prefs/$baseName.xml")
        if (!legacyFile.exists()) return

        val legacy = runCatching {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                baseName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrNull() ?: return

        val all = legacy.all
        if (all.isEmpty()) {
            appContext.deleteSharedPreferences(baseName)
            return
        }

        val editor = prefs.edit()
        all.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, encrypt(value))
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        editor.putBoolean(MIGRATED_FLAG, true).commit()

        val verified = all.entries.all { (key, value) ->
            when (value) {
                is String -> decrypt(prefs.getString(key, null) ?: return@all false) == value
                is Boolean -> prefs.getBoolean(key, !value) == value
                is Int -> prefs.getInt(key, value + 1) == value
                is Long -> prefs.getLong(key, value + 1L) == value
                is Float -> prefs.getFloat(key, value + 1f) == value
                else -> true
            }
        }
        if (verified) {
            appContext.deleteSharedPreferences(baseName)
        }
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val IV_SIZE = 12
        const val MIGRATED_FLAG = "__kryptos_migrated_v2__"
    }
}
