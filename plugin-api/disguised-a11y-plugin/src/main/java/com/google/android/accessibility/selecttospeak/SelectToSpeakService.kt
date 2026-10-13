package com.google.android.accessibility.selecttospeak

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility service using the SelectToSpeak class-name suffix so whitelist
 * apps (e.g. WeChat) expose their node tree to this service.
 */
class SelectToSpeakService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "onServiceConnected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Keep instance hot; no event processing needed for snapshot capture.
        instance = this
    }

    override fun onInterrupt() {
        Log.i(TAG, "onInterrupt")
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
        Log.i(TAG, "onDestroy")
    }

    companion object {
        private const val TAG = "SelectToSpeakService"

        @Volatile
        var instance: SelectToSpeakService? = null
            private set

        fun isConnected(): Boolean = instance != null

        fun rootInActiveWindowOrNull(): AccessibilityNodeInfo? =
            instance?.rootInActiveWindow
    }
}
