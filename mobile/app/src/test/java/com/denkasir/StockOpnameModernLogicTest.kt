package com.denkasir

import com.denkasir.ui.stockopname.OpnameItem
import com.denkasir.utils.SessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockOpnameModernLogicTest {

    @Test
    fun testInitialStateIsUncounted() {
        val item = OpnameItem(
            id = "prod_1",
            name = "Minyak Goreng 1L",
            category = "Sembako",
            costPrice = 14000.0,
            systemStock = 20,
            physicalStock = null // Belum dihitung
        )

        assertFalse("Item baru harus berstatus belum dihitung", item.isCounted)
        assertFalse("Item yang belum dihitung tidak boleh otomatis cocok", item.isMatched)
        assertFalse("Item yang belum dihitung tidak boleh dianggap selisih", item.hasDifference)
        assertEquals("Safe physical stock fallback ke system stock jika null", 20, item.safePhysicalStock)
        assertEquals("Loss value item belum dihitung adalah 0.0", 0.0, item.lossValue, 0.001)
    }

    @Test
    fun testCountedMatchedState() {
        val item = OpnameItem(
            id = "prod_2",
            name = "Gula Pasir 1kg",
            category = "Sembako",
            costPrice = 12000.0,
            systemStock = 15,
            physicalStock = 15 // Cocok
        )

        assertTrue("Item harus berstatus terhitung", item.isCounted)
        assertTrue("Item harus berstatus cocok", item.isMatched)
        assertFalse("Item cocok tidak memiliki selisih", item.hasDifference)
        assertEquals(0, item.difference)
        assertEquals(0.0, item.lossValue, 0.001)
    }

    @Test
    fun testCountedDiscrepancyLossCalculation() {
        val itemLoss = OpnameItem(
            id = "prod_3",
            name = "Rokok Marlboro",
            category = "Rokok",
            costPrice = 35000.0,
            systemStock = 10,
            physicalStock = 8 // Kurang 2 bungkus (hilang/rusak)
        )

        assertTrue(itemLoss.isCounted)
        assertFalse(itemLoss.isMatched)
        assertTrue(itemLoss.hasDifference)
        assertEquals(-2, itemLoss.difference)
        // 2 bungkus * 35.000 = 70.000 kerugian persediaan
        assertEquals(70000.0, itemLoss.lossValue, 0.001)

        val itemGain = OpnameItem(
            id = "prod_4",
            name = "Korek Api Gas",
            category = "Lainnya",
            costPrice = 2000.0,
            systemStock = 50,
            physicalStock = 53 // Lebih 3 unit (bonus supplier)
        )

        assertTrue(itemGain.isCounted)
        assertFalse(itemGain.isMatched)
        assertTrue(itemGain.hasDifference)
        assertEquals(3, itemGain.difference)
        // Selisih lebih tidak menimbulkan kerugian (loss = 0)
        assertEquals(0.0, itemGain.lossValue, 0.001)
    }

    @Test
    fun testTotalLossCalculationAcrossItems() {
        val items = listOf(
            OpnameItem(id = "1", name = "Beras 5kg", costPrice = 60000.0, systemStock = 10, physicalStock = 9), // -1 (loss 60.000)
            OpnameItem(id = "2", name = "Minyak 2L", costPrice = 28000.0, systemStock = 5, physicalStock = 5),   // cocok (0)
            OpnameItem(id = "3", name = "Telur 1kg", costPrice = 26000.0, systemStock = 8, physicalStock = 6),  // -2 (loss 52.000)
            OpnameItem(id = "4", name = "Sabun Mandi", costPrice = 3000.0, systemStock = 20, physicalStock = 22), // +2 (loss 0)
            OpnameItem(id = "5", name = "Kecap Manis", costPrice = 10000.0, systemStock = 12, physicalStock = null) // uncounted (loss 0)
        )

        val totalLoss = items.filter { it.hasDifference }.sumOf { it.lossValue }
        // 60.000 + 52.000 = 112.000
        assertEquals(112000.0, totalLoss, 0.001)
    }

    @Test
    fun testRolePermissionsOnStockOpname() {
        fun canModifyStockAlertRules(role: String): Boolean {
            return role.equals(SessionManager.ROLE_OWNER, ignoreCase = true)
        }

        fun isBlindCountRequired(role: String): Boolean {
            return role.equals(SessionManager.ROLE_KASIR, ignoreCase = true)
        }

        fun canDirectlyApplyReconciliation(role: String): Boolean {
            return role.equals(SessionManager.ROLE_OWNER, ignoreCase = true)
        }

        // Test OWNER permissions
        assertTrue(canModifyStockAlertRules("OWNER"))
        assertFalse(isBlindCountRequired("OWNER"))
        assertTrue(canDirectlyApplyReconciliation("OWNER"))

        // Test KASIR permissions
        assertFalse(canModifyStockAlertRules("KASIR"))
        assertTrue(isBlindCountRequired("KASIR")) // Kasir wajib Blind Count
        assertFalse(canDirectlyApplyReconciliation("KASIR")) // Kasir harus mengajukan draft ke Owner
    }

    @Test
    fun testCycleCountAndStatusFiltering() {
        val catalog = listOf(
            OpnameItem(id = "1", name = "Beras Rojolele", category = "Sembako", systemStock = 10, physicalStock = 10),
            OpnameItem(id = "2", name = "Minyak SunCo", category = "Sembako", systemStock = 15, physicalStock = null),
            OpnameItem(id = "3", name = "Coca Cola 390ml", category = "Minuman", systemStock = 24, physicalStock = 22),
            OpnameItem(id = "4", name = "Teh Pucuk 350ml", category = "Minuman", systemStock = 30, physicalStock = null)
        )

        // 1. Filter Kategori "Sembako"
        val sembakoItems = catalog.filter { it.category == "Sembako" }
        assertEquals(2, sembakoItems.size)

        // 2. Filter Status "UNCOUNTED" di kategori "Sembako"
        val sembakoUncounted = sembakoItems.filter { !it.isCounted }
        assertEquals(1, sembakoUncounted.size)
        assertEquals("Minyak SunCo", sembakoUncounted.first().name)

        // 3. Filter Status "SELISIH" di semua kategori
        val allDiscrepancies = catalog.filter { it.hasDifference }
        assertEquals(1, allDiscrepancies.size)
        assertEquals("Coca Cola 390ml", allDiscrepancies.first().name)
    }
}
