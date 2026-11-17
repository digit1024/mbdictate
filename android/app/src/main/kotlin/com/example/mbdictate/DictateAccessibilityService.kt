package com.example.mbdictate

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.mbdictate.focus.FocusDecision
import com.example.mbdictate.focus.FocusDecider
import com.example.mbdictate.focus.classifyFocus

class DictateAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile
        var instance: DictateAccessibilityService? = null
            private set

        private const val POLL_MS = 400L
        private const val EVENT_COALESCE_MS = 150L
    }

    private var overlay: OverlayController? = null
    private val handler = Handler(Looper.getMainLooper())
    private val decider = FocusDecider()

    private val pollRunnable = object : Runnable {
        override fun run() {
            evaluateFocus()
            handler.postDelayed(this, POLL_MS)
        }
    }

    private val eventRunnable = Runnable { evaluateFocus() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        DictationController.attach(this)
        overlay = OverlayController(this).also {
            it.attachListener()
        }
        try {
            DictationService.ensureRunning(this)
        } catch (_: Exception) {
        }
        handler.post(pollRunnable)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        handler.removeCallbacks(eventRunnable)
        handler.postDelayed(eventRunnable, EVENT_COALESCE_MS)
    }

    private fun evaluateFocus() {
        val root = rootInActiveWindow
        val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val editable = focused != null &&
            focused.isEditable &&
            !focused.isPassword &&
            focused.packageName?.toString() != packageName
        val activeIsIme = windows.any {
            it.isActive && it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }
        val sample = classifyFocus(
            hasRoot = root != null,
            activeWindowIsIme = activeIsIme,
            focusedEditable = editable,
        )

        val overlayController = overlay ?: return
        when (decider.feed(sample)) {
            FocusDecision.SHOW -> overlayController.showBubble()
            FocusDecision.HIDE -> overlayController.hideBubble()
            FocusDecision.NONE -> {}
        }
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        handler.removeCallbacks(eventRunnable)
        overlay?.destroy()
        overlay = null
        DictationController.detach()
        if (instance === this) instance = null
        super.onDestroy()
    }
}