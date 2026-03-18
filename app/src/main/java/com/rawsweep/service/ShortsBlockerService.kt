package com.rawsweep.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class ShortsBlockerService : AccessibilityService() {

    companion object {
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val BLOCK_COOLDOWN_MS = 2000L
        private const val SCAN_COOLDOWN_MS = 200L
        private const val PREFS_NAME = "shorts_blocker"
        private const val KEY_BLOCKING_ENABLED = "blocking_enabled"

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

        fun isBlockingEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_BLOCKING_ENABLED, true)
        }

        fun setBlockingEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_BLOCKING_ENABLED, enabled)
                .apply()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastBlockTime = 0L
    private var lastScanTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() != YOUTUBE_PACKAGE) return
        if (!isBlockingEnabled(this)) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                handleClick(event)
                scheduleDelayedChecks(300)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                checkForShorts()
                scheduleDelayedChecks(300, 700, 1200)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                throttledCheck()
            }
        }
    }

    private fun handleClick(event: AccessibilityEvent) {
        val node = event.source ?: return

        if (isShortsText(node)) {
            blockShorts()
            return
        }

        // Walk up the parent chain — click might land on the icon inside the tab
        var current = node.parent
        var depth = 0
        while (current != null && depth < 4) {
            if (isShortsText(current)) {
                blockShorts()
                return
            }
            current = current.parent
            depth++
        }

        // Check immediate children — click might land on a container wrapping the label
        for (i in 0 until node.childCount.coerceAtMost(5)) {
            val child = node.getChild(i) ?: continue
            if (isShortsText(child)) {
                blockShorts()
                return
            }
        }
    }

    private fun isShortsText(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        return text.equals("Shorts", ignoreCase = true) ||
                desc.equals("Shorts", ignoreCase = true)
    }

    private fun scheduleDelayedChecks(vararg delaysMs: Long) {
        for (delay in delaysMs) {
            handler.postDelayed({ checkForShorts() }, delay)
        }
    }

    private fun throttledCheck() {
        val now = System.currentTimeMillis()
        if (now - lastScanTime < SCAN_COOLDOWN_MS) return
        lastScanTime = now
        checkForShorts()
    }

    @Suppress("DEPRECATION")
    private fun checkForShorts() {
        if (!isBlockingEnabled(this)) return
        val root = rootInActiveWindow ?: return
        if (isShortsActive(root)) {
            blockShorts()
        }
    }

    @Suppress("DEPRECATION")
    private fun isShortsActive(root: AccessibilityNodeInfo): Boolean {
        val shortsNodes = root.findAccessibilityNodeInfosByText("Shorts")
        if (shortsNodes.isEmpty()) return false

        val screenHeight = resources.displayMetrics.heightPixels

        for (node in shortsNodes) {
            // Detection 1: Shorts tab is selected
            if (node.isSelected && (node.isClickable || node.isFocusable)) {
                return true
            }

            // Detection 2: Shorts tab is "checked" (some YouTube versions)
            if (node.isChecked && node.isClickable) {
                return true
            }

            // Detection 3: Shorts player indicator in the upper part of the screen.
            // When a Short opens from a feed, YouTube may show a "Shorts" badge/label
            // near the top that is NOT the bottom navigation tab.
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val isInUpperHalf = bounds.centerY() < screenHeight * 0.5
            val isBottomNavTab = bounds.top > screenHeight * 0.8 && node.isClickable

            if (isInUpperHalf && !isBottomNavTab && isShortsText(node)) {
                // Verify this isn't a "Shorts" shelf header on the home feed
                // by checking whether the Home tab is currently selected.
                // If Home tab is selected, this is likely the shelf header — skip.
                if (!isHomeTabSelected(root)) {
                    return true
                }
            }
        }

        return false
    }

    @Suppress("DEPRECATION")
    private fun isHomeTabSelected(root: AccessibilityNodeInfo): Boolean {
        val homeNodes = root.findAccessibilityNodeInfosByText("Home")
        return homeNodes.any { it.isSelected && it.isClickable }
    }

    private fun blockShorts() {
        val now = System.currentTimeMillis()
        if (now - lastBlockTime < BLOCK_COOLDOWN_MS) return
        lastBlockTime = now

        handler.removeCallbacksAndMessages(null)
        performGlobalAction(GLOBAL_ACTION_BACK)
        Toast.makeText(this, "Shorts blocked", Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {}
}
