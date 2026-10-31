package com.denkasir

import com.denkasir.data.local.entity.CashFlowEntity
import com.denkasir.utils.DateFilterHelper
import com.denkasir.utils.ReportPeriodType
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class DateFilterAndChartTest {

    @Test
    fun testTodayRangeCalculation() {
        val today = DateFilterHelper.getTodayRange()
        assertEquals(ReportPeriodType.HARIAN, today.type)
        assertEquals("Hari Ini", today.shortLabel)
        assertTrue(today.startMs < today.endMs)

        val cal = Calendar.getInstance().apply { timeInMillis = today.startMs }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))

        val calEnd = Calendar.getInstance().apply { timeInMillis = today.endMs }
        assertEquals(23, calEnd.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, calEnd.get(Calendar.MINUTE))
        assertEquals(59, calEnd.get(Calendar.SECOND))
    }

    @Test
    fun testWeeklyRangeCalculation() {
        val week = DateFilterHelper.getWeeklyRange()
        assertEquals(ReportPeriodType.MINGGUAN, week.type)
        assertEquals("7 Hari", week.shortLabel)
        assertTrue(week.startMs < week.endMs)

        val durationDays = (week.endMs - week.startMs) / (24L * 3600 * 1000)
        assertEquals(6L, durationDays) // 6 intervals between 7 day boundaries
    }

    @Test
    fun testMonthlyRangeCalculation() {
        val month = DateFilterHelper.getMonthlyRange()
        assertEquals(ReportPeriodType.BULANAN, month.type)
        assertEquals("Bulan Ini", month.shortLabel)

        val calStart = Calendar.getInstance().apply { timeInMillis = month.startMs }
        assertEquals(1, calStart.get(Calendar.DAY_OF_MONTH))

        val calEnd = Calendar.getInstance().apply { timeInMillis = month.endMs }
        assertEquals(calStart.getActualMaximum(Calendar.DAY_OF_MONTH), calEnd.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testCustomRangeCalculation() {
        val now = System.currentTimeMillis()
        val custom = DateFilterHelper.getCustomRange(now - 3 * 24 * 3600 * 1000L, now)
        assertEquals(ReportPeriodType.KUSTOM, custom.type)
        assertEquals("Kustom", custom.shortLabel)
        assertTrue(custom.startMs <= custom.endMs)
    }

    @Test
    fun testChartBucketsHourlyForToday() {
        val today = DateFilterHelper.getTodayRange()
        val sampleFlows = listOf(
            CashFlowEntity(flowType = "IN", category = "PENJUALAN", amount = 50000.0, description = "Pagi", createdAt = today.startMs + 5 * 3600 * 1000L), // 05:00 -> bucket 1
            CashFlowEntity(flowType = "OUT", category = "OPERASIONAL", amount = 15000.0, description = "Siang", createdAt = today.startMs + 13 * 3600 * 1000L), // 13:00 -> bucket 3
            CashFlowEntity(flowType = "IN", category = "PENJUALAN", amount = 100000.0, description = "Malam", createdAt = today.startMs + 21 * 3600 * 1000L) // 21:00 -> bucket 5
        )

        val bucketResult = DateFilterHelper.buildChartBuckets(today, sampleFlows)
        assertEquals(6, bucketResult.labels.size)
        assertEquals(6, bucketResult.cashIn.size)
        assertEquals(6, bucketResult.cashOut.size)

        assertEquals(50000.0, bucketResult.cashIn[1], 0.001)
        assertEquals(15000.0, bucketResult.cashOut[3], 0.001)
        assertEquals(100000.0, bucketResult.cashIn[5], 0.001)
    }

    @Test
    fun testChartBucketsWeekly() {
        val week = DateFilterHelper.getWeeklyRange()
        val dayMs = 24L * 3600 * 1000L
        val sampleFlows = listOf(
            CashFlowEntity(flowType = "IN", category = "PENJUALAN", amount = 200000.0, description = "Day 1", createdAt = week.startMs + 1000L),
            CashFlowEntity(flowType = "OUT", category = "KULAKAN", amount = 80000.0, description = "Day 3", createdAt = week.startMs + 2 * dayMs + 1000L)
        )

        val bucketResult = DateFilterHelper.buildChartBuckets(week, sampleFlows)
        assertEquals(7, bucketResult.labels.size)
        assertEquals(200000.0, bucketResult.cashIn[0], 0.001)
        assertEquals(80000.0, bucketResult.cashOut[2], 0.001)
    }

    @Test
    fun testEmptyDataHandling() {
        val week = DateFilterHelper.getWeeklyRange()
        val bucketResult = DateFilterHelper.buildChartBuckets(week, emptyList())

        assertEquals(7, bucketResult.labels.size)
        assertEquals(7, bucketResult.cashIn.size)
        assertEquals(7, bucketResult.cashOut.size)
        assertTrue(bucketResult.cashIn.all { it == 0.0 })
        assertTrue(bucketResult.cashOut.all { it == 0.0 })
    }
}
