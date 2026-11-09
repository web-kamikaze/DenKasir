package com.denkasir

import com.denkasir.data.local.entity.CashFlowEntity
import com.denkasir.data.local.entity.DebtEntity
import com.denkasir.data.local.entity.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BkuAndTransactionLogicTest {

    @Test
    fun testKasbonDoesNotRecordCashInflowToPreventDoubleCounting() {
        val simulatedCashFlows = mutableListOf<CashFlowEntity>()
        val simulatedDebts = mutableListOf<DebtEntity>()

        fun processCheckout(paymentMethod: String, amount: Double, custName: String?, custPhone: String?) {
            // Logika modern DenKasir:
            if (paymentMethod in listOf("CASH", "QRIS", "TRANSFER")) {
                simulatedCashFlows.add(
                    CashFlowEntity(
                        flowType = "IN",
                        category = "PENJUALAN",
                        amount = amount,
                        description = "Penjualan ($paymentMethod)"
                    )
                )
            } else if (paymentMethod == "KASBON") {
                simulatedDebts.add(
                    DebtEntity(
                        customerName = custName ?: "Pelanggan Kasbon",
                        customerPhone = custPhone,
                        totalDebt = amount,
                        paidAmount = 0.0,
                        status = "UNPAID",
                        debtType = "PIUTANG"
                    )
                )
            }
        }

        // 1. Transaksi Tunai Rp 50.000
        processCheckout("CASH", 50000.0, "Budi", "08123456789")
        assertEquals(1, simulatedCashFlows.size)
        assertEquals(50000.0, simulatedCashFlows.first().amount, 0.001)
        assertEquals(0, simulatedDebts.size)

        // 2. Transaksi Kasbon Rp 75.000
        processCheckout("KASBON", 75000.0, "Pak Slamet", "08987654321")
        // Kas masuk BKU TIDAK bertambah karena uang belum diterima
        assertEquals(1, simulatedCashFlows.size)
        assertEquals(1, simulatedDebts.size)
        assertEquals("Pak Slamet", simulatedDebts.first().customerName)
        assertEquals("08987654321", simulatedDebts.first().customerPhone)
        assertEquals(75000.0, simulatedDebts.first().totalDebt, 0.001)

        // 3. Pelanggan Melunasi Kasbon Sebesar Rp 75.000 di kemudian hari
        val debt = simulatedDebts.first()
        val payAmount = 75000.0
        val updatedDebt = debt.copy(
            paidAmount = debt.paidAmount + payAmount,
            status = if (debt.paidAmount + payAmount >= debt.totalDebt) "PAID" else "PARTIAL"
        )
        simulatedDebts[0] = updatedDebt

        // Uang tunai riil baru masuk kas toko saat pelunasan:
        simulatedCashFlows.add(
            CashFlowEntity(
                flowType = "IN",
                category = "Penerimaan Pelunasan Piutang",
                amount = payAmount,
                description = "Pelunasan an. ${debt.customerName}"
            )
        )

        // Verifikasi: Kas masuk BKU totalnya tepat Rp 125.000 (Rp 50.000 tunai + Rp 75.000 pelunasan piutang), TIDAK ADA dobel hitung!
        val totalCashIn = simulatedCashFlows.filter { it.flowType == "IN" }.sumOf { it.amount }
        assertEquals(125000.0, totalCashIn, 0.001)
        assertEquals("PAID", simulatedDebts.first().status)
    }

    @Test
    fun testOperationalExpensesIncludesStockOpnameLoss() {
        val flows = listOf(
            CashFlowEntity(flowType = "IN", category = "PENJUALAN", amount = 500000.0, description = "Penjualan"),
            CashFlowEntity(flowType = "OUT", category = "Beban Listrik Toko", amount = 50000.0, description = "PLN"),
            CashFlowEntity(flowType = "OUT", category = "Gaji Karyawan Toko", amount = 100000.0, description = "Gaji"),
            CashFlowEntity(flowType = "OUT", category = "Penyesuaian Selisih Stok", amount = 35000.0, description = "Barang Rusak"),
            CashFlowEntity(flowType = "OUT", category = "Belanja Stok Barang / Kulakan", amount = 200000.0, description = "Kulakan")
        )

        val opExpenses = flows.filter {
            it.flowType == "OUT" && (
                it.category.contains("OPERASIONAL", ignoreCase = true) ||
                it.category.contains("BEBAN", ignoreCase = true) ||
                it.category.contains("LISTRIK", ignoreCase = true) ||
                it.category.contains("GAJI", ignoreCase = true) ||
                it.category.contains("SEWA", ignoreCase = true) ||
                it.category.contains("SELISIH STOK", ignoreCase = true) ||
                it.category.contains("PENYUSUTAN", ignoreCase = true)
            )
        }.sumOf { it.amount }

        // Beban: 50.000 (listrik) + 100.000 (gaji) + 35.000 (selisih stok) = 185.000
        // Kulakan 200.000 adalah persediaan aset, bukan beban operasional
        assertEquals(185000.0, opExpenses, 0.001)
    }

    @Test
    fun testTransferPaymentMethodSupport() {
        val trx = TransactionEntity(
            invoiceNumber = "TRX-20261101-001",
            totalAmount = 150000.0,
            paymentMethod = "TRANSFER",
            customerName = "Ibu Dewi"
        )

        assertEquals("TRANSFER", trx.paymentMethod)
        assertEquals(150000.0, trx.totalAmount, 0.001)
    }
}
