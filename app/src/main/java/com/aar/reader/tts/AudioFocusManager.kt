package com.aar.reader.tts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Manages audio focus acquisition, transient ducking, and noisy audio disconnection events.
 */
class AudioFocusManager(
    private val context: Context,
    private val onAudioFocusLost: () -> Unit,
    private val onAudioFocusGained: () -> Unit = {}
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var isFocusHeld = false
    private var isReceiverRegistered = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                // Headset unplugged or Bluetooth disconnected -> pause immediately
                onAudioFocusLost()
            }
        }
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                isFocusHeld = false
                onAudioFocusLost()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Ducking or pause reading depending on focus priority
                onAudioFocusLost()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                isFocusHeld = true
                onAudioFocusGained()
            }
        }
    }

    fun requestAudioFocus(): Boolean {
        if (audioManager == null) return false
        if (isFocusHeld) return true

        registerNoisyReceiver()

        val playbackAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(playbackAttributes)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .setAcceptsDelayedFocusGain(false)
            .build()

        val res = audioManager.requestAudioFocus(focusRequest!!)
        isFocusHeld = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        return isFocusHeld
    }

    fun abandonAudioFocus() {
        if (!isFocusHeld) return
        unregisterNoisyReceiver()
        if (focusRequest != null) {
            audioManager?.abandonAudioFocusRequest(focusRequest!!)
            focusRequest = null
        }
        isFocusHeld = false
    }

    private fun registerNoisyReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            context.registerReceiver(noisyReceiver, filter)
            isReceiverRegistered = true
        }
    }

    private fun unregisterNoisyReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(noisyReceiver)
            } catch (e: IllegalArgumentException) {
                // Ignored if already unregistered
            }
            isReceiverRegistered = false
        }
    }
}
