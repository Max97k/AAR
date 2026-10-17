package com.aar.reader.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.aar.reader.service.model.ReaderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Central system service binding to Android's Accessibility framework.
 *
 * Responsibilities:
 * 1. Life cycle management & binding verification.
 * 2. Receiving window content, scroll, and state change events.
 * 3. Dispatching auto-scroll touch gestures (dispatchGesture).
 * 4. Interfacing with ReaderStateController and Quick Settings Tile.
 */
class AutoReaderAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        _isBoundFlow.value = true
        Log.i(TAG, "AutoReaderAccessibilityService connected.")

        // Synchronize Quick Settings Tile
        requestTileUpdate(this)

        // Observe state machine changes to trigger tile updates
        serviceScope.launch {
            ReaderStateController.state.collect { state ->
                Log.d(TAG, "Reader state transitioned to: $state")
                requestTileUpdate(this@AutoReaderAccessibilityService)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                handleViewScrolled(event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleWindowContentChanged(event)
            }
        }
    }

    /**
     * Resolves the current foreground application package name by querying active
     * interactive application windows, falling back to rootInActiveWindow and
     * the cached lastForegroundPackage.
     */
    fun resolveForegroundPackage(): String? {
        try {
            val appWindow = windows?.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            val pkg = appWindow?.root?.packageName?.toString()
            if (!pkg.isNullOrBlank() && pkg != "com.android.systemui" && pkg != packageName) {
                return pkg
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve package from windows list: ${e.message}")
        }

        try {
            val rootPkg = rootInActiveWindow?.packageName?.toString()
            if (!rootPkg.isNullOrBlank() && rootPkg != "com.android.systemui" && rootPkg != packageName) {
                return rootPkg
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve package from rootInActiveWindow: ${e.message}")
        }

        return lastForegroundPackage
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val eventPkg = event.packageName?.toString() ?: return

        // Safety Guard 1: Detect Notification Shade pull-down
        if (eventPkg == "com.android.systemui") {
            Log.d(TAG, "System UI overlay detected; preserving current reading state.")
            return
        }

        // Ignore AAR's own package for foreign app switch detection
        if (eventPkg == packageName) {
            return
        }

        // Cache last seen application package for tile package locking
        lastForegroundPackage = eventPkg

        // Safety Guard 2: Lock on target package if started from QS tile with null target, or detect app switch away
        val targetPkg = ReaderStateController.targetPackage.value
        if (targetPkg == null && ReaderStateController.state.value != ReaderState.IDLE) {
            Log.i(TAG, "Locking target package to active window: $eventPkg")
            ReaderStateController.setTargetPackage(eventPkg)
        } else if (targetPkg != null && eventPkg != targetPkg) {
            Log.w(TAG, "User switched away to $eventPkg. Pausing reader.")
            ReaderStateController.pauseReading()
        }
    }

    private fun handleViewScrolled(event: AccessibilityEvent) {
        // M4 Auto-Scroll Settling hook: resets settling debounce timer if view is still scrolling
        if (ReaderStateController.state.value == ReaderState.SETTLING) {
            Log.v(TAG, "View scroll event received during Settling; debounce timer refreshed.")
        }
    }

    private fun handleWindowContentChanged(event: AccessibilityEvent) {
        // M2 ScreenTreeParser hook: active content changes during scanning
    }

    override fun onInterrupt() {
        Log.w(TAG, "AutoReaderAccessibilityService interrupted by system.")
        ReaderStateController.pauseReading()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "AutoReaderAccessibilityService destroying.")
        ReaderStateController.stopReading()
        serviceScope.cancel()
        isRunning = false
        instance = null
        lastForegroundPackage = null
        _isBoundFlow.value = false
        requestTileUpdate(this)
    }

    companion object {
        const val TAG = "AutoReaderService"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var instance: AutoReaderAccessibilityService? = null
            private set

        @Volatile
        var lastForegroundPackage: String? = null
            internal set

        private val _isBoundFlow = MutableStateFlow(false)
        val isBoundFlow: StateFlow<Boolean> = _isBoundFlow.asStateFlow()

        /**
         * Resolves the current target package using the active service instance or last seen foreground package.
         */
        fun resolveCurrentTargetPackage(): String? {
            return instance?.resolveForegroundPackage() ?: lastForegroundPackage
        }

        /**
         * Requests the system to re-invoke onStartListening() on the Quick Settings Tile.
         */
        fun requestTileUpdate(context: Context) {
            try {
                TileService.requestListeningState(
                    context,
                    ComponentName(context, AutoReaderTileService::class.java)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Unable to request tile listening state: ${e.message}")
            }
        }
    }
}
