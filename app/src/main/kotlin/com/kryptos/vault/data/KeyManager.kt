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

    fun getDatabasePassphrase(context: Context): ByteArray {
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
        val stored = prefs.getString(KEY, null)
        if (stored != null) return android.util.Base64.decode(stored, android.util.Base64.NO_WRAP)

        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY, android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
            .apply()
        return bytes
    }

    /** Overwrites the local passphrase — only call when restoring a Drive backup. */
    fun setDatabasePassphrase(context: Context, passphrase: ByteArray) {
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
            .putString(KEY, android.util.Base64.encodeToString(passphrase, android.util.Base64.NO_WRAP))
            .apply()
    }
}
