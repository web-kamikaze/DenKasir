package com.denkasir.ui.common

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class CashFlowChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E2E8F0")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val selectedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3418D6")
        textSize = 30f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private val labelYPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = 26f
        textAlign = Paint.Align.RIGHT
    }

    private val greenLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0E7453")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val greenNodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val greenNodeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0E7453")
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val redLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B91C1C")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val redNodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val redNodeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B91C1C")
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val dates = arrayOf("19/5", "20/5", "21/5", "22/5", "23/5", "24/5", "25/5")
    private val cashInValues = floatArrayOf(5.0f, 7.5f, 5.8f, 9.0f, 10.0f, 9.2f, 7.8f) // in Millions (Jt)
    private val cashOutValues = floatArrayOf(2.2f, 2.7f, 3.0f, 2.4f, 4.5f, 3.0f, 2.5f) // in Millions (Jt)
    private val maxVal = 12.0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val paddingLeft = 80f
        val paddingRight = 40f
        val paddingTop = 40f
        val paddingBottom = 80f

        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        // Draw Y-Axis labels & horizontal dashed gridlines
        val yLabels = arrayOf("0", "4jt", "8jt", "12jt")
        val ySteps = floatArrayOf(0f, 4f, 8f, 12f)

        for (i in ySteps.indices) {
            val yPos = paddingTop + chartHeight - (ySteps[i] / maxVal) * chartHeight
            canvas.drawText(yLabels[i], paddingLeft - 20f, yPos + 10f, labelYPaint)
            val gridPath = Path().apply {
                moveTo(paddingLeft, yPos)
                lineTo(width - paddingRight, yPos)
            }
            canvas.drawPath(gridPath, gridPaint)
        }

        // Calculate points
        val stepX = chartWidth / (dates.size - 1)
        val greenPoints = mutableListOf<PointF>()
        val redPoints = mutableListOf<PointF>()

        for (i in dates.indices) {
            val px = paddingLeft + i * stepX
            val pyGreen = paddingTop + chartHeight - (cashInValues[i] / maxVal) * chartHeight
            val pyRed = paddingTop + chartHeight - (cashOutValues[i] / maxVal) * chartHeight
            greenPoints.add(PointF(px, pyGreen))
            redPoints.add(PointF(px, pyRed))

            // X date labels
            val isSelected = (dates[i] == "23/5")
            val p = if (isSelected) selectedTextPaint else textPaint
            canvas.drawText(dates[i], px, height - 20f, p)
        }

        // Draw Green Area & Line
        val greenPath = Path()
        val greenFillPath = Path()
        for (i in greenPoints.indices) {
            if (i == 0) {
                greenPath.moveTo(greenPoints[i].x, greenPoints[i].y)
                greenFillPath.moveTo(greenPoints[i].x, greenPoints[i].y)
            } else {
                greenPath.lineTo(greenPoints[i].x, greenPoints[i].y)
                greenFillPath.lineTo(greenPoints[i].x, greenPoints[i].y)
            }
        }
        greenFillPath.lineTo(greenPoints.last().x, paddingTop + chartHeight)
        greenFillPath.lineTo(greenPoints.first().x, paddingTop + chartHeight)
        greenFillPath.close()

        val greenGradient = LinearGradient(
            0f, paddingTop, 0f, paddingTop + chartHeight,
            Color.parseColor("#3310B981"), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        val greenFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = greenGradient
            style = Paint.Style.FILL
        }
        canvas.drawPath(greenFillPath, greenFillPaint)
        canvas.drawPath(greenPath, greenLinePaint)

        // Draw Red Area & Line
        val redPath = Path()
        val redFillPath = Path()
        for (i in redPoints.indices) {
            if (i == 0) {
                redPath.moveTo(redPoints[i].x, redPoints[i].y)
                redFillPath.moveTo(redPoints[i].x, redPoints[i].y)
            } else {
                redPath.lineTo(redPoints[i].x, redPoints[i].y)
                redFillPath.lineTo(redPoints[i].x, redPoints[i].y)
            }
        }
        redFillPath.lineTo(redPoints.last().x, paddingTop + chartHeight)
        redFillPath.lineTo(redPoints.first().x, paddingTop + chartHeight)
        redFillPath.close()

        val redGradient = LinearGradient(
            0f, paddingTop, 0f, paddingTop + chartHeight,
            Color.parseColor("#33EF4444"), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        val redFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = redGradient
            style = Paint.Style.FILL
        }
        canvas.drawPath(redFillPath, redFillPaint)
        canvas.drawPath(redPath, redLinePaint)

        // Draw circles on data points
        for (p in greenPoints) {
            canvas.drawCircle(p.x, p.y, 10f, greenNodePaint)
            canvas.drawCircle(p.x, p.y, 10f, greenNodeStrokePaint)
        }
        for (p in redPoints) {
            canvas.drawCircle(p.x, p.y, 10f, redNodePaint)
            canvas.drawCircle(p.x, p.y, 10f, redNodeStrokePaint)
        }
    }
}
