package org.autojs.autojs.core.accessibility

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Rect
import androidx.annotation.Keep
import android.view.accessibility.AccessibilityNodeInfo

import org.autojs.autojs.core.automator.UiObject
import org.autojs.plugin.disguised.a11y.api.NodeSnapshot
import org.opencv.core.Point

import java.util.ArrayList
import java.util.HashMap

/**
 * Created by Stardust on Mar 10, 2017.
 * Modified by SuperMonster003 as of Jun 17, 2022.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
@Keep
class NodeInfo private constructor(
    var parent: NodeInfo?,
    val boundsInParent: Rect,
    val boundsInScreen: Rect,
    val packageName: String?,
    val id: String?,
    val fullId: String?,
    val idHex: String?,
    val desc: String?,
    val text: String?,
    val center: Point,
    val className: String?,
    val clickable: Boolean,
    val longClickable: Boolean,
    val scrollable: Boolean,
    val indexInParent: Int,
    val childCount: Int,
    val depth: Int,
    val checked: Boolean,
    val enabled: Boolean,
    val editable: Boolean,
    val focusable: Boolean,
    val checkable: Boolean,
    val selected: Boolean,
    val dismissable: Boolean,
    val visibleToUser: Boolean,
    val contextClickable: Boolean,
    val focused: Boolean,
    val accessibilityFocused: Boolean,
    val rowCount: Int,
    val columnCount: Int,
    val row: Int,
    val column: Int,
    val rowSpan: Int,
    val columnSpan: Int,
    val drawingOrder: Int,
    val actionNames: List<String>,
    private val summarySuffix: String,
) {

    val bounds = boundsInScreen
    var hidden = false
    val children = ArrayList<NodeInfo>()

    @Suppress("DEPRECATION")
    constructor(resources: Resources?, node: UiObject, parent: NodeInfo?) : this(
        parent = parent,
        boundsInParent = Rect().also { node.getBoundsInParent(it) },
        boundsInScreen = Rect().also { node.getBoundsInScreen(it) },
        packageName = node.packageName(),
        id = node.simpleId(),
        fullId = node.fullId(),
        idHex = resolveIdHex(resources, node.packageName(), node.fullId()),
        desc = node.desc(),
        text = node.text(),
        center = node.center(),
        className = node.className(),
        clickable = node.isClickable,
        longClickable = node.isLongClickable,
        scrollable = node.isScrollable,
        indexInParent = node.indexInParent(),
        childCount = node.childCount(),
        depth = node.depth(),
        checked = node.isChecked,
        enabled = node.isEnabled,
        editable = node.isEditable,
        focusable = node.isFocusable,
        checkable = node.isCheckable,
        selected = node.isSelected,
        dismissable = node.isDismissable,
        visibleToUser = node.visibleToUser(),
        contextClickable = node.isContextClickable,
        focused = node.focused(),
        accessibilityFocused = node.isAccessibilityFocused,
        rowCount = node.rowCount(),
        columnCount = node.columnCount(),
        row = node.row(),
        column = node.column(),
        rowSpan = node.rowSpan(),
        columnSpan = node.columnSpan(),
        drawingOrder = node.drawingOrder,
        actionNames = node.actionNames(),
        summarySuffix = node.summary(),
    )

    override fun toString() = "$className$summarySuffix"

    companion object {

        @JvmStatic
        fun boundsToString(rect: Rect) = rect.toString().replace(" - ", " , ").replace(" ", "").substring(4)

        @SuppressLint("DiscouragedApi")
        private fun resolveIdHex(resources: Resources?, packageName: String?, fullId: String?): String? {
            if (resources == null || packageName == null || fullId == null) return null
            return "0x${Integer.toHexString(resources.getIdentifier(fullId, null, null))}"
        }

        internal fun capture(resourcesCache: HashMap<String, Resources>, context: Context, uiObject: UiObject, parent: NodeInfo?): NodeInfo {
            val resources: Resources? = uiObject.packageName()?.let { pkg ->
                return@let resourcesCache[pkg] ?: run {
                    try {
                        return@run context.packageManager.getResourcesForApplication(pkg).also { resourcesCache[pkg] = it }
                    } catch (e: PackageManager.NameNotFoundException) {
                        e.printStackTrace()
                        return@run null
                    }
                }
            }
            return NodeInfo(resources, uiObject, parent).apply {
                0.until(uiObject.childCount)
                    .asSequence()
                    .mapNotNull { uiObject.child(it) }
                    .forEach { children += capture(resourcesCache, context, it, this) }
            }
        }

        fun capture(context: Context, root: AccessibilityNodeInfo): NodeInfo {
            val r = UiObject.createRoot(root)
            val resourcesCache = HashMap<String, Resources>()
            return capture(resourcesCache, context, r, null)
        }

        fun fromSnapshot(snapshot: NodeSnapshot, parent: NodeInfo? = null): NodeInfo {
            val boundsInParent = Rect(snapshot.boundsInParent ?: Rect())
            val boundsInScreen = Rect(snapshot.boundsInScreen ?: Rect())
            val center = Point(boundsInScreen.exactCenterX().toDouble(), boundsInScreen.exactCenterY().toDouble())
            val node = NodeInfo(
                parent = parent,
                boundsInParent = boundsInParent,
                boundsInScreen = boundsInScreen,
                packageName = snapshot.packageName,
                id = snapshot.id,
                fullId = snapshot.fullId,
                idHex = null,
                desc = snapshot.desc,
                text = snapshot.text,
                center = center,
                className = snapshot.className,
                clickable = snapshot.clickable,
                longClickable = snapshot.longClickable,
                scrollable = snapshot.scrollable,
                indexInParent = snapshot.indexInParent,
                childCount = snapshot.children?.size ?: snapshot.childCount,
                depth = snapshot.depth,
                checked = snapshot.checked,
                enabled = snapshot.enabled,
                editable = snapshot.editable,
                focusable = snapshot.focusable,
                checkable = snapshot.checkable,
                selected = snapshot.selected,
                dismissable = snapshot.dismissable,
                visibleToUser = snapshot.visibleToUser,
                contextClickable = snapshot.contextClickable,
                focused = snapshot.focused,
                accessibilityFocused = snapshot.accessibilityFocused,
                rowCount = snapshot.rowCount,
                columnCount = snapshot.columnCount,
                row = snapshot.row,
                column = snapshot.column,
                rowSpan = snapshot.rowSpan,
                columnSpan = snapshot.columnSpan,
                drawingOrder = snapshot.drawingOrder,
                actionNames = snapshot.actionNames?.toList() ?: emptyList(),
                summarySuffix = "",
            )
            snapshot.children?.forEach { child ->
                node.children += fromSnapshot(child, node)
            }
            return node
        }
    }
}
