package com.kryptos.vault.backup

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupPassphraseRequiredException :
    IOException("This backup is protected by a passphrase. Enter your backup passphrase to restore.")

class BackupPassphraseIncorrectException :
    IOException("The backup passphrase is incorrect.")

/**
 * Wraps/unwraps the SQLCipher passphrase with a user recovery passphrase so the cloud
 * copy of the key is useless without it.
 *
 * Envelope format: `[ "KRY2" ][ salt(16) ][ iv(12) ][ AES/GCM ciphertext ]`,
 * where the AES key is PBKDF2-HMAC-SHA256-derived from the passphrase.
 * Legacy backups (plain JSON key files) are detected by the missing magic prefix.
 */
object BackupKeyProtection {
    private const val MAGIC = "KRY2"
    private const val ITERATIONS = 200_000
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val KEY_SIZE_BITS = 256
    const val MIN_PASSPHRASE_LENGTH = 6

    fun isWrapped(data: ByteArray): Boolean =
        data.size > MAGIC.length + SALT_SIZE + IV_SIZE &&
            data.copyOfRange(0, MAGIC.length).toString(Charsets.US_ASCII) == MAGIC

    fun wrap(passphrase: String, key: ByteArray): ByteArray {
        require(passphrase.length >= MIN_PASSPHRASE_LENGTH) {
            "Backup passphrase must be at least $MIN_PASSPHRASE_LENGTH characters."
        }
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val derived = derive(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(key)
        return ByteArrayOutputStream().apply {
            write(MAGIC.toByteArray(Charsets.US_ASCII))
            write(salt)
            write(iv)
            write(encrypted)
        }.toByteArray()
    }

    fun unwrap(passphrase: String, data: ByteArray): ByteArray? {
        if (!isWrapped(data)) return null
        var offset = MAGIC.length
        val salt = data.copyOfRange(offset, offset + SALT_SIZE)
        offset += SALT_SIZE
        val iv = data.copyOfRange(offset, offset + IV_SIZE)
        offset += IV_SIZE
        val encrypted = data.copyOfRange(offset, data.size)
        return try {
            val derived = derive(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted)
        } catch (_: Exception) {
            null
        }
    }

    private fun derive(passphrase: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_SIZE_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
