package com.aar.reader.service.model

import android.graphics.Rect

/**
 * 7-State Finite State Machine governing reading, scrolling, settling, and safety interruptions.
 * Defined in accordance with PROJECT.md Interface Contracts.
 */
enum class ReaderState {
    /** Waiting state; no active reading or scrolling. QS Tile inactive. */
    IDLE,

    /** Inspecting active window view tree via ScreenTreeParser DFS. */
    SCANNING,

    /** Actively speaking current sentence via TtsController with visual highlight. */
    SPEAKING,

    /** Speech suspended; current queue retained. */
    PAUSED,

    /** Executing container scroll or simulated swipe gesture. */
    SCROLLING,

    /** Waiting for post-scroll inertial animation to stabilize (450ms debounce). */
    SETTLING,

    /** Bottom of content reached; announcing completion before returning to Idle. */
    COMPLETED
}

/**
 * Represents a discrete sentence reading unit with spatial bounds and window metadata.
 */
data class SentenceUnit(
    val id: String = "",
    val text: String = "",
    val boundsInScreen: Rect = Rect(),
    val windowId: Int = 0,
    val packageName: String = ""
)
