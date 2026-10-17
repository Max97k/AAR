package com.aar.reader.scroll

/**
 * Detects whether the active content has reached the bottom of the page.
 */
class BottomDetector(
    private val maxZeroUnreadRounds: Int = 2
) {
    private var lastScreenSignature: Long? = null
    private var consecutiveZeroUnreadRounds: Int = 0

    /**
     * Records the pre-scroll screen content signature so that the first scroll attempt
     * can accurately compare the post-scroll signature against the pre-scroll baseline.
     */
    @Synchronized
    fun recordBaselineSignature(signature: Long) {
        if (lastScreenSignature == null) {
            lastScreenSignature = signature
        }
    }

    @Synchronized
    fun onScrollAttempt(
        scrollSucceeded: Boolean,
        newScreenSignature: Long,
        unreadCount: Int
    ): Boolean {
        // Criterion 1: Scroll action failed and screen signature remained 100% identical
        if (!scrollSucceeded && lastScreenSignature != null && lastScreenSignature == newScreenSignature) {
            return true
        }

        // Criterion 2: Consecutively encountering 0 unread sentences after scroll settling
        if (unreadCount == 0) {
            consecutiveZeroUnreadRounds++
            if (consecutiveZeroUnreadRounds >= maxZeroUnreadRounds) {
                return true
            }
        } else {
            consecutiveZeroUnreadRounds = 0
        }

        lastScreenSignature = newScreenSignature
        return false
    }

    @Synchronized
    fun reset() {
        lastScreenSignature = null
        consecutiveZeroUnreadRounds = 0
    }
}
