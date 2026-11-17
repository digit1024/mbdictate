package com.example.mbdictate

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class DictateAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile
        var instance: DictateAccessibilityService? = null
            private set
    }

    private var overlay: OverlayController? = null
    private val handler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            evaluateFocus()
            handler.postDelayed(this, 600)
        }
    }

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
        evaluateFocus()
    }

    private fun evaluateFocus() {
        val root = rootInActiveWindow
        val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val editable = focused != null &&
            focused.isEditable &&
            !focused.isPassword &&
            focused.packageName?.toString() != packageName

        val overlayController = overlay ?: return
        if (editable) {
            overlayController.showBubble()
        } else if (!DictationController.isBusy()) {
            overlayController.hideBubble()
        }
    }

    override fun onInterrupt() {
        if (!DictationController.isBusy()) {
            overlay?.hideBubble()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        overlay?.destroy()
        overlay = null
        DictationController.detach()
        if (instance === this) instance = null
        super.onDestroy()
    }
}
