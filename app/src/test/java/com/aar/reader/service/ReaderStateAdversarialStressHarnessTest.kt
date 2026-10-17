package com.aar.reader.service

import com.aar.reader.service.model.ReaderState
import com.aar.reader.service.model.SentenceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Empirical Adversarial Challenger Suite for ReaderStateController runtime and concurrency invariants.
 *
 * Designed to challenge and stress-test:
 * 1. Pause transitions from SCROLLING and SETTLING under race conditions.
 * 2. Utterance callbacks firing late during IDLE, PAUSED, SCROLLING, SETTLING, and COMPLETED states.
 * 3. Atomic invariants across 32 concurrent worker threads executing 32,000 operations.
 * 4. Continuous observer invariant verification.
 */
class ReaderStateAdversarialStressHarnessTest {

    private val controller = ReaderStateController

    @Before
    fun setUp() {
        controller.stopReading()
    }

    // =========================================================================
    // TASK 1: Empirically verify pauseReading in SCROLLING and SETTLING
    // =========================================================================

    @Test
    fun `empirical verification - pauseReading from SCROLLING transitions to PAUSED`() {
        controller.startReading("com.test.app")
        controller.updateState(ReaderState.SCROLLING)
        assertEquals(ReaderState.SCROLLING, controller.state.value)

        controller.pauseReading()
        assertEquals("Must transition to PAUSED from SCROLLING", ReaderState.PAUSED, controller.state.value)

        // Late scroll settled callback must be ignored and not resurrect SCANNING
        controller.onScrollSettled()
        assertEquals("onScrollSettled during PAUSED must not resurrect SCANNING", ReaderState.PAUSED, controller.state.value)

        // Resuming from PAUSED transitions cleanly to SCANNING
        controller.resumeReading()
        assertEquals("resumeReading must transition to SCANNING", ReaderState.SCANNING, controller.state.value)
    }

    @Test
    fun `empirical verification - pauseReading from SETTLING transitions to PAUSED`() {
        controller.startReading("com.test.app")
        controller.updateState(ReaderState.SETTLING)
        assertEquals(ReaderState.SETTLING, controller.state.value)

        controller.pauseReading()
        assertEquals("Must transition to PAUSED from SETTLING", ReaderState.PAUSED, controller.state.value)

        // Late scroll settled callback must be dropped
        controller.onScrollSettled()
        assertEquals("Late onScrollSettled during PAUSED must be ignored", ReaderState.PAUSED, controller.state.value)

        // Resume reading transitions cleanly to SCANNING
        controller.resumeReading()
        assertEquals("resumeReading must transition to SCANNING", ReaderState.SCANNING, controller.state.value)
    }

    @Test
    fun `empirical verification - updateState cannot override PAUSED or IDLE`() {
        controller.stopReading()
        assertEquals(ReaderState.IDLE, controller.state.value)

        controller.updateState(ReaderState.SCROLLING)
        assertEquals("updateState to SCROLLING while IDLE must be rejected", ReaderState.IDLE, controller.state.value)

        controller.startReading("com.test.app")
        controller.pauseReading()
        assertEquals(ReaderState.PAUSED, controller.state.value)

        controller.updateState(ReaderState.SETTLING)
        assertEquals("updateState to SETTLING while PAUSED must be rejected", ReaderState.PAUSED, controller.state.value)

        controller.updateState(ReaderState.SPEAKING)
        assertEquals("updateState to SPEAKING while PAUSED must be rejected", ReaderState.PAUSED, controller.state.value)
    }

    // =========================================================================
    // TASK 2: Empirically verify onUtteranceStart non-resurrection
    // =========================================================================

    @Test
    fun `empirical verification - onUtteranceStart does not resurrect inactive or non-speaking states`() {
        // Case 1: IDLE
        controller.stopReading()
        controller.onUtteranceStart("delayed_utt_idle")
        assertEquals("onUtteranceStart while IDLE must remain IDLE", ReaderState.IDLE, controller.state.value)

        // Case 2: PAUSED
        controller.startReading("com.test.app")
        controller.pauseReading()
        controller.onUtteranceStart("delayed_utt_paused")
        assertEquals("onUtteranceStart while PAUSED must remain PAUSED", ReaderState.PAUSED, controller.state.value)

        // Case 3: SCROLLING
        controller.resumeReading()
        controller.updateState(ReaderState.SCROLLING)
        controller.onUtteranceStart("delayed_utt_scrolling")
        assertEquals("onUtteranceStart while SCROLLING must remain SCROLLING", ReaderState.SCROLLING, controller.state.value)

        // Case 4: SETTLING
        controller.updateState(ReaderState.SETTLING)
        controller.onUtteranceStart("delayed_utt_settling")
        assertEquals("onUtteranceStart while SETTLING must remain SETTLING", ReaderState.SETTLING, controller.state.value)

        // Case 5: COMPLETED
        controller.updateState(ReaderState.COMPLETED)
        controller.onUtteranceStart("delayed_utt_completed")
        assertEquals("onUtteranceStart while COMPLETED must remain COMPLETED", ReaderState.COMPLETED, controller.state.value)
    }

    // =========================================================================
    // TASK 3: High-concurrency 32-thread 32,000-operation adversarial stress harness
    // =========================================================================

    @Test
    fun `adversarial 32-thread stress harness asserts zero invariant violations`() {
        val numWorkerThreads = 32
        val iterationsPerThread = 1000
        val totalOperations = numWorkerThreads * iterationsPerThread // 32,000 ops
        val executor = Executors.newFixedThreadPool(numWorkerThreads + 2)
        val violations = ConcurrentLinkedQueue<String>()
        val startLatch = CountDownLatch(1)
        val isRunning = AtomicBoolean(true)
        val totalOpsCompleted = AtomicInteger(0)

        // Concurrent Observer 1: Continuously inspects snapshot consistency
        val observerFuture = executor.submit {
            while (isRunning.get()) {
                val (state, pkg, sentence) = controller.getFullSnapshot()
                if (state == ReaderState.IDLE) {
                    if (pkg != null) {
                        violations.add("Observer violation: state is IDLE but targetPackage is '$pkg'")
                    }
                    if (sentence != null) {
                        violations.add("Observer violation: state is IDLE but currentSentence is non-null")
                    }
                }
            }
        }

        // Concurrent Observer 2: StateFlow observation
        val flowObserverFuture = executor.submit {
            while (isRunning.get()) {
                val state = controller.state.value
                val pkg = controller.targetPackage.value
                if (state == ReaderState.IDLE && pkg != null) {
                    // Double check with snapshot to ensure no cross-flow read race
                    val (snapState, snapPkg) = controller.getSnapshot()
                    if (snapState == ReaderState.IDLE && snapPkg != null) {
                        violations.add("Flow observer violation: snapshot confirmed IDLE with targetPackage '$snapPkg'")
                    }
                }
            }
        }

        // 32 Worker Threads executing high-frequency random state operations
        val workerFutures = (0 until numWorkerThreads).map { threadId ->
            executor.submit {
                startLatch.await()
                val rng = Random(threadId * 1009)
                val testSentence = SentenceUnit(
                    id = "sent_$threadId",
                    text = "Test sentence from thread $threadId",
                    boundsInScreen = android.graphics.Rect(0, 0, 100, 50)
                )

                for (i in 0 until iterationsPerThread) {
                    when (rng.nextInt(10)) {
                        0 -> controller.startReading("pkg_thread_$threadId")
                        1 -> controller.pauseReading()
                        2 -> controller.resumeReading()
                        3 -> controller.onUtteranceStart("utt_${threadId}_$i")
                        4 -> controller.onScrollSettled()
                        5 -> controller.stopReading()
                        6 -> controller.setTargetPackage("pkg_updated_$threadId")
                        7 -> controller.setCurrentSentence(if (i % 2 == 0) testSentence else null)
                        8 -> controller.updateState(ReaderState.SCROLLING)
                        9 -> controller.updateState(ReaderState.SETTLING)
                    }

                    // Validate snapshot invariant immediately after each operation
                    val (snapshotState, snapshotPkg) = controller.getSnapshot()
                    if (snapshotState == ReaderState.IDLE && snapshotPkg != null) {
                        violations.add("Worker $threadId op $i violation: state is IDLE but targetPackage is '$snapshotPkg'")
                    }

                    totalOpsCompleted.incrementAndGet()
                }
            }
        }

        // Release all workers simultaneously
        startLatch.countDown()

        // Await all workers
        workerFutures.forEach { it.get(15, TimeUnit.SECONDS) }

        // Stop observers
        isRunning.set(false)
        observerFuture.get(2, TimeUnit.SECONDS)
        flowObserverFuture.get(2, TimeUnit.SECONDS)

        executor.shutdown()
        val terminated = executor.awaitTermination(5, TimeUnit.SECONDS)
        assertTrue("Executor must terminate cleanly", terminated)

        // Final cleanup & state validation
        controller.stopReading()
        val (finalState, finalPkg) = controller.getSnapshot()
        assertEquals(ReaderState.IDLE, finalState)
        assertNull(finalPkg)
        assertNull(controller.currentSentence.value)

        assertEquals(
            "Expected 32,000 total operations completed",
            totalOperations,
            totalOpsCompleted.get()
        )

        assertTrue(
            "Found ${violations.size} invariant violations:\n${violations.take(10).joinToString("\n")}",
            violations.isEmpty()
        )
    }
}
