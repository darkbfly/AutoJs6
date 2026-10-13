package org.autojs.autojs.core.accessibility

import android.content.Context
import android.os.TransactionTooLargeException
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import org.autojs.autojs.core.plugin.a11y.DisguisedA11yPluginHost
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs6.R
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Created by Stardust on Mar 10, 2017.
 * Modified by SuperMonster003 as of May 26, 2022.
 */
class LayoutInspector(private val mContext: Context) {

    @Volatile
    var capture: Capture? = null
        private set

    @Volatile
    var lastCaptureError: String? = null
        private set

    private val mCaptureAvailableListeners = CopyOnWriteArrayList<CaptureAvailableListener>()
    private val mCaptureFailedListeners = CopyOnWriteArrayList<CaptureFailedListener>()
    private val mExecutor = Executors.newSingleThreadExecutor()
    private val mA11yTool = AccessibilityTool(mContext)

    interface CaptureAvailableListener {
        fun onCaptureAvailable(capture: Capture, context: Context)
    }

    interface CaptureFailedListener {
        fun onCaptureFailed(error: String, context: Context)
    }

    fun captureCurrentWindow(): Boolean {
        lastCaptureError = null
        return when (Pref.layoutNodeScheme) {
            LayoutNodeScheme.DISGUISED_PLUGIN -> captureViaDisguisedPlugin()
            LayoutNodeScheme.BUILTIN -> captureViaBuiltinService()
        }
    }

    private fun captureViaBuiltinService(): Boolean {
        val service = AccessibilityService.instance
        if (service == null) {
            Log.d(LOG_TAG, "captureCurrentWindow: service = null")
            capture = null
            notifyCaptureFailed(mContext.getString(R.string.error_no_accessibility_permission_to_capture))
            return false
        }
        mA11yTool.clearCache()
        val root = getRootInActiveWindow(service)
        if (root == null) {
            Log.d(LOG_TAG, "captureCurrentWindow: root = null")
            capture = null
            notifyCaptureFailed(mContext.getString(R.string.error_layout_node_capture_empty))
            return false
        }
        mExecutor.execute {
            try {
                val windowInfoList = service.windows.map {
                    WindowInfo.create(mContext, it)
                }
                capture = Capture(windowInfoList, NodeInfo.capture(mContext, root))
                lastCaptureError = null
                notifyCaptureAvailable()
            } catch (t: Throwable) {
                capture = null
                notifyCaptureFailed(
                    t.message ?: mContext.getString(R.string.error_layout_node_capture_empty),
                )
                Log.e(LOG_TAG, "captureViaBuiltinService failed", t)
            }
        }
        return true
    }

    private fun captureViaDisguisedPlugin(): Boolean {
        if (!DisguisedA11yPluginHost.hasInstalledPlugin(mContext)) {
            capture = null
            notifyCaptureFailed(mContext.getString(R.string.error_disguised_a11y_plugin_not_installed))
            Log.d(LOG_TAG, "captureViaDisguisedPlugin: plugin not installed")
            return false
        }
        mExecutor.execute {
            try {
                val snapshot = DisguisedA11yPluginHost.captureActiveWindowSync(mContext)
                if (snapshot == null) {
                    capture = null
                    notifyCaptureFailed(mContext.getString(R.string.error_disguised_a11y_plugin_capture_empty))
                    Log.d(LOG_TAG, "captureViaDisguisedPlugin: snapshot = null")
                    return@execute
                }
                val root = NodeInfo.fromSnapshot(snapshot)
                capture = Capture(emptyList(), root)
                lastCaptureError = null
                notifyCaptureAvailable()
            } catch (t: Throwable) {
                capture = null
                notifyCaptureFailed(mapDisguisedError(t))
                Log.e(LOG_TAG, "captureViaDisguisedPlugin failed", t)
            }
        }
        return true
    }

    private fun mapDisguisedError(t: Throwable): String {
        val causedByTooLarge = generateSequence(t) { it.cause }
            .any { it is TransactionTooLargeException || it.message?.contains("TransactionTooLarge", ignoreCase = true) == true }
        if (causedByTooLarge) {
            return mContext.getString(R.string.error_disguised_a11y_plugin_tree_too_large)
        }
        return t.message?.takeIf { it.isNotBlank() }
            ?: mContext.getString(R.string.error_disguised_a11y_plugin_capture_failed)
    }

    private fun notifyCaptureAvailable() {
        val c = capture ?: return
        for (l in mCaptureAvailableListeners) {
            l.onCaptureAvailable(c, mContext)
        }
    }

    private fun notifyCaptureFailed(error: String) {
        lastCaptureError = error
        for (l in mCaptureFailedListeners) {
            l.onCaptureFailed(error, mContext)
        }
    }

    fun addCaptureAvailableListener(l: CaptureAvailableListener) {
        mCaptureAvailableListeners.add(l)
    }

    fun removeCaptureAvailableListener(l: CaptureAvailableListener): Boolean {
        return mCaptureAvailableListeners.remove(l)
    }

    fun addCaptureFailedListener(l: CaptureFailedListener) {
        mCaptureFailedListeners.add(l)
    }

    fun removeCaptureFailedListener(l: CaptureFailedListener): Boolean {
        return mCaptureFailedListeners.remove(l)
    }

    private fun getRootInActiveWindow(service: AccessibilityService): AccessibilityNodeInfo? {
        return service.rootInActiveWindow ?: service.fastRootInActiveWindow
    }

    private fun refreshChildList(root: AccessibilityNodeInfo?) {
        root?.refresh() ?: return
        val childCount = root.childCount
        for (i in 0 until childCount) {
            refreshChildList(root.getChild(i))
        }
    }

    fun clearCapture() {
        capture = null
    }

    companion object {
        private val LOG_TAG = LayoutInspector::class.java.simpleName
    }
}
