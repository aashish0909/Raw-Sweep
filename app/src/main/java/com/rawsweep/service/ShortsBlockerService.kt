package com.rawsweep.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class ShortsBlockerService : AccessibilityService() {

    companion object {
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val BLOCK_COOLDOWN_MS = 2000L
        private const val SCAN_COOLDOWN_MS = 500L

        fun isServiceEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            val expected = ComponentName(context, ShortsBlockerService::class.java)

            while (splitter.hasNext()) {
                val component = ComponentName.unflattenFromString(splitter.next())
                if (component == expected) return true
            }
            return false
        }
    }

    private var lastBlockTime = 0L
    private var lastScanTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() != YOUTUBE_PACKAGE) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> handleClick(event)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleContentChange()
        }
    }

    @Suppress("DEPRECATION")
    private fun handleClick(event: AccessibilityEvent) {
        val node = event.source ?: return
        if (isShortsLabel(node)) {
            blockShorts()
        }
    }

    @Suppress("DEPRECATION")
    private fun handleContentChange() {
        val now = System.currentTimeMillis()
        if (now - lastScanTime < SCAN_COOLDOWN_MS) return
        lastScanTime = now

        val root = rootInActiveWindow ?: return
        if (isShortsTabSelected(root)) {
            blockShorts()
        }
    }

    private fun isShortsLabel(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        return text.equals("Shorts", ignoreCase = true) ||
                desc.equals("Shorts", ignoreCase = true)
    }

    @Suppress("DEPRECATION")
    private fun isShortsTabSelected(root: AccessibilityNodeInfo): Boolean {
        val nodes = root.findAccessibilityNodeInfosByText("Shorts")
        return nodes.any { node ->
            node.isSelected && (node.isClickable || node.isFocusable)
        }
    }

    private fun blockShorts() {
        val now = System.currentTimeMillis()
        if (now - lastBlockTime < BLOCK_COOLDOWN_MS) return
        lastBlockTime = now

        performGlobalAction(GLOBAL_ACTION_BACK)
        Toast.makeText(this, "Shorts blocked", Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {}
}
