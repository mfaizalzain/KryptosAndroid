package com.kryptos.vault.ui.cards

import org.junit.Assert.assertEquals
import org.junit.Test

class CardMaskingTest {

    @Test
    fun `maskCardNumber masks all but last four digits`() {
        assertEquals("•••• •••• •••• 1234", maskCardNumber("4111 1111 1111 1234"))
    }

    @Test
    fun `maskCardNumber strips non-digit separators`() {
        assertEquals("•••• •••• •••• 5678", maskCardNumber("4111-1111-1111-5678"))
        assertEquals("•••• •••• •••• 5678", maskCardNumber("4111.1111.1111.5678"))
    }

    @Test
    fun `maskCardNumber handles fewer than four digits`() {
        // "12" -> last4="12" -> padStart(4, '•') = "••12"
        assertEquals("•••• •••• •••• ••12", maskCardNumber("12"))
    }

    @Test
    fun `maskCardNumber handles empty input`() {
        // "" -> last4="" -> padStart(4, '•') = "••••"
        assertEquals("•••• •••• •••• ••••", maskCardNumber(""))
    }
}
