package com.clipnotes.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.clipnotes.app.NoteApplication
import com.clipnotes.app.data.ContentType
import com.clipnotes.app.data.NoteEntity
import kotlinx.coroutines.*

class ClipboardAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var clipboardManager: ClipboardManager? = null
    private var lastClipboardText: String? = null
    private val recentlySavedContents = LinkedHashSet<String>()
    private val MAX_SAVED_CACHE = 50

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    override fun onCreate() {
        super.onCreate()
        try {
            clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        } catch (e: Exception) {
        }
        
        startMonitoring()
    }

    private fun startMonitoring() {
        scope.launch {
            while (isActive) {
                try {
                    checkClipboardContent()
                    delay(500)
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun checkClipboardContent() {
        try {
            val app = NoteApplication.instance
            if (!app.preferenceManager.isClipboardMonitoringEnabled) {
                return
            }
            
            val clip = clipboardManager?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val clipText = clip.getItemAt(0).coerceToText(this@ClipboardAccessibilityService).toString()
                
                if (clipText.isNotEmpty() && clipText != lastClipboardText && 
                    !recentlySavedContents.contains(clipText)) {
                    
                    lastClipboardText = clipText
                    recentlySavedContents.add(clipText)
                    
                    if (recentlySavedContents.size > MAX_SAVED_CACHE) {
                        recentlySavedContents.remove(recentlySavedContents.first())
                    }
                    
                    saveToDatabase(clipText)
                }
            }
        } catch (e: Exception) {
        }
    }

    private fun saveToDatabase(text: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val app = NoteApplication.instance
                
                if (!app.preferenceManager.isClipboardMonitoringEnabled) {
                    return@launch
                }
                
                val color = app.preferenceManager.clipboardTextColor
                val note = NoteEntity(
                    content = text,
                    contentType = ContentType.CLIPBOARD_TEXT,
                    textColor = color
                )
                app.repository.insertNote(note)
            } catch (e: Exception) {
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            return try {
                val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) 
                    as android.view.accessibility.AccessibilityManager
                accessibilityManager.isEnabled && isClipboardAccessibilityServiceEnabled(context)
            } catch (e: Exception) {
                false
            }
        }

        private fun isClipboardAccessibilityServiceEnabled(context: Context): Boolean {
            return try {
                val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) 
                    as android.view.accessibility.AccessibilityManager
                val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(-1)
                enabledServices.any { it.id.contains("com.clipnotes.app") && it.id.contains("ClipboardAccessibilityService") }
            } catch (e: Exception) {
                false
            }
        }
    }
}
