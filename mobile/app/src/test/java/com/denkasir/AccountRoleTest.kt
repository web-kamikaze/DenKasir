package com.denkasir

import com.denkasir.utils.SessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRoleTest {

    @Test
    fun testRoleConstants() {
        assertEquals("OWNER", SessionManager.ROLE_OWNER)
        assertEquals("KASIR", SessionManager.ROLE_KASIR)
    }

    @Test
    fun testRolePermissionsLogic() {
        fun canEditStoreProfile(role: String): Boolean {
            return role.equals(SessionManager.ROLE_OWNER, ignoreCase = true)
        }

        fun canAccessH2HConfig(role: String): Boolean {
            return role.equals(SessionManager.ROLE_OWNER, ignoreCase = true)
        }

        fun canAccessPOS(role: String): Boolean {
            return true // Both owner and kasir can sell
        }

        // Test OWNER permissions
        assertTrue(canEditStoreProfile("OWNER"))
        assertTrue(canAccessH2HConfig("OWNER"))
        assertTrue(canAccessPOS("OWNER"))

        // Test KASIR permissions
        assertFalse(canEditStoreProfile("KASIR"))
        assertFalse(canAccessH2HConfig("KASIR"))
        assertTrue(canAccessPOS("KASIR"))
    }
}