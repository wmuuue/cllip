package com.clipnotes.app.utils

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

class AudioRecorderManager(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentRecordingFile: File? = null
    private var recordingStartTime: Long = 0

    fun startRecording(): File? {
        try {
            val audioDir = File(context.filesDir, "audio")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }

            val audioFile = File(audioDir, "audio_${System.currentTimeMillis()}.m4a")
            currentRecordingFile = audioFile

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }

            recordingStartTime = System.currentTimeMillis()
            return audioFile
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }

    fun stopRecording(): Pair<File?, Long> {
        val duration = System.currentTimeMillis() - recordingStartTime
        mediaRecorder?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        mediaRecorder = null
        val file = currentRecordingFile
        currentRecordingFile = null
        return Pair(file, duration)
    }

    fun playAudio(filePath: String, onCompletion: () -> Unit) {
        stopAudio()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()
                setOnCompletionListener {
                    onCompletion()
                    release()
                    mediaPlayer = null
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun stopAudio() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun release() {
        stopRecording()
        stopAudio()
    }
}
