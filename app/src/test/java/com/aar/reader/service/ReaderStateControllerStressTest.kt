package com.aar.reader.service

import com.aar.reader.service.model.ReaderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stress harness and regression verification suite for ReaderStateController.
 */
class ReaderStateControllerStressTest {

    private val controller = ReaderStateController

    @Before
    fun setUp() {
        controller.stopReading()
    }

    @Test
    fun `test valid nominal lifecycle progression`() {
        assertEquals(ReaderState.IDLE, controller.state.value)
        assertNull(controller.targetPackage.value)

        // 1. IDLE -> SCANNING
        controller.startReading("com.example.browser")
        assertEquals(ReaderState.SCANNING, controller.state.value)
        assertEquals("com.example.browser", controller.targetPackage.value)

        // 2. SCANNING -> SPEAKING (via utterance start)
        controller.onUtteranceStart("utt_1")
        assertEquals(ReaderState.SPEAKING, controller.state.value)

        // 3. SPEAKING -> PAUSED
        controller.pauseReading()
        assertEquals(ReaderState.PAUSED, controller.state.value)

        // 4. PAUSED -> SCANNING (resume)
        controller.resumeReading()
        assertEquals(ReaderState.SCANNING, controller.state.value)

        // 5. SCANNING -> SCROLLING (via updateState)
        controller.updateState(ReaderState.SCROLLING)
        assertEquals(ReaderState.SCROLLING, controller.state.value)

        // 6. SCROLLING -> SETTLING (via updateState)
        controller.updateState(ReaderState.SETTLING)
        assertEquals(ReaderState.SETTLING, controller.state.value)

        // 7. SETTLING -> SCANNING (onScrollSettled)
        controller.onScrollSettled()
        assertEquals(ReaderState.SCANNING, controller.state.value)

        // 8. SCANNING -> COMPLETED
        controller.updateState(ReaderState.COMPLETED)
        assertEquals(ReaderState.COMPLETED, controller.state.value)

        // 9. COMPLETED -> IDLE
        controller.stopReading()
        assertEquals(ReaderState.IDLE, controller.state.value)
        assertNull(controller.targetPackage.value)
    }

    /**
     * Regression verification: pauseReading succeeds during SCROLLING.
     */
    @Test
    fun `pauseReading succeeds during SCROLLING state`() {
        controller.startReading("com.example.news")
        controller.updateState(ReaderState.SCROLLING)
        assertEquals(ReaderState.SCROLLING, controller.state.value)

        controller.pauseReading()

        val stateAfterPause = controller.state.value
        assertEquals("pauseReading during SCROLLING must transition to PAUSED", ReaderState.PAUSED, stateAfterPause)
    }

    /**
     * Regression verification: pauseReading succeeds during SETTLING.
     */
    @Test
    fun `pauseReading succeeds during SETTLING state`() {
        controller.startReading("com.example.news")
        controller.updateState(ReaderState.SETTLING)
        assertEquals(ReaderState.SETTLING, controller.state.value)

        controller.pauseReading()

        val stateAfterPause = controller.state.value
        assertEquals("pauseReading during SETTLING must transition to PAUSED", ReaderState.PAUSED, stateAfterPause)
    }

    /**
     * Regression verification: onUtteranceStart is safely guarded against IDLE and PAUSED.
     */
    @Test
    fun `onUtteranceStart is safely guarded against resurrecting IDLE and PAUSED`() {
        // Case A: IDLE
        controller.stopReading()
        assertEquals(ReaderState.IDLE, controller.state.value)

        controller.onUtteranceStart("delayed_utt_from_idle")
        val stateAfterIdleUtterance = controller.state.value
        assertEquals("onUtteranceStart while IDLE must be ignored and remain IDLE", ReaderState.IDLE, stateAfterIdleUtterance)

        // Case B: PAUSED
        controller.startReading("com.example.news")
        controller.pauseReading()
        assertEquals(ReaderState.PAUSED, controller.state.value)

        controller.onUtteranceStart("delayed_utt_from_paused")
        val stateAfterPausedUtterance = controller.state.value
        assertEquals("onUtteranceStart while PAUSED must be ignored and remain PAUSED", ReaderState.PAUSED, stateAfterPausedUtterance)
    }

    /**
     * Regression verification: High Concurrency Thread Safety.
     * Concurrent calls to startReading, pauseReading, resumeReading, stopReading
     * maintain atomic invariant (if state == IDLE, targetPackage == null) with 0 violations.
     */
    @Test
    fun `concurrent stress operations maintain zero invariant violations`() {
        val numThreads = 16
        val iterationsPerThread = 200
        val executor = Executors.newFixedThreadPool(numThreads)
        val invariantViolations = AtomicInteger(0)

        val futures = (0 until numThreads).map { threadId ->
            executor.submit {
                for (i in 0 until iterationsPerThread) {
                    when ((threadId + i) % 6) {
                        0 -> controller.startReading("pkg_$threadId")
                        1 -> controller.pauseReading()
                        2 -> controller.resumeReading()
                        3 -> controller.onUtteranceStart("utt_$i")
                        4 -> controller.onScrollSettled()
                        5 -> controller.stopReading()
                    }

                    // Check atomic invariant: If state is IDLE, targetPackage must be null
                    val (s, p) = controller.getSnapshot()
                    if (s == ReaderState.IDLE && p != null) {
                        invariantViolations.incrementAndGet()
                    }
                }
            }
        }

        executor.shutdown()
        val finished = executor.awaitTermination(10, TimeUnit.SECONDS)
        assertTrue("Executor did not terminate within timeout", finished)
        controller.stopReading()

        val violations = invariantViolations.get()
        assertEquals("Concurrent stress must produce zero atomic invariant violations", 0, violations)
    }
}
