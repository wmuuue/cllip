package com.clipnotes.app.service

import android.app.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.clipnotes.app.NoteApplication
import com.clipnotes.app.R
import com.clipnotes.app.data.ContentType
import com.clipnotes.app.data.NoteEntity
import com.clipnotes.app.ui.MainActivity
import kotlinx.coroutines.*

class ClipboardMonitorService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var clipboardManager: ClipboardManager? = null
    private var lastClipboardText: String? = null
    private var isMonitoringPaused = false
    private var pollingJob: Job? = null
    private val recentlySavedContents = LinkedHashSet<String>()
    private val MAX_SAVED_CACHE = 50
    private val mainHandler = Handler(Looper.getMainLooper())
    private var clipboardNotifyCount = 0
    private var savedNotifyCount = 0
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    override fun onCreate() {
        super.onCreate()
        try {
            clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        } catch (e: Exception) {
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_MONITORING -> {
                pauseMonitoring()
            }
            ACTION_RESUME_MONITORING -> {
                resumeMonitoring()
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
            }
            else -> {
                // 定期检查剪贴板，即使服务被重启也会继续运行
                scope.launch {
                    checkClipboard()
                }
            }
        }
        return START_STICKY
    }

    private fun startPolling() {
        try {
            // 注册剪贴板变化监听器
            clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
                if (!isMonitoringPaused) {
                    checkClipboard()
                }
            }
            clipboardManager?.addPrimaryClipChangedListener(clipboardListener!!)
        } catch (e: Exception) {
            // 如果监听器方式失败，使用轮询备用方案
            startPollingFallback()
        }
    }

    private fun startPollingFallback() {
        pollingJob = scope.launch {
            while (isActive) {
                try {
                    if (!isMonitoringPaused) {
                        checkClipboard()
                    }
                    delay(1000) // 每秒检查一次
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun checkClipboard() {
        try {
            val app = applicationContext as NoteApplication
            if (!app.preferenceManager.isClipboardMonitoringEnabled) {
                return
            }
            
            val clip = clipboardManager?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val clipText = clip.getItemAt(0).coerceToText(this@ClipboardMonitorService).toString()
                
                // 检查是否为新内容且未被保存过
                if (clipText.isNotEmpty() && clipText != lastClipboardText && 
                    !recentlySavedContents.contains(clipText)) {
                    
                    lastClipboardText = clipText
                    recentlySavedContents.add(clipText)
                    
                    // 控制缓存大小
                    if (recentlySavedContents.size > MAX_SAVED_CACHE) {
                        recentlySavedContents.remove(recentlySavedContents.first())
                    }
                    
                    saveToDatabase(clipText)
                }
            }
        } catch (e: Exception) {
        }
    }
    
    fun getLatestClipboardText(): String? {
        return lastClipboardText
    }
    
    fun saveClipboardToNote() {
        lastClipboardText?.let { text ->
            saveToDatabase(text)
        }
    }

    private fun saveToDatabase(text: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val app = application as NoteApplication
                val color = app.preferenceManager.clipboardTextColor
                val note = NoteEntity(
                    content = text,
                    contentType = ContentType.CLIPBOARD_TEXT,
                    textColor = color
                )
                app.repository.insertNote(note)
                
                // 成功保存提示
                mainHandler.post {
                    Toast.makeText(this@ClipboardMonitorService, "✅ 已保存到笔记", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
            }
        }
    }

    fun pauseMonitoring() {
        isMonitoringPaused = true
    }

    fun resumeMonitoring() {
        isMonitoringPaused = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "剪贴板监听服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "监听剪贴板变化"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("剪贴板笔记")
            .setContentText("剪切板监控中")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        // 移除剪贴板监听器
        try {
            if (clipboardListener != null) {
                clipboardManager?.removePrimaryClipChangedListener(clipboardListener!!)
            }
        } catch (e: Exception) {
        }
        pollingJob?.cancel()
        scope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "clipboard_monitor_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_PAUSE_MONITORING = "com.clipnotes.app.PAUSE_MONITORING"
        const val ACTION_RESUME_MONITORING = "com.clipnotes.app.RESUME_MONITORING"
        const val ACTION_STOP_SERVICE = "com.clipnotes.app.STOP_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, ClipboardMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) {
            val intent = Intent(context, ClipboardMonitorService::class.java).apply {
                action = ACTION_PAUSE_MONITORING
            }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, ClipboardMonitorService::class.java).apply {
                action = ACTION_RESUME_MONITORING
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ClipboardMonitorService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
