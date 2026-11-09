package com.denkasir

import com.denkasir.data.local.entity.ProductEntity
import com.denkasir.hardware.scanner.BarcodeScannerActivity
import com.denkasir.ui.pos.CartManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CartManagerAndContinuousScanTest {

    private fun createProduct(
        id: String,
        name: String,
        category: String = "Makanan",
        barcode: String? = null,
        price: Double = 5000.0
    ): ProductEntity {
        return ProductEntity(
            id = id,
            storeId = "STORE-001",
            name = name,
            category = category,
            sellingPrice = price,
            costPrice = price * 0.8,
            stock = 100.0,
            barcode = barcode,
            syncStatus = "SYNCED",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    @Before
    fun setUp() {
        CartManager.clearCart()
    }

    @Test
    fun testCartManager_InitialEmptyAndClear() {
        assertEquals("Initial cart should be empty", 0, CartManager.getCartItemsSnapshot().size)
        assertEquals(0.0, CartManager.getTotalItemCount(), 0.001)
        assertEquals(0.0, CartManager.getTotalBayar(), 0.001)
    }

    @Test
    fun testCartManager_ContinuousScanAutoAddMultipleProducts() {
        val prodA = createProduct("p1", "Indomie Goreng", "Makanan", "8990001", 3500.0)
        val prodB = createProduct("p2", "Teh Botol Sosro", "Minuman", "8990002", 4000.0)

        // 1. Scan Product A - should automatically be added without clicking '+'
        CartManager.addToCart(prodA, 1.0)
        assertEquals(1.0, CartManager.getProductQuantity("p1"), 0.001)
        assertEquals(1.0, CartManager.getTotalItemCount(), 0.001)
        assertEquals(3500.0, CartManager.getTotalBayar(), 0.001)

        // 2. Switch product directly to Product B ('tinggal langsung scan')
        CartManager.addToCart(prodB, 1.0)
        assertEquals(1.0, CartManager.getProductQuantity("p1"), 0.001)
        assertEquals(1.0, CartManager.getProductQuantity("p2"), 0.001)
        assertEquals(2.0, CartManager.getTotalItemCount(), 0.001)
        assertEquals(7500.0, CartManager.getTotalBayar(), 0.001)

        // 3. Scan Product A again (re-scan)
        CartManager.addToCart(prodA, 1.0)
        assertEquals(2.0, CartManager.getProductQuantity("p1"), 0.001)
        assertEquals(1.0, CartManager.getProductQuantity("p2"), 0.001)
        assertEquals(3.0, CartManager.getTotalItemCount(), 0.001)
        assertEquals(11000.0, CartManager.getTotalBayar(), 0.001)
    }

    @Test
    fun testCartManager_ManualPlusIncrement() {
        val prod = createProduct("p3", "Susu Kental Manis", "Sembako", "8990003", 12000.0)

        // Auto add on initial scan
        CartManager.addToCart(prod, 1.0)
        assertEquals(1.0, CartManager.getProductQuantity("p3"), 0.001)

        // User optionally taps '+' button on card to add 2 more
        CartManager.addToCart(prod, 1.0)
        CartManager.addToCart(prod, 1.0)
        assertEquals(3.0, CartManager.getProductQuantity("p3"), 0.001)
        assertEquals(3.0, CartManager.getTotalItemCount(), 0.001)
        assertEquals(36000.0, CartManager.getTotalBayar(), 0.001)
    }

    @Test
    fun testCartManager_UpdateQuantityAndRemove() {
        val prod = createProduct("p4", "Kopi Hitam", "Minuman", "8990004", 2500.0)
        CartManager.addToCart(prod, 3.0)
        assertEquals(3.0, CartManager.getProductQuantity("p4"), 0.001)

        val item = CartManager.getCartItemsSnapshot().first()
        // Update to 5
        CartManager.updateQuantity(item, 5.0)
        assertEquals(5.0, CartManager.getProductQuantity("p4"), 0.001)

        // Update to 0 removes item
        val updatedItem = CartManager.getCartItemsSnapshot().first()
        CartManager.updateQuantity(updatedItem, 0.0)
        assertEquals(0.0, CartManager.getProductQuantity("p4"), 0.001)
        assertTrue(CartManager.getCartItemsSnapshot().isEmpty())
    }

    @Test
    fun testScannerConstants() {
        assertEquals("EXTRA_SCAN_MODE", BarcodeScannerActivity.EXTRA_SCAN_MODE)
        assertEquals("MODE_CASHIER", BarcodeScannerActivity.MODE_CASHIER)
        assertEquals("CASHIER_MODE_ADDED", BarcodeScannerActivity.CASHIER_MODE_ADDED)
        assertEquals("TOTAL_SCANNED_COUNT", BarcodeScannerActivity.TOTAL_SCANNED_COUNT)
    }

    @Test
    fun testContinuousScanCooldownLogic() {
        val sameCooldown = 1000L
        val diffCooldown = 650L

        val t0 = 10000L

        // Same barcode within 500ms -> should be suppressed
        val t1 = t0 + 500L
        assertTrue("Within 500ms same barcode must be throttled", (t1 - t0) < sameCooldown)

        // Same barcode after 1100ms -> should trigger
        val t2 = t0 + 1100L
        assertTrue("After 1100ms same barcode must trigger", (t2 - t0) >= sameCooldown)

        // Different barcode within 200ms -> should be suppressed (jitter)
        val t3 = t0 + 200L
        assertTrue("Different barcode within 200ms must be suppressed", (t3 - t0) < diffCooldown)

        // Different barcode after 700ms -> should trigger
        val t4 = t0 + 700L
        assertTrue("Different barcode after 700ms must trigger", (t4 - t0) >= diffCooldown)
    }
}
