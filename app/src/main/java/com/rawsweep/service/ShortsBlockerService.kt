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

class ShortsBlockerService : AccessibilityService() {

    companion object {
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val BLOCK_COOLDOWN_MS = 2000L
        private const val SCAN_COOLDOWN_MS = 200L
        private const val FULL_SCAN_COOLDOWN_MS = 800L
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
                scheduleDelayed(400) { fullShortsCheck() }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val className = event.className?.toString()?.lowercase() ?: ""
                if ("reel" in className || "shorts" in className) {
                    blockShortsAuto()
                } else {
                    fullShortsCheck()
                }
                scheduleDelayed(400) { fullShortsCheck() }
                scheduleDelayed(800) { fullShortsCheck() }
                scheduleDelayed(1500) { fullShortsCheck() }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                throttledCheck()
            }
        }
    }

    // --- Click handling (no cooldown) ---

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

    // --- Automatic detection ---

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
            if (isShortsPlayer(root)) {
                blockShortsAuto()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun fullShortsCheck() {
        if (!isBlockingEnabled(this)) return
        val root = rootInActiveWindow ?: return

        if (isShortsTabActive(root) || isShortsPlayer(root)) {
            blockShortsAuto()
        }
    }

    // --- Detection: Shorts tab state ---

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

    // --- Detection: Shorts reel player (structural + view ID + class name) ---

    @Suppress("DEPRECATION")
    private fun isShortsPlayer(root: AccessibilityNodeInfo): Boolean {
        return hasReelViewIds(root, 0) ||
                hasReelClassName(root, 0) ||
                hasRightSideActionButtons(root)
    }

    /**
     * Check view resource IDs for "reel" (YouTube's internal name for Shorts views).
     */
    @Suppress("DEPRECATION")
    private fun hasReelViewIds(node: AccessibilityNodeInfo, depth: Int): Boolean {
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
            if (hasReelViewIds(child, depth + 1)) return true
        }

        return false
    }

    /**
     * Check node class names for "reel" or "shorts" patterns.
     */
    @Suppress("DEPRECATION")
    private fun hasReelClassName(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > 10) return false

        val cls = node.className?.toString()?.lowercase() ?: ""
        if (("reel" in cls || "shorts" in cls) && "android" !in cls) {
            return true
        }

        val limit = when {
            depth < 3 -> node.childCount.coerceAtMost(20)
            depth < 6 -> node.childCount.coerceAtMost(10)
            else -> node.childCount.coerceAtMost(5)
        }

        for (i in 0 until limit) {
            val child = node.getChild(i) ?: continue
            if (hasReelClassName(child, depth + 1)) return true
        }

        return false
    }

    /**
     * Structural detection: the Shorts reel player has 4+ small clickable
     * action buttons (like, dislike, comments, share, remix, sound) stacked
     * vertically on the right side of the screen. No other YouTube screen
     * has this layout.
     */
    @Suppress("DEPRECATION")
    private fun hasRightSideActionButtons(root: AccessibilityNodeInfo): Boolean {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val rightZoneLeft = (screenWidth * 0.65).toInt()
        val maxBtnWidth = (screenWidth * 0.25).toInt()
        val maxBtnHeight = (screenHeight * 0.12).toInt()
        val topBound = (screenHeight * 0.15).toInt()
        val bottomBound = (screenHeight * 0.90).toInt()
        val counter = intArrayOf(0)

        countButtonsInRightZone(
            root, rightZoneLeft, maxBtnWidth, maxBtnHeight,
            topBound, bottomBound, 0, counter,
        )

        return counter[0] >= 4
    }

    @Suppress("DEPRECATION")
    private fun countButtonsInRightZone(
        node: AccessibilityNodeInfo,
        rightZoneLeft: Int,
        maxWidth: Int,
        maxHeight: Int,
        topBound: Int,
        bottomBound: Int,
        depth: Int,
        counter: IntArray,
    ) {
        if (depth > 10 || counter[0] >= 4) return

        if (node.isClickable && !node.isScrollable) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            if (bounds.left >= rightZoneLeft &&
                bounds.width() in 1..maxWidth &&
                bounds.height() in 1..maxHeight &&
                bounds.top >= topBound &&
                bounds.bottom <= bottomBound
            ) {
                counter[0]++
                if (counter[0] >= 4) return
            }
        }

        val limit = when {
            depth < 3 -> node.childCount.coerceAtMost(20)
            depth < 6 -> node.childCount.coerceAtMost(12)
            else -> node.childCount.coerceAtMost(6)
        }

        for (i in 0 until limit) {
            if (counter[0] >= 4) return
            val child = node.getChild(i) ?: continue
            countButtonsInRightZone(
                child, rightZoneLeft, maxWidth, maxHeight,
                topBound, bottomBound, depth + 1, counter,
            )
        }
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
