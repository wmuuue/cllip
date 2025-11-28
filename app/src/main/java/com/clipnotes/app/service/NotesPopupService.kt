package com.clipnotes.app.service

import android.app.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.clipnotes.app.NoteApplication
import com.clipnotes.app.R
import com.clipnotes.app.data.NoteEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class NotesPopupService : Service() {
    private var windowManager: WindowManager? = null
    private var popupView: View? = null
    private var popupParams: WindowManager.LayoutParams? = null
    private var scope: CoroutineScope? = null
    private var observeJob: Job? = null
    private var notesAdapter: PopupNotesAdapter? = null
    private var allNotes: List<NoteEntity> = emptyList()

    companion object {
        private const val NOTIFICATION_ID = 3
        private const val CHANNEL_ID = "notes_popup_channel"
        private const val PREFS_NAME = "popup_settings"
        private const val KEY_POPUP_X = "popup_x"
        private const val KEY_POPUP_Y = "popup_y"
        private const val KEY_POPUP_WIDTH = "popup_width"
        private const val KEY_POPUP_HEIGHT = "popup_height"
        private const val KEY_HAS_SAVED_SETTINGS = "has_saved_settings"
        private const val CLICKED_NOTE_COLOR = 0xFFFFE4B5.toInt()

        fun show(context: Context) {
            val intent = Intent(context, NotesPopupService::class.java).apply {
                action = "SHOW"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun hide(context: Context) {
            val intent = Intent(context, NotesPopupService::class.java).apply {
                action = "HIDE"
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "SHOW" -> {
                if (popupView == null) {
                    showPopup()
                }
            }
            "HIDE" -> {
                hidePopup()
            }
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "笔记弹窗服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("笔记弹窗")
            .setContentText("笔记弹窗运行中...")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }

    private fun showPopup() {
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasSavedSettings = prefs.getBoolean(KEY_HAS_SAVED_SETTINGS, false)
        
        val popupWidth: Int
        val popupHeight: Int
        val popupX: Int
        val popupY: Int
        
        if (hasSavedSettings) {
            popupWidth = prefs.getInt(KEY_POPUP_WIDTH, (screenWidth * 0.4).toInt())
            popupHeight = prefs.getInt(KEY_POPUP_HEIGHT, (screenHeight * 0.5).toInt())
            popupX = prefs.getInt(KEY_POPUP_X, screenWidth - popupWidth - 20)
            popupY = prefs.getInt(KEY_POPUP_Y, (screenHeight - popupHeight) / 2)
        } else {
            popupWidth = (screenWidth * 0.4).toInt()
            popupHeight = (screenHeight * 0.5).toInt()
            popupX = screenWidth - popupWidth - 20
            popupY = (screenHeight - popupHeight) / 2
        }

        val app = applicationContext as NoteApplication
        val opacity = app.preferenceManager.popupOpacity

        popupParams = WindowManager.LayoutParams(
            popupWidth,
            popupHeight,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = popupX
            y = popupY
            alpha = opacity
        }

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        popupView = inflater.inflate(R.layout.notes_popup_window, null)

        setupPopupView()
        windowManager?.addView(popupView, popupParams)

        observeNotes()
    }

    private fun setupPopupView() {
        val container = popupView?.findViewById<View>(R.id.popupContainer)
        val headerLayout = popupView?.findViewById<View>(R.id.headerLayout)
        val resizeHandle = popupView?.findViewById<View>(R.id.resizeHandle)
        val recyclerView = popupView?.findViewById<RecyclerView>(R.id.rvNotes)

        notesAdapter = PopupNotesAdapter { note ->
            onNoteClicked(note)
        }

        recyclerView?.apply {
            layoutManager = LinearLayoutManager(this@NotesPopupService)
            adapter = notesAdapter
        }

        setupDragToMove(headerLayout)
        setupResizeHandle(resizeHandle)
    }

    private fun setupDragToMove(headerView: View?) {
        headerView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = popupParams?.x ?: 0
                        initialY = popupParams?.y ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        popupParams?.x = initialX + (event.rawX - initialTouchX).toInt()
                        popupParams?.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(popupView, popupParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        savePopupSettings()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun setupResizeHandle(resizeView: View?) {
        resizeView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialWidth = 0
            private var initialHeight = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialWidth = popupParams?.width ?: 0
                        initialHeight = popupParams?.height ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val minSize = 100
                        val newWidth = (initialWidth + (event.rawX - initialTouchX)).toInt()
                        val newHeight = (initialHeight + (event.rawY - initialTouchY)).toInt()
                        popupParams?.width = maxOf(minSize, newWidth)
                        popupParams?.height = maxOf(minSize, newHeight)
                        windowManager?.updateViewLayout(popupView, popupParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        savePopupSettings()
                        return true
                    }
                }
                return false
            }
        })
    }
    
    private fun savePopupSettings() {
        popupParams?.let { params ->
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
                putInt(KEY_POPUP_X, params.x)
                putInt(KEY_POPUP_Y, params.y)
                putInt(KEY_POPUP_WIDTH, params.width)
                putInt(KEY_POPUP_HEIGHT, params.height)
                putBoolean(KEY_HAS_SAVED_SETTINGS, true)
                apply()
            }
        }
    }

    private fun onNoteClicked(note: NoteEntity) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("note", note.content)
        clipboard.setPrimaryClip(clip)

        scope?.launch(Dispatchers.IO) {
            val app = applicationContext as NoteApplication
            app.repository.markNoteAsRead(note.id)
        }
    }

    private fun observeNotes() {
        val app = applicationContext as NoteApplication
        observeJob = scope?.launch {
            app.repository.getAllNotes().collectLatest { notes ->
                allNotes = notes
                notesAdapter?.submitList(notes)
                updateReadCount(notes)
            }
        }
    }

    private fun updateReadCount(notes: List<NoteEntity>) {
        val readCount = notes.count { it.isRead }
        val totalCount = notes.size
        popupView?.findViewById<TextView>(R.id.tvReadCount)?.text = "$readCount/$totalCount"
    }

    private fun hidePopup() {
        observeJob?.cancel()
        observeJob = null
        scope?.cancel()
        scope = null
        popupView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
            }
        }
        popupView = null
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        observeJob?.cancel()
        scope?.cancel()
        popupView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
            }
        }
    }

    inner class PopupNotesAdapter(
        private val onNoteClick: (NoteEntity) -> Unit
    ) : RecyclerView.Adapter<PopupNotesAdapter.NoteViewHolder>() {

        private var notes: List<NoteEntity> = emptyList()
        private val clickedNoteIds = mutableSetOf<Long>()

        fun submitList(newNotes: List<NoteEntity>) {
            notes = newNotes
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_popup_note, parent, false)
            return NoteViewHolder(view)
        }

        override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
            holder.bind(notes[position])
        }

        override fun getItemCount(): Int = notes.size

        inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvContent: TextView = itemView.findViewById(R.id.tvNoteContent)

            fun bind(note: NoteEntity) {
                tvContent.text = note.content
                if (clickedNoteIds.contains(note.id)) {
                    tvContent.setTextColor(CLICKED_NOTE_COLOR)
                } else if (note.isRead) {
                    tvContent.setTextColor(Color.GRAY)
                } else {
                    tvContent.setTextColor(Color.WHITE)
                }
                itemView.setOnClickListener {
                    clickedNoteIds.add(note.id)
                    tvContent.setTextColor(CLICKED_NOTE_COLOR)
                    onNoteClick(note)
                }
            }
        }
    }
}
