package com.clipnotes.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.clipnotes.app.data.ContentType
import com.clipnotes.app.data.NoteEntity
import com.clipnotes.app.databinding.ItemNoteBinding
import java.text.SimpleDateFormat
import java.util.*

class NotesAdapter(
    private val onNoteClick: (NoteEntity) -> Unit,
    private val onNoteLongClick: (NoteEntity) -> Unit,
    private val onAudioClick: (String) -> Unit,
    private val onNoteMarkRead: (NoteEntity) -> Unit
) : ListAdapter<NoteEntity, NotesAdapter.NoteViewHolder>(NoteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NoteViewHolder(
        private val binding: ItemNoteBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(note: NoteEntity) {
            binding.apply {
                textContent.text = note.content
                textContent.setTextColor(note.textColor)
                
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                textTimestamp.text = dateFormat.format(Date(note.timestamp))

                // 设置背景色：已读为深灰色，未读为白色
                if (note.isRead) {
                    root.setBackgroundColor(Color.parseColor("#E8E8E8"))
                } else {
                    root.setBackgroundColor(Color.WHITE)
                }

                when (note.contentType) {
                    ContentType.CLIPBOARD_TEXT -> {
                        textType.text = "📋 剪贴板"
                    }
                    ContentType.USER_INPUT_TEXT -> {
                        textType.text = "✏️ 手动输入"
                    }
                    ContentType.AUDIO_RECORDING -> {
                        textType.text = "🎤 录音"
                        root.setOnClickListener {
                            note.audioFilePath?.let { path -> onAudioClick(path) }
                        }
                    }
                }

                if (note.contentType != ContentType.AUDIO_RECORDING) {
                    root.setOnClickListener {
                        // 自动复制到剪贴板
                        val clipboard = root.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("note", note.content)
                        clipboard.setPrimaryClip(clip)
                        // 已复制
                        
                        // 标记为已读
                        onNoteMarkRead(note)
                        onNoteClick(note)
                    }
                }

                root.setOnLongClickListener {
                    onNoteLongClick(note)
                    true
                }
            }
        }
    }

    private class NoteDiffCallback : DiffUtil.ItemCallback<NoteEntity>() {
        override fun areItemsTheSame(oldItem: NoteEntity, newItem: NoteEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: NoteEntity, newItem: NoteEntity): Boolean {
            return oldItem == newItem
        }
    }
}
