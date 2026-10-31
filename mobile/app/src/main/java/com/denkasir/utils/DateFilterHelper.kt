package com.denkasir.utils

import com.denkasir.data.local.entity.CashFlowEntity
import java.text.SimpleDateFormat
import java.util.*

enum class ReportPeriodType {
    HARIAN,
    MINGGUAN,
    BULANAN,
    KUSTOM,
    SEMUA
}

data class DateFilterRange(
    val type: ReportPeriodType,
    val startMs: Long,
    val endMs: Long,
    val shortLabel: String,
    val detailedLabel: String
)

data class ChartBucketResult(
    val labels: List<String>,
    val cashIn: List<Double>,
    val cashOut: List<Double>
)

object DateFilterHelper {

    private val localeId = Locale("in", "ID")

    fun getTodayRange(): DateFilterRange {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startMs = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endMs = cal.timeInMillis

        val sdf = SimpleDateFormat("dd MMM yyyy", localeId)
        val dateStr = sdf.format(Date(startMs))

        return DateFilterRange(
            type = ReportPeriodType.HARIAN,
            startMs = startMs,
            endMs = endMs,
            shortLabel = "Hari Ini",
            detailedLabel = "Hari Ini ($dateStr)"
        )
    }

    fun getWeeklyRange(): DateFilterRange {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endMs = cal.timeInMillis

        // 6 hari sebelumnya pukul 00:00:00 (total 7 hari)
        cal.add(Calendar.DAY_OF_YEAR, -6)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startMs = cal.timeInMillis

        val sdf = SimpleDateFormat("dd MMM", localeId)
        val startStr = sdf.format(Date(startMs))
        val endStr = sdf.format(Date(endMs))

        return DateFilterRange(
            type = ReportPeriodType.MINGGUAN,
            startMs = startMs,
            endMs = endMs,
            shortLabel = "7 Hari",
            detailedLabel = "7 Hari Terakhir ($startStr - $endStr)"
        )
    }

    fun getMonthlyRange(): DateFilterRange {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startMs = cal.timeInMillis

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endMs = cal.timeInMillis

        val sdf = SimpleDateFormat("MMMM yyyy", localeId)
        val monthStr = sdf.format(Date(startMs))

        return DateFilterRange(
            type = ReportPeriodType.BULANAN,
            startMs = startMs,
            endMs = endMs,
            shortLabel = "Bulan Ini",
            detailedLabel = "Bulan Ini ($monthStr)"
        )
    }

    fun getCustomRange(selectedStart: Long, selectedEnd: Long): DateFilterRange {
        val s = minOf(selectedStart, selectedEnd)
        val e = maxOf(selectedStart, selectedEnd)

        val calStart = Calendar.getInstance().apply {
            timeInMillis = s
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calEnd = Calendar.getInstance().apply {
            timeInMillis = e
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        val startMs = calStart.timeInMillis
        val endMs = calEnd.timeInMillis

        val sdf = SimpleDateFormat("dd MMM yyyy", localeId)
        val startStr = sdf.format(Date(startMs))
        val endStr = sdf.format(Date(endMs))

        val detailed = if (startStr == endStr) startStr else "$startStr - $endStr"

        return DateFilterRange(
            type = ReportPeriodType.KUSTOM,
            startMs = startMs,
            endMs = endMs,
            shortLabel = "Kustom",
            detailedLabel = "Rentang: $detailed"
        )
    }

    fun getAllTimeRange(): DateFilterRange {
        return DateFilterRange(
            type = ReportPeriodType.SEMUA,
            startMs = 0L,
            endMs = Long.MAX_VALUE,
            shortLabel = "Semua",
            detailedLabel = "Semua Riwayat Database"
        )
    }

    /**
     * Membagi data arus kas ke dalam ember (bucket) waktu untuk disajikan pada grafik.
     */
    fun buildChartBuckets(
        filterRange: DateFilterRange,
        cashFlows: List<CashFlowEntity>
    ): ChartBucketResult {
        return when (filterRange.type) {
            ReportPeriodType.HARIAN -> buildHourlyBuckets(filterRange.startMs, cashFlows)
            ReportPeriodType.MINGGUAN -> buildDailyBuckets(filterRange.startMs, 7, cashFlows)
            ReportPeriodType.BULANAN -> buildMonthlyIntervalBuckets(filterRange.startMs, filterRange.endMs, cashFlows)
            ReportPeriodType.KUSTOM -> {
                val diffDays = ((filterRange.endMs - filterRange.startMs) / (24L * 3600 * 1000)).toInt() + 1
                when {
                    diffDays <= 1 -> buildHourlyBuckets(filterRange.startMs, cashFlows)
                    diffDays in 2..7 -> buildDailyBuckets(filterRange.startMs, diffDays, cashFlows)
                    else -> buildEqualIntervalBuckets(filterRange.startMs, filterRange.endMs, 7, cashFlows)
                }
            }
            ReportPeriodType.SEMUA -> {
                if (cashFlows.isEmpty()) {
                    buildDailyBuckets(System.currentTimeMillis() - 6 * 24 * 3600 * 1000L, 7, emptyList())
                } else {
                    val minTime = cashFlows.minOf { it.createdAt }
                    val maxTime = maxOf(System.currentTimeMillis(), cashFlows.maxOf { it.createdAt })
                    buildEqualIntervalBuckets(minTime, maxTime, 7, cashFlows)
                }
            }
        }
    }

    private fun buildHourlyBuckets(startDayMs: Long, cashFlows: List<CashFlowEntity>): ChartBucketResult {
        val labels = listOf("04:00", "08:00", "12:00", "16:00", "20:00", "24:00")
        val cashIn = MutableList(6) { 0.0 }
        val cashOut = MutableList(6) { 0.0 }

        val cal = Calendar.getInstance().apply { timeInMillis = startDayMs }
        val startOfToday = cal.timeInMillis

        for (flow in cashFlows) {
            val offsetHours = (flow.createdAt - startOfToday) / (3600 * 1000.0)
            val bucketIdx = when {
                offsetHours < 4.0 -> 0
                offsetHours < 8.0 -> 1
                offsetHours < 12.0 -> 2
                offsetHours < 16.0 -> 3
                offsetHours < 20.0 -> 4
                else -> 5
            }
            if (bucketIdx in 0..5) {
                if (flow.flowType == "IN") cashIn[bucketIdx] += flow.amount
                else if (flow.flowType == "OUT") cashOut[bucketIdx] += flow.amount
            }
        }

        return ChartBucketResult(labels, cashIn, cashOut)
    }

    private fun buildDailyBuckets(startMs: Long, daysCount: Int, cashFlows: List<CashFlowEntity>): ChartBucketResult {
        val labels = mutableListOf<String>()
        val cashIn = MutableList(daysCount) { 0.0 }
        val cashOut = MutableList(daysCount) { 0.0 }

        val sdf = SimpleDateFormat("dd/M", localeId)
        val dayMs = 24L * 3600 * 1000

        for (i in 0 until daysCount) {
            val currentDayStart = startMs + i * dayMs
            labels.add(sdf.format(Date(currentDayStart)))
        }

        for (flow in cashFlows) {
            val dayIndex = ((flow.createdAt - startMs) / dayMs).toInt()
            if (dayIndex in 0 until daysCount) {
                if (flow.flowType == "IN") cashIn[dayIndex] += flow.amount
                else if (flow.flowType == "OUT") cashOut[dayIndex] += flow.amount
            }
        }

        return ChartBucketResult(labels, cashIn, cashOut)
    }

    private fun buildMonthlyIntervalBuckets(startMs: Long, endMs: Long, cashFlows: List<CashFlowEntity>): ChartBucketResult {
        return buildEqualIntervalBuckets(startMs, endMs, 7, cashFlows)
    }

    private fun buildEqualIntervalBuckets(startMs: Long, endMs: Long, intervals: Int, cashFlows: List<CashFlowEntity>): ChartBucketResult {
        val labels = mutableListOf<String>()
        val cashIn = MutableList(intervals) { 0.0 }
        val cashOut = MutableList(intervals) { 0.0 }

        val totalSpan = maxOf(1L, endMs - startMs)
        val step = totalSpan / intervals
        val sdf = SimpleDateFormat("dd/MM", localeId)

        for (i in 0 until intervals) {
            val pointTime = startMs + i * step + step / 2
            labels.add(sdf.format(Date(pointTime)))
        }

        for (flow in cashFlows) {
            if (flow.createdAt in startMs..endMs) {
                val idx = ((flow.createdAt - startMs) / step).toInt().coerceIn(0, intervals - 1)
                if (flow.flowType == "IN") cashIn[idx] += flow.amount
                else if (flow.flowType == "OUT") cashOut[idx] += flow.amount
            }
        }

        return ChartBucketResult(labels, cashIn, cashOut)
    }
}
