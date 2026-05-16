package com.kryptos.vault.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Generates and persists the 256-bit SQLCipher passphrase, wrapped via Android Keystore
 * (through Jetpack EncryptedSharedPreferences). The passphrase never leaves the device.
 */
object KeyManager {
    private const val PREFS = "kryptos_secure_prefs"
    private const val KEY = "db_passphrase"

    private fun keyFor(userId: String?): String {
        val sanitizedId = userId?.replace(Regex("[^a-zA-Z0-9]"), "_")
        return if (sanitizedId == null) KEY else "${KEY}_$sanitizedId"
    }

    fun getDatabasePassphrase(context: Context, userId: String? = null): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        val key = keyFor(userId)
        val stored = prefs.getString(key, null)
        if (stored != null) return android.util.Base64.decode(stored, android.util.Base64.NO_WRAP)

        val legacy = if (userId != null) prefs.getString(KEY, null) else null
        if (legacy != null) {
            prefs.edit().putString(key, legacy).apply()
            return android.util.Base64.decode(legacy, android.util.Base64.NO_WRAP)
        }

        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(key, android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
            .apply()
        return bytes
    }

    /** Overwrites the local passphrase — only call when restoring a Drive backup. */
    fun setDatabasePassphrase(context: Context, passphrase: ByteArray, userId: String? = null) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        prefs.edit()
            .putString(keyFor(userId), android.util.Base64.encodeToString(passphrase, android.util.Base64.NO_WRAP))
            .commit()
    }

    fun clearPassphrase(context: Context, userId: String? = null) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        prefs.edit().remove(keyFor(userId)).commit()
    }
}
