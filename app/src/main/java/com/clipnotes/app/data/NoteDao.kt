package com.clipnotes.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY timestamp ASC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isRead = 1 ORDER BY lastReadTime DESC LIMIT 1")
    suspend fun getLastReadNote(): NoteEntity?
    
    @Query("SELECT COUNT(*) FROM notes WHERE isRead = 1")
    suspend fun getReadNotesCount(): Int

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: Long): NoteEntity?

    @Insert
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun getNotesCount(): Int
    
    @Query("SELECT * FROM notes WHERE contentType != 'AUDIO_RECORDING' ORDER BY timestamp ASC")
    suspend fun getAllTextNotes(): List<NoteEntity>
    
    @Query("DELETE FROM notes WHERE contentType != 'AUDIO_RECORDING'")
    suspend fun deleteAllTextNotes()
}
