package com.aar.reader

import android.app.Application
import android.util.Log

/**
 * Application class for Accessibility Auto-Reader.
 * Initializes process-wide singletons and logging guards.
 */
class AarApplication : Application() {

    companion object {
        const val TAG = "AarApplication"
        lateinit var instance: AarApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "AAR initialized in offline integrity mode (API 37)")
    }
}
