package com.clipnotes.app.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromContentType(value: ContentType): String {
        return value.name
    }

    @TypeConverter
    fun toContentType(value: String): ContentType {
        return ContentType.valueOf(value)
    }
}
