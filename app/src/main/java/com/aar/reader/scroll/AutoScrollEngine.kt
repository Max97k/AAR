package com.aar.reader.scroll

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityNodeInfo

interface IAutoScrollEngine {
    fun triggerScroll(
        container: AccessibilityNodeInfo?,
        service: AccessibilityService,
        screenWidth: Int,
        screenHeight: Int,
        preferGesture: Boolean = false,
        onScrollDispatched: (ScrollResult) -> Unit = {}
    ): ScrollResult
}

object AutoScrollEngine : IAutoScrollEngine {

    const val SETTLING_DEBOUNCE_MS = 450L
    const val GESTURE_DURATION_MS = 350L

    override fun triggerScroll(
        container: AccessibilityNodeInfo?,
        service: AccessibilityService,
        screenWidth: Int,
        screenHeight: Int,
        preferGesture: Boolean,
        onScrollDispatched: (ScrollResult) -> Unit
    ): ScrollResult {
        // Strategy A: Native Accessibility Action Scroll (preferred unless forced gesture)
        if (!preferGesture && container != null) {
            val scrolled = container.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ||
                           container.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.id)
            if (scrolled) {
                val result = ScrollResult.SuccessContainer
                onScrollDispatched(result)
                return result
            }
        }

        // Strategy B: Smooth upward simulated swipe gesture (Fallback)
        if (screenWidth > 0 && screenHeight > 0) {
            val gestureDispatched = dispatchSmoothSwipe(
                service = service,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                onComplete = { success ->
                    val res = if (success) ScrollResult.SuccessGesture else ScrollResult.Failed
                    onScrollDispatched(res)
                }
            )

            if (gestureDispatched) {
                return ScrollResult.SuccessGesture
            }
        }

        val failed = ScrollResult.Failed
        onScrollDispatched(failed)
        return failed
    }

    fun buildSwipePath(
        screenWidth: Int,
        screenHeight: Int,
        startPercentY: Float = 0.78f,
        endPercentY: Float = 0.22f
    ): Path {
        val path = Path()
        val centerX = screenWidth * 0.5f
        val startY = screenHeight * startPercentY
        val endY = screenHeight * endPercentY
        path.moveTo(centerX, startY)
        path.lineTo(centerX, endY)
        return path
    }

    private fun dispatchSmoothSwipe(
        service: AccessibilityService,
        screenWidth: Int,
        screenHeight: Int,
        onComplete: (Boolean) -> Unit
    ): Boolean {
        val path = buildSwipePath(screenWidth, screenHeight)
        val stroke = GestureDescription.StrokeDescription(path, 0L, GESTURE_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onComplete(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    onComplete(false)
                }
            },
            null
        )
    }
}
