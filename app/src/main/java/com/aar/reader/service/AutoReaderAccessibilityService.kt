package com.aar.reader.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.service.quicksettings.TileService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.aar.reader.data.UserPreferences
import com.aar.reader.data.UserPreferencesRepository
import com.aar.reader.parser.ParsedScreenResult
import com.aar.reader.parser.ScreenTreeParser
import com.aar.reader.scroll.AutoScrollEngine
import com.aar.reader.scroll.BottomDetector
import com.aar.reader.scroll.DeduplicationEngine
import com.aar.reader.scroll.ScrollResult
import com.aar.reader.service.model.ReaderState
import com.aar.reader.service.model.SentenceUnit
import com.aar.reader.tts.AudioFocusManager
import com.aar.reader.tts.TtsController
import com.aar.reader.ui.overlay.OverlayHudController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Central system service binding to Android's Accessibility framework.
 * Orchestrates DFS parsing, TTS playback, reading highlight frames, and auto-scrolling.
 */
class AutoReaderAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var preferencesRepo: UserPreferencesRepository
    private lateinit var audioFocusManager: AudioFocusManager
    private lateinit var ttsController: TtsController
    private lateinit var overlayHudController: OverlayHudController
    private val deduplicationEngine = DeduplicationEngine()
    private val bottomDetector = BottomDetector()

    private var readingJob: Job? = null
    private var activeScrollableContainer: AccessibilityNodeInfo? = null
    private var currentUtteranceDeferred: CompletableDeferred<Boolean>? = null
    private var lastScrollSucceeded: Boolean? = null

    @Volatile
    private var lastViewScrolledTimestampMs: Long = 0L

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                Log.i(TAG, "Screen turned off. Pausing reader.")
                ReaderStateController.pauseReading()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        _isBoundFlow.value = true
        Log.i(TAG, "AutoReaderAccessibilityService connected.")

        preferencesRepo = UserPreferencesRepository(applicationContext)

        audioFocusManager = AudioFocusManager(
            context = this,
            onAudioFocusLost = {
                Log.w(TAG, "Audio focus lost. Pausing reader.")
                ReaderStateController.pauseReading()
            }
        )

        ttsController = TtsController(
            context = this,
            audioFocusManager = audioFocusManager,
            onUtteranceStarted = { uttId ->
                if (uttId != COMPLETION_UTTERANCE_ID) {
                    ReaderStateController.onUtteranceStart(uttId)
                }
            },
            onUtteranceCompleted = { uttId ->
                ReaderStateController.onUtteranceDone(uttId)
                currentUtteranceDeferred?.complete(true)
            },
            onUtteranceError = { uttId, err ->
                Log.e(TAG, "TTS Utterance error on $uttId: code $err")
                ReaderStateController.onUtteranceDone(uttId)
                currentUtteranceDeferred?.complete(false)
            },
            onUtteranceStopped = { uttId, _ ->
                Log.d(TAG, "TTS Utterance stopped/interrupted: $uttId")
                currentUtteranceDeferred?.complete(false)
            }
        )
        ttsController.initialize()

        overlayHudController = OverlayHudController(
            service = this,
            onCycleSpeedRequested = {
                serviceScope.launch {
                    val currentRate = preferencesRepo.preferences.first().speechRate
                    val nextRate = when {
                        currentRate < 0.95f -> 1.0f
                        currentRate < 1.2f -> 1.25f
                        currentRate < 1.45f -> 1.5f
                        currentRate < 1.9f -> 2.0f
                        else -> 0.75f
                    }
                    preferencesRepo.updateSpeechRate(nextRate)
                    ttsController.setSpeechRate(nextRate)
                    overlayHudController.updateSpeedLabel(nextRate)
                }
            }
        )
        overlayHudController.show()

        // Wire skip listener so HUD skip buttons immediately interrupt current utterance
        ReaderStateController.setOnSkipListener {
            ttsController.stop()
            currentUtteranceDeferred?.complete(false)
        }

        // Register screen off receiver
        val screenFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, screenFilter)

        // Synchronize Quick Settings Tile
        requestTileUpdate(this)

        // Observe preference changes for HUD speed badge sync
        serviceScope.launch {
            preferencesRepo.preferences.collect { prefs ->
                overlayHudController.updateSpeedLabel(prefs.speechRate)
            }
        }

        // Observe FSM states to drive the reading loop
        serviceScope.launch {
            ReaderStateController.state.collect { state ->
                Log.d(TAG, "Reader state transitioned to: $state")
                requestTileUpdate(this@AutoReaderAccessibilityService)
                handleStateChange(state)
            }
        }
    }

    private fun handleStateChange(state: ReaderState) {
        overlayHudController.updateState(state)
        when (state) {
            ReaderState.SCANNING -> {
                startScanningAndReading()
            }
            ReaderState.PAUSED -> {
                readingJob?.cancel()
                currentUtteranceDeferred?.complete(false)
                ttsController.stop()
                overlayHudController.updateHighlight(null, false)
            }
            ReaderState.COMPLETED -> {
                // Keep readingJob and TTS active during COMPLETED so announceCompletion()
                // can finish speaking before transitioning to IDLE.
                overlayHudController.updateHighlight(null, false)
            }
            ReaderState.IDLE -> {
                readingJob?.cancel()
                currentUtteranceDeferred?.complete(false)
                ttsController.stop()
                deduplicationEngine.reset()
                bottomDetector.reset()
                lastScrollSucceeded = null
                overlayHudController.updateHighlight(null, false)
            }
            else -> {
                // Other states managed within reading loop
            }
        }
    }

    private fun startScanningAndReading() {
        readingJob?.cancel()
        readingJob = serviceScope.launch {
            val prefs = preferencesRepo.preferences.first()
            val root = rootInActiveWindow
            if (root == null) {
                Log.w(TAG, "rootInActiveWindow is null during SCANNING. Retrying after delay...")
                delay(300)
                if (ReaderStateController.state.value == ReaderState.SCANNING) {
                    startScanningAndReading()
                }
                return@launch
            }

            val metrics = resources.displayMetrics
            val screenBounds = Rect(0, 0, metrics.widthPixels, metrics.heightPixels)

            val parseResult: ParsedScreenResult = ScreenTreeParser.parseScreen(
                root = root,
                screenBounds = screenBounds,
                overlayPackageName = packageName
            )
            activeScrollableContainer = parseResult.scrollableContainer

            val unreadSentences = deduplicationEngine.filterUnread(
                sentences = parseResult.sentences,
                screenHeight = screenBounds.height()
            )

            // Evaluate bottom-of-page condition once per post-scroll scan cycle
            val previousScrollResult = lastScrollSucceeded
            lastScrollSucceeded = null
            if (previousScrollResult != null) {
                val isBottom = bottomDetector.onScrollAttempt(
                    scrollSucceeded = previousScrollResult,
                    newScreenSignature = parseResult.screenContentSignature,
                    unreadCount = unreadSentences.size
                )
                if (isBottom && unreadSentences.isEmpty()) {
                    Log.i(TAG, "Bottom of page reached after post-scroll verification.")
                    ReaderStateController.updateState(ReaderState.COMPLETED)
                    announceCompletion(prefs)
                    ReaderStateController.stopReading()
                    return@launch
                }
            } else {
                bottomDetector.recordBaselineSignature(parseResult.screenContentSignature)
            }

            if (unreadSentences.isNotEmpty()) {
                var index = 0
                while (index in unreadSentences.indices) {
                    if (ReaderStateController.state.value != ReaderState.SCANNING &&
                        ReaderStateController.state.value != ReaderState.SPEAKING
                    ) {
                        return@launch
                    }

                    val sentence = unreadSentences[index]
                    // Always fetch latest preferences for real-time speed/highlight updates
                    val currentPrefs = preferencesRepo.preferences.first()

                    ReaderStateController.setCurrentSentence(sentence)
                    overlayHudController.updateHighlight(sentence.boundsInScreen, currentPrefs.highlightEnabled)

                    val deferred = CompletableDeferred<Boolean>()
                    currentUtteranceDeferred = deferred

                    val spoken = ttsController.speak(
                        sentence = sentence,
                        rate = currentPrefs.speechRate,
                        pitch = currentPrefs.speechPitch,
                        language = currentPrefs.language
                    )

                    var completedNormally = false
                    if (spoken) {
                        completedNormally = withTimeoutOrNull(30_000L) {
                            deferred.await()
                        } ?: false
                    } else {
                        Log.w(TAG, "TTS speak returned false or not ready for sentence: ${sentence.id}")
                        delay(400)
                        completedNormally = true
                    }

                    // Check if user requested skip forward/backward while speaking
                    val skipDelta = ReaderStateController.consumePendingSkipDelta()
                    if (skipDelta != 0) {
                        if (skipDelta > 0) {
                            // Mark skipped sentence as read so it is not re-read after scroll
                            deduplicationEngine.recordRead(sentence)
                        }
                        index = (index + skipDelta).coerceAtLeast(0)
                        continue
                    }

                    // If paused or stopped mid-sentence, exit without marking sentence as read
                    if (ReaderStateController.state.value != ReaderState.SCANNING &&
                        ReaderStateController.state.value != ReaderState.SPEAKING
                    ) {
                        return@launch
                    }

                    if (completedNormally) {
                        deduplicationEngine.recordRead(sentence)
                    }

                    if (currentPrefs.pauseBetweenSentencesMs > 0) {
                        delay(currentPrefs.pauseBetweenSentencesMs)
                    }
                    index++
                }

                // Current viewport finished reading, record signature and trigger auto-scroll
                val latestPrefs = preferencesRepo.preferences.first()
                bottomDetector.recordBaselineSignature(parseResult.screenContentSignature)
                triggerAutoScroll(latestPrefs, screenBounds)
            } else {
                // No unread sentences on current viewport; attempt to scroll down for more content
                triggerAutoScroll(prefs, screenBounds)
            }
        }
    }

    private suspend fun triggerAutoScroll(
        prefs: UserPreferences,
        screenBounds: Rect
    ) {
        ReaderStateController.updateState(ReaderState.SCROLLING)
        overlayHudController.updateHighlight(null, false)

        val scrollResult = AutoScrollEngine.triggerScrollAwait(
            container = activeScrollableContainer,
            service = this,
            screenWidth = screenBounds.width(),
            screenHeight = screenBounds.height(),
            preferGesture = (prefs.scrollMode == "GESTURE_ONLY"),
            scrollPercent = prefs.scrollPercent
        )

        lastScrollSucceeded = (scrollResult != ScrollResult.Failed)

        // Transition to SETTLING debounce after scroll action / swipe gesture completes
        ReaderStateController.updateState(ReaderState.SETTLING)
        delay(AutoScrollEngine.SETTLING_DEBOUNCE_MS)

        // If inertial fling is still emitting TYPE_VIEW_SCROLLED events, wait an extra debounce window
        val elapsedSinceLastScrollEvent = System.currentTimeMillis() - lastViewScrolledTimestampMs
        if (elapsedSinceLastScrollEvent in 0 until AutoScrollEngine.POST_SCROLL_EVENT_DEBOUNCE_MS) {
            delay(AutoScrollEngine.POST_SCROLL_EVENT_DEBOUNCE_MS - elapsedSinceLastScrollEvent)
        }

        ReaderStateController.onScrollSettled()
    }

    private suspend fun announceCompletion(prefs: UserPreferences) {
        val completionText = if (prefs.language.startsWith("ZH", ignoreCase = true) ||
            (prefs.language == "AUTO" && Locale.getDefault().language == "zh")
        ) {
            "已朗讀至內容底部"
        } else {
            "Reached the bottom of content"
        }
        val sentence = SentenceUnit(id = COMPLETION_UTTERANCE_ID, text = completionText)
        val deferred = CompletableDeferred<Boolean>()
        currentUtteranceDeferred = deferred

        val spoken = ttsController.speak(sentence, prefs.speechRate, prefs.speechPitch, prefs.language)
        if (spoken) {
            withTimeoutOrNull(5_000L) {
                deferred.await()
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

        lastForegroundPackage = eventPkg

        // Safety Guard 2: Lock on target package, detect app switch away
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
        lastViewScrolledTimestampMs = System.currentTimeMillis()
        if (ReaderStateController.state.value == ReaderState.SETTLING) {
            Log.v(TAG, "View scroll event received during Settling.")
        }
    }

    private fun handleWindowContentChanged(event: AccessibilityEvent) {
        // Handled reactively during post-scroll SCANNING
    }

    override fun onInterrupt() {
        Log.w(TAG, "AutoReaderAccessibilityService interrupted by system.")
        ReaderStateController.pauseReading()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "AutoReaderAccessibilityService destroying.")
        ReaderStateController.setOnSkipListener(null)
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {
            // Ignored
        }
        ReaderStateController.stopReading()
        if (::ttsController.isInitialized) {
            ttsController.shutdown()
        }
        if (::overlayHudController.isInitialized) {
            overlayHudController.hide()
        }
        serviceScope.cancel()
        isRunning = false
        instance = null
        lastForegroundPackage = null
        _isBoundFlow.value = false
        requestTileUpdate(this)
    }

    companion object {
        const val TAG = "AutoReaderService"
        private const val COMPLETION_UTTERANCE_ID = "completion_announcement"

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

        fun resolveCurrentTargetPackage(): String? {
            return instance?.resolveForegroundPackage() ?: lastForegroundPackage
        }

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
