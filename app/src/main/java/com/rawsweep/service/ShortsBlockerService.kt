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
        private const val FULL_SCAN_COOLDOWN_MS = 1000L
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
    private var lastAutoBlockTime = 0L
    private var lastScanTime = 0L
    private var lastFullScanTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() != YOUTUBE_PACKAGE) return
        if (!isBlockingEnabled(this)) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                handleClick(event)
                scheduleDelayed(300) { fullShortsCheck() }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val className = event.className?.toString()?.lowercase() ?: ""
                if ("reel" in className || "shorts" in className) {
                    blockShortsAuto()
                } else {
                    fullShortsCheck()
                }
                scheduleDelayed(300) { fullShortsCheck() }
                scheduleDelayed(700) { fullShortsCheck() }
                scheduleDelayed(1200) { fullShortsCheck() }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                throttledCheck()
            }
        }
    }

    // --- Click handling (no cooldown — every Shorts click is blocked) ---

    private fun handleClick(event: AccessibilityEvent) {
        val node = event.source ?: return

        if (isShortsText(node)) {
            blockShortsFromClick()
            return
        }

        var current = node.parent
        var depth = 0
        while (current != null && depth < 4) {
            if (isShortsText(current)) {
                blockShortsFromClick()
                return
            }
            current = current.parent
            depth++
        }

        for (i in 0 until node.childCount.coerceAtMost(5)) {
            val child = node.getChild(i) ?: continue
            if (isShortsText(child)) {
                blockShortsFromClick()
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

    // --- Automatic detection (with cooldown to prevent loops) ---

    private fun scheduleDelayed(delayMs: Long, action: () -> Unit) {
        handler.postDelayed(action, delayMs)
    }

    private fun throttledCheck() {
        val now = System.currentTimeMillis()
        if (now - lastScanTime < SCAN_COOLDOWN_MS) return
        lastScanTime = now

        if (!isBlockingEnabled(this)) return
        val root = rootInActiveWindow ?: return

        if (isShortsTabActive(root)) {
            blockShortsAuto()
            return
        }

        if (now - lastFullScanTime > FULL_SCAN_COOLDOWN_MS) {
            lastFullScanTime = now
            if (containsReelPlayer(root)) {
                blockShortsAuto()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun fullShortsCheck() {
        if (!isBlockingEnabled(this)) return
        val root = rootInActiveWindow ?: return

        if (isShortsTabActive(root) || containsReelPlayer(root)) {
            blockShortsAuto()
        }
    }

    // --- Shorts detection methods ---

    @Suppress("DEPRECATION")
    private fun isShortsTabActive(root: AccessibilityNodeInfo): Boolean {
        val shortsNodes = root.findAccessibilityNodeInfosByText("Shorts")
        if (shortsNodes.isEmpty()) return false

        val screenHeight = resources.displayMetrics.heightPixels

        for (node in shortsNodes) {
            if (node.isSelected && (node.isClickable || node.isFocusable)) {
                return true
            }
            if (node.isChecked && node.isClickable) {
                return true
            }

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val isInUpperHalf = bounds.centerY() < screenHeight * 0.5
            val isBottomNavTab = bounds.top > screenHeight * 0.8 && node.isClickable

            if (isInUpperHalf && !isBottomNavTab && isShortsText(node)) {
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

    /**
     * Walks the accessibility tree looking for YouTube's Shorts reel player.
     * The reel player uses views whose resource IDs contain "reel"
     * (e.g. reel_recycler, reel_player_overlay). Shelf-related IDs
     * (Shorts shelf on the home feed) are excluded to avoid false positives.
     */
    @Suppress("DEPRECATION")
    private fun containsReelPlayer(node: AccessibilityNodeInfo, depth: Int = 0): Boolean {
        if (depth > 12) return false

        val viewId = node.viewIdResourceName
        if (viewId != null) {
            val lower = viewId.lowercase()
            if ("reel" in lower && "shelf" !in lower && "header" !in lower) {
                return true
            }
        }

        val limit = when {
            depth < 3 -> node.childCount.coerceAtMost(20)
            depth < 6 -> node.childCount.coerceAtMost(12)
            else -> node.childCount.coerceAtMost(6)
        }

        for (i in 0 until limit) {
            val child = node.getChild(i) ?: continue
            if (containsReelPlayer(child, depth + 1)) return true
        }

        return false
    }

    // --- Block actions ---

    private fun blockShortsFromClick() {
        handler.removeCallbacksAndMessages(null)
        lastAutoBlockTime = System.currentTimeMillis()
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun blockShortsAuto() {
        val now = System.currentTimeMillis()
        if (now - lastAutoBlockTime < BLOCK_COOLDOWN_MS) return
        lastAutoBlockTime = now

        handler.removeCallbacksAndMessages(null)
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun onInterrupt() {}
}
