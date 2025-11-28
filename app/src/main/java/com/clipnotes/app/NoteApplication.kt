package com.clipnotes.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.clipnotes.app.data.AppDatabase
import com.clipnotes.app.data.NoteRepository
import com.clipnotes.app.service.NotesPopupService
import com.clipnotes.app.utils.LoggerUtil
import com.clipnotes.app.utils.PreferenceManager

class NoteApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val preferenceManager: PreferenceManager by lazy { PreferenceManager(this) }
    val repository: NoteRepository by lazy { 
        NoteRepository(database.noteDao(), database.pairedDeviceDao()) 
    }
    
    private var startedActivityCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private var showPopupRunnable: Runnable? = null
    private var isPopupShowing = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        LoggerUtil.init(this)
        LoggerUtil.log("应用启动")
        
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount++
                showPopupRunnable?.let { handler.removeCallbacks(it) }
                showPopupRunnable = null
                if (isPopupShowing) {
                    NotesPopupService.hide(this@NoteApplication)
                    isPopupShowing = false
                }
            }
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                startedActivityCount--
                if (startedActivityCount == 0) {
                    showPopupRunnable = Runnable {
                        if (startedActivityCount == 0 && !isPopupShowing) {
                            NotesPopupService.show(this@NoteApplication)
                            isPopupShowing = true
                        }
                    }
                    handler.postDelayed(showPopupRunnable!!, 300)
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    companion object {
        lateinit var instance: NoteApplication
            private set
    }
}
