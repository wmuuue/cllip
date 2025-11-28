package com.clipnotes.app.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object LoggerUtil {
    private var logFile: File? = null
    private var context: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext
        // 创建日志文件
        val logsDir = File(context.getExternalFilesDir(null), "logs")
        logsDir.mkdirs()
        logFile = File(logsDir, "clipboard_monitor_${System.currentTimeMillis()}.log")
    }

    fun log(message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logMessage = "[$timestamp] $message"
        
        // 输出到 Logcat
        android.util.Log.d("ClipboardMonitor", message)
        
        // 写入文件
        writeToFile(logMessage)
        
        // 显示 Toast（需要在主线程）
        context?.let {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun logError(message: String, exception: Exception? = null) {
        val errorMessage = if (exception != null) {
            "$message\n${exception.message}\n${exception.stackTraceToString()}"
        } else {
            message
        }
        
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logMessage = "[$timestamp] ERROR: $errorMessage"
        
        // 输出到 Logcat
        android.util.Log.e("ClipboardMonitor", errorMessage, exception)
        
        // 写入文件
        writeToFile(logMessage)
        
        // 显示 Toast
        context?.let {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(it, "错误: $message", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun writeToFile(message: String) {
        try {
            logFile?.let { file ->
                FileWriter(file, true).use { writer ->
                    writer.append(message)
                    writer.append("\n")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LoggerUtil", "写入日志文件失败", e)
        }
    }

    fun getLogFilePath(): String = logFile?.absolutePath ?: "No log file"
}
