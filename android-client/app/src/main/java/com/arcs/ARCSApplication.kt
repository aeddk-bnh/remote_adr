package com.arcs.client

import android.app.Application
import timber.log.Timber

/**
 * ARCS Application class
 * Initializes global app components
 */
class ARCSApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }
        
        Timber.i("ARCS Application initialized")
    }
    
    /**
     * Production logging tree (no verbose/debug logs)
     */
    private class ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority == android.util.Log.ERROR || priority == android.util.Log.WARN) {
                // Send to crash reporting service in production
                // CrashReporter.log(priority, tag, message, t)
            }
        }
    }
}
