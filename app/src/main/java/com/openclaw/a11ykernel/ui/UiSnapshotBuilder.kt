package com.openclaw.a11ykernel.ui

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.openclaw.a11ykernel.model.UiElement

object UiSnapshotBuilder {
    fun build(root: AccessibilityNodeInfo?): List<UiElement> {
        if (root == null) return emptyList()
        val out = mutableListOf<UiElement>()
        walk(root, out)
        return out
    }

    private fun walk(node: AccessibilityNodeInfo, out: MutableList<UiElement>) {
        val isCandidate = node.isClickable || node.isFocusable || node.className?.toString()?.contains("EditText") == true
        if (isCandidate) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val centerX = (bounds.left + bounds.right) / 2
            val centerY = (bounds.top + bounds.bottom) / 2
            out.add(
                UiElement(
                    text = node.text?.toString(),
                    resourceId = node.viewIdResourceName,
                    contentDesc = node.contentDescription?.toString(),
                    className = node.className?.toString(),
                    clickable = node.isClickable,
                    enabled = node.isEnabled,
                    bounds = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}",
                    center = listOf(centerX, centerY)
                )
            )
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, out)
            child.recycle()
        }
    }
}
