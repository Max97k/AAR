package com.aar.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TtsLanguageDetectionTest {

    @Test
    fun detectLanguage_identifiesPureTraditionalChinese() {
        val text = "無障礙朗讀與自動捲動應用程式開發規格書"
        val detected = TtsController.detectLanguage(text)
        assertEquals(Locale.TRADITIONAL_CHINESE, detected)
    }

    @Test
    fun detectLanguage_identifiesPureEnglish() {
        val text = "Accessibility Auto-Reader reads screen text continuously."
        val detected = TtsController.detectLanguage(text)
        assertEquals(Locale.US, detected)
    }

    @Test
    fun detectLanguage_identifiesChinesePredominantMixedText() {
        val text = "這款 Android 應用的 Accessibility 服務非常流暢且完全本地離線運作。"
        // 24 Chinese chars vs 20 Latin chars
        val detected = TtsController.detectLanguage(text)
        assertEquals(Locale.TRADITIONAL_CHINESE, detected)
    }

    @Test
    fun detectLanguage_identifiesEnglishPredominantMixedText() {
        val text = "The application implements an on-screen text parser with 中文 support."
        val detected = TtsController.detectLanguage(text)
        assertEquals(Locale.US, detected)
    }

    @Test
    fun detectLanguage_handlesEmptyOrPunctuationDefaults() {
        val text = "123456... --- !??"
        val detected = TtsController.detectLanguage(text)
        assertEquals(Locale.TRADITIONAL_CHINESE, detected)
    }
}
