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
 * Comprehensive unit test suite for ReaderStateController verifying:
 * 1. Initial state invariants
 * 2. Nominal full lifecycle progression across all 7 FSM states
 * 3. Pausing capability from SPEAKING, SCANNING, SCROLLING, and SETTLING
 * 4. Behavior after pausing during SCROLLING or SETTLING
 * 5. Guarding onUtteranceStart against resurrecting IDLE, PAUSED, SCROLLING, and COMPLETED
 * 6. Stop reading from all 7 states ensuring atomic reset of target package and sentence
 * 7. Start reading and target package lock mechanics
 * 8. Concurrency stress testing ensuring zero invariant violations
 * 9. Skip forward and backward delta tracking and listener notifications
 */
class ReaderStateControllerTest {

    private val controller = ReaderStateController

    @Before
    fun setUp() {
        controller.setOnSkipListener(null)
        controller.stopReading()
    }

    // ==========================================
    // 1. Initial State & Invariant Checks
    // ==========================================

    @Test
    fun `initial state is IDLE and target package is null`() {
        assertEquals(ReaderState.IDLE, controller.state.value)
        assertNull(controller.targetPackage.value)
        assertNull(controller.currentSentence.value)
    }

    @Test
    fun `pauseReading from IDLE is a no-op`() {
        controller.pauseReading()
        assertEquals(ReaderState.IDLE, controller.state.value)
    }

    @Test
    fun `resumeReading from IDLE is a no-op`() {
        controller.resumeReading()
        assertEquals(ReaderState.IDLE, controller.state.value)
    }

    @Test
    fun `onScrollSettled from IDLE is a no-op`() {
        controller.onScrollSettled()
        assertEquals(ReaderState.IDLE, controller.state.value)
    }

    // ==========================================
    // 2. Nominal Full Lifecycle Progression (All 7 States)
    // ==========================================

    @Test
    fun `full nominal lifecycle progression across all 7 FSM states`() {
        // 1. IDLE -> SCANNING
        controller.startReading("com.example.browser")
        assertEquals(ReaderState.SCANNING, controller.state.value)
        assertEquals("com.example.browser", controller.targetPackage.value)

        // 2. SCANNING -> SPEAKING (via utterance start)
        controller.onUtteranceStart("utt_1")
        assertEquals(ReaderState.SPEAKING, controller.state.value)

        // 3. SPEAKING -> PAUSED (via pause reading)
        controller.pauseReading()
        assertEquals(ReaderState.PAUSED, controller.state.value)

        // 4. PAUSED -> SCANNING (via resume reading)
        controller.resumeReading()
        assertEquals(ReaderState.SCANNING, controller.state.value)

        // 5. SCANNING -> SCROLLING (via updateState)
        controller.updateState(ReaderState.SCROLLING)
        assertEquals(ReaderState.SCROLLING, controller.state.value)

        // 6. SCROLLING -> SETTLING (via updateState)
        controller.updateState(ReaderState.SETTLING)
        assertEquals(ReaderState.SETTLING, controller.state.value)

        // 7. SETTLING -> SCANNING (via onScrollSettled)
        controller.onScrollSettled()
        assertEquals(ReaderState.SCANNING, controller.state.value)

        // 8. SCANNING -> SCROLLING -> COMPLETED
        controller.updateState(ReaderState.SCROLLING)
        controller.updateState(ReaderState.COMPLETED)
        assertEquals(ReaderState.COMPLETED, controller.state.value)

        // 9. COMPLETED -> IDLE
        controller.stopReading()
        assertEquals(ReaderState.IDLE, controller.state.value)
        assertNull(controller.targetPackage.value)
        assertNull(controller.currentSentence.value)
    }

    // ==========================================
    // 3. Pausing from Active States
    // ==========================================

    @Test
    fun `pauseReading succeeds from SPEAKING state`() {
        controller.startReading("com.example.app")
        controller.onUtteranceStart("utt_1")
        assertEquals(ReaderState.SPEAKING, controller.state.value)

        controller.pauseReading()
        assertEquals(ReaderState.PAUSED, controller.state.value)
    }

    @Test
    fun `pauseReading succeeds from SCANNING state`() {
        controller.startReading("com.example.app")
        assertEquals(ReaderState.SCANNING, controller.state.value)

        controller.pauseReading()
        assertEquals(ReaderState.PAUSED, controller.state.value)
    }

    @Test
    fun `pauseReading succeeds from SCROLLING state`() {
        controller.startReading("com.example.app")
        controller.updateState(ReaderState.SCROLLING)
        assertEquals(ReaderState.SCROLLING, controller.state.value)

        controller.pauseReading()
        assertEquals(ReaderState.PAUSED, controller.state.value)
    }

    @Test
    fun `pauseReading succeeds from SETTLING state`() {
        controller.startReading("com.example.app")
        controller.updateState(ReaderState.SCROLLING)
        controller.updateState(ReaderState.SETTLING)
        assertEquals(ReaderState.SETTLING, controller.state.value)

        controller.pauseReading()
        assertEquals(ReaderState.PAUSED, controller.state.value)
    }

    @Test
    fun `pauseReading when already PAUSED remains PAUSED`() {
        controller.startReading("com.example.app")
        controller.pauseReading()
        assertEquals(ReaderState.PAUSED, controller.state.value)

        controller.pauseReading()
        assertEquals(ReaderState.PAUSED, controller.state.value)
    }

    @Test
    fun `pauseReading when COMPLETED remains COMPLETED`() {
        controller.startReading("com.example.app")
        controller.updateState(ReaderState.SCROLLING)
        controller.updateState(ReaderState.COMPLETED)
        assertEquals(ReaderState.COMPLETED, controller.state.value)

        controller.pauseReading()
        assertEquals(ReaderState.COMPLETED, controller.state.value)
    }

    // ==========================================
    // 4. Post-Pause Event Dropping
    // ==========================================

    @Test
    fun `onScrollSettled while PAUSED does not transition to SCANNING`() {
        controller.startReading("com.example.app")
        controller.updateState(ReaderState.SCROLLING)
        controller.updateState(ReaderState.SETTLING)
        controller.pauseReading()
        assertEquals(ReaderState.PAUSED, controller.state.value)

        controller.onScrollSettled()
        assertEquals(ReaderState.PAUSED, controller.state.value)

        // Resuming explicitly recovers to SCANNING
        controller.resumeReading()
        assertEquals(ReaderState.SCANNING, controller.state.value)
    }

    @Test
    fun `updateState SETTLING while PAUSED is rejected`() {
        controller.startReading("com.example.app")
        controller.updateState(ReaderState.SCROLLING)
        controller.pauseReading()
        assertEquals(ReaderState.PAUSED, controller.state.value)

        controller.updateState(ReaderState.SETTLING)
        assertEquals(ReaderState.PAUSED, controller.state.value)
    }

    // ==========================================
    // 5. Guarding onUtteranceStart Against Resurrection
    // ==========================================

    @Test
    fun `onUtteranceStart while IDLE is ignored and does not resurrect controller`() {
        assertEquals(ReaderState.IDLE, controller.state.value)

        controller.onUtteranceStart("delayed_utt")
        assertEquals(ReaderState.IDLE, controller.state.value)
    }

    @Test
    fun `onUtteranceStart while PAUSED is ignored and does not resurrect controller`() {
        controller.startReading("com.example.app")
        controller.pauseReading()
        assertEquals(ReaderState.PAUSED, controller.state.value)

        controller.onUtteranceStart("delayed_utt")
        assertEquals(ReaderState.PAUSED, controller.state.value)
    }

    @Test
    fun `onUtteranceStart while SCROLLING is ignored`() {
        controller.startReading("com.example.app")
        controller.updateState(ReaderState.SCROLLING)
        assertEquals(ReaderState.SCROLLING, controller.state.value)

        controller.onUtteranceStart("delayed_utt")
        assertEquals(ReaderState.SCROLLING, controller.state.value)
    }

    @Test
    fun `onUtteranceStart while SETTLING is ignored`() {
        controller.startReading("com.example.app")
        controller.updateState(ReaderState.SCROLLING)
        controller.updateState(ReaderState.SETTLING)
        assertEquals(ReaderState.SETTLING, controller.state.value)

        controller.onUtteranceStart("delayed_utt")
        assertEquals(ReaderState.SETTLING, controller.state.value)
    }

    @Test
    fun `onUtteranceStart while COMPLETED is ignored`() {
        controller.startReading("com.example.app")
        controller.updateState(ReaderState.SCROLLING)
        controller.updateState(ReaderState.COMPLETED)
        assertEquals(ReaderState.COMPLETED, controller.state.value)

        controller.onUtteranceStart("delayed_utt")
        assertEquals(ReaderState.COMPLETED, controller.state.value)
    }

    // ==========================================
    // 6. Stop Reading Clean Reset Across All States
    // ==========================================

    @Test
    fun `stopReading from SCANNING resets to IDLE and clears target package`() {
        controller.startReading("com.example.app")
        assertEquals(ReaderState.SCANNING, controller.state.value)
        assertEquals("com.example.app", controller.targetPackage.value)

        controller.stopReading()
        assertEquals(ReaderState.IDLE, controller.state.value)
        assertNull(controller.targetPackage.value)
    }

    @Test
    fun `stopReading from SPEAKING resets to IDLE`() {
        controller.startReading("com.example.app")
        controller.onUtteranceStart("utt_1")
        assertEquals(ReaderState.SPEAKING, controller.state.value)

        controller.stopReading()
        assertEquals(ReaderState.IDLE, controller.state.value)
        assertNull(controller.targetPackage.value)
    }

    @Test
    fun `stopReading from PAUSED resets to IDLE`() {
        controller.startReading("com.example.app")
        controller.pauseReading()
        assertEquals(ReaderState.PAUSED, controller.state.value)

        controller.stopReading()
        assertEquals(ReaderState.IDLE, controller.state.value)
        assertNull(controller.targetPackage.value)
    }

    @Test
    fun `stopReading from SCROLLING resets to IDLE`() {
        controller.startReading("com.example.app")
        controller.updateState(ReaderState.SCROLLING)
        assertEquals(ReaderState.SCROLLING, controller.state.value)

        controller.stopReading()
        assertEquals(ReaderState.IDLE, controller.state.value)
        assertNull(controller.targetPackage.value)
    }

    @Test
    fun `stopReading from SETTLING resets to IDLE`() {
        controller.startReading("com.example.app")
        controller.updateState(ReaderState.SCROLLING)
        controller.updateState(ReaderState.SETTLING)
        assertEquals(ReaderState.SETTLING, controller.state.value)

        controller.stopReading()
        assertEquals(ReaderState.IDLE, controller.state.value)
        assertNull(controller.targetPackage.value)
    }

    @Test
    fun `stopReading from COMPLETED resets to IDLE`() {
        controller.startReading("com.example.app")
        controller.updateState(ReaderState.SCROLLING)
        controller.updateState(ReaderState.COMPLETED)
        assertEquals(ReaderState.COMPLETED, controller.state.value)

        controller.stopReading()
        assertEquals(ReaderState.IDLE, controller.state.value)
        assertNull(controller.targetPackage.value)
    }

    // ==========================================
    // 7. Target Package Lock Mechanics
    // ==========================================

    @Test
    fun `startReading with blank package sets targetPackage to null initially`() {
        controller.startReading("")
        assertEquals(ReaderState.SCANNING, controller.state.value)
        assertNull(controller.targetPackage.value)

        // Lock target package later when active window is detected
        controller.setTargetPackage("com.example.late_bound")
        assertEquals("com.example.late_bound", controller.targetPackage.value)
    }

    @Test
    fun `setTargetPackage when IDLE is ignored`() {
        assertEquals(ReaderState.IDLE, controller.state.value)
        controller.setTargetPackage("com.example.ignored")
        assertNull(controller.targetPackage.value)
    }

    @Test
    fun `startReading when already SCANNING is ignored and preserves existing package`() {
        controller.startReading("com.example.first")
        assertEquals("com.example.first", controller.targetPackage.value)

        controller.startReading("com.example.second")
        assertEquals("com.example.first", controller.targetPackage.value)
        assertEquals(ReaderState.SCANNING, controller.state.value)
    }

    // ==========================================
    // 8. Concurrency & Atomic Invariant Verification
    // ==========================================

    @Test
    fun `concurrent stress operations maintain atomic invariant zero violations`() {
        val numThreads = 16
        val iterationsPerThread = 200
        val executor = Executors.newFixedThreadPool(numThreads)
        val invariantViolations = AtomicInteger(0)

        val futures = (0 until numThreads).map { threadId ->
            executor.submit {
                for (i in 0 until iterationsPerThread) {
                    when ((threadId + i) % 7) {
                        0 -> controller.startReading("pkg_$threadId")
                        1 -> controller.pauseReading()
                        2 -> controller.resumeReading()
                        3 -> controller.onUtteranceStart("utt_$i")
                        4 -> controller.updateState(ReaderState.SCROLLING)
                        5 -> controller.onScrollSettled()
                        6 -> controller.stopReading()
                    }

                    // Strict invariant check: If state is IDLE, targetPackage MUST be null
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
        assertEquals("Atomic invariant violated: observed state=IDLE with non-null targetPackage", 0, violations)
    }

    // ==========================================
    // 9. Skip Forward & Backward Navigation
    // ==========================================

    @Test
    fun `skipForward and skipBackward record delta only when SPEAKING or SCANNING`() {
        var callbackDelta = 0
        controller.setOnSkipListener { delta -> callbackDelta = delta }

        // Ignored while IDLE
        controller.skipForward()
        assertEquals(0, controller.consumePendingSkipDelta())
        assertEquals(0, callbackDelta)

        // Active while SPEAKING
        controller.startReading("com.example.app")
        controller.onUtteranceStart("utt_1")
        controller.skipForward()
        assertEquals(1, callbackDelta)
        assertEquals(1, controller.consumePendingSkipDelta())
        assertEquals(0, controller.consumePendingSkipDelta())

        controller.skipBackward()
        assertEquals(-1, callbackDelta)
        assertEquals(-1, controller.consumePendingSkipDelta())
    }
}
