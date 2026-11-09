package com.denkasir

import com.denkasir.data.local.entity.ProductEntity
import com.denkasir.ui.pos.DigitalCatalog
import com.denkasir.ui.pos.DigitalCategoryItem
import com.denkasir.ui.pos.DigitalPackageFormatter
import com.denkasir.ui.pos.DigitalSection
import com.denkasir.ui.pos.NominalOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitalPackageCardUiTest {

    private val dataCategory = DigitalCatalog.getAllCategories().first { it.id == "paket_data" }
    private val pulsaCategory = DigitalCatalog.getAllCategories().first { it.id == "pulsa" }
    private val plnCategory = DigitalCatalog.getAllCategories().first { it.id == "listrik_pln" }

    @Test
    fun testDataPackageByUCardFormattingMatchesImage2() {
        // Simulating the exact package shown in Image #2: Data ByU 10GB 5 Hari (Rp 12.500)
        val product = ProductEntity(
            name = "[ByU] Data ByU 10GB 5 Hari",
            category = "Paket Data",
            productType = "DIGITAL",
            sellingPrice = 12500.0,
            costPrice = 11000.0,
            providerCode = "BYU10GB5D",
            stock = 999.0
        )

        val option = DigitalPackageFormatter.createOptionFromProduct(product, dataCategory, "By.U")

        // 1. Badge Top-Left
        assertEquals("Muraaah", option.badgeText)

        // 2. Column 1: Kuota
        assertEquals("Kuota", option.column1Title)
        assertEquals("10 GB", option.quotaLabel)

        // 3. Column 2: Masa Berlaku
        assertEquals("Masa berlaku", option.column2Title)
        assertEquals("5 hari", option.validityLabel)

        // 4. Column 3: Harga
        assertEquals(12500.0, option.sellingPrice, 0.01)
        assertTrue("Original price must be higher than selling price for strikethrough promo", option.originalPrice > option.sellingPrice)
        assertEquals(15500.0, option.originalPrice, 0.01)

        // 5. Bottom Bar
        assertEquals("Data ByU 10GB 5 Hari", option.label)
        assertEquals("BYU10GB5D", option.productEntity?.providerCode)
    }

    @Test
    fun testVoicePackageWithZeroGbMatchesImage2() {
        // Simulating a voice / call package with 0 GB quota as seen in Image #2
        val product = ProductEntity(
            name = "[ByU] Paket Nelpon 100 Menit 7 Hari",
            category = "Paket Data",
            productType = "DIGITAL",
            sellingPrice = 8500.0,
            costPrice = 7500.0,
            providerCode = "BYUNEL100",
            stock = 999.0
        )

        val option = DigitalPackageFormatter.createOptionFromProduct(product, dataCategory, "By.U")

        assertEquals("Muraaah", option.badgeText)
        assertEquals("Kuota", option.column1Title)
        assertEquals("100 Menit", option.quotaLabel)
        assertEquals("5 hari".takeLast(4), option.validityLabel.takeLast(4)) // ends with "hari"
        assertEquals("7 hari", option.validityLabel)
        assertEquals(8500.0, option.sellingPrice, 0.01)
        assertTrue(option.originalPrice > option.sellingPrice)
    }

    @Test
    fun testPulsaCardFormatting() {
        val product = ProductEntity(
            name = "[Pulsa Telkomsel] Pulsa 10.000",
            category = "Pulsa",
            productType = "DIGITAL",
            sellingPrice = 12000.0,
            costPrice = 10200.0,
            providerCode = "TREG10",
            stock = 999.0
        )

        val option = DigitalPackageFormatter.createOptionFromProduct(product, pulsaCategory, "Telkomsel")

        assertEquals("Muraaah", option.badgeText)
        assertEquals("Pulsa", option.column1Title)
        assertEquals("10.000", option.quotaLabel)
        assertEquals("Masa berlaku", option.column2Title)
        assertEquals("30 hari", option.validityLabel)
        assertEquals(12000.0, option.sellingPrice, 0.01)
        assertTrue(option.originalPrice > option.sellingPrice)
        assertEquals("Pulsa 10.000", option.label)
    }

    @Test
    fun testPlnTokenCardFormatting() {
        val product = ProductEntity(
            name = "[Token PLN] TOKEN PLN 50RB",
            category = "Listrik PLN",
            productType = "DIGITAL",
            sellingPrice = 52500.0,
            costPrice = 50200.0,
            providerCode = "PLN50",
            stock = 999.0
        )

        val option = DigitalPackageFormatter.createOptionFromProduct(product, plnCategory, "PLN Prabayar")

        assertEquals("Muraaah", option.badgeText)
        assertEquals("Token", option.column1Title)
        assertEquals("50.000", option.quotaLabel)
        assertEquals("Masa berlaku", option.column2Title)
        assertEquals("Selamanya", option.validityLabel)
        assertEquals(52500.0, option.sellingPrice, 0.01)
    }

    @Test
    fun testCustomNominalOptionEnrichment() {
        val rawCustom = NominalOption("Lainnya", 0.0, 0.0, 0.0, isCustom = true)
        val enriched = DigitalPackageFormatter.enrichOption(rawCustom, dataCategory, "Telkomsel")

        assertTrue(enriched.isCustom)
        assertEquals("Manual", enriched.badgeText)
        assertEquals("Bebas", enriched.quotaLabel)
        assertEquals("-", enriched.validityLabel)
        assertEquals(0.0, enriched.originalPrice, 0.01)
        assertEquals("Input Nominal Khusus / Tagihan", enriched.label)
    }

    @Test
    fun testFallbackDefaultNominalsAutoEnrichment() {
        val defaultOption = NominalOption("1 GB / 30 Hari", 1000.0, 15000.0, 13000.0)
        val enriched = DigitalPackageFormatter.enrichOption(defaultOption, dataCategory, "Telkomsel Data")

        assertEquals("Muraaah", enriched.badgeText)
        assertEquals("Kuota", enriched.column1Title)
        assertEquals("1 GB", enriched.quotaLabel)
        assertEquals("Masa berlaku", enriched.column2Title)
        assertEquals("30 hari", enriched.validityLabel)
        assertEquals(15000.0, enriched.sellingPrice, 0.01)
        assertTrue(enriched.originalPrice > enriched.sellingPrice)
    }
}
