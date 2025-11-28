package com.clipnotes.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clipnotes.app.data.ContentType
import com.clipnotes.app.data.NoteEntity
import com.clipnotes.app.data.NoteRepository
import com.clipnotes.app.utils.PreferenceManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: NoteRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {
    
    val notes: Flow<List<NoteEntity>> = repository.getAllNotes()
    
    fun insertNote(content: String, contentType: ContentType, audioFilePath: String? = null, audioDuration: Long = 0) {
        viewModelScope.launch {
            val color = when (contentType) {
                ContentType.CLIPBOARD_TEXT -> preferenceManager.clipboardTextColor
                ContentType.USER_INPUT_TEXT, ContentType.AUDIO_RECORDING -> 
                    preferenceManager.userInputTextColor
            }
            
            val note = NoteEntity(
                content = content,
                contentType = contentType,
                textColor = color,
                audioFilePath = audioFilePath,
                audioDuration = audioDuration
            )
            repository.insertNote(note)
        }
    }
    
    fun updateNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.updateNote(note)
        }
    }
    
    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }
    
    fun deleteAllNotes() {
        viewModelScope.launch {
            repository.deleteAllNotes()
        }
    }

    fun markNoteAsRead(note: NoteEntity) {
        viewModelScope.launch {
            repository.markNoteAsRead(note.id)
        }
    }
    
    suspend fun getNotesCount(): Int = repository.getNotesCount()
    
    suspend fun getAllNotesSnapshot(): List<NoteEntity> {
        return repository.getAllNotes().first()
    }
    
    fun receiveNotes(notesJson: String, onSuccess: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            try {
                val jsonArray = org.json.JSONArray(notesJson)
                
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val note = NoteEntity(
                        content = jsonObject.getString("content"),
                        contentType = ContentType.valueOf(jsonObject.getString("contentType")),
                        textColor = jsonObject.getInt("textColor")
                    )
                    repository.insertNote(note)
                }
                
                onSuccess()
            } catch (e: Exception) {
                onError()
            }
        }
    }
}

class MainViewModelFactory(
    private val repository: NoteRepository,
    private val preferenceManager: PreferenceManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, preferenceManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
