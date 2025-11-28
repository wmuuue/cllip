package com.clipnotes.app.utils

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("clipboard_notes_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CLIPBOARD_TEXT_COLOR = "clipboard_text_color"
        private const val KEY_USER_INPUT_TEXT_COLOR = "user_input_text_color"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_CLIPBOARD_MONITORING_ENABLED = "clipboard_monitoring_enabled"
        private const val KEY_POPUP_OPACITY = "popup_opacity"
        private const val DEFAULT_CLIPBOARD_COLOR = -16776961  // Blue
        private const val DEFAULT_USER_INPUT_COLOR = -16777216  // Black
        private const val DEFAULT_POPUP_OPACITY = 0.60f
    }

    var clipboardTextColor: Int
        get() = prefs.getInt(KEY_CLIPBOARD_TEXT_COLOR, DEFAULT_CLIPBOARD_COLOR)
        set(value) = prefs.edit().putInt(KEY_CLIPBOARD_TEXT_COLOR, value).apply()

    var userInputTextColor: Int
        get() = prefs.getInt(KEY_USER_INPUT_TEXT_COLOR, DEFAULT_USER_INPUT_COLOR)
        set(value) = prefs.edit().putInt(KEY_USER_INPUT_TEXT_COLOR, value).apply()

    var isClipboardMonitoringEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLIPBOARD_MONITORING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CLIPBOARD_MONITORING_ENABLED, value).apply()

    var popupOpacity: Float
        get() = prefs.getFloat(KEY_POPUP_OPACITY, DEFAULT_POPUP_OPACITY)
        set(value) = prefs.edit().putFloat(KEY_POPUP_OPACITY, value).apply()

    var deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) 
            ?: generateDeviceId().also { deviceId = it }
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    private fun generateDeviceId(): String {
        return "device_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
}
