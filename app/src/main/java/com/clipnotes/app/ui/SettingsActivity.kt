package com.clipnotes.app.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.clipnotes.app.NoteApplication
import com.clipnotes.app.R
import com.clipnotes.app.data.ContentType
import com.clipnotes.app.data.NoteEntity
import com.clipnotes.app.databinding.ActivitySettingsBinding
import com.clipnotes.app.service.ClipboardMonitorService
import com.clipnotes.app.service.FloatingWindowService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let { exportNotesToFile(it) }
    }
    
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { showImportConfirmDialog(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "设置"

        val app = application as NoteApplication
        
        updateColorPreview()
        
        binding.switchClipboardMonitor.isChecked = app.preferenceManager.isClipboardMonitoringEnabled
        
        binding.switchClipboardMonitor.setOnCheckedChangeListener { _, isChecked ->
            app.preferenceManager.isClipboardMonitoringEnabled = isChecked
            if (isChecked) {
                ClipboardMonitorService.start(this)
                FloatingWindowService.start(this)
            } else {
                ClipboardMonitorService.stop(this)
                FloatingWindowService.stop(this)
            }
        }

        binding.btnClipboardColor.setOnClickListener {
            showColorPicker(true)
        }

        binding.btnUserInputColor.setOnClickListener {
            showColorPicker(false)
        }
        
        binding.btnExportNotes.setOnClickListener {
            startExport()
        }
        
        binding.btnImportNotes.setOnClickListener {
            startImport()
        }

        setupOpacitySeekBar()
    }

    private fun setupOpacitySeekBar() {
        val app = application as NoteApplication
        val currentOpacity = (app.preferenceManager.popupOpacity * 100).toInt()
        
        binding.seekBarOpacity.progress = currentOpacity
        binding.tvOpacityValue.text = "$currentOpacity%"
        
        binding.seekBarOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val displayProgress = if (progress < 10) 10 else progress
                binding.tvOpacityValue.text = "$displayProgress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val progress = seekBar?.progress ?: 60
                val finalProgress = if (progress < 10) 10 else progress
                val opacity = finalProgress / 100f
                app.preferenceManager.popupOpacity = opacity
                binding.tvOpacityValue.text = "$finalProgress%"
                seekBar?.progress = finalProgress
            }
        })
    }
    
    private fun startExport() {
        val app = application as NoteApplication
        lifecycleScope.launch {
            val notes = withContext(Dispatchers.IO) {
                app.repository.getAllTextNotes()
            }
            if (notes.isEmpty()) {
                Toast.makeText(this@SettingsActivity, R.string.no_notes_to_export, Toast.LENGTH_SHORT).show()
            } else {
                exportLauncher.launch("clipnotes_export.txt")
            }
        }
    }
    
    private fun exportNotesToFile(uri: Uri) {
        val app = application as NoteApplication
        lifecycleScope.launch {
            try {
                val notes = withContext(Dispatchers.IO) {
                    app.repository.getAllTextNotes()
                }
                
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        val content = notes.joinToString("\n\n") { it.content }
                        outputStream.write(content.toByteArray())
                    }
                }
                
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.export_success, notes.size),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, R.string.export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun startImport() {
        importLauncher.launch(arrayOf("text/plain"))
    }
    
    private fun showImportConfirmDialog(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(R.string.import_confirm_title)
            .setMessage(R.string.import_confirm_message)
            .setPositiveButton(R.string.confirm) { _, _ ->
                importNotesFromFile(uri)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun importNotesFromFile(uri: Uri) {
        val app = application as NoteApplication
        lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).readText()
                    } ?: ""
                }
                
                val noteContents = content.split("\n\n").filter { it.isNotBlank() }
                
                if (noteContents.isEmpty()) {
                    Toast.makeText(this@SettingsActivity, R.string.import_failed, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                withContext(Dispatchers.IO) {
                    app.repository.deleteAllTextNotes()
                    
                    noteContents.forEach { noteContent ->
                        val note = NoteEntity(
                            content = noteContent.trim(),
                            contentType = ContentType.USER_INPUT_TEXT,
                            textColor = app.preferenceManager.userInputTextColor
                        )
                        app.repository.insertNote(note)
                    }
                }
                
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.import_success, noteContents.size),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, R.string.import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateColorPreview() {
        val app = application as NoteApplication
        binding.viewClipboardColorPreview.setBackgroundColor(app.preferenceManager.clipboardTextColor)
        binding.viewUserInputColorPreview.setBackgroundColor(app.preferenceManager.userInputTextColor)
    }

    private fun showColorPicker(isClipboardColor: Boolean) {
        val colors = arrayOf(
            "黑色" to Color.BLACK,
            "蓝色" to Color.BLUE,
            "红色" to Color.RED,
            "绿色" to Color.GREEN,
            "黄色" to Color.YELLOW,
            "紫色" to Color.MAGENTA,
            "青色" to Color.CYAN,
            "灰色" to Color.GRAY
        )

        val colorNames = colors.map { it.first }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(if (isClipboardColor) "选择剪贴板文字颜色" else "选择用户输入文字颜色")
            .setItems(colorNames) { _, which ->
                val color = colors[which].second
                val app = application as NoteApplication
                
                if (isClipboardColor) {
                    app.preferenceManager.clipboardTextColor = color
                } else {
                    app.preferenceManager.userInputTextColor = color
                }
                
                updateColorPreview()
            }
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
