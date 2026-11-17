package com.example.mbdictate.insert

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

data class InsertResult(
    val success: Boolean,
    val copied: Boolean,
    val message: String? = null,
)

object TextInserter {
    fun insert(service: AccessibilityService, text: String): InsertResult {
        if (text.isBlank()) return InsertResult(false, false, "Nothing to insert")
        val root = service.rootInActiveWindow
            ?: return InsertResult(false, false, "No active window")
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return InsertResult(false, false, "No focused input")
        return insertIntoNode(service, node, text)
    }

    private fun insertIntoNode(
        service: AccessibilityService,
        node: AccessibilityNodeInfo,
        text: String,
    ): InsertResult {
        if (!node.isEditable) {
            return InsertResult(false, false, "Focused element is not editable")
        }
        if (node.isPassword) {
            return InsertResult(false, false, "Password fields are not supported")
        }

        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val existing = node.text?.toString() ?: ""
        val selectionStart = node.textSelectionStart
            .takeIf { it in 0..existing.length } ?: existing.length
        val selectionEnd = node.textSelectionEnd
            .takeIf { it in selectionStart..existing.length } ?: selectionStart
        val combined = buildString {
            append(existing.substring(0, selectionStart))
            append(text)
            append(existing.substring(selectionEnd))
        }

        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                combined,
            )
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
            return InsertResult(true, false)
        }

        val clipboard =
            service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("mbdictate", text))
        if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
            return InsertResult(true, false)
        }
        return InsertResult(
            false,
            true,
            "Could not insert text. Copied to clipboard.",
        )
    }
}
