package com.example.mbdictate.focus

enum class FocusSample {
    EDITABLE,
    NOT_EDITABLE,
    UNKNOWN,
}

enum class FocusDecision {
    NONE,
    SHOW,
    HIDE,
}

internal fun classifyFocus(
    hasRoot: Boolean,
    activeWindowIsIme: Boolean,
    focusedEditable: Boolean,
): FocusSample {
    return when {
        !hasRoot -> FocusSample.UNKNOWN
        focusedEditable -> FocusSample.EDITABLE
        activeWindowIsIme -> FocusSample.UNKNOWN
        else -> FocusSample.NOT_EDITABLE
    }
}

class FocusDecider(
    private val showAfter: Int = 2,
    private val hideAfter: Int = 3,
) {
    private var editableRun = 0
    private var notEditableRun = 0

    var visible: Boolean = false
        private set

    fun feed(sample: FocusSample): FocusDecision {
        when (sample) {
            FocusSample.UNKNOWN -> return FocusDecision.NONE
            FocusSample.EDITABLE -> {
                editableRun++
                notEditableRun = 0
            }
            FocusSample.NOT_EDITABLE -> {
                notEditableRun++
                editableRun = 0
            }
        }
        if (!visible && editableRun >= showAfter) {
            visible = true
            editableRun = 0
            return FocusDecision.SHOW
        }
        if (visible && notEditableRun >= hideAfter) {
            visible = false
            notEditableRun = 0
            return FocusDecision.HIDE
        }
        return FocusDecision.NONE
    }

    fun reset() {
        editableRun = 0
        notEditableRun = 0
        visible = false
    }
}