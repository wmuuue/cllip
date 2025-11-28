package com.clipnotes.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val contentType: ContentType,
    val textColor: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val audioFilePath: String? = null,
    val audioDuration: Long = 0,
    val isRead: Boolean = false,
    val lastReadTime: Long = 0
)

enum class ContentType {
    CLIPBOARD_TEXT,
    USER_INPUT_TEXT,
    AUDIO_RECORDING
}
