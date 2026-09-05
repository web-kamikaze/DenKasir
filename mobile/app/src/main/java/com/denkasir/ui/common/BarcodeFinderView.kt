package com.denkasir.ui.common

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class BarcodeFinderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val scrimPaint = Paint().apply {
        color = Color.parseColor("#990F172A") // Dark transparent overlay
    }

    private val framePaint = Paint().apply {
        color = Color.parseColor("#44334155") // Subtle viewfinder card background
        style = Paint.Style.FILL
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10B981") // Green corners from screenshot
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val laserPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var laserY = 0f
    private var laserSpeed = 6f
    private var isGoingDown = true

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        val boxWidth = w * 0.76f
        val boxHeight = boxWidth * 1.05f

        val left = (w - boxWidth) / 2f
        val top = h * 0.22f
        val right = left + boxWidth
        val bottom = top + boxHeight
        val cornerRadius = 32f

        // 1. Draw outer dark scrim
        canvas.drawRect(0f, 0f, w, top, scrimPaint)
        canvas.drawRect(0f, top, left, bottom, scrimPaint)
        canvas.drawRect(right, top, w, bottom, scrimPaint)
        canvas.drawRect(0f, bottom, w, h, scrimPaint)

        // 2. Draw viewfinder frame box
        val frameRect = RectF(left, top, right, bottom)
        canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, framePaint)

        // 3. Draw 4 Green Corner Brackets
        val cornerLength = 50f

        // Top-Left
        canvas.drawLine(left - 20f, top - 20f, left - 20f + cornerLength, top - 20f, cornerPaint)
        canvas.drawLine(left - 20f, top - 20f, left - 20f, top - 20f + cornerLength, cornerPaint)

        // Top-Right
        canvas.drawLine(right + 20f, top - 20f, right + 20f - cornerLength, top - 20f, cornerPaint)
        canvas.drawLine(right + 20f, top - 20f, right + 20f, top - 20f + cornerLength, cornerPaint)

        // Bottom-Left
        canvas.drawLine(left - 20f, bottom + 20f, left - 20f + cornerLength, bottom + 20f, cornerPaint)
        canvas.drawLine(left - 20f, bottom + 20f, left - 20f, bottom + 20f - cornerLength, cornerPaint)

        // Bottom-Right
        canvas.drawLine(right + 20f, bottom + 20f, right + 20f - cornerLength, bottom + 20f, cornerPaint)
        canvas.drawLine(right + 20f, bottom + 20f, right + 20f, bottom + 20f - cornerLength, cornerPaint)

        // 4. Moving Laser Beam
        if (laserY < top) laserY = top
        if (isGoingDown) {
            laserY += laserSpeed
            if (laserY > bottom) isGoingDown = false
        } else {
            laserY -= laserSpeed
            if (laserY < top) isGoingDown = true
        }

        val laserGradient = LinearGradient(
            0f, laserY, w, laserY,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#10B981"), Color.parseColor("#34D399"), Color.TRANSPARENT),
            floatArrayOf(0f, 0.3f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        laserPaint.shader = laserGradient
        laserPaint.strokeWidth = 8f
        canvas.drawLine(0f, laserY, w, laserY, laserPaint)

        postInvalidateDelayed(16)
    }
}
