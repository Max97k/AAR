package com.aar.reader.scroll

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BottomDetectorTest {

    private lateinit var detector: BottomDetector

    @Before
    fun setUp() {
        detector = BottomDetector(maxZeroUnreadRounds = 2)
    }

    @Test
    fun onScrollAttempt_detectsBottomOnConsecutiveZeroUnreadRounds() {
        // Round 1: zero unread
        val r1 = detector.onScrollAttempt(scrollSucceeded = true, newScreenSignature = 100L, unreadCount = 0)
        assertFalse("First zero-unread round should not trigger bottom", r1)

        // Round 2: zero unread again -> bottom reached
        val r2 = detector.onScrollAttempt(scrollSucceeded = true, newScreenSignature = 100L, unreadCount = 0)
        assertTrue("Second consecutive zero-unread round must trigger bottom", r2)
    }

    @Test
    fun onScrollAttempt_resetsCounterWhenUnreadFound() {
        // Round 1: zero unread
        detector.onScrollAttempt(scrollSucceeded = true, newScreenSignature = 100L, unreadCount = 0)

        // Round 2: found 3 unread sentences -> resets counter
        val r2 = detector.onScrollAttempt(scrollSucceeded = true, newScreenSignature = 200L, unreadCount = 3)
        assertFalse(r2)

        // Round 3: zero unread again (first after reset)
        val r3 = detector.onScrollAttempt(scrollSucceeded = true, newScreenSignature = 200L, unreadCount = 0)
        assertFalse("Counter was reset, so this is only 1st round", r3)
    }

    @Test
    fun onScrollAttempt_detectsBottomOnScrollFailureWithSameSignature() {
        // Initial scroll establishes signature
        detector.onScrollAttempt(scrollSucceeded = true, newScreenSignature = 9999L, unreadCount = 5)

        // Next scroll fails and signature is identical
        val isBottom = detector.onScrollAttempt(scrollSucceeded = false, newScreenSignature = 9999L, unreadCount = 0)
        assertTrue("Scroll failure with identical screen signature must trigger bottom", isBottom)
    }

    @Test
    fun reset_clearsInternalState() {
        detector.onScrollAttempt(scrollSucceeded = true, newScreenSignature = 100L, unreadCount = 0)
        detector.reset()

        val result = detector.onScrollAttempt(scrollSucceeded = true, newScreenSignature = 100L, unreadCount = 0)
        assertFalse("After reset, counter should start over", result)
    }
}
