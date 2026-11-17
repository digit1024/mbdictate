package com.example.mbdictate

import com.example.mbdictate.focus.FocusDecision
import com.example.mbdictate.focus.FocusDecider
import com.example.mbdictate.focus.FocusSample
import com.example.mbdictate.focus.classifyFocus
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusDeciderTest {
    @Test
    fun showNeedsConsecutiveEditableSamples() {
        val decider = FocusDecider(showAfter = 2, hideAfter = 3)
        assertEquals(FocusDecision.NONE, decider.feed(FocusSample.EDITABLE))
        assertEquals(FocusDecision.SHOW, decider.feed(FocusSample.EDITABLE))
        assertEquals(true, decider.visible)
    }

    @Test
    fun singleNotEditableSampleDoesNotHide() {
        val decider = FocusDecider(showAfter = 2, hideAfter = 3)
        decider.feed(FocusSample.EDITABLE)
        decider.feed(FocusSample.EDITABLE)
        assertEquals(FocusDecision.NONE, decider.feed(FocusSample.NOT_EDITABLE))
        assertEquals(FocusDecision.NONE, decider.feed(FocusSample.NOT_EDITABLE))
        assertEquals(true, decider.visible)
        assertEquals(FocusDecision.HIDE, decider.feed(FocusSample.NOT_EDITABLE))
        assertEquals(false, decider.visible)
    }

    @Test
    fun unknownSamplesNeverChangeVisibility() {
        val decider = FocusDecider(showAfter = 2, hideAfter = 3)
        assertEquals(FocusDecision.NONE, decider.feed(FocusSample.UNKNOWN))
        decider.feed(FocusSample.EDITABLE)
        decider.feed(FocusSample.EDITABLE)
        assertEquals(FocusDecision.NONE, decider.feed(FocusSample.UNKNOWN))
        assertEquals(true, decider.visible)
    }

    @Test
    fun interruptedRunRestartsTheCounter() {
        val decider = FocusDecider(showAfter = 2, hideAfter = 3)
        decider.feed(FocusSample.EDITABLE)
        decider.feed(FocusSample.NOT_EDITABLE)
        assertEquals(FocusDecision.NONE, decider.feed(FocusSample.EDITABLE))
        assertEquals(false, decider.visible)
    }

    @Test
    fun classificationTreatsMissingRootAndImeAsUnknown() {
        assertEquals(
            FocusSample.UNKNOWN,
            classifyFocus(hasRoot = false, activeWindowIsIme = false, focusedEditable = false),
        )
        assertEquals(
            FocusSample.UNKNOWN,
            classifyFocus(hasRoot = true, activeWindowIsIme = true, focusedEditable = false),
        )
        assertEquals(
            FocusSample.EDITABLE,
            classifyFocus(hasRoot = true, activeWindowIsIme = true, focusedEditable = true),
        )
        assertEquals(
            FocusSample.NOT_EDITABLE,
            classifyFocus(hasRoot = true, activeWindowIsIme = false, focusedEditable = false),
        )
    }
}