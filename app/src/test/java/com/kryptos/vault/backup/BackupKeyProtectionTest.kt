package com.kryptos.vault.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupKeyProtectionTest {

    @Test
    fun `wrap and unwrap round trip`() {
        val key = ByteArray(32) { it.toByte() }
        val wrapped = BackupKeyProtection.wrap("test-passphrase-123", key)
        assertTrue(BackupKeyProtection.isWrapped(wrapped))
        assertArrayEquals(key, BackupKeyProtection.unwrap("test-passphrase-123", wrapped))
    }

    @Test
    fun `wrong passphrase fails to unwrap`() {
        val wrapped = BackupKeyProtection.wrap("test-passphrase-123", ByteArray(32) { 7 })
        assertNull(BackupKeyProtection.unwrap("wrong-passphrase", wrapped))
    }

    @Test
    fun `raw key is not treated as wrapped`() {
        assertFalse(BackupKeyProtection.isWrapped(ByteArray(32)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `short passphrase is rejected`() {
        BackupKeyProtection.wrap("abc", ByteArray(32))
    }
}
