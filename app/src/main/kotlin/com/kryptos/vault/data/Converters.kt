package com.kryptos.vault.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun fromTemplate(value: Template): String = value.name
    @TypeConverter fun toTemplate(value: String): Template {
        return runCatching { Template.valueOf(value) }.getOrElse {
            // Handle legacy names or missing values
            when (value) {
                "CREDIT_CARD" -> Template.PAYMENT_CARD
                else -> Template.NOTE // Safe fallback
            }
        }
    }
}
