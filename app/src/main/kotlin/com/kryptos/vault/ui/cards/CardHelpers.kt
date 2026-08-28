package com.kryptos.vault.ui.cards
import androidx.compose.material.icons.filled.Key
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.kryptos.vault.data.Template

internal fun compactFallbackTitle(template: Template): String = when (template) {
    Template.ID_CARD -> "ID Card"
    Template.PASSPORT -> "Passport"
    Template.DRIVERS_LICENSE -> "License"
    Template.BIRTH_CERTIFICATE -> "Certificate"
    Template.PAYMENT_CARD -> "Payment Card"
    Template.BANK_ACCOUNT -> "Bank Account"
    Template.TAX_NUMBER -> "Tax Number"
    Template.API_KEY -> "API Key"
    Template.NOTE -> "Secure Note"
    Template.QR_CODE -> "QR Code"
}

internal fun heroContentColor(template: Template): Color =
    if (template == Template.NOTE) Color(0xFF2B230A) else Color.White

internal fun primaryFieldsFor(template: Template): List<String> = when (template) {
    Template.ID_CARD -> listOf("ID number", "Full name", "Date of birth")
    Template.PASSPORT -> listOf("Passport number", "Surname", "Given names")
    Template.DRIVERS_LICENSE -> listOf("License number", "Full name", "Expiry")
    Template.BIRTH_CERTIFICATE -> listOf("Full name", "Date of birth", "Registration number")
    Template.PAYMENT_CARD -> listOf("Number", "Card number", "Cardholder", "Expiry")
    Template.BANK_ACCOUNT -> listOf("Bank", "Account holder", "Account number")
    Template.TAX_NUMBER -> listOf("Tax number", "Full name", "Country")
    Template.API_KEY -> listOf("Service", "Environment", "Key")
    Template.NOTE -> listOf("Content")
    Template.QR_CODE -> listOf("Data", "Content")
}
