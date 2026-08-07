package com.kryptos.vault.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    @Test
    fun `password has requested length and all character sets`() {
        repeat(50) {
            val password = PasswordGenerator.password()
            assertEquals(20, password.length)
            assertTrue(password.any { it.isLowerCase() })
            assertTrue(password.any { it.isUpperCase() })
            assertTrue(password.any { it.isDigit() })
            assertTrue(password.any { "!@#\$%^&*()-_=+[]{};:,.<>?/~".contains(it) })
            assertFalse(password.any { "Il1O0o".contains(it) })
        }
    }

    @Test
    fun `api key is hex with correct length`() {
        val key = PasswordGenerator.apiKey()
        assertEquals(48, key.length)
        assertTrue(key.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })
        assertNotEquals(key, PasswordGenerator.apiKey())
    }

    @Test
    fun `pin contains only digits`() {
        val pin = PasswordGenerator.pin()
        assertEquals(6, pin.length)
        assertTrue(pin.all { it.isDigit() })
    }
}
