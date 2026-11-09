package com.denkasir

import com.denkasir.data.local.entity.BkuAccountEntity
import com.denkasir.data.local.entity.CashFlowEntity
import com.denkasir.data.local.entity.DebtEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BkuGeneralLedgerTest {

    @Test
    fun testNetCashCalculation() {
        val cashFlows = listOf(
            CashFlowEntity(id = UUID.randomUUID().toString(), flowType = "IN", category = "Penjualan Kasir", amount = 1000000.0, description = "Shift 1"),
            CashFlowEntity(id = UUID.randomUUID().toString(), flowType = "IN", category = "Pendapatan Pulsa", amount = 250000.0, description = "Top Up"),
            CashFlowEntity(id = UUID.randomUUID().toString(), flowType = "OUT", category = "Belanja Stok", amount = 400000.0, description = "Grosir"),
            CashFlowEntity(id = UUID.randomUUID().toString(), flowType = "OUT", category = "Biaya Listrik", amount = 150000.0, description = "PLN Toko")
        )

        val totalIn = cashFlows.filter { it.flowType == "IN" }.sumOf { it.amount }
        val totalOut = cashFlows.filter { it.flowType == "OUT" }.sumOf { it.amount }
        val netCash = totalIn - totalOut

        assertEquals(1250000.0, totalIn, 0.001)
        assertEquals(550000.0, totalOut, 0.001)
        assertEquals(700000.0, netCash, 0.001)
    }

    @Test
    fun testDebtSettlementLogic() {
        val initialDebt = DebtEntity(
            id = "debt_1",
            customerName = "Ibu Siti",
            customerPhone = "081234567890",
            totalDebt = 100000.0,
            paidAmount = 0.0,
            status = "UNPAID",
            debtType = "PIUTANG"
        )

        // Partial payment 40.000
        val partialPay = 40000.0
        val paidAfter1 = initialDebt.paidAmount + partialPay
        val status1 = if (paidAfter1 >= initialDebt.totalDebt) "PAID" else "PARTIAL"
        assertEquals(40000.0, paidAfter1, 0.001)
        assertEquals("PARTIAL", status1)

        // Full settlement remaining 60.000
        val finalPay = 60000.0
        val paidAfter2 = paidAfter1 + finalPay
        val status2 = if (paidAfter2 >= initialDebt.totalDebt) "PAID" else "PARTIAL"
        assertEquals(100000.0, paidAfter2, 0.001)
        assertEquals("PAID", status2)
    }

    @Test
    fun testBkuAccountCategories() {
        val inAccount = BkuAccountEntity(
            id = "in_1",
            accountName = "Pendapatan Titipan",
            accountType = "IN",
            accountCode = "4-1006"
        )
        val outAccount = BkuAccountEntity(
            id = "out_1",
            accountName = "Biaya Wifi",
            accountType = "OUT",
            accountCode = "5-1009"
        )

        assertEquals("IN", inAccount.accountType)
        assertEquals("OUT", outAccount.accountType)
        assertTrue(inAccount.accountCode?.startsWith("4-") == true)
        assertTrue(outAccount.accountCode?.startsWith("5-") == true)
    }
}
