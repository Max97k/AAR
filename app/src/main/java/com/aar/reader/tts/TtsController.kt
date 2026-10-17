package com.aar.reader.tts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.aar.reader.service.model.SentenceUnit
import java.util.Locale

interface ITtsController {
    val isInitialized: Boolean
    fun initialize(onInitSuccess: () -> Unit = {}, onInitFailure: () -> Unit = {})
    fun speak(sentence: SentenceUnit, rate: Float = 1.0f, pitch: Float = 1.0f, language: String = "AUTO"): Boolean
    fun stop()
    fun shutdown()
    fun setSpeechRate(rate: Float)
    fun setPitch(pitch: Float)
    fun isLanguageAvailable(locale: Locale): Int
    fun checkVoiceDataInstalled(locale: Locale): Boolean
    fun getInstallVoiceDataIntent(): Intent
}

class TtsController(
    private val context: Context,
    private val audioFocusManager: AudioFocusManager? = null,
    private val onUtteranceStarted: (String) -> Unit = {},
    private val onUtteranceCompleted: (String) -> Unit = {},
    private val onUtteranceError: (String, Int) -> Unit = { _, _ -> },
    private val onUtteranceStopped: (String, Boolean) -> Unit = { _, _ -> }
) : ITtsController {

    private var tts: TextToSpeech? = null
    override var isInitialized: Boolean = false
        private set

    override fun initialize(onInitSuccess: () -> Unit, onInitFailure: () -> Unit) {
        if (isInitialized && tts != null) {
            onInitSuccess()
            return
        }

        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                setupUtteranceListener()
                onInitSuccess()
            } else {
                isInitialized = false
                onInitFailure()
            }
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                utteranceId?.let { onUtteranceStarted(it) }
            }

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { onUtteranceCompleted(it) }
            }

            override fun onError(utteranceId: String?) {
                utteranceId?.let { onUtteranceError(it, -1) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { onUtteranceError(it, errorCode) }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                // Separate interrupted/stopped callback from normal completion to prevent
                // prematurely marking interrupted sentences as read in DeduplicationEngine.
                utteranceId?.let { onUtteranceStopped(it, interrupted) }
            }
        })
    }

    override fun speak(
        sentence: SentenceUnit,
        rate: Float,
        pitch: Float,
        language: String
    ): Boolean {
        if (!isInitialized || tts == null) return false

        // Request audio focus before speech synthesis
        audioFocusManager?.requestAudioFocus()

        val targetLocale = when (language.uppercase()) {
            "ZH", "ZH-TW", "TRADITIONAL_CHINESE" -> Locale.TRADITIONAL_CHINESE
            "EN", "EN-US" -> Locale.US
            else -> detectLanguage(sentence.text)
        }

        return tts?.let { engine ->
            engine.setLanguage(targetLocale)
            engine.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
            engine.setPitch(pitch.coerceIn(0.5f, 1.5f))

            val maxInputLength = TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(400)
            val safeText = if (sentence.text.length > maxInputLength) {
                sentence.text.substring(0, maxInputLength)
            } else {
                sentence.text
            }

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, sentence.id)
            }
            val result = engine.speak(safeText, TextToSpeech.QUEUE_FLUSH, params, sentence.id)
            result == TextToSpeech.SUCCESS
        } ?: false
    }

    override fun stop() {
        tts?.stop()
        audioFocusManager?.abandonAudioFocus()
    }

    override fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    override fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    override fun setPitch(pitch: Float) {
        tts?.setPitch(pitch.coerceIn(0.5f, 1.5f))
    }

    override fun isLanguageAvailable(locale: Locale): Int {
        return tts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
    }

    override fun checkVoiceDataInstalled(locale: Locale): Boolean {
        val result = isLanguageAvailable(locale)
        return result >= TextToSpeech.LANG_AVAILABLE
    }

    override fun getInstallVoiceDataIntent(): Intent {
        return Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
    }

    companion object {
        /**
         * Detects whether text is predominantly Chinese or English.
         */
        fun detectLanguage(text: String): Locale {
            var cjkCount = 0
            var latinCount = 0

            for (c in text) {
                when {
                    Character.UnicodeScript.of(c.code) == Character.UnicodeScript.HAN -> cjkCount++
                    c in 'a'..'z' || c in 'A'..'Z' -> latinCount++
                }
            }

            return if (latinCount > cjkCount) {
                Locale.US
            } else {
                Locale.TRADITIONAL_CHINESE
            }
        }
    }
}
