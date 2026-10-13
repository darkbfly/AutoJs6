package org.autojs.plugin.disguised.a11y

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.autojs.plugin.disguised.a11y.api.NodeSnapshot

object NodeSnapshotBuilder {

    private const val MAX_DEPTH = 64
    private const val MAX_NODES = 2_500

    fun from(root: AccessibilityNodeInfo?): NodeSnapshot? {
        root ?: return null
        return capture(
            node = AccessibilityNodeInfoCompat.wrap(root),
            depth = 0,
            indexInParent = -1,
            remaining = intArrayOf(MAX_NODES),
        )
    }

    private fun capture(
        node: AccessibilityNodeInfoCompat,
        depth: Int,
        indexInParent: Int,
        remaining: IntArray,
    ): NodeSnapshot {
        remaining[0] = remaining[0] - 1

        val boundsInParent = Rect()
        val boundsInScreen = Rect()
        @Suppress("DEPRECATION")
        node.getBoundsInParent(boundsInParent)
        node.getBoundsInScreen(boundsInScreen)

        val fullId = node.viewIdResourceName
        val simpleId = fullId?.let {
            val sep = ":id/"
            if (it.contains(sep)) it.substringAfterLast(sep) else it
        }

        val rawChildCount = node.childCount
        val children = ArrayList<NodeSnapshot>(rawChildCount.coerceAtMost(64))
        if (depth < MAX_DEPTH && remaining[0] > 0) {
            for (i in 0 until rawChildCount) {
                if (remaining[0] <= 0) break
                val child = node.getChild(i) ?: continue
                try {
                    children += capture(child, depth + 1, i, remaining)
                } finally {
                    child.recycle()
                }
            }
        }

        val actionNames = node.actionList.map { action ->
            action.label?.toString()?.takeIf { it.isNotBlank() } ?: "ACTION_${action.id}"
        }.toTypedArray()

        val collection = node.collectionItemInfo
        val collectionInfo = node.collectionInfo

        return NodeSnapshot().apply {
            packageName = node.packageName?.toString()
            id = simpleId
            this.fullId = fullId
            desc = node.contentDescription?.toString()
            text = if (node.isPassword) "" else (node.text?.toString() ?: "")
            this.boundsInParent = boundsInParent
            this.boundsInScreen = boundsInScreen
            className = node.className?.toString()
            clickable = node.isClickable
            longClickable = node.isLongClickable
            scrollable = node.isScrollable
            checked = node.isChecked
            enabled = node.isEnabled
            editable = node.isEditable
            focusable = node.isFocusable
            checkable = node.isCheckable
            selected = node.isSelected
            dismissable = node.isDismissable
            visibleToUser = node.isVisibleToUser
            contextClickable = node.isContextClickable
            focused = node.isFocused
            accessibilityFocused = node.isAccessibilityFocused
            this.indexInParent = indexInParent
            // Match serialized children so truncated trees stay consistent in UI.
            this.childCount = children.size
            this.depth = depth
            rowCount = collectionInfo?.rowCount ?: 0
            columnCount = collectionInfo?.columnCount ?: 0
            row = collection?.rowIndex ?: -1
            column = collection?.columnIndex ?: -1
            rowSpan = collection?.rowSpan ?: -1
            columnSpan = collection?.columnSpan ?: -1
            drawingOrder = node.drawingOrder
            this.actionNames = actionNames
            this.children = children.toTypedArray()
        }
    }
}
