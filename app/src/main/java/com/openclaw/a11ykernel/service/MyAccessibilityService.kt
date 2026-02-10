package com.openclaw.a11ykernel.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.openclaw.a11ykernel.action.ActionExecutor
import com.openclaw.a11ykernel.model.ActRequest
import com.openclaw.a11ykernel.model.ActionResult
import com.openclaw.a11ykernel.model.ScreenResponse
import com.openclaw.a11ykernel.server.LocalApiServer
import com.openclaw.a11ykernel.ui.UiSnapshotBuilder

class MyAccessibilityService : AccessibilityService() {
    private var apiServer: LocalApiServer? = null
    private var actionExecutor: ActionExecutor? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        actionExecutor = ActionExecutor(this)
        apiServer = LocalApiServer(this).also { it.start() }
        Log.i(TAG, "Accessibility service connected. API started on 127.0.0.1:7333")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // On-demand mode: no background processing needed.
    }

    override fun onInterrupt() {
        Log.i(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        apiServer?.stop()
        apiServer = null
        super.onDestroy()
    }

    fun buildScreenResponse(): ScreenResponse {
        val root = rootInActiveWindow
        val elements = UiSnapshotBuilder.build(root)
        val packageName = root?.packageName?.toString()
        root?.recycle()
        return ScreenResponse(
            packageName = packageName,
            activity = null,
            ts = System.currentTimeMillis(),
            elements = elements
        )
    }

    fun executeAction(req: ActRequest): ActionResult {
        val executor = actionExecutor ?: return ActionResult(
            ok = false,
            error = "Action executor not ready",
            elapsedMs = 0
        )
        return executor.execute(req)
    }

    fun isRootAvailable(): Boolean {
        val executor = actionExecutor ?: return false
        return executor.isRootAvailable()
    }

    companion object {
        private const val TAG = "A11yKernel"
    }
}
