package com.aar.reader.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Production state model managing the presentation lifecycle, user consent,
 * and system settings redirection for [ProminentDisclosureDialog].
 *
 * Strictly aligns with Google Play's Prominent Disclosure & Consent requirements:
 * 1. Tracks affirmative user consent before enabling AccessibilityService.
 * 2. Emits a one-time redirect request for Android Accessibility Settings.
 * 3. Survives Android configuration changes via [Saver].
 *
 * @param initialVisible Initial visibility of the disclosure dialog.
 * @param initialAccepted Whether disclosure was previously accepted (e.g. from DataStore).
 * @param initialRedirectRequested Initial state of the settings navigation request.
 */
class ProminentDisclosureState(
    initialVisible: Boolean = true,
    initialAccepted: Boolean = false,
    initialRedirectRequested: Boolean = false
) {
    /**
     * Whether the prominent disclosure dialog should be rendered.
     */
    var isDialogVisible: Boolean by mutableStateOf(initialVisible)
        internal set

    /**
     * Whether the user has provided affirmative consent to the accessibility disclosure terms.
     */
    var isDisclosureAccepted: Boolean by mutableStateOf(initialAccepted)
        internal set

    /**
     * Flag indicating navigation to system accessibility settings has been requested.
     * Should be consumed via [consumeRedirectRequest] to avoid repeat triggers.
     */
    var isRedirectToSettingsRequested: Boolean by mutableStateOf(initialRedirectRequested)
        internal set

    /**
     * Called when the user clicks "Agree & Continue" (同意並前往啟用).
     * Records consent, closes the dialog, and requests settings redirection.
     */
    fun onAgreeAndEnable() {
        isDisclosureAccepted = true
        isDialogVisible = false
        isRedirectToSettingsRequested = true
    }

    /**
     * Called when the user clicks "Deny" (拒絕並返回) or dismisses the dialog.
     * Closes the dialog without granting consent or requesting navigation.
     */
    fun onDismiss() {
        isDialogVisible = false
        isRedirectToSettingsRequested = false
    }

    /**
     * Re-displays the disclosure dialog (e.g. when user toggles service from UI without prior consent).
     */
    fun show() {
        isDialogVisible = true
        isRedirectToSettingsRequested = false
    }

    /**
     * Atomically consumes the pending redirect request, resetting it to false.
     *
     * @return `true` if a redirect was pending and is now consumed, `false` otherwise.
     */
    fun consumeRedirectRequest(): Boolean {
        val wasRequested = isRedirectToSettingsRequested
        isRedirectToSettingsRequested = false
        return wasRequested
    }

    /**
     * Resets the disclosure state to its unaccepted, visible default state.
     */
    fun reset() {
        isDialogVisible = true
        isDisclosureAccepted = false
        isRedirectToSettingsRequested = false
    }

    companion object {
        /**
         * Jetpack Compose Saver for persisting [ProminentDisclosureState] across
         * activity recreation and configuration changes.
         */
        val Saver: Saver<ProminentDisclosureState, List<Boolean>> = Saver(
            save = { state ->
                listOf(
                    state.isDialogVisible,
                    state.isDisclosureAccepted,
                    state.isRedirectToSettingsRequested
                )
            },
            restore = { saved ->
                ProminentDisclosureState(
                    initialVisible = saved.getOrElse(0) { true },
                    initialAccepted = saved.getOrElse(1) { false },
                    initialRedirectRequested = saved.getOrElse(2) { false }
                )
            }
        )
    }
}

/**
 * Creates and remembers a [ProminentDisclosureState] across recompositions and configuration changes.
 *
 * @param initialVisible Initial visibility state of the dialog (defaults to true).
 * @param initialAccepted Initial acceptance state (defaults to false).
 */
@Composable
fun rememberProminentDisclosureState(
    initialVisible: Boolean = true,
    initialAccepted: Boolean = false
): ProminentDisclosureState {
    return rememberSaveable(saver = ProminentDisclosureState.Saver) {
        ProminentDisclosureState(
            initialVisible = initialVisible,
            initialAccepted = initialAccepted
        )
    }
}
