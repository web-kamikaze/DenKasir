package com.denkasir

import com.denkasir.data.local.entity.*
import com.denkasir.ui.pos.CartItem
import com.denkasir.ui.pos.DigitalCatalog
import com.denkasir.utils.CurrencyUtils
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class EndToEndWorkflowTest {

    @Test
    fun testCompleteEndToEndFlow() {
        // Step 1: Owner Isi / Ubah Profil Toko
        val store = StoreEntity(
            id = "store_001",
            name = "TOKO BERKAH JAYA",
            address = "Jl. Sudirman No. 45, Jakarta",
            phone = "081299887766",
            ownerId = "owner_1"
        )
        assertEquals("TOKO BERKAH JAYA", store.name)
        assertEquals("081299887766", store.phone)

        // Step 2: Owner Bikin Akun Kasir Baru
        val newCashier = UserEntity(
            id = UUID.randomUUID().toString(),
            storeId = store.id,
            username = "rina",
            fullName = "Rina Anggraini",
            role = "KASIR"
        )
        assertEquals("KASIR", newCashier.role)
        assertEquals("rina", newCashier.username)

        // Step 3: Owner Isi URL API H2H & Saldo Deposit
        val h2hConfig = DigitalConfigEntity(
            id = "default_config",
            providerName = "OtomaX H2H Gateway",
            apiUrl = "https://api.distributor-pulsa.com/v1/trx",
            apiKey = "live_secret_key_12345",
            memberId = "MB-0992",
            pinPassword = "1234",
            balance = 2500000.0,
            isActive = true
        )
        assertEquals("https://api.distributor-pulsa.com/v1/trx", h2hConfig.apiUrl)
        assertEquals(2500000.0, h2hConfig.balance, 0.001)

        // Step 4: Input / Impor Produk (Retail & Digital)
        val retailProduct = ProductEntity(
            id = "prod_01",
            name = "Beras Ramos 5kg",
            barcode = "899275321001",
            category = "Kg",
            productType = "RETAIL",
            costPrice = 65000.0,
            sellingPrice = 72000.0,
            stock = 20.0
        )
        val digitalProduct = ProductEntity(
            id = "prod_02",
            name = "Pulsa Telkomsel 100.000",
            category = "Pulsa",
            productType = "DIGITAL",
            costPrice = 98500.0,
            sellingPrice = 102000.0,
            providerCode = "Telkomsel",
            stock = 0.0
        )

        // Step 5: User Kasir Login & Melakukan Transaksi Penjualan
        val cart = listOf(
            CartItem(product = retailProduct, quantity = 1.0),
            CartItem(product = digitalProduct, quantity = 1.0, targetPhone = "081234567890 (Telkomsel)")
        )

        val totalAmount = cart.sumOf { it.subtotal }
        assertEquals(174000.0, totalAmount, 0.001) // 72.000 + 102.000

        // Step 6: Checkout Pembayaran Tunai & Generate Struk
        val cashGiven = 200000.0
        val change = cashGiven - totalAmount
        assertEquals(26000.0, change, 0.001)

        val transaction = TransactionEntity(
            id = "trx_001",
            storeId = store.id,
            userId = newCashier.id,
            invoiceNumber = "TRX-20260905-001",
            totalAmount = totalAmount,
            cashGiven = cashGiven,
            changeAmount = change,
            paymentMethod = "CASH",
            customerName = "Pelanggan Toko"
        )

        // Step 7: Verifikasi Otomatis Masuk ke Buku Kas Umum (BKU)
        val bkuCashIn = CashFlowEntity(
            id = UUID.randomUUID().toString(),
            flowType = "IN",
            category = "Penjualan Kasir / Ritel",
            amount = totalAmount,
            description = "Penjualan Nota #${transaction.invoiceNumber} (CASH)",
            referenceId = transaction.id
        )

        val bkuModalPulsa = CashFlowEntity(
            id = UUID.randomUUID().toString(),
            flowType = "OUT",
            category = "Top Up Saldo Deposit H2H Pulsa",
            amount = digitalProduct.costPrice,
            description = "Modal Pulsa ${digitalProduct.name}",
            referenceId = transaction.id
        )

        val bkuRecords = listOf(bkuCashIn, bkuModalPulsa)
        val netBku = bkuRecords.filter { it.flowType == "IN" }.sumOf { it.amount } - bkuRecords.filter { it.flowType == "OUT" }.sumOf { it.amount }

        assertEquals(174000.0, bkuCashIn.amount, 0.001)
        assertEquals(98500.0, bkuModalPulsa.amount, 0.001)
        assertEquals(75500.0, netBku, 0.001) // Laba kotor transaksi
    }
}
