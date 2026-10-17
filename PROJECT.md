# Project: Accessibility Auto-Reader (AAR)

## Architecture
AAR is a 100% offline, zero-network Android application (minSdk = 37, targetSdk = 37, compileSdk = 37) built with Kotlin 2.2+, Coroutines, StateFlow, Jetpack Compose, Material 3, and native Android Accessibility and TTS APIs.

The system is organized into decoupled layers:
1. **Core Service & State Machine**:
   - `AutoReaderAccessibilityService`: Central system service binding to `AccessibilityService`. Manages accessibility event callbacks, dispatches scrolling gestures, hosts overlay views via Accessibility WindowManager (`TYPE_ACCESSIBILITY_OVERLAY`), and enforces lifecycle guards.
   - `ReaderStateController`: Thread-safe unidirectional data flow (UDF) singleton coordinating the 7-state finite state machine (`IDLE`, `SCANNING`, `SPEAKING`, `PAUSED`, `SCROLLING`, `SETTLING`, `COMPLETED`), active window package lock, reading queue, and speech progress.
2. **Screen Content Extraction Engine**:
   - `ScreenTreeParser`: Depth-First Search (DFS) traversal over `AccessibilityNodeInfo` hierarchy. Applies visibility checks, password filters (`isPassword`), app overlay exclusion, and control blacklist pruning.
   - `SpatialSorter`: Top-to-bottom, left-to-right topological coordinate ordering using Y-axis tolerance clustering ($\Delta Y \approx 18\text{px}$).
   - `SentenceSplitter`: Punctuation-based sentence splitting supporting Traditional Chinese (`zh-TW`) and English (`en-US`) with fallback chunking for oversized paragraphs.
3. **Speech & Audio Control**:
   - `TtsController`: Native `android.speech.tts.TextToSpeech` synthesis with `UtteranceProgressListener` callback tracking, offline voice pack availability detection, dynamic speech rate (0.5x ~ 2.0x), and pitch control.
   - `AudioManager` integration: `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` acquisition/abandonment and `ACTION_AUDIO_BECOMING_NOISY` broadcast receiver for automatic pausing.
4. **Continuous Auto-Scroll & Deduplication**:
   - `AutoScrollEngine`: Dual-engine strategy prioritizing container `ACTION_SCROLL_FORWARD` with smooth `dispatchGesture` upward swipe (350ms) fallback. Enforces 450ms settling debounce.
   - `DeduplicationEngine`: Sliding-window fingerprint ring buffer ($K=50$) with tail-anchor matching ($M=3$) to prune overlapping text visible after partial scrolls.
   - `BottomDetector`: Multi-factor end-of-content detection (scroll rejection + screen signature match, or 2 consecutive zero unread returns) and safety interruption guards (app switch, screen off, notification shade freeze).
5. **UI & User Controls**:
   - `OverlayHudController`: Collapsible floating control pill (`TYPE_ACCESSIBILITY_OVERLAY`) with Play/Pause, Skip Back/Forward, Speed Cycler, and Stop controls without requiring `SYSTEM_ALERT_WINDOW`.
   - `HighlightOverlayView`: Dynamic color reading focus highlight frame drawn on an accessibility overlay canvas synchronized with TTS utterance progress.
   - `AutoReaderTileService`: System Quick Settings Tile for starting/stopping the reader from notification shade.
   - `MainActivity` & `SettingsViewModel`: Jetpack Compose Material 3 UI with Dynamic Color and edge-to-edge layout, featuring a Prominent Disclosure dialog for accessibility usage.
   - `UserPreferencesRepository`: Jetpack DataStore preferences for speech rate, pitch, language, highlight frame toggle, scroll mode, scroll percentage, and pause duration.

---

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Gradle Kotlin DSL & Version Catalog | Project scaffolding with `libs.versions.toml`, AGP 9.4.1, Kotlin 2.2.10, minSdk 37 | M1 | survey |
| 2 | Zero-Network Manifest Configuration | `AndroidManifest.xml` omitting `android.permission.INTERNET` | M1 | survey |
| 3 | Accessibility Service Registration | `AutoReaderAccessibilityService` registered with `BIND_ACCESSIBILITY_SERVICE` and XML config | M1 | survey |
| 4 | Quick Settings Tile Skeleton | `AutoReaderTileService` extending `TileService` with toggle capability | M1 | survey |
| 5 | Prominent Disclosure Dialog | Material 3 dialog explaining accessibility usage before system settings activation | M1 | survey |
| 6 | DFS Screen Tree Traversal | Recursive traversal of `AccessibilityNodeInfo` extracting text and bounds | M2 | survey |
| 7 | Visibility & Bounds Filter | Exclusion of invisible nodes, zero/negative bounds, and off-screen clipped nodes | M2 | survey |
| 8 | Password Input Filter | Skipping nodes with `isPassword == true` | M2 | survey |
| 9 | Overlay Pruning | Automatic exclusion of AAR's own `TYPE_ACCESSIBILITY_OVERLAY` windows | M2 | survey |
| 10 | Control & Noise Blacklist Filter | Pruning system status/navigation bars and common interactive labels (複製, 分享, 返回, etc.) | M2 | survey |
| 11 | Y-Axis Spatial Sorter | Coordinate-based topological sorting with vertical tolerance clustering | M2 | survey |
| 12 | Punctuation Sentence Splitter | Splitting text by Chinese/English punctuation (`。！？；.!?; \n`) into sentence units | M2 | survey |
| 13 | Max Length Fallback Chunking | Sub-splitting long sentences exceeding TTS limits (150-char secondary chunks) | M2 | survey |
| 14 | Native TTS Initialization & Lifecycle | Native `TextToSpeech` setup with `OnInitListener` and shutdown cleanup | M3 | survey |
| 15 | Utterance Callback Tracking | `UtteranceProgressListener` driving state machine onStart, onDone, onStop, onError | M3 | survey |
| 16 | Speech Rate & Pitch Adjustment | Real-time speech rate (0.5x~2.0x) and pitch (0.5x~1.5x) modification | M3 | survey |
| 17 | Voice Pack Availability Check | Checking `isLanguageAvailable` and launching `ACTION_INSTALL_TTS_DATA` if missing | M3 | survey |
| 18 | Bilingual & Auto Language Support | Supporting `zh-TW`, `en-US`, and automated language detection | M3 | survey |
| 19 | AudioFocus Transient Ducking | Requesting `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` before speech, abandoning on completion | M3 | survey |
| 20 | Audio Becoming Noisy Receiver | Pausing playback upon headset unplugging or Bluetooth disconnection | M3 | survey |
| 21 | Audio Focus Loss Interruption | Pausing speech on permanent or transient focus loss (calls, alarms) | M3 | survey |
| 22 | Native Container Auto-Scroll | Strategy A: finding scrollable node and dispatching `ACTION_SCROLL_FORWARD` | M4 | survey |
| 23 | Simulated Gesture Swipe Fallback | Strategy B: upward stroke from `(0.5w, 0.78h)` to `(0.5w, 0.22h)` over 350ms | M4 | survey |
| 24 | Scroll Settling Debounce | 450ms waiting period after scroll before rescanning active window | M4 | survey |
| 25 | Sliding-Window Fingerprint Buffer | Ring buffer ($K=50$) of 64-bit normalized text hashes for deduplication | M4 | survey |
| 26 | Tail-Anchor Sequence Matching | Matching top of new page against last 3 read sentences to eliminate overlap | M4 | survey |
| 27 | Bottom-of-Page Detection | Detecting scroll failure with identical screen signature or 2 zero-unread rounds | M4 | survey |
| 28 | Completion Announcement | TTS announcement upon reaching bottom of content and transition to IDLE | M4 | survey |
| 29 | Target Package Change Protection | Pausing reading and gestures when user switches apps or returns to launcher | M4 | survey |
| 30 | Screen-Off Interruption Guard | Stopping auto-scroll and pausing TTS when receiving `ACTION_SCREEN_OFF` | M4 | survey |
| 31 | Notification Shade Protection | Freezing reading when system UI (`com.android.systemui`) covers the screen | M4 | survey |
| 32 | Collapsible Floating HUD Pill | 48dp pill expanding to show Play/Pause, Skip, Speed, and Stop | M5 | survey |
| 33 | Reading Focus Highlight Frame | Real-time highlight border drawn around the active sentence bounding box | M5 | survey |
| 34 | Jetpack Compose Settings UI | Material 3 settings screen with Dynamic Color, dark/light theme, and sliders | M5 | survey |
| 35 | Jetpack DataStore Persistence | Reactive `UserPreferencesRepository` storing speech and scroll preferences | M5 | survey |
| 36 | QS Tile State Synchronization | Reflecting `STATE_ACTIVE`, `STATE_INACTIVE`, and `STATE_UNAVAILABLE` on QS Tile | M5 | survey |
| 37 | FSM Unidirectional Data Flow | Central `ReaderStateController` coordinating 7 states and broadcasting to UI | M5 | survey |
| 38 | End-to-End Test Suite | Four-tier test suite covering JVM unit tests, Robolectric, and API 37 E2E tests | M6 / E2E | survey |
| 39 | Production R8 & Build Release | ProGuard / R8 optimization rules and release APK validation | M6 | survey |

---

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Foundation & Service Skeleton | Gradle build scaffolding, libs.versions.toml, zero-network manifest, AutoReaderAccessibilityService skeleton, accessibility_service_config.xml, AutoReaderTileService skeleton, Prominent Disclosure dialog | none | COMPLETED |
| M2 | Screen Parser & Sentence Segmentation | ScreenTreeParser DFS engine, node filters (visibility, password, overlay, blacklist), SpatialSorter topological ordering, SentenceSplitter CJK/Latin segmentation | M1 | COMPLETED |
| M3 | TTS Engine & AudioFocus | TtsController, UtteranceProgressListener integration, voice pack checking, bilingual support, AudioFocus management, ACTION_AUDIO_BECOMING_NOISY receiver | M1 | COMPLETED |
| M4 | Dual Auto-Scroll & Deduplication | AutoScrollEngine (ACTION_SCROLL_FORWARD & dispatchGesture), 450ms settling debounce, DeduplicationEngine ring buffer, bottom detection, safety interruption guards | M2, M3 | COMPLETED |
| M5 | Floating HUD & Compose Settings UI | OverlayHudController, HighlightOverlayView, MainActivity Jetpack Compose M3 UI, SettingsViewModel, UserPreferencesRepository DataStore, QS Tile sync | M1, M4 | COMPLETED |
| M6 | Integration, Hardening & Release | Full integration pass, 100% E2E test verification, memory profiling, R8 optimization, assembleRelease APK | M1, M2, M3, M4, M5, TEST_READY | COMPLETED |
| M7 | v1.1.0 Audit, Hardening & Refinement | 9 critical root-cause fixes (completion announcement race, two-pass dedup, baseline signature bottom detection, async gesture await, HUD bidirectional sentence skipping & speed toggle, decimal split protection, SystemUI noise filter, ViewModelProvider lifecycle), 85/85 unit tests passing | M6 | COMPLETED |

In parallel:
| Track | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| E2E | E2E Testing Suite | Requirements-driven 4-tier test suite (Tiers 1-4), test runner scripts, publishing TEST_READY.md | M1 | COMPLETED |

---

## Interface Contracts

### ReaderStateController & FSM
```kotlin
enum class ReaderState {
    IDLE, SCANNING, SPEAKING, PAUSED, SCROLLING, SETTLING, COMPLETED
}

data class SentenceUnit(
    val id: String,
    val text: String,
    val boundsInScreen: Rect,
    val windowId: Int,
    val packageName: String
)

interface IReaderStateController {
    val state: StateFlow<ReaderState>
    val currentSentence: StateFlow<SentenceUnit?>
    val targetPackage: StateFlow<String?>
    fun startReading(packageName: String)
    fun pauseReading()
    fun resumeReading()
    fun stopReading()
    fun skipForward()
    fun skipBackward()
    fun onUtteranceStart(utteranceId: String)
    fun onUtteranceDone(utteranceId: String)
    fun onScrollSettled()
}
```

### Screen Parser & Spatial Sorter
```kotlin
data class ParsedScreenResult(
    val sentences: List<SentenceUnit>,
    val scrollableContainer: AccessibilityNodeInfo?,
    val screenContentSignature: Long
)

interface IScreenTreeParser {
    fun parseScreen(root: AccessibilityNodeInfo, appPackageName: String): ParsedScreenResult
}

interface ISpatialSorter {
    fun sortNodes(nodes: List<AccessibilityNodeInfo>): List<AccessibilityNodeInfo>
}

interface ISentenceSplitter {
    fun splitIntoSentences(text: String, bounds: Rect, windowId: Int, pkg: String): List<SentenceUnit>
}
```

### TTS Controller
```kotlin
interface ITtsController {
    fun initialize(onInitSuccess: () -> Unit, onInitFailure: () -> Unit)
    fun speak(sentence: SentenceUnit, rate: Float, pitch: Float, language: String)
    fun stop()
    fun shutdown()
    fun setSpeechRate(rate: Float)
    fun isLanguageAvailable(locale: Locale): Int
}
```

### Auto-Scroll Engine & Deduplication
```kotlin
sealed class ScrollResult {
    data object SuccessContainer : ScrollResult()
    data object SuccessGesture : ScrollResult()
    data object Failed : ScrollResult()
}

interface IAutoScrollEngine {
    fun triggerScroll(container: AccessibilityNodeInfo?, service: AccessibilityService): ScrollResult
}

interface IDeduplicationEngine {
    fun filterUnread(sentences: List<SentenceUnit>, screenHeight: Int): List<SentenceUnit>
    fun recordRead(sentence: SentenceUnit)
    fun reset()
}
```

### Preferences Repository
```kotlin
data class UserPreferences(
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val language: String = "AUTO",
    val highlightEnabled: Boolean = true,
    val scrollMode: String = "NODE_PRIORITY",
    val scrollPercent: Float = 0.65f,
    val pauseBetweenSentencesMs: Long = 150L
)

interface IUserPreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun updateSpeechRate(rate: Float)
    suspend fun updateSpeechPitch(pitch: Float)
    suspend fun updateLanguage(lang: String)
    suspend fun updateHighlightEnabled(enabled: Boolean)
    suspend fun updateScrollMode(mode: String)
    suspend fun updateScrollPercent(percent: Float)
    suspend fun updatePauseDuration(pauseMs: Long)
}
```

---

## Code Layout
```
c:\Users\b\AAR\
├── .agents\teamwork\
├── gradle\
│   ├── libs.versions.toml
│   └── wrapper\
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── local.properties
├── app\
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src\
│       ├── main\
│       │   ├── AndroidManifest.xml
│       │   ├── java\com\aar\reader\
│       │   │   ├── AarApplication.kt
│       │   │   ├── service\
│       │   │   │   ├── AutoReaderAccessibilityService.kt
│       │   │   │   ├── AutoReaderTileService.kt
│       │   │   │   ├── ReaderStateController.kt
│       │   │   │   ├── SafetyGuardsReceiver.kt
│       │   │   │   └── model\
│       │   │   │       ├── ReaderState.kt
│       │   │   │       └── SentenceUnit.kt
│       │   │   ├── parser\
│       │   │   │   ├── ScreenTreeParser.kt
│       │   │   │   ├── SpatialSorter.kt
│       │   │   │   ├── SentenceSplitter.kt
│       │   │   │   └── NodeFilters.kt
│       │   │   ├── tts\
│       │   │   │   ├── TtsController.kt
│       │   │   │   └── AudioFocusManager.kt
│       │   │   ├── scroll\
│       │   │   │   ├── AutoScrollEngine.kt
│       │   │   │   ├── DeduplicationEngine.kt
│       │   │   │   └── BottomDetector.kt
│       │   │   ├── ui\
│       │   │   │   ├── MainActivity.kt
│       │   │   │   ├── SettingsViewModel.kt
│       │   │   │   ├── overlay\
│       │   │   │   │   ├── OverlayHudController.kt
│       │   │   │   │   └── HighlightOverlayView.kt
│       │   │   │   ├── theme\
│       │   │   │   │   ├── Color.kt
│       │   │   │   │   ├── Theme.kt
│       │   │   │   │   └── Type.kt
│       │   │   │   └── components\
│       │   │   │       ├── ProminentDisclosureDialog.kt
│       │   │   │       └── SettingsScreen.kt
│       │   │   └── data\
│       │   │       └── UserPreferencesRepository.kt
│       │   └── res\
│       │       ├── values\
│       │       │   ├── strings.xml
│       │       │   └── colors.xml
│       │       ├── xml\
│       │       │   └── accessibility_service_config.xml
│       │       └── drawable\
│       └── test\java\com\aar\reader\
│           ├── parser\
│           ├── tts\
│           ├── scroll\
│           ├── service\
│           └── data\
```
