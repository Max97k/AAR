<div align="center">

# 🔊 AAR — Accessibility Auto-Reader for Android

**A modern, lightweight, privacy-first Android Accessibility auto-reader that extracts on-screen text, speaks it aloud via native TTS, and automatically scrolls continuously until the end of the page.**

[![Platform](https://img.shields.io/badge/Platform-Android%2017%20(API%2037%2B)-3DDC84?style=flat-square&logo=android&logoColor=white)](https://android.com)
[![Version](https://img.shields.io/badge/Version-v1.1.0-blue?style=flat-square)](https://github.com/Max97k/AAR)
[![Tests](https://img.shields.io/badge/Tests-85%2F85%20Passing-brightgreen?style=flat-square)](https://github.com/Max97k/AAR)
[![Target Device](https://img.shields.io/badge/Target-Google%20Pixel%208%20Pro-4285F4?style=flat-square&logo=google&logoColor=white)](https://store.google.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%7C%20Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20Offline%20%7C%20No%20Internet%20Permission-success?style=flat-square&logo=shield&logoColor=white)](#-privacy--security-by-design)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)

[English](README.md) • [繁體中文](README_zh.md) • [Specification (開發規格書)](SPEC.md)

</div>

---

## 📖 Overview

**AAR (Accessibility Auto-Reader)** is an open-source Android utility built with **Kotlin** and **Jetpack Compose (Material 3)**. Powered by Android's native `AccessibilityService` and `TextToSpeech` (TTS) engine, AAR parses visible text on the active screen in real time, reads it aloud sentence by sentence, and automatically performs downward scroll gestures to provide a hands-free, continuous reading experience across articles, e-books, web pages, and social feeds.

Unlike cloud-based screen readers or third-party voice assistants, AAR processes all screen hierarchy analysis and speech synthesis **100% locally on your device**. The application requests **zero internet permissions (`android.permission.INTERNET`)**, guaranteeing that your on-screen content and personal data never leave your phone.

---

## ✨ Key Features

| Feature | Description |
| :--- | :--- |
| 🧠 **Smart DFS Screen Parser** | Traverses `AccessibilityNodeInfo` view hierarchies via Depth-First Search (DFS), sorting visible text top-to-bottom and left-to-right by screen coordinates. |
| 🧹 **Noise & Control Filtering** | Automatically excludes hidden nodes, decorative icons, password fields, and non-content UI controls (e.g., "Copy", "Share", "Back", "More"). |
| 🔄 **Continuous Auto-Scroll** | Dual-engine downward scrolling: prioritizes native `ACTION_SCROLL_FORWARD` node actions and seamlessly falls back to smooth `dispatchGesture` swipe paths. |
| 🛡️ **Overlap Deduplication** | Sliding-window text fingerprint buffer prevents re-reading overlapping paragraphs remaining at the top of the screen after a scroll gesture. |
| 🛑 **Bottom-of-Page Detection** | Automatically detects when a page can no longer scroll or no new text appears, gracefully concluding the reading session. |
| ⚡ **Quick Settings Tile & Enhanced HUD** | Toggle reading instantly from system Quick Settings (`TileService`) or control playback via a collapsible `TYPE_ACCESSIBILITY_OVERLAY` floating pill with **Skip Previous/Next (`⏮`/`⏭`)**, **Quick Speed Cycler (`1.0x`)**, and Play/Pause. |
| 🛡️ **Two-Pass Overlap Deduplication** | Two-pass deduplication: Pass 1 tail-anchor window cutoff eliminates dynamic headers and overlapping viewport text; Pass 2 64-bit FNV-1a sliding-window fingerprint ring buffer filters already-read sentences. |
| 🛑 **Smart Bottom-of-Page Detection** | Accurately identifies page bottom using pre-scroll baseline screen signature comparison and handles blank pages without false-positive premature completions. |
| 🎯 **Visual Reading Highlight** | Optional Material 3 highlight box overlay that tracks and frames the currently spoken paragraph in real time on screen. |
| 🗣️ **Native TTS & Offline Pack Check** | Supports adjustable speech rate (`0.5x` – `2.0x`), inter-sentence pause slider (`0ms` – `500ms`), Traditional Chinese (`zh-TW`) & English (`en-US`) switching, decimal number (`3.14`) protection, and direct offline voice pack installation. |
## 📱 Screenshots (Tested on Google Pixel 8 Pro • Android 17)

<div align="center">

| Material 3 Settings UI | Reading Highlight & HUD | Auto-Scroll & Deduplication | Paused Floating Controls |
| :---: | :---: | :---: | :---: |
| <img src="docs/screenshots/aar_main_settings.png" width="210" /> | <img src="docs/screenshots/aar_reading_highlight.png" width="210" /> | <img src="docs/screenshots/aar_autoscroll_dedup.png" width="210" /> | <img src="docs/screenshots/aar_hud_paused.png" width="210" /> |

</div>

---

## 🔒 Privacy & Security by Design

Because `AccessibilityService` has access to on-screen content, privacy is the core pillar of AAR:

```mermaid
flowchart LR
    A[Active Screen View Tree] --> B[AAR AccessibilityService]
    B -->|In-Memory DFS & Filter| C[Android System TTS Engine]
    B -->|Auto-Scroll Action / Gesture| A
    B x-.-x|NO INTERNET PERMISSION| D[External Cloud / Server]
    B x-.-x|NO DISK LOGGING| E[Local Storage]
```

1. **Zero Internet Access**: `AndroidManifest.xml` does not declare `android.permission.INTERNET`. The app is physically incapable of transmitting screen content over the network.
2. **Ephemeral In-Memory Processing**: Extracted screen text is held strictly in transient memory queues for immediate TTS playback and is never written to disk or databases.
3. **Password & Sensitive Node Exclusion**: Nodes marked with `isPassword == true` are strictly ignored during tree traversal.
4. **No Third-Party SDKs**: Zero analytics, zero ad networks, and zero tracking dependencies.

---

## 🏗️ Architecture & Tech Stack

AAR follows Modern Android Development (MAD) best practices with Clean Architecture and Unidirectional Data Flow (UDF):

```text
AAR Architecture
├── Presentation Layer (Jetpack Compose + Material 3)
│   ├── MainActivity.kt ── SettingsViewModel.kt (StateFlow / UDF)
│   └── OverlayHudController.kt (TYPE_ACCESSIBILITY_OVERLAY Control & Highlight Box)
├── Core Service & Domain Layer
│   ├── AutoReaderAccessibilityService.kt (AccessibilityService + Lifecycle)
│   ├── AutoReaderTileService.kt (Quick Settings TileService)
│   ├── ScreenTreeParser.kt (DFS Traversal, Coordinate Sorting & Noise Filter)
│   ├── AutoScrollEngine.kt (Node Scroll + Gesture Dispatch + Deduplication)
│   └── TtsController.kt (TextToSpeech, UtteranceProgressListener, AudioFocus)
└── Data Layer (Jetpack DataStore)
    └── UserPreferencesRepository.kt (Speech Rate, Locale, Scroll & Filter Settings)
```

### Technology Highlights

- **Platform & Target**: Android 17 (API Level 37+) • Target Hardware: **Google Pixel 8 Pro**.
- **UI & Design**: [Jetpack Compose](https://developer.android.com/jetpack/compose), Material Design 3 (M3) Dynamic Color.
- **Language & Concurrency**: [Kotlin 2.2+](https://kotlinlang.org/), Kotlin Coroutines, `StateFlow`.
- **Core Android APIs**:
  - `android.accessibilityservice.AccessibilityService` & `GestureDescription`
  - `android.view.accessibility.AccessibilityNodeInfo`
  - `android.speech.tts.TextToSpeech` & `UtteranceProgressListener`
  - `android.service.quicksettings.TileService`
- **Persistence**: Jetpack DataStore (`Preferences DataStore`) for reactive user settings.
- **Quality & Testing**: JUnit 4, Robolectric, Kotlinx Coroutines Test, JaCoCo Code Coverage.

---

## 🔄 Automated Reading & Scroll State Machine

```mermaid
stateDiagram-v2
    [*] --> IDLE
    IDLE --> SCANNING : User toggles QS Tile / Overlay Start
    SCANNING --> SPEAKING : Filtered unread sentences queued
    SCANNING --> SCROLLING : No unread text on current viewport
    SPEAKING --> SPEAKING : Next sentence (Utterance onDone)
    SPEAKING --> SCROLLING : Viewport queue finished
    SPEAKING --> PAUSED : User pause / Touch interrupt / AudioFocus lost
    PAUSED --> SPEAKING : User resumes
    PAUSED --> IDLE : User stops / App switched / Screen off
    SCROLLING --> SETTLING : Scroll action or gesture dispatched (450ms debounce)
    SETTLING --> SCANNING : Re-scan active window & deduplicate overlap
    SCROLLING --> COMPLETED : Bottom of page reached
    COMPLETED --> IDLE : Announce completion & reset
```

---

## 🗺️ Development Milestones & Roadmap

```mermaid
flowchart LR
    M1["M1: Foundation & Service Skeleton"] --> M2["M2: DFS Parser & Smart Segmentation"]
    M2 --> M3["M3: Native TTS & AudioFocus"]
    M3 --> M4["M4: Dual Auto-Scroll & Deduplication"]
    M4 --> M5["M5: Overlay HUD & Material 3 UI"]
    M5 --> M6["M6: Pixel 8 Pro QA & Release"]
    M6 --> M7["M7: v1.1.0 Audit, Hardening & Refinement"]
```

| Milestone | Phase | Key Deliverables | Status |
| :--- | :--- | :--- | :--- |
| **M1** | **Foundation & Service Skeleton** | • Gradle Kotlin DSL project (`minSdk = 37`, `targetSdk = 37`, zero internet permission)<br>• `AutoReaderAccessibilityService` & `accessibility_service_config.xml`<br>• Quick Settings `AutoReaderTileService` & M3 Prominent Disclosure dialog | ✅ Completed |
| **M2** | **DFS Screen Parser & Segmentation** | • `ScreenTreeParser` with DFS traversal, visibility/password/noise filtering<br>• Top-to-bottom, left-to-right spatial coordinate sorting (`getBoundsInScreen`)<br>• Sentence-level punctuation splitter | ✅ Completed |
| **M3** | **Native TTS & AudioFocus** | • `TtsController` with `UtteranceProgressListener` & offline voice pack check<br>• `zh-TW` / `en-US` switching + smart bilingual character-ratio detection<br>• `AudioFocus` & `ACTION_AUDIO_BECOMING_NOISY` auto-pause | ✅ Completed |
| **M4** | **Dual Auto-Scroll & Deduplication** | • `AutoScrollEngine`: priority `ACTION_SCROLL_FORWARD` + fallback `dispatchGesture`<br>• `SETTLING` debounce (450ms) & Sliding Window Fingerprint deduplication<br>• Bottom-of-page detection & app-switch / notification-shade / screen-off guards | ✅ Completed |
| **M5** | **Overlay HUD & Material 3 UI** | • `TYPE_ACCESSIBILITY_OVERLAY` floating control pill & real-time reading highlight box<br>• Jetpack Compose M3 Settings screen (`MainActivity`, `SettingsViewModel`)<br>• Reactive persistence via Jetpack DataStore | ✅ Completed |
| **M6** | **Pixel 8 Pro QA & Release** | • End-to-end verified on **Google Pixel 8 Pro** (API 37)<br>• Zero-network verified, clean background lifecycle detachment | ✅ Completed & Verified |
| **M7** | **v1.1.0 Audit, Hardening & Refinement** | • 9 critical root-cause bug fixes (completion announcement race, 2-pass dedup, baseline signature bottom detection, async gesture await, HUD bidirectional sentence skipping & speed toggle, decimal split protection, SystemUI filter, ViewModelProvider lifecycle)<br>• 85/85 unit tests passing (100% success rate)<br>• R8 / ProGuard release rules fortified | ✅ Completed & Verified |

---

## 📋 Development Specification

For detailed architectural requirements, node filtering heuristics, TTS language verification, and auto-scroll state control, please refer to the full specification document:
👉 **[SPEC.md — 無障礙朗讀與自動捲動應用程式開發規格書](SPEC.md)**

---

## 🤝 Contributing

Contributions, issue reports, and feature suggestions are welcome!

1. **Fork** the repository.
2. **Create** a feature branch: `git checkout -b feature/awesome-feature`.
3. **Commit** your changes following conventional commits: `git commit -m "feat: add support for xyz"`.
4. **Push** to your branch: `git push origin feature/awesome-feature`.
5. **Open a Pull Request**.

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
