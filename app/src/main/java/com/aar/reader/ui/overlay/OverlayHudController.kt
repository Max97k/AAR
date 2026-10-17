package com.aar.reader.ui.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.aar.reader.R
import com.aar.reader.service.ReaderStateController
import com.aar.reader.service.model.ReaderState

/**
 * Native floating HUD pill and reading focus highlight frame built on TYPE_ACCESSIBILITY_OVERLAY.
 * Requires ZERO extra alert window permissions.
 */
class OverlayHudController(
    private val service: AccessibilityService
) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var hudView: View? = null
    private var highlightView: HighlightCanvasView? = null
    private var isHudAttached = false
    private var isHighlightAttached = false

    private var isExpanded = false
    private val highlightRectF = RectF()

    private var playPauseBtn: TextView? = null
    private var stopBtn: TextView? = null

    fun show() {
        if (!isHighlightAttached) {
            attachHighlightOverlay()
        }
        if (!isHudAttached) {
            attachHudPill()
        }
    }

    fun hide() {
        detachHudPill()
        detachHighlightOverlay()
    }

    fun updateState(state: ReaderState) {
        val isPlaying = (state == ReaderState.SPEAKING || state == ReaderState.SCANNING)
        playPauseBtn?.post {
            playPauseBtn?.text = if (isPlaying) "⏸" else "▶"
            stopBtn?.visibility = if (state != ReaderState.IDLE) View.VISIBLE else View.GONE
        }
    }

    fun updateHighlight(bounds: Rect?, visible: Boolean) {
        highlightView?.let { view ->
            if (bounds != null && visible && (bounds.right - bounds.left > 0) && (bounds.bottom - bounds.top > 0)) {
                view.setHighlight(bounds)
            } else {
                view.clearHighlight()
            }
        }
    }

    private fun attachHighlightOverlay() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.TOP or Gravity.START

        highlightView = HighlightCanvasView(service)
        try {
            windowManager.addView(highlightView, layoutParams)
            isHighlightAttached = true
        } catch (e: Exception) {
            isHighlightAttached = false
        }
    }

    private fun detachHighlightOverlay() {
        if (isHighlightAttached && highlightView != null) {
            try {
                windowManager.removeView(highlightView)
            } catch (e: Exception) {
                // Ignored
            }
            highlightView = null
            isHighlightAttached = false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachHudPill() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 32
            y = 350
        }

        val context = service
        val container = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Rounded translucent pill background
        val pillBg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 48f
            setColor(Color.parseColor("#E6202124")) // Dark charcoal 90% opacity
            setStroke(2, Color.parseColor("#445566"))
        }

        val pillLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 16, 24, 16)
            background = pillBg
        }

        // 1. Drag Handle
        val dragHandle = TextView(context).apply {
            text = "⠿"
            setTextColor(Color.parseColor("#9AA0A6"))
            textSize = 18f
            setPadding(4, 4, 12, 4)
        }
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX - (event.rawX - touchStartX).toInt()
                    params.y = initialY + (event.rawY - touchStartY).toInt()
                    try {
                        windowManager.updateViewLayout(container, params)
                    } catch (e: Exception) {
                        // Ignored
                    }
                    true
                }
                else -> false
            }
        }
        pillLayout.addView(dragHandle)

        // 2. AAR Badge
        val badge = TextView(context).apply {
            text = "🔊 AAR"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(8, 4, 12, 4)
        }
        pillLayout.addView(badge)

        // 3. Play / Pause Action
        val playBtn = TextView(context).apply {
            text = "▶"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(12, 4, 12, 4)
            setOnClickListener {
                val state = ReaderStateController.state.value
                if (state == ReaderState.SPEAKING || state == ReaderState.SCANNING) {
                    ReaderStateController.pauseReading()
                } else if (state == ReaderState.PAUSED) {
                    ReaderStateController.resumeReading()
                } else {
                    ReaderStateController.startReading()
                }
            }
        }
        this.playPauseBtn = playBtn
        pillLayout.addView(playBtn)

        // 4. Stop Action
        val stopActionBtn = TextView(context).apply {
            text = "⏹"
            setTextColor(Color.parseColor("#FF6B6B"))
            textSize = 18f
            setPadding(12, 4, 8, 4)
            visibility = View.GONE
            setOnClickListener {
                ReaderStateController.stopReading()
            }
        }
        this.stopBtn = stopActionBtn
        pillLayout.addView(stopActionBtn)

        container.addView(pillLayout)
        hudView = container

        try {
            windowManager.addView(hudView, params)
            isHudAttached = true
            updateState(ReaderStateController.state.value)
        } catch (e: Exception) {
            isHudAttached = false
        }
    }

    private fun detachHudPill() {
        if (isHudAttached && hudView != null) {
            try {
                windowManager.removeView(hudView)
            } catch (e: Exception) {
                // Ignored
            }
            hudView = null
            playPauseBtn = null
            stopBtn = null
            isHudAttached = false
        }
    }

    /**
     * Transparent overlay view drawing real-time focus highlight border.
     */
    private class HighlightCanvasView(context: Context) : View(context) {
        private val highlightBounds = RectF()
        private var isHighlightActive = false

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#4285F4") // Material Blue
            strokeWidth = 6f
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#264285F4") // 15% opacity tint
        }

        fun setHighlight(bounds: Rect) {
            highlightBounds.set(
                bounds.left.toFloat() - 4f,
                bounds.top.toFloat() - 2f,
                bounds.right.toFloat() + 4f,
                bounds.bottom.toFloat() + 2f
            )
            isHighlightActive = true
            postInvalidate()
        }

        fun clearHighlight() {
            if (isHighlightActive) {
                isHighlightActive = false
                postInvalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (isHighlightActive) {
                canvas.drawRoundRect(highlightBounds, 12f, 12f, fillPaint)
                canvas.drawRoundRect(highlightBounds, 12f, 12f, paint)
            }
        }
    }
}
