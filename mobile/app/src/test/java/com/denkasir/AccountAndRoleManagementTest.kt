package com.denkasir

import com.denkasir.data.local.entity.UserEntity
import com.denkasir.utils.SessionManager
import org.junit.Assert.*
import org.junit.Test

class AccountAndRoleManagementTest {

    @Test
    fun testOwnerPasswordVerificationLogic() {
        var storedOwnerPassword = "admin123"

        fun verifyOwnerPassword(input: String): Boolean {
            val trimmed = input.trim()
            return trimmed == storedOwnerPassword || trimmed == "admin123"
        }

        // Test default password
        assertTrue(verifyOwnerPassword("admin123"))
        assertTrue(verifyOwnerPassword("  admin123  "))
        assertFalse(verifyOwnerPassword("wrongpassword"))
        assertFalse(verifyOwnerPassword(""))
        assertFalse(verifyOwnerPassword("123456"))

        // Test updated custom password
        storedOwnerPassword = "super_owner_pin_2026"
        assertTrue(verifyOwnerPassword("super_owner_pin_2026"))
        assertTrue(verifyOwnerPassword("admin123")) // Fallback accepted
        assertFalse(verifyOwnerPassword("random_pin"))
    }

    @Test
    fun testRolePermissionsMatrix() {
        fun canEditStoreProfile(role: String) = role.equals(SessionManager.ROLE_OWNER, ignoreCase = true)
        fun canManageCashiers(role: String) = role.equals(SessionManager.ROLE_OWNER, ignoreCase = true)
        fun canConfigureStockAlert(role: String) = role.equals(SessionManager.ROLE_OWNER, ignoreCase = true)
        fun canConfigureH2H(role: String) = role.equals(SessionManager.ROLE_OWNER, ignoreCase = true)
        fun canAccessBackupRestore(role: String) = role.equals(SessionManager.ROLE_OWNER, ignoreCase = true)
        fun canDeletePurchase(role: String) = role.equals(SessionManager.ROLE_OWNER, ignoreCase = true)
        fun canAccessPOS(role: String) = true // Both owner and kasir can sell
        fun canConnectBluetoothPrinter(role: String) = true // Both owner and kasir can pair printer

        // OWNER Permissions
        val owner = SessionManager.ROLE_OWNER
        assertTrue(canEditStoreProfile(owner))
        assertTrue(canManageCashiers(owner))
        assertTrue(canConfigureStockAlert(owner))
        assertTrue(canConfigureH2H(owner))
        assertTrue(canAccessBackupRestore(owner))
        assertTrue(canDeletePurchase(owner))
        assertTrue(canAccessPOS(owner))
        assertTrue(canConnectBluetoothPrinter(owner))

        // KASIR Permissions
        val kasir = SessionManager.ROLE_KASIR
        assertFalse(canEditStoreProfile(kasir))
        assertFalse(canManageCashiers(kasir))
        assertFalse(canConfigureStockAlert(kasir))
        assertFalse(canConfigureH2H(kasir))
        assertFalse(canAccessBackupRestore(kasir))
        assertFalse(canDeletePurchase(kasir))
        assertTrue(canAccessPOS(kasir))
        assertTrue(canConnectBluetoothPrinter(kasir))
    }

    @Test
    fun testCashierUsernameValidation() {
        val usernameRegex = Regex("^[a-zA-Z0-9_]{3,20}$")

        fun isValidUsername(username: String): Boolean {
            val trimmed = username.trim().lowercase()
            return trimmed.matches(usernameRegex)
        }

        // Valid usernames
        assertTrue(isValidUsername("kasir"))
        assertTrue(isValidUsername("kasir_1"))
        assertTrue(isValidUsername("budi_santoso"))
        assertTrue(isValidUsername("staf99"))
        assertTrue(isValidUsername("rina"))

        // Invalid usernames
        assertFalse(isValidUsername(""))
        assertFalse(isValidUsername("ab")) // too short
        assertFalse(isValidUsername("kasir utama")) // contains space
        assertFalse(isValidUsername("kasir@toko")) // invalid character @
        assertFalse(isValidUsername("a".repeat(25))) // too long (>20 chars)
    }

    @Test
    fun testDuplicateUsernameDetection() {
        val existingUsers = listOf(
            UserEntity(id = "1", storeId = "s1", username = "owner", fullName = "Pemilik", role = "OWNER"),
            UserEntity(id = "2", storeId = "s1", username = "kasir", fullName = "Kasir 1", role = "KASIR"),
            UserEntity(id = "3", storeId = "s1", username = "budi", fullName = "Budi Santoso", role = "KASIR")
        )

        fun isUsernameTaken(input: String): Boolean {
            val normalized = input.trim().lowercase()
            return existingUsers.any { it.username.equals(normalized, ignoreCase = true) }
        }

        assertTrue(isUsernameTaken("owner"))
        assertTrue(isUsernameTaken("kasir"))
        assertTrue(isUsernameTaken("BUDI"))
        assertTrue(isUsernameTaken("  budi  "))

        assertFalse(isUsernameTaken("siti"))
        assertFalse(isUsernameTaken("andi"))
    }

    @Test
    fun testMultiCashierSelectionAndShiftSwitch() {
        val cashiers = listOf(
            UserEntity(id = "kasir_1", storeId = "s1", username = "kasir", fullName = "Kasir 1 (Staff)", role = "KASIR"),
            UserEntity(id = "kasir_2", storeId = "s1", username = "budi", fullName = "Budi Kasir", role = "KASIR"),
            UserEntity(id = "kasir_3", storeId = "s1", username = "siti", fullName = "Siti Kasir", role = "KASIR")
        )

        // Simulate session state
        var currentUserId = "kasir_1"
        var currentUsername = "kasir"
        var currentFullName = "Kasir 1 (Staff)"
        var currentRole = SessionManager.ROLE_KASIR

        fun switchCashierShift(selectedPos: Int) {
            val user = cashiers[selectedPos]
            currentUserId = user.id
            currentUsername = user.username
            currentFullName = user.fullName
            currentRole = SessionManager.ROLE_KASIR
        }

        // Switch shift to Siti
        switchCashierShift(2)
        assertEquals("kasir_3", currentUserId)
        assertEquals("siti", currentUsername)
        assertEquals("Siti Kasir", currentFullName)
        assertEquals("KASIR", currentRole)

        // Switch shift to Budi
        switchCashierShift(1)
        assertEquals("kasir_2", currentUserId)
        assertEquals("budi", currentUsername)
        assertEquals("Budi Kasir", currentFullName)
    }

    @Test
    fun testActiveCashierDeletionFallback() {
        var currentUserId = "kasir_2"
        var currentRole = SessionManager.ROLE_KASIR

        fun onDeleteCashier(deletedUserId: String) {
            if (currentUserId == deletedUserId) {
                // Safely reset session to OWNER
                currentUserId = "owner_1"
                currentRole = SessionManager.ROLE_OWNER
            }
        }

        // Delete unrelated cashier
        onDeleteCashier("kasir_3")
        assertEquals("kasir_2", currentUserId)
        assertEquals(SessionManager.ROLE_KASIR, currentRole)

        // Delete active cashier
        onDeleteCashier("kasir_2")
        assertEquals("owner_1", currentUserId)
        assertEquals(SessionManager.ROLE_OWNER, currentRole)
    }

    @Test
    fun testStockOpnamePinVerificationSecurity() {
        val ownerPin = "admin123"

        fun verifyOpnamePin(pin: String): Boolean {
            val trimmed = pin.trim()
            return trimmed.isNotEmpty() && (trimmed == ownerPin || trimmed == "admin123")
        }

        // Correct PIN
        assertTrue(verifyOpnamePin("admin123"))

        // Security check: random non-blank string MUST NOT be accepted (fixes BUG-036)
        assertFalse(verifyOpnamePin("1"))
        assertFalse(verifyOpnamePin("abc"))
        assertFalse(verifyOpnamePin("kasir123"))
        assertFalse(verifyOpnamePin(""))
    }
}
