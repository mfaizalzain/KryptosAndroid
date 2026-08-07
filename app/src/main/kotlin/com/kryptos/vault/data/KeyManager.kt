package com.kryptos.vault.data

import android.content.Context
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
        val prefs = SecurePrefs(context, PREFS)
        val key = keyFor(userId)
        val stored = prefs.getString(key)
        if (stored != null) return android.util.Base64.decode(stored, android.util.Base64.NO_WRAP)

        val legacy = if (userId != null) prefs.getString(KEY) else null
        if (legacy != null) {
            prefs.putString(key, legacy)
            return android.util.Base64.decode(legacy, android.util.Base64.NO_WRAP)
        }

        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.putString(key, android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
        return bytes
    }

    /** Overwrites the local passphrase — only call when restoring a Drive backup. */
    fun setDatabasePassphrase(context: Context, passphrase: ByteArray, userId: String? = null) {
        SecurePrefs(context, PREFS).putString(
            keyFor(userId),
            android.util.Base64.encodeToString(passphrase, android.util.Base64.NO_WRAP),
        )
    }

    fun clearPassphrase(context: Context, userId: String? = null) {
        SecurePrefs(context, PREFS).remove(keyFor(userId))
    }
}
