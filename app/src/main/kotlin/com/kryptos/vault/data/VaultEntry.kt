package com.kryptos.vault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Template {
    ID_CARD,
    PASSPORT,
    DRIVERS_LICENSE,
    BIRTH_CERTIFICATE,
    PAYMENT_CARD,
    BANK_ACCOUNT,
    TAX_NUMBER,
    API_KEY,
    NOTE,
}

@Entity(tableName = "vault_entries")
data class VaultEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val template: Template,
    val title: String,
    /** JSON list of {k,v} field pairs. Stored encrypted at the DB level via SQLCipher. */
    val fieldsJson: String,
    /** Optional encrypted attachment (e.g. scanned document image). */
    val attachment: ByteArray? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
