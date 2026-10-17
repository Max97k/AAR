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
    private var currentUtteranceDeferred: kotlinx.coroutines.CompletableDeferred<Unit>? = null

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
                ReaderStateController.onUtteranceStart(uttId)
            },
            onUtteranceCompleted = { uttId ->
                ReaderStateController.onUtteranceDone(uttId)
                currentUtteranceDeferred?.complete(Unit)
            },
            onUtteranceError = { uttId, err ->
                Log.e(TAG, "TTS Utterance error on $uttId: code $err")
                ReaderStateController.onUtteranceDone(uttId)
                currentUtteranceDeferred?.complete(Unit)
            }
        )
        ttsController.initialize()

        overlayHudController = OverlayHudController(this)
        overlayHudController.show()

        // Register screen off receiver
        val screenFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, screenFilter)

        // Synchronize Quick Settings Tile
        requestTileUpdate(this)

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
                currentUtteranceDeferred?.cancel()
                ttsController.stop()
                overlayHudController.updateHighlight(null, false)
            }
            ReaderState.IDLE,
            ReaderState.COMPLETED -> {
                readingJob?.cancel()
                currentUtteranceDeferred?.cancel()
                ttsController.stop()
                deduplicationEngine.reset()
                bottomDetector.reset()
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

            if (unreadSentences.isNotEmpty()) {
                // Read each sentence sequentially
                for (sentence in unreadSentences) {
                    if (ReaderStateController.state.value != ReaderState.SCANNING &&
                        ReaderStateController.state.value != ReaderState.SPEAKING
                    ) {
                        return@launch
                    }

                    ReaderStateController.setCurrentSentence(sentence)
                    overlayHudController.updateHighlight(sentence.boundsInScreen, prefs.highlightEnabled)

                    val deferred = kotlinx.coroutines.CompletableDeferred<Unit>()
                    currentUtteranceDeferred = deferred

                    val spoken = ttsController.speak(
                        sentence = sentence,
                        rate = prefs.speechRate,
                        pitch = prefs.speechPitch,
                        language = prefs.language
                    )

                    if (spoken) {
                        kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                            deferred.await()
                        }
                    } else {
                        Log.w(TAG, "TTS speak returned false or not ready for sentence: ${sentence.id}")
                        delay(400)
                    }

                    deduplicationEngine.recordRead(sentence)

                    if (prefs.pauseBetweenSentencesMs > 0) {
                        delay(prefs.pauseBetweenSentencesMs)
                    }
                }

                // Current viewport finished reading, trigger auto-scroll
                triggerAutoScroll(prefs, screenBounds, parseResult.screenContentSignature, unreadSentences.size)
            } else {
                // Check if bottom reached
                val isBottom = bottomDetector.onScrollAttempt(
                    scrollSucceeded = true,
                    newScreenSignature = parseResult.screenContentSignature,
                    unreadCount = 0
                )

                if (isBottom) {
                    Log.i(TAG, "Bottom of page reached.")
                    ReaderStateController.updateState(ReaderState.COMPLETED)
                    announceCompletion(prefs)
                    ReaderStateController.stopReading()
                } else {
                    // Try another scroll to advance content
                    triggerAutoScroll(prefs, screenBounds, parseResult.screenContentSignature, 0)
                }
            }
        }
    }

    private suspend fun triggerAutoScroll(
        prefs: UserPreferences,
        screenBounds: Rect,
        screenSignature: Long,
        unreadCount: Int
    ) {
        ReaderStateController.updateState(ReaderState.SCROLLING)
        overlayHudController.updateHighlight(null, false)

        val scrollResult = AutoScrollEngine.triggerScroll(
            container = activeScrollableContainer,
            service = this,
            screenWidth = screenBounds.width(),
            screenHeight = screenBounds.height(),
            preferGesture = (prefs.scrollMode == "GESTURE_ONLY")
        )

        val scrollSuccess = scrollResult != ScrollResult.Failed

        // Transition to SETTLING debounce
        ReaderStateController.updateState(ReaderState.SETTLING)
        delay(AutoScrollEngine.SETTLING_DEBOUNCE_MS)

        val isBottom = bottomDetector.onScrollAttempt(
            scrollSucceeded = scrollSuccess,
            newScreenSignature = screenSignature,
            unreadCount = unreadCount
        )

        if (isBottom) {
            Log.i(TAG, "Bottom detected after scroll failure or zero unread.")
            ReaderStateController.updateState(ReaderState.COMPLETED)
            announceCompletion(prefs)
            ReaderStateController.stopReading()
        } else {
            ReaderStateController.onScrollSettled()
        }
    }

    private fun announceCompletion(prefs: UserPreferences) {
        val completionText = if (prefs.language.startsWith("ZH", ignoreCase = true) ||
            (prefs.language == "AUTO" && Locale.getDefault().language == "zh")
        ) {
            "已朗讀至內容底部"
        } else {
            "Reached the bottom of content"
        }
        val sentence = SentenceUnit(id = "completion_announcement", text = completionText)
        ttsController.speak(sentence, prefs.speechRate, prefs.speechPitch, prefs.language)
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
        if (ReaderStateController.state.value == ReaderState.SETTLING) {
            Log.v(TAG, "View scroll event received during Settling.")
        }
    }

    private fun handleWindowContentChanged(event: AccessibilityEvent) {
        // Handled reactively
    }

    override fun onInterrupt() {
        Log.w(TAG, "AutoReaderAccessibilityService interrupted by system.")
        ReaderStateController.pauseReading()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "AutoReaderAccessibilityService destroying.")
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
