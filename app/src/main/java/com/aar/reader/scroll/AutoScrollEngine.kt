package com.aar.reader.scroll

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

interface IAutoScrollEngine {
    fun triggerScroll(
        container: AccessibilityNodeInfo?,
        service: AccessibilityService,
        screenWidth: Int,
        screenHeight: Int,
        preferGesture: Boolean = false,
        scrollPercent: Float = 0.65f,
        onScrollDispatched: (ScrollResult) -> Unit = {}
    ): ScrollResult
}

object AutoScrollEngine : IAutoScrollEngine {

    const val SETTLING_DEBOUNCE_MS = 450L
    const val POST_SCROLL_EVENT_DEBOUNCE_MS = 250L
    const val GESTURE_DURATION_MS = 350L

    override fun triggerScroll(
        container: AccessibilityNodeInfo?,
        service: AccessibilityService,
        screenWidth: Int,
        screenHeight: Int,
        preferGesture: Boolean,
        scrollPercent: Float,
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
            val (startYPercent, endYPercent) = computeVerticalPercentRange(scrollPercent)
            val gestureDispatched = dispatchSmoothSwipe(
                service = service,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                startPercentY = startYPercent,
                endPercentY = endYPercent,
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

    /**
     * Suspending scroll execution that awaits the full 350ms gesture stroke completion
     * before returning, ensuring post-scroll settling debounce is not consumed during the swipe.
     */
    suspend fun triggerScrollAwait(
        container: AccessibilityNodeInfo?,
        service: AccessibilityService,
        screenWidth: Int,
        screenHeight: Int,
        preferGesture: Boolean = false,
        scrollPercent: Float = 0.65f
    ): ScrollResult {
        if (!preferGesture && container != null) {
            val scrolled = container.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ||
                           container.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.id)
            if (scrolled) {
                return ScrollResult.SuccessContainer
            }
        }

        if (screenWidth > 0 && screenHeight > 0) {
            val (startYPercent, endYPercent) = computeVerticalPercentRange(scrollPercent)
            val gestureOutcome = withTimeoutOrNull(GESTURE_DURATION_MS + 300L) {
                suspendCancellableCoroutine<ScrollResult> { cont ->
                    val accepted = dispatchSmoothSwipe(
                        service = service,
                        screenWidth = screenWidth,
                        screenHeight = screenHeight,
                        startPercentY = startYPercent,
                        endPercentY = endYPercent,
                        onComplete = { success ->
                            if (cont.isActive) {
                                cont.resume(if (success) ScrollResult.SuccessGesture else ScrollResult.Failed)
                            }
                        }
                    )
                    if (!accepted && cont.isActive) {
                        cont.resume(ScrollResult.Failed)
                    }
                }
            }
            return gestureOutcome ?: ScrollResult.Failed
        }

        return ScrollResult.Failed
    }

    fun computeVerticalPercentRange(scrollPercent: Float): Pair<Float, Float> {
        val clamped = scrollPercent.coerceIn(0.4f, 0.9f)
        val halfSpan = clamped * 0.5f
        val startPercentY = (0.5f + halfSpan).coerceIn(0.55f, 0.90f)
        val endPercentY = (0.5f - halfSpan).coerceIn(0.10f, 0.45f)
        return startPercentY to endPercentY
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
        startPercentY: Float = 0.78f,
        endPercentY: Float = 0.22f,
        onComplete: (Boolean) -> Unit
    ): Boolean {
        val path = buildSwipePath(screenWidth, screenHeight, startPercentY, endPercentY)
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
