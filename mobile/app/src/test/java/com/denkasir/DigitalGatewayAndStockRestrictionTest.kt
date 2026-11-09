package com.denkasir

import com.denkasir.data.local.entity.DigitalConfigEntity
import com.denkasir.data.local.entity.ProductEntity
import com.denkasir.data.local.entity.isDigitalOutOfStock
import com.denkasir.ui.pos.CartItem
import com.denkasir.ui.pos.CartManager
import com.denkasir.ui.pos.DigitalCatalog
import com.denkasir.ui.pos.DigitalPackageFormatter
import com.denkasir.ui.pos.NominalOption
import com.denkasir.utils.DistributorRule
import com.denkasir.utils.SmsGatewayHelper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DigitalGatewayAndStockRestrictionTest {

    private val dataCategory = DigitalCatalog.getAllCategories().first { it.id == "paket_data" }
    private val pulsaCategory = DigitalCatalog.getAllCategories().first { it.id == "pulsa" }

    @Before
    fun setUp() {
        CartManager.clearCart()
    }

    // =========================================================================
    // 1. PENGUJIAN LOGIKA GATEWAY (H2H API & OTOMAX SMS CENTER)
    // =========================================================================

    @Test
    fun testGatewayUnconfiguredOnNullOrFreshSeededDatabase() {
        // 1. Config Null -> Tidak Boleh Dipesan
        assertFalse(SmsGatewayHelper.isGatewayConfigured(null))

        // 2. Default Seeded Config (URL default ada, tapi API Key kosong, SMS Center kosong, PIN kosong)
        val defaultSeededConfig = DigitalConfigEntity(
            id = "default_config",
            providerName = "OtomaX H2H Server",
            apiUrl = "https://api.distributor-pulsa.com/h2h",
            apiKey = "",
            smsCenterNumbers = "",
            pinPassword = "",
            distributorRulesJson = ""
        )
        assertFalse("H2H API belum siap jika API Key kosong", SmsGatewayHelper.isApiConfigured(defaultSeededConfig))
        assertFalse("SMS Center belum siap jika nomor center dan PIN kosong", SmsGatewayHelper.isSmsConfigured(defaultSeededConfig))
        assertFalse("Gateway wajib diblokir jika H2H dan SMS Center belum disetting", SmsGatewayHelper.isGatewayConfigured(defaultSeededConfig))
    }

    @Test
    fun testGatewayAllowedWhenOnlyH2hApiConfigured() {
        // Pengguna hanya mengisi API H2H (URL & API Key), SMS Center dibiarkan kosong
        val h2hOnlyConfig = DigitalConfigEntity(
            apiUrl = "https://api.otomax-server.com/v1/trx",
            apiKey = "live_secret_key_8899aabb",
            smsCenterNumbers = "",
            pinPassword = "",
            distributorRulesJson = ""
        )
        assertTrue(SmsGatewayHelper.isApiConfigured(h2hOnlyConfig))
        assertFalse(SmsGatewayHelper.isSmsConfigured(h2hOnlyConfig))
        assertTrue("Asal salah satu sudah disetting (H2H API), produk digital boleh dipesan", SmsGatewayHelper.isGatewayConfigured(h2hOnlyConfig))
    }

    @Test
    fun testGatewayAllowedWhenOnlySmsCenterConfigured() {
        // Pengguna hanya mengisi SMS Center OtomaX dan PIN, API Key kosong
        val smsOnlyConfig = DigitalConfigEntity(
            apiUrl = "https://api.distributor-pulsa.com/h2h",
            apiKey = "",
            smsCenterNumbers = "081234567890, 085678901234",
            pinPassword = "1234",
            distributorRulesJson = ""
        )
        assertFalse(SmsGatewayHelper.isApiConfigured(smsOnlyConfig))
        assertTrue(SmsGatewayHelper.isSmsConfigured(smsOnlyConfig))
        assertTrue("Asal salah satu sudah disetting (SMS Center), produk digital boleh dipesan", SmsGatewayHelper.isGatewayConfigured(smsOnlyConfig))
    }

    @Test
    fun testGatewayAllowedWhenOnlyDistributorRulesConfigured() {
        // Pengguna mengatur SMS lewat aturan multi-distributor ber-PIN
        val rules = listOf(
            DistributorRule(
                name = "Distributor Pulsa",
                category = "Pulsa",
                centerNumber = "081122334455",
                pin = "9988"
            )
        )
        val rulesConfig = DigitalConfigEntity(
            apiKey = "",
            smsCenterNumbers = "",
            pinPassword = "",
            distributorRulesJson = SmsGatewayHelper.distributorRulesToJson(rules)
        )
        assertFalse(SmsGatewayHelper.isApiConfigured(rulesConfig))
        assertTrue(SmsGatewayHelper.isSmsConfigured(rulesConfig))
        assertTrue("Aturan distributor ber-PIN memenuhi syarat gateway SMS", SmsGatewayHelper.isGatewayConfigured(rulesConfig))
    }

    @Test
    fun testGatewayAllowedWhenBothH2hAndSmsConfigured() {
        val dualConfig = DigitalConfigEntity(
            apiUrl = "https://api.otomax-server.com/v1/trx",
            apiKey = "live_secret_key_8899aabb",
            smsCenterNumbers = "081234567890",
            pinPassword = "1234"
        )
        assertTrue(SmsGatewayHelper.isApiConfigured(dualConfig))
        assertTrue(SmsGatewayHelper.isSmsConfigured(dualConfig))
        assertTrue(SmsGatewayHelper.isGatewayConfigured(dualConfig))
    }

    @Test
    fun testSmsCenterBlockedIfPinMissing() {
        // Memiliki nomor SMS center tapi tidak ada PIN transaksi
        val noPinConfig = DigitalConfigEntity(
            apiKey = "",
            smsCenterNumbers = "081234567890",
            pinPassword = "",
            distributorRulesJson = ""
        )
        assertFalse("SMS Center wajib memiliki PIN transaksi", SmsGatewayHelper.isSmsConfigured(noPinConfig))
        assertFalse(SmsGatewayHelper.isGatewayConfigured(noPinConfig))
    }

    // =========================================================================
    // 2. PENGUJIAN LOGIKA STOK KOSONG PRODUK DIGITAL
    // =========================================================================

    @Test
    fun testDigitalOutOfStockDetection() {
        // Kasus 1: Produk digital dengan stok = 0.0 (status KOSONG dari distributor)
        val prodZeroStock = ProductEntity(
            name = "[Axis] Kuota 1GB 30hr",
            category = "Paket Data",
            productType = "DIGITAL",
            stock = 0.0,
            providerCode = "AXDG11"
        )
        assertTrue("Produk digital dengan stok 0 wajib ditandai out of stock", prodZeroStock.isDigitalOutOfStock())

        // Kasus 2: Produk digital dengan kata 'KOSONG' di nama
        val prodNameKosong = ProductEntity(
            name = "[Telkomsel] Pulsa 50,000 (Stok Kosong)",
            category = "Pulsa",
            productType = "DIGITAL",
            stock = 999.0,
            providerCode = "T50"
        )
        assertTrue("Produk dengan indikasi KOSONG di nama wajib out of stock", prodNameKosong.isDigitalOutOfStock())

        // Kasus 3: Produk digital dengan kata 'GANGGUAN' di nama
        val prodGangguan = ProductEntity(
            name = "[Indosat] Paket Data 10GB - Server Gangguan",
            category = "Paket Data",
            productType = "DIGITAL",
            stock = 999.0,
            providerCode = "ID10"
        )
        assertTrue("Produk berstatus gangguan wajib out of stock", prodGangguan.isDigitalOutOfStock())

        // Kasus 4: Produk digital normal tersedia (stok 999)
        val prodAvailable = ProductEntity(
            name = "[ByU] Data ByU 10GB 5 Hari",
            category = "Paket Data",
            productType = "DIGITAL",
            stock = 999.0,
            providerCode = "BYU10"
        )
        assertFalse("Produk normal dengan stok 999 harus tersedia", prodAvailable.isDigitalOutOfStock())

        // Kasus 5: Produk ritel dengan stok 0 tidak diproses oleh rule digital
        val retailProd = ProductEntity(
            name = "Indomie Goreng",
            category = "Makanan",
            productType = "RETAIL",
            stock = 0.0
        )
        assertFalse("Produk RETAIL tidak diproses oleh validator isDigitalOutOfStock", retailProd.isDigitalOutOfStock())
    }

    @Test
    fun testNominalOptionAvailabilityAndBadgeTransformation() {
        // Produk KOSONG -> badge "Stok Kosong" & isAvailable = false
        val emptyProduct = ProductEntity(
            name = "[Axis] Kuota 2GB 30hr",
            category = "Paket Data",
            productType = "DIGITAL",
            stock = 0.0,
            sellingPrice = 25000.0,
            costPrice = 22000.0,
            providerCode = "AXDG26"
        )
        val optionEmpty = DigitalPackageFormatter.createOptionFromProduct(emptyProduct, dataCategory, "Axis")
        assertFalse("Option untuk produk stok 0 wajib tidak tersedia", optionEmpty.isAvailable)
        assertEquals("Stok Kosong", optionEmpty.badgeText)

        // Produk TERSEDIA -> badge "Muraaah" & isAvailable = true
        val readyProduct = ProductEntity(
            name = "[Axis] Kuota 5GB 30hr",
            category = "Paket Data",
            productType = "DIGITAL",
            stock = 999.0,
            sellingPrice = 45000.0,
            costPrice = 41000.0,
            providerCode = "AXDG50"
        )
        val optionReady = DigitalPackageFormatter.createOptionFromProduct(readyProduct, dataCategory, "Axis")
        assertTrue("Option untuk produk stok 999 wajib tersedia", optionReady.isAvailable)
        assertEquals("Muraaah", optionReady.badgeText)
    }

    // =========================================================================
    // 3. PENGUJIAN INTEGRASI KERANJANG KASIR & VALIDASI PEMBAYARAN
    // =========================================================================

    @Test
    fun testCartCheckoutValidationSimulation() {
        val readyDigital = ProductEntity(
            name = "[Telkomsel] Pulsa 20.000",
            category = "Pulsa",
            productType = "DIGITAL",
            stock = 999.0,
            sellingPrice = 22000.0,
            costPrice = 20200.0,
            providerCode = "TREG20"
        )
        val emptyDigital = ProductEntity(
            name = "[Telkomsel] Pulsa 100.000 (Gangguan)",
            category = "Pulsa",
            productType = "DIGITAL",
            stock = 0.0,
            sellingPrice = 102000.0,
            costPrice = 98500.0,
            providerCode = "TREG100"
        )
        val retailItem = ProductEntity(
            name = "Minyak Goreng 2L",
            category = "Sembako",
            productType = "RETAIL",
            stock = 15.0,
            sellingPrice = 34000.0
        )

        fun canProceedToPayment(gatewayConfig: DigitalConfigEntity?): Pair<Boolean, String> {
            val snapshot = CartManager.getCartItemsSnapshot()
            val digitalInCart = snapshot.filter { it.product.productType == "DIGITAL" }
            if (digitalInCart.isNotEmpty()) {
                if (!SmsGatewayHelper.isGatewayConfigured(gatewayConfig)) {
                    return Pair(false, "Gateway belum dikonfigurasi")
                }
                val outOfStock = digitalInCart.find { it.product.isDigitalOutOfStock() }
                if (outOfStock != null) {
                    return Pair(false, "Stok produk '${outOfStock.product.name}' sedang kosong / gangguan")
                }
            }
            return Pair(true, "Pembayaran diizinkan")
        }

        // Skenario 1: Keranjang berisi produk digital, tetapi gateway belum diatur
        CartManager.addToCart(readyDigital, 1.0, "081234567890")
        val (ok1, msg1) = canProceedToPayment(null)
        assertFalse(ok1)
        assertEquals("Gateway belum dikonfigurasi", msg1)

        // Skenario 2: Gateway sudah disetting (misal H2H API aktif), tapi ada item digital yang stoknya KOSONG
        val activeH2h = DigitalConfigEntity(apiUrl = "https://api.otomax.com", apiKey = "token123")
        CartManager.addToCart(emptyDigital, 1.0, "081234567890")
        val (ok2, msg2) = canProceedToPayment(activeH2h)
        assertFalse(ok2)
        assertTrue(msg2.contains("sedang kosong / gangguan"))

        // Skenario 3: Hapus produk kosong, sekarang hanya produk digital tersedia dengan gateway aktif
        CartManager.removeItem(CartItem(product = emptyDigital, quantity = 1.0, targetPhone = "081234567890"))
        val (ok3, msg3) = canProceedToPayment(activeH2h)
        assertTrue(ok3)
        assertEquals("Pembayaran diizinkan", msg3)

        // Skenario 4: Keranjang hanya berisi produk ritel -> tidak terpengaruh status gateway digital
        CartManager.clearCart()
        CartManager.addToCart(retailItem, 2.0)
        val (ok4, msg4) = canProceedToPayment(null) // Gateway null
        assertTrue("Produk ritel bebas dari pembatasan gateway digital", ok4)
        assertEquals("Pembayaran diizinkan", msg4)
    }

    @Test
    fun testH2hOrderPayloadCreation() {
        val payload = com.denkasir.data.remote.dto.H2hOrderRequestDto(
            productCode = "PLN20",
            targetNumber = "14123456789",
            clientTrxId = "INV-20261104-001",
            pin = "1234",
            apiKey = "DENKASIR-H2H-LIVE"
        )

        assertEquals("PLN20", payload.productCode)
        assertEquals("14123456789", payload.targetNumber)
        assertEquals("INV-20261104-001", payload.clientTrxId)
        assertEquals("DENKASIR-H2H-LIVE", payload.apiKey)
    }
}
