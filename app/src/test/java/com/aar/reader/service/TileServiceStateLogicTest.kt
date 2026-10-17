package com.aar.reader.service

import android.service.quicksettings.Tile
import com.aar.reader.service.model.ReaderState
import org.junit.Assert.assertEquals
import org.junit.Test

class TileServiceStateLogicTest {

    @Test
    fun `when service is not running tile state is always INACTIVE across all FSM states`() {
        for (state in ReaderState.entries) {
            val resolved = AutoReaderTileService.resolveTileState(
                isServiceRunning = false,
                currentState = state
            )
            assertEquals(
                "Expected Tile.STATE_INACTIVE when service is not running in state $state",
                Tile.STATE_INACTIVE,
                resolved
            )
        }
    }

    @Test
    fun `when service is running and idle, paused, or completed tile state is INACTIVE`() {
        val inactiveStates = listOf(
            ReaderState.IDLE,
            ReaderState.PAUSED,
            ReaderState.COMPLETED
        )

        for (state in inactiveStates) {
            val resolved = AutoReaderTileService.resolveTileState(
                isServiceRunning = true,
                currentState = state
            )
            assertEquals(
                "Expected Tile.STATE_INACTIVE for state $state when service is running",
                Tile.STATE_INACTIVE,
                resolved
            )
        }
    }

    @Test
    fun `when service is running and actively scanning, speaking, scrolling, or settling tile state is ACTIVE`() {
        val activeStates = listOf(
            ReaderState.SCANNING,
            ReaderState.SPEAKING,
            ReaderState.SCROLLING,
            ReaderState.SETTLING
        )

        for (state in activeStates) {
            val resolved = AutoReaderTileService.resolveTileState(
                isServiceRunning = true,
                currentState = state
            )
            assertEquals(
                "Expected Tile.STATE_ACTIVE for state $state when service is running",
                Tile.STATE_ACTIVE,
                resolved
            )
        }
    }
}
