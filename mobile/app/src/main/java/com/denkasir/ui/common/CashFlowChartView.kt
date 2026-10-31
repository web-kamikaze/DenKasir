package com.denkasir.ui.common

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.max

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
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }

    private val selectedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3E24E2")
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private val labelYPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = 24f
        textAlign = Paint.Align.RIGHT
    }

    private val emptyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val greenLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#059669")
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
        color = Color.parseColor("#059669")
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val redLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DC2626")
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
        color = Color.parseColor("#DC2626")
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val selectedRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333E24E2")
        style = Paint.Style.FILL
    }

    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        style = Paint.Style.FILL
    }

    private val tooltipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private var dates: List<String> = emptyList()
    private var cashInValues: List<Double> = emptyList()
    private var cashOutValues: List<Double> = emptyList()
    private var maxVal: Double = 100_000.0
    private var selectedIndex: Int = -1

    var onPointSelectedListener: ((index: Int, label: String, cashIn: Double, cashOut: Double) -> Unit)? = null

    /**
     * Memperbarui data grafik secara dinamis.
     */
    fun setChartData(
        newLabels: List<String>,
        newCashIn: List<Double>,
        newCashOut: List<Double>,
        defaultSelectedIndex: Int = -1
    ) {
        dates = newLabels
        cashInValues = newCashIn
        cashOutValues = newCashOut

        val maxIn = newCashIn.maxOrNull() ?: 0.0
        val maxOut = newCashOut.maxOrNull() ?: 0.0
        val rawMax = max(maxIn, maxOut)

        maxVal = calculateNiceMax(rawMax)
        selectedIndex = if (defaultSelectedIndex in newLabels.indices) {
            defaultSelectedIndex
        } else if (newLabels.isNotEmpty()) {
            newLabels.size - 1
        } else {
            -1
        }

        invalidate()
    }

    private fun calculateNiceMax(rawMax: Double): Double {
        if (rawMax <= 0.0) return 100_000.0
        val target = rawMax * 1.15
        val magnitude = Math.pow(10.0, Math.floor(Math.log10(target)))
        val normalized = target / magnitude
        val niceNormalized = when {
            normalized <= 1.2 -> 1.2
            normalized <= 1.5 -> 1.5
            normalized <= 2.0 -> 2.0
            normalized <= 2.5 -> 2.5
            normalized <= 3.0 -> 3.0
            normalized <= 4.0 -> 4.0
            normalized <= 5.0 -> 5.0
            normalized <= 7.5 -> 7.5
            normalized <= 10.0 -> 10.0
            else -> 10.0
        }
        return niceNormalized * magnitude
    }

    private fun formatYLabel(value: Double): String {
        if (value <= 0.0) return "0"
        return when {
            value >= 1_000_000_000.0 -> {
                val b = value / 1_000_000_000.0
                if (b % 1.0 == 0.0) "M" else String.format(Locale.US, "%.1fM", b)
            }
            value >= 1_000_000.0 -> {
                val jt = value / 1_000_000.0
                if (jt % 1.0 == 0.0) "jt" else String.format(Locale.US, "%.1fjt", jt)
            }
            value >= 1_000.0 -> {
                val rb = value / 1_000.0
                if (rb % 1.0 == 0.0) "rb" else String.format(Locale.US, "%.0frb", rb)
            }
            else -> value.toInt().toString()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (dates.size < 2) return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val paddingLeft = 85f
                val paddingRight = 35f
                val chartWidth = width - paddingLeft - paddingRight
                val stepX = chartWidth / (dates.size - 1)

                val touchX = event.x.coerceIn(paddingLeft, width - paddingRight)
                val newIndex = ((touchX - paddingLeft + stepX / 2f) / stepX).toInt().coerceIn(0, dates.size - 1)

                if (newIndex != selectedIndex) {
                    selectedIndex = newIndex
                    val cIn = cashInValues.getOrElse(selectedIndex) { 0.0 }
                    val cOut = cashOutValues.getOrElse(selectedIndex) { 0.0 }
                    onPointSelectedListener?.invoke(selectedIndex, dates[selectedIndex], cIn, cOut)
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val paddingLeft = 85f
        val paddingRight = 35f
        val paddingTop = 40f
        val paddingBottom = 70f

        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom
        if (chartWidth <= 0 || chartHeight <= 0) return

        // 1. Gambar Garis Kisi Horizontal & Label Sumbu Y (4 interval: 0, 25%, 50%, 75%, 100%)
        val stepsCount = 4
        for (i in 0..stepsCount) {
            val fraction = i.toFloat() / stepsCount.toFloat()
            val yVal = maxVal * fraction
            val yPos = paddingTop + chartHeight - fraction * chartHeight

            canvas.drawText(formatYLabel(yVal), paddingLeft - 16f, yPos + 8f, labelYPaint)

            val gridPath = Path().apply {
                moveTo(paddingLeft, yPos)
                lineTo(width - paddingRight, yPos)
            }
            canvas.drawPath(gridPath, gridPaint)
        }

        // 2. Jika belum ada data
        if (dates.isEmpty()) {
            canvas.drawText(
                "Belum ada data pada rentang ini",
                paddingLeft + chartWidth / 2f,
                paddingTop + chartHeight / 2f,
                emptyTextPaint
            )
            return
        }

        val hasActivity = cashInValues.any { it > 0.0 } || cashOutValues.any { it > 0.0 }

        // 3. Hitung koordinat X & Y untuk masing-masing titik
        val greenPoints = mutableListOf<PointF>()
        val redPoints = mutableListOf<PointF>()

        if (dates.size == 1) {
            val px = paddingLeft + chartWidth / 2f
            val pyGreen = paddingTop + chartHeight - ((cashInValues.firstOrNull() ?: 0.0) / maxVal).toFloat() * chartHeight
            val pyRed = paddingTop + chartHeight - ((cashOutValues.firstOrNull() ?: 0.0) / maxVal).toFloat() * chartHeight
            greenPoints.add(PointF(px, pyGreen))
            redPoints.add(PointF(px, pyRed))

            val isSelected = (selectedIndex == 0)
            val p = if (isSelected) selectedTextPaint else textPaint
            canvas.drawText(dates[0], px, height - 16f, p)
        } else {
            val stepX = chartWidth / (dates.size - 1)
            for (i in dates.indices) {
                val px = paddingLeft + i * stepX
                val inVal = cashInValues.getOrElse(i) { 0.0 }
                val outVal = cashOutValues.getOrElse(i) { 0.0 }

                val pyGreen = paddingTop + chartHeight - (inVal / maxVal).toFloat() * chartHeight
                val pyRed = paddingTop + chartHeight - (outVal / maxVal).toFloat() * chartHeight

                greenPoints.add(PointF(px, pyGreen.coerceIn(paddingTop, paddingTop + chartHeight)))
                redPoints.add(PointF(px, pyRed.coerceIn(paddingTop, paddingTop + chartHeight)))

                // Label tanggal sumbu X
                val isSelected = (i == selectedIndex)
                val p = if (isSelected) selectedTextPaint else textPaint
                canvas.drawText(dates[i], px, height - 16f, p)
            }
        }

        if (!hasActivity) {
            canvas.drawText(
                "Tidak ada mutasi kas pada periode ini",
                paddingLeft + chartWidth / 2f,
                paddingTop + chartHeight / 2f,
                emptyTextPaint
            )
        }

        // 4. Gambar Area & Garis Kas Masuk (Hijau)
        if (greenPoints.size >= 2) {
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
        }

        // 5. Gambar Area & Garis Kas Keluar (Merah)
        if (redPoints.size >= 2) {
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
        }

        // 6. Gambar titik lingkaran pada koordinat
        for (i in greenPoints.indices) {
            val gp = greenPoints[i]
            val rp = redPoints[i]
            val isSelected = (i == selectedIndex)

            if (isSelected) {
                canvas.drawCircle(gp.x, gp.y, 16f, selectedRingPaint)
                canvas.drawCircle(rp.x, rp.y, 16f, selectedRingPaint)
            }

            val radius = if (isSelected) 10f else 8f
            canvas.drawCircle(gp.x, gp.y, radius, greenNodePaint)
            canvas.drawCircle(gp.x, gp.y, radius, greenNodeStrokePaint)

            canvas.drawCircle(rp.x, rp.y, radius, redNodePaint)
            canvas.drawCircle(rp.x, rp.y, radius, redNodeStrokePaint)
        }

        // 7. Jika ada titik yang dipilih dan memiliki nilai > 0, gambar info badge ringkas di atas titik
        if (selectedIndex in greenPoints.indices && hasActivity) {
            val sp = greenPoints[selectedIndex]
            val selIn = cashInValues.getOrElse(selectedIndex) { 0.0 }
            val selOut = cashOutValues.getOrElse(selectedIndex) { 0.0 }
            val infoText = "+${formatYLabel(selIn)} / -${formatYLabel(selOut)}"

            val textWidth = tooltipTextPaint.measureText(infoText)
            val badgeW = textWidth + 24f
            val badgeH = 34f
            val badgeX = sp.x.coerceIn(paddingLeft + badgeW / 2f, width - paddingRight - badgeW / 2f)
            val badgeY = max(sp.y - 30f, paddingTop + badgeH)

            val badgeRect = RectF(badgeX - badgeW / 2f, badgeY - badgeH, badgeX + badgeW / 2f, badgeY)
            canvas.drawRoundRect(badgeRect, 8f, 8f, tooltipBgPaint)
            canvas.drawText(infoText, badgeX, badgeY - 10f, tooltipTextPaint)
        }
    }
}