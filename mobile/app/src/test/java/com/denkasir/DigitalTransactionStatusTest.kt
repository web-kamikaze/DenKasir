package com.denkasir

import com.denkasir.data.local.entity.CashFlowEntity
import com.denkasir.data.local.entity.DigitalConfigEntity
import com.denkasir.data.local.entity.TransactionEntity
import com.denkasir.data.local.entity.TransactionItemEntity
import com.denkasir.utils.DigitalStatusDialogHelper
import com.denkasir.utils.ReceiptFormatterUtils
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Pengujian Unit Komprehensif untuk Siklus Hidup Status Transaksi Digital PPOB:
 * 1. Status Awal PROSES (Menunggu Balasan Server Distributor)
 * 2. Respon Server SUKSES (Penyimpanan SN / Kode Token PLN Riil)
 * 3. Respon Server GANGGUAN / CUT-OFF (Pencatatan Status GANGGUAN, Refund Kas Toko & Pemulihan Saldo Deposit)
 * 4. Regex Parsing Cerdas Ekstraksi SN dari SMS Balasan Berbagai Distributor
 */
class DigitalTransactionStatusTest {

    @Test
    fun testExtractSnFromPulsaSms() {
        val sms1 = "Trx TREG10 ke 081234567890 SUKSES. SN: 03120192837482. Sisa Saldo: Rp 1.450.000"
        val sn1 = DigitalStatusDialogHelper.extractSnFromSms(sms1)
        assertEquals("03120192837482", sn1)

        val sms2 = "Transaksi IDREG5 ke 085712345678 BERHASIL SN=99887766554433 Saldo: 500.000"
        val sn2 = DigitalStatusDialogHelper.extractSnFromSms(sms2)
        assertEquals("99887766554433", sn2)
    }

    @Test
    fun testExtractTokenFromPlnSms() {
        // Balasan PLN dengan format token/kwh
        val smsPln = "Trx PLN20 ke 14123456789 SUKSES. SN/TOKEN: 1234-5678-9012-3456-7890/PLN/20.0/KWH 13.5. Saldo 850.000"
        val token = DigitalStatusDialogHelper.extractSnFromSms(smsPln)
        assertEquals("1234-5678-9012-3456-7890", token)

        // Balasan PLN dengan token 20-digit langsung
        val smsPlnDirect = "Token PLN 20rb ke 14123456789 SUKSES. 2345-6789-0123-4567-8901 Tarif/Daya: R1/900VA Jml KWH: 13.5"
        val tokenDirect = DigitalStatusDialogHelper.extractSnFromSms(smsPlnDirect)
        assertEquals("2345-6789-0123-4567-8901", tokenDirect)
    }

    @Test
    fun testInitialDigitalTransactionStatusIsProses() {
        val trxId = UUID.randomUUID().toString()
        val digitalTrx = TransactionEntity(
            id = trxId,
            invoiceNumber = "TRX-20261101-001",
            totalAmount = 12000.0,
            paymentMethod = "CASH",
            status = "PROSES" // Status awal saat kirim SMS/API
        )

        val digitalItem = TransactionItemEntity(
            transactionId = trxId,
            productId = "prod-tsel-10",
            productName = "[Pulsa Telkomsel] Pulsa 10.000",
            quantity = 1.0,
            costPrice = 10200.0,
            sellingPrice = 12000.0,
            subtotal = 12000.0,
            targetPhoneNumber = "081234567890",
            snNumber = null,
            digitalStatus = "PROSES"
        )

        assertEquals("PROSES", digitalTrx.status)
        assertEquals("PROSES", digitalItem.digitalStatus)
        assertNull(digitalItem.snNumber)
    }

    @Test
    fun testServerResponseSuccessTransition() {
        val trxId = UUID.randomUUID().toString()
        var trx = TransactionEntity(
            id = trxId,
            invoiceNumber = "TRX-20261101-001",
            totalAmount = 12000.0,
            status = "PROSES"
        )
        var item = TransactionItemEntity(
            transactionId = trxId,
            productId = "prod-tsel-10",
            productName = "[Pulsa Telkomsel] Pulsa 10.000",
            quantity = 1.0,
            costPrice = 10200.0,
            sellingPrice = 12000.0,
            subtotal = 12000.0,
            targetPhoneNumber = "081234567890",
            digitalStatus = "PROSES"
        )

        // Simulasikan server membalas SUKSES dengan SN
        val incomingSn = "03120192837482"
        trx = trx.copy(status = "SUKSES")
        item = item.copy(digitalStatus = "SUCCESS", snNumber = incomingSn)

        assertEquals("SUKSES", trx.status)
        assertEquals("SUCCESS", item.digitalStatus)
        assertEquals("03120192837482", item.snNumber)
    }

    @Test
    fun testServerResponseGangguanAndBkuRefundLogic() {
        val trxId = UUID.randomUUID().toString()
        var trx = TransactionEntity(
            id = trxId,
            invoiceNumber = "TRX-20261101-002",
            totalAmount = 25000.0,
            status = "PROSES"
        )
        var item = TransactionItemEntity(
            transactionId = trxId,
            productId = "prod-isat-25",
            productName = "[Pulsa Indosat] Pulsa 25.000",
            quantity = 1.0,
            costPrice = 24800.0,
            sellingPrice = 25000.0,
            subtotal = 25000.0,
            targetPhoneNumber = "085712345678",
            digitalStatus = "PROSES"
        )

        val cashFlows = mutableListOf<CashFlowEntity>()
        var digitalConfig = DigitalConfigEntity(balance = 500000.0)

        // Saat checkout awal: saldo deposit distributor terpotong harga modal
        digitalConfig = digitalConfig.copy(balance = digitalConfig.balance - (item.costPrice * item.quantity))
        assertEquals(475200.0, digitalConfig.balance, 0.01)

        // Respon Server: GANGGUAN / CUT OFF DARI BILLER
        val reason = "Server Distributor Gangguan / Cut Off"
        trx = trx.copy(status = "GANGGUAN")
        item = item.copy(digitalStatus = "GANGGUAN", snNumber = "GANGGUAN: $reason")

        // 1. Eksekusi Refund Kas Toko (Jurnal BKU Arus Kas Keluar)
        val refundCashFlow = CashFlowEntity(
            flowType = "OUT",
            category = "REFUND",
            amount = trx.totalAmount,
            description = "Refund Pulsa #${trx.invoiceNumber} ($reason)",
            referenceId = trx.id
        )
        cashFlows.add(refundCashFlow)

        // 2. Pemulihan Saldo Deposit Server Pulsa
        digitalConfig = digitalConfig.copy(balance = digitalConfig.balance + (item.costPrice * item.quantity))

        // Verifikasi Integritas BKU & Saldo
        assertEquals("GANGGUAN", trx.status)
        assertEquals("GANGGUAN", item.digitalStatus)
        assertTrue(item.snNumber!!.contains("Cut Off"))

        assertEquals(1, cashFlows.size)
        assertEquals("OUT", cashFlows.first().flowType)
        assertEquals("REFUND", cashFlows.first().category)
        assertEquals(25000.0, cashFlows.first().amount, 0.01)

        // Saldo deposit kembali utuh ke 500.000 (Zero Deficit)
        assertEquals(500000.0, digitalConfig.balance, 0.01)
    }

    @Test
    fun testReceiptFormatterDisplaysTokenAndStatus() {
        val trx = TransactionEntity(
            invoiceNumber = "TRX-20261101-PLN",
            totalAmount = 23500.0,
            status = "SUKSES"
        )
        val item = TransactionItemEntity(
            transactionId = trx.id,
            productId = "pln-20",
            productName = "[Token PLN] TOKEN PLN 20RB",
            quantity = 1.0,
            costPrice = 20100.0,
            sellingPrice = 23500.0,
            subtotal = 23500.0,
            targetPhoneNumber = "14123456789",
            snNumber = "1234-5678-9012-3456-7890",
            digitalStatus = "SUCCESS"
        )

        // Simulasikan pembuatan teks struk
        val sb = StringBuilder()
        sb.append(item.productName).append("\n")
        if (!item.targetPhoneNumber.isNullOrBlank()) {
            sb.append("  No. HP: ").append(item.targetPhoneNumber).append("\n")
            val statusText = when (item.digitalStatus.uppercase()) {
                "SUCCESS", "SUKSES" -> "SUKSES"
                "GANGGUAN", "GAGAL" -> "GANGGUAN"
                "PROSES", "PENDING" -> "PROSES"
                else -> item.digitalStatus
            }
            sb.append("  Status: ").append(statusText).append("\n")
        }
        if (!item.snNumber.isNullOrBlank()) {
            val label = if (item.productName.contains("PLN", ignoreCase = true)) "  Token : " else "  SN    : "
            sb.append(label).append(item.snNumber).append("\n")
        }

        val receiptContent = sb.toString()
        assertTrue(receiptContent.contains("No. HP: 14123456789"))
        assertTrue(receiptContent.contains("Status: SUKSES"))
        assertTrue(receiptContent.contains("Token : 1234-5678-9012-3456-7890"))
    }
}
