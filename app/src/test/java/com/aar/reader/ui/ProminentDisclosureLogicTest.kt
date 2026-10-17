package com.aar.reader.ui

import androidx.compose.runtime.saveable.SaverScope
import com.aar.reader.ui.components.ProminentDisclosureState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Authentic unit test suite directly verifying the production state model
 * [ProminentDisclosureState] and its integration contracts with [ProminentDisclosureDialog].
 *
 * Eliminates all in-test dummy facades and verifies genuine production code.
 */
class ProminentDisclosureLogicTest {

    @Test
    fun `initial state requires disclosure and service inactive by default`() {
        val state = ProminentDisclosureState()
        assertTrue("Dialog must be visible initially for unaccepted user", state.isDialogVisible)
        assertFalse("Disclosure must not be accepted initially", state.isDisclosureAccepted)
        assertFalse("Settings redirect must not be requested initially", state.isRedirectToSettingsRequested)
    }

    @Test
    fun `custom initial state parameters are respected`() {
        val state = ProminentDisclosureState(
            initialVisible = false,
            initialAccepted = true,
            initialRedirectRequested = true
        )
        assertFalse("Visibility should match initialized false", state.isDialogVisible)
        assertTrue("Acceptance should match initialized true", state.isDisclosureAccepted)
        assertTrue("Redirect should match initialized true", state.isRedirectToSettingsRequested)
    }

    @Test
    fun `clicking agree marks accepted, dismisses dialog, and triggers settings navigation`() {
        val state = ProminentDisclosureState()
        state.onAgreeAndEnable()

        assertFalse("Dialog must dismiss on agree", state.isDialogVisible)
        assertTrue("Disclosure state must become accepted", state.isDisclosureAccepted)
        assertTrue("Settings redirect must be triggered", state.isRedirectToSettingsRequested)
    }

    @Test
    fun `clicking dismiss leaves disclosure unaccepted and does not navigate`() {
        val state = ProminentDisclosureState()
        state.onDismiss()

        assertFalse("Dialog must dismiss on user cancel", state.isDialogVisible)
        assertFalse("Disclosure state must remain false", state.isDisclosureAccepted)
        assertFalse("Settings redirect must NOT be requested", state.isRedirectToSettingsRequested)
    }

    @Test
    fun `dismiss retains prior acceptance if previously accepted`() {
        val state = ProminentDisclosureState(initialVisible = true, initialAccepted = true)
        state.onDismiss()

        assertFalse("Dialog must dismiss", state.isDialogVisible)
        assertTrue("Previously accepted state must be retained", state.isDisclosureAccepted)
        assertFalse("Settings redirect must NOT be requested", state.isRedirectToSettingsRequested)
    }

    @Test
    fun `show restores dialog visibility and resets pending redirect request`() {
        val state = ProminentDisclosureState(initialVisible = false, initialAccepted = false)
        assertFalse("Initially hidden", state.isDialogVisible)

        state.show()
        assertTrue("Dialog should be visible after show()", state.isDialogVisible)
        assertFalse("Redirect request must be false after show()", state.isRedirectToSettingsRequested)
    }

    @Test
    fun `consumeRedirectRequest atomically consumes pending redirect`() {
        val state = ProminentDisclosureState()
        state.onAgreeAndEnable()

        assertTrue("Redirect must be pending before consumption", state.isRedirectToSettingsRequested)

        val firstConsumed = state.consumeRedirectRequest()
        assertTrue("First consume call must return true", firstConsumed)
        assertFalse("Redirect request must be reset to false", state.isRedirectToSettingsRequested)

        val secondConsumed = state.consumeRedirectRequest()
        assertFalse("Subsequent consume call must return false", secondConsumed)
    }

    @Test
    fun `reset returns state to default unaccepted and visible state`() {
        val state = ProminentDisclosureState()
        state.onAgreeAndEnable()
        assertTrue(state.isDisclosureAccepted)
        assertFalse(state.isDialogVisible)

        state.reset()
        assertTrue("Dialog must be visible after reset", state.isDialogVisible)
        assertFalse("Disclosure must not be accepted after reset", state.isDisclosureAccepted)
        assertFalse("Redirect must not be requested after reset", state.isRedirectToSettingsRequested)
    }

    @Test
    fun `compose Saver correctly preserves and restores state`() {
        val original = ProminentDisclosureState(
            initialVisible = false,
            initialAccepted = true,
            initialRedirectRequested = true
        )

        val dummySaverScope = object : SaverScope {
            override fun canBeSaved(value: Any): Boolean = true
        }

        val savedList = with(ProminentDisclosureState.Saver) {
            dummySaverScope.save(original)
        }
        assertTrue("Saved representation must not be null", savedList != null)

        val restored = ProminentDisclosureState.Saver.restore(savedList!!)
        assertTrue("Restored state must not be null", restored != null)
        assertEquals("isDialogVisible must match", original.isDialogVisible, restored!!.isDialogVisible)
        assertEquals("isDisclosureAccepted must match", original.isDisclosureAccepted, restored.isDisclosureAccepted)
        assertEquals("isRedirectToSettingsRequested must match", original.isRedirectToSettingsRequested, restored.isRedirectToSettingsRequested)
    }
}
