package com.openclaw.a11ykernel.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.openclaw.a11ykernel.model.ActRequest
import com.openclaw.a11ykernel.model.ActionResult
import com.openclaw.a11ykernel.model.Selector
import com.openclaw.a11ykernel.model.UiElement

class ActionExecutor(private val service: AccessibilityService) {
    fun execute(req: ActRequest): ActionResult {
        val start = SystemClock.elapsedRealtime()
        val root = service.rootInActiveWindow
        val action = req.action.lowercase()

        val result = when (action) {
            "tap" -> tap(root, req.selector, req.fallbackCoordinates)
            "type" -> type(root, req.selector, req.text)
            "back" -> simpleGlobal(AccessibilityService.GLOBAL_ACTION_BACK)
            "home" -> simpleGlobal(AccessibilityService.GLOBAL_ACTION_HOME)
            "scroll" -> scroll(root, req.direction)
            "wait" -> waitAction(req.timeoutMs ?: 350)
            "done" -> ActionResult(ok = true, elapsedMs = 0)
            else -> ActionResult(ok = false, error = "Unsupported action: ${req.action}", elapsedMs = 0)
        }

        root?.recycle()
        return result.copy(elapsedMs = SystemClock.elapsedRealtime() - start)
    }

    private fun tap(root: AccessibilityNodeInfo?, selector: Selector?, fallbackCoordinates: List<Int>?): ActionResult {
        val matched = findNode(root, selector)
        if (matched != null) {
            val clickable = findClickableParent(matched)
            if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return ActionResult(ok = true, matchedElement = toElement(clickable), elapsedMs = 0)
            }
        }

        if (fallbackCoordinates != null && fallbackCoordinates.size == 2) {
            val ok = tapByGesture(fallbackCoordinates[0].toFloat(), fallbackCoordinates[1].toFloat())
            return if (ok) ActionResult(ok = true, elapsedMs = 0) else ActionResult(ok = false, error = "Fallback tap failed", elapsedMs = 0)
        }
        return ActionResult(ok = false, error = "No tappable node matched selector", elapsedMs = 0)
    }

    private fun type(root: AccessibilityNodeInfo?, selector: Selector?, text: String?): ActionResult {
        if (text == null) return ActionResult(ok = false, error = "Missing text for type action", elapsedMs = 0)
        val target = findNode(root, selector) ?: findFirstEditable(root)
        if (target == null) return ActionResult(ok = false, error = "No input node found", elapsedMs = 0)

        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return if (ok) {
            ActionResult(ok = true, matchedElement = toElement(target), elapsedMs = 0)
        } else {
            ActionResult(ok = false, error = "ACTION_SET_TEXT failed", elapsedMs = 0)
        }
    }

    private fun scroll(root: AccessibilityNodeInfo?, direction: String?): ActionResult {
        val node = findFirstScrollable(root) ?: return ActionResult(ok = false, error = "No scrollable node found", elapsedMs = 0)
        val action = if ((direction ?: "forward").lowercase() == "backward") {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        val ok = node.performAction(action)
        return if (ok) ActionResult(ok = true, matchedElement = toElement(node), elapsedMs = 0)
        else ActionResult(ok = false, error = "Scroll action failed", elapsedMs = 0)
    }

    private fun waitAction(timeoutMs: Long): ActionResult {
        Thread.sleep(timeoutMs.coerceIn(50, 3000))
        return ActionResult(ok = true, elapsedMs = 0)
    }

    private fun simpleGlobal(action: Int): ActionResult {
        val ok = service.performGlobalAction(action)
        return if (ok) ActionResult(ok = true, elapsedMs = 0)
        else ActionResult(ok = false, error = "Global action failed", elapsedMs = 0)
    }

    private fun tapByGesture(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()

        var success = false
        val lock = Object()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                synchronized(lock) {
                    success = true
                    lock.notifyAll()
                }
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                synchronized(lock) {
                    success = false
                    lock.notifyAll()
                }
            }
        }, null)

        synchronized(lock) {
            lock.wait(300)
        }
        return success
    }

    private fun findNode(root: AccessibilityNodeInfo?, selector: Selector?): AccessibilityNodeInfo? {
        if (root == null || selector == null) return null
        val all = flatten(root)
        val by = selector.by.lowercase()
        val value = selector.value

        fun match(input: String?): Boolean {
            if (input == null) return false
            return input.contains(value, ignoreCase = true)
        }

        return when (by) {
            "id" -> all.firstOrNull { match(it.viewIdResourceName) }
            "desc" -> all.firstOrNull { match(it.contentDescription?.toString()) }
            "text" -> all.firstOrNull { match(it.text?.toString()) }
            "class" -> all.firstOrNull { match(it.className?.toString()) }
            else -> null
        }
    }

    private fun findClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var curr = node
        while (curr != null) {
            if (curr.isClickable && curr.isEnabled) return curr
            curr = curr.parent
        }
        return node
    }

    private fun findFirstEditable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        return flatten(root).firstOrNull {
            it.className?.toString()?.contains("EditText") == true || it.isEditable
        }
    }

    private fun findFirstScrollable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        return flatten(root).firstOrNull { it.isScrollable }
    }

    private fun flatten(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo) {
            out.add(node)
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child)
            }
        }
        walk(root)
        return out
    }

    private fun toElement(node: AccessibilityNodeInfo): UiElement {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return UiElement(
            text = node.text?.toString(),
            resourceId = node.viewIdResourceName,
            contentDesc = node.contentDescription?.toString(),
            className = node.className?.toString(),
            clickable = node.isClickable,
            enabled = node.isEnabled,
            bounds = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}",
            center = listOf((bounds.left + bounds.right) / 2, (bounds.top + bounds.bottom) / 2)
        )
    }
}
