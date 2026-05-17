package com.kryptos.vault.data

fun Template.shareId(): String = when (this) {
    Template.ID_CARD -> "idCard"
    Template.PASSPORT -> "passport"
    Template.DRIVERS_LICENSE -> "driversLicense"
    Template.BIRTH_CERTIFICATE -> "birthCertificate"
    Template.PAYMENT_CARD -> "paymentCard"
    Template.BANK_ACCOUNT -> "bankAccount"
    Template.TAX_NUMBER -> "taxNumber"
    Template.API_KEY -> "apiKey"
    Template.NOTE -> "note"
    Template.QR_CODE -> "qrCode"
}

fun templateFromShareId(value: String): Template? {
    return when (value) {
        "idCard", "ID_CARD" -> Template.ID_CARD
        "passport", "PASSPORT" -> Template.PASSPORT
        "driversLicense", "DRIVERS_LICENSE" -> Template.DRIVERS_LICENSE
        "birthCertificate", "BIRTH_CERTIFICATE" -> Template.BIRTH_CERTIFICATE
        "paymentCard", "PAYMENT_CARD" -> Template.PAYMENT_CARD
        "bankAccount", "BANK_ACCOUNT" -> Template.BANK_ACCOUNT
        "taxNumber", "TAX_NUMBER" -> Template.TAX_NUMBER
        "apiKey", "API_KEY" -> Template.API_KEY
        "note", "NOTE" -> Template.NOTE
        "qrCode", "QR_CODE" -> Template.QR_CODE
        else -> null
    }
}
