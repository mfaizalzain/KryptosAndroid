package com.kryptos.vault.security

import java.security.SecureRandom

/**
 * Cryptographically secure random password / API key / PIN generation.
 */
object PasswordGenerator {
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#\$%^&*()-_=+[]{};:,.<>?/~"
    private val ambiguous = "Il1O0o".toSet()
    private val random = SecureRandom()

    fun password(length: Int = 20, includeSymbols: Boolean = true, excludeAmbiguous: Boolean = true): String {
        require(length > 0) { "Password length must be positive" }
        val sets = mutableListOf(LOWERCASE, UPPERCASE, DIGITS)
        if (includeSymbols) sets += SYMBOLS
        val pool = sets.joinToString("").let { all ->
            if (excludeAmbiguous) all.filter { it !in ambiguous } else all
        }
        require(pool.isNotEmpty()) { "No characters available" }

        val chars = mutableListOf<Char>()
        // Guarantee at least one character from each requested set.
        for (set in sets) {
            val eligible = if (excludeAmbiguous) set.filter { it !in ambiguous } else set
            if (eligible.isNotEmpty()) chars += eligible[random.nextInt(eligible.length)]
        }
        while (chars.size < length) {
            chars += pool[random.nextInt(pool.length)]
        }
        chars.shuffle(random)
        return chars.take(length).joinToString("")
    }

    fun apiKey(length: Int = 48): String {
        require(length > 0) { "API key length must be positive" }
        val bytes = ByteArray((length + 1) / 2).also { random.nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }.take(length)
    }

    fun pin(length: Int = 6): String {
        require(length > 0) { "PIN length must be positive" }
        return (1..length).joinToString("") { DIGITS[random.nextInt(DIGITS.length)].toString() }
    }
}
