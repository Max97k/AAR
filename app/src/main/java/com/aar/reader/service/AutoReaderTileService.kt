package com.aar.reader.service

import android.app.PendingIntent
import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.aar.reader.R
import com.aar.reader.service.model.ReaderState

/**
 * Quick Settings Tile service enabling users to toggle AAR from the system notification shade.
 *
 * Implements Android 17 (API 34+) pending intent collapse patterns.
 */
class AutoReaderTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val isServiceRunning = AutoReaderAccessibilityService.isRunning

        if (!isServiceRunning) {
            Log.i(TAG, "Tile clicked while AccessibilityService disabled; launching system accessibility settings.")
            launchSettingsActivity()
            return
        }

        // Toggle state based on FSM
        val currentState = ReaderStateController.state.value
        when (currentState) {
            ReaderState.IDLE, ReaderState.COMPLETED -> {
                Log.i(TAG, "Tile toggle: Starting reading.")
                val targetPackage = AutoReaderAccessibilityService.resolveCurrentTargetPackage().orEmpty()
                Log.i(TAG, "Target package locked to: '$targetPackage'")
                ReaderStateController.startReading(targetPackage)
            }
            ReaderState.SPEAKING, ReaderState.SCANNING, ReaderState.SCROLLING, ReaderState.SETTLING -> {
                Log.i(TAG, "Tile toggle: Pausing reading.")
                ReaderStateController.pauseReading()
            }
            ReaderState.PAUSED -> {
                Log.i(TAG, "Tile toggle: Resuming reading.")
                ReaderStateController.resumeReading()
            }
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isServiceRunning = AutoReaderAccessibilityService.isRunning
        val currentState = ReaderStateController.state.value

        tile.state = resolveTileState(isServiceRunning, currentState)

        when {
            !isServiceRunning -> {
                tile.subtitle = getString(R.string.tile_state_disabled)
                tile.contentDescription = getString(R.string.tile_desc_disabled)
            }
            currentState == ReaderState.SPEAKING ||
            currentState == ReaderState.SCANNING ||
            currentState == ReaderState.SCROLLING ||
            currentState == ReaderState.SETTLING -> {
                tile.subtitle = getString(R.string.tile_state_reading)
                tile.contentDescription = getString(R.string.tile_desc_reading)
            }
            currentState == ReaderState.PAUSED -> {
                tile.subtitle = getString(R.string.tile_state_paused)
                tile.contentDescription = getString(R.string.tile_desc_idle)
            }
            else -> {
                tile.subtitle = getString(R.string.tile_state_idle)
                tile.contentDescription = getString(R.string.tile_desc_idle)
            }
        }

        tile.updateTile()
    }

    private fun launchSettingsActivity() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        unlockAndRun {
            startActivityAndCollapse(pendingIntent)
        }
    }

    companion object {
        const val TAG = "AutoReaderTileService"

        /**
         * Resolves the visual Quick Settings Tile state based on service running status and FSM state.
         * Returns Tile.STATE_INACTIVE when service is disabled to ensure SystemUI delivers user click events.
         */
        fun resolveTileState(isServiceRunning: Boolean, currentState: ReaderState): Int {
            if (!isServiceRunning) {
                return Tile.STATE_INACTIVE
            }
            return when (currentState) {
                ReaderState.SPEAKING,
                ReaderState.SCANNING,
                ReaderState.SCROLLING,
                ReaderState.SETTLING -> Tile.STATE_ACTIVE
                ReaderState.IDLE,
                ReaderState.PAUSED,
                ReaderState.COMPLETED -> Tile.STATE_INACTIVE
            }
        }
    }
}
