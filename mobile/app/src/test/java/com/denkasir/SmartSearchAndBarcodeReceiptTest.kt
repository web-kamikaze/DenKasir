package com.denkasir

import com.denkasir.data.local.entity.ProductEntity
import com.denkasir.utils.ReceiptFormatterUtils
import com.denkasir.utils.SmartSearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSearchAndBarcodeReceiptTest {

    private fun createProduct(
        id: String,
        name: String,
        category: String = "Makanan",
        barcode: String? = null,
        price: Double = 10000.0
    ): ProductEntity {
        return ProductEntity(
            id = id,
            storeId = "STORE-001",
            name = name,
            category = category,
            sellingPrice = price,
            costPrice = price * 0.8,
            stock = 50.0,
            barcode = barcode,
            syncStatus = "SYNCED",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    // ==========================================
    // 1. SMART SEARCH ENGINE TESTS
    // ==========================================

    @Test
    fun testSmartSearch_UnorderedKeywordsMatch() {
        val product = createProduct("p1", "Indomie Mi Instan Rasa Ayam Goreng", "Makanan", "8991234567")
        
        // Exact user example from prompt: "mei ayam goreng"
        assertTrue("Query 'mei ayam goreng' must match 'Indomie Mi Instan Rasa Ayam Goreng'",
            SmartSearchEngine.matches("mei ayam goreng", product.name, product.category, product.barcode))

        // Out-of-order variations
        assertTrue("Query 'ayam goreng mei' out-of-order must match",
            SmartSearchEngine.matches("ayam goreng mei", product.name, product.category, product.barcode))

        assertTrue("Query 'goreng mie ayam' out-of-order must match",
            SmartSearchEngine.matches("goreng mie ayam", product.name, product.category, product.barcode))

        // Partial tokens
        assertTrue("Query 'ayam mie' must match",
            SmartSearchEngine.matches("ayam mie", product.name, product.category, product.barcode))
    }

    @Test
    fun testSmartSearch_IndonesianPhoneticAndColloquial() {
        val eggProduct = createProduct("p2", "Telur Ayam Ras 1 Kg", "Sembako", "899222333")
        assertTrue("Colloquial 'telor ayam' must match 'Telur Ayam Ras'",
            SmartSearchEngine.matches("telor ayam", eggProduct.name, eggProduct.category, eggProduct.barcode))

        val chiliProduct = createProduct("p3", "Cabai Rawit Merah Segar 250g", "Bumbu", "899333444")
        assertTrue("Colloquial 'cabe rawit' must match 'Cabai Rawit Merah'",
            SmartSearchEngine.matches("cabe rawit", chiliProduct.name, chiliProduct.category, chiliProduct.barcode))

        val oilProduct = createProduct("p4", "Minyak Goreng Bimoli Spesial 2L", "Sembako", "899444555")
        assertTrue("Colloquial 'minak bimoli' must match 'Minyak Goreng Bimoli'",
            SmartSearchEngine.matches("minak bimoli", oilProduct.name, oilProduct.category, oilProduct.barcode))

        val waterProduct = createProduct("p5", "Aqua Air Mineral Botol 600ml", "Minuman", "899555666")
        assertTrue("Colloquial 'akua botol' must match 'Aqua Air Mineral Botol'",
            SmartSearchEngine.matches("akua botol", waterProduct.name, waterProduct.category, waterProduct.barcode))
    }

    @Test
    fun testSmartSearch_TypoToleranceLevenshtein() {
        val product = createProduct("p6", "Indomie Mi Instan Ayam Bawang 69g", "Makanan", "899666777")

        // 1-char typo: "ayamm" instead of "ayam"
        assertTrue("Typo 'ayamm bawangg' must match 'Ayam Bawang'",
            SmartSearchEngine.matches("ayamm bawangg", product.name, product.category, product.barcode))

        // Typo: "indomye" instead of "indomie"
        assertTrue("Typo 'indomye bawang' must match 'Indomie Mi Instan Ayam Bawang'",
            SmartSearchEngine.matches("indomye bawang", product.name, product.category, product.barcode))
    }

    @Test
    fun testSmartSearch_FilterAndRanking() {
        val p1 = createProduct("1", "Indomie Mi Goreng Spesial")
        val p2 = createProduct("2", "Minyak Goreng Sania 2L")
        val p3 = createProduct("3", "Bumbu Racik Ayam Goreng")

        val products = listOf(p1, p2, p3)
        val results = SmartSearchEngine.filterProducts("mei goreng", products)

        assertEquals("Only p1 should match 'mei goreng'", 1, results.size)
        assertEquals("p1 should be the top match", "Indomie Mi Goreng Spesial", results[0].name)
    }

    @Test
    fun testSmartSearch_UnrelatedDoesNotMatch() {
        val product = createProduct("p7", "Sabun Cuci Piring Sunlight Jeruk Nipis 750ml", "Kebersihan", "899777888")

        assertFalse("Query 'mei ayam goreng' must NOT match soap product",
            SmartSearchEngine.matches("mei ayam goreng", product.name, product.category, product.barcode))
    }

    // ==========================================
    // 2. RECEIPT FORMATTER & SMART WRAP TESTS
    // ==========================================

    @Test
    fun testReceiptWrap_NoOrphanUnits() {
        // Issue reported: "Indomie Mi Instan Ayam Bawang 69 g" wrapping 'g' alone on line 2
        val text = "Indomie Mi Instan Ayam Bawang 69 g"
        val wrapped = ReceiptFormatterUtils.wrapText(text, 32)

        assertEquals(2, wrapped.size)
        // Line 2 should never be just "g", it should pull down "69" to make "69 g"
        assertEquals("Indomie Mi Instan Ayam Bawang", wrapped[0])
        assertEquals("69 g", wrapped[1])
        assertTrue("No line should exceed 32 chars", wrapped[0].length <= 32 && wrapped[1].length <= 32)
    }

    @Test
    fun testReceiptWrap_FooterBalancedLines() {
        // "Terima Kasih Atas Kunjungan Anda!" shouldn't leave "Anda!" alone on line 2
        val text = "Terima Kasih Atas Kunjungan Anda!"
        val wrapped = ReceiptFormatterUtils.wrapText(text, 32)

        assertEquals(2, wrapped.size)
        assertEquals("Terima Kasih Atas", wrapped[0])
        assertEquals("Kunjungan Anda!", wrapped[1])
        assertTrue("Line 1 <= 32", wrapped[0].length <= 32)
        assertTrue("Line 2 <= 32", wrapped[1].length <= 32)
    }

    @Test
    fun testReceipt_KeyColonValueAlignment() {
        val line1 = ReceiptFormatterUtils.keyColonValue("No. Nota", "TRX-20261031-001", 32, 11)
        val line2 = ReceiptFormatterUtils.keyColonValue("Tanggal", "31/10/2026 14:00", 32, 11)
        val line3 = ReceiptFormatterUtils.keyColonValue("Kasir", "Kasir Warung", 32, 11)

        val colonIdx1 = line1.indexOf(":")
        val colonIdx2 = line2.indexOf(":")
        val colonIdx3 = line3.indexOf(":")

        assertEquals("Colons must align vertically at the exact same index", colonIdx1, colonIdx2)
        assertEquals("Colons must align vertically at the exact same index", colonIdx2, colonIdx3)
        assertEquals(11, colonIdx1)
    }

    @Test
    fun testReceipt_TwoColumnsRespectsWidth() {
        val result = ReceiptFormatterUtils.twoColumns("Total Tagihan", "Rp 150.000", 32)
        assertEquals(32, result.length)
        assertTrue(result.startsWith("Total Tagihan"))
        assertTrue(result.endsWith("Rp 150.000"))
    }
}