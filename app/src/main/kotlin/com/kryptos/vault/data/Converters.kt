package com.kryptos.vault.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun fromTemplate(value: Template): String = value.name
    @TypeConverter fun toTemplate(value: String): Template = Template.valueOf(value)
}
