package com.denkasir.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "denkasir_session_pref"
        private const val KEY_USER_ID = "key_user_id"
        private const val KEY_USERNAME = "key_username"
        private const val KEY_FULL_NAME = "key_full_name"
        private const val KEY_ROLE = "key_role"
        private const val KEY_STORE_ID = "key_store_id"
        private const val KEY_STORE_NAME = "key_store_name"
        private const val KEY_STORE_ADDRESS = "key_store_address"
        private const val KEY_STORE_PHONE = "key_store_phone"

        // Stock Opname & Auto-Draft Rule Keys
        private const val KEY_MIN_STOCK_ALERT = "key_min_stock_alert"
        private const val KEY_AUTO_DRAFT_ENABLED = "key_auto_draft_enabled"
        private const val KEY_DEFAULT_SUPPLIER = "key_default_supplier"
        private const val KEY_AUTO_REORDER_QTY = "key_auto_reorder_qty"

        const val ROLE_OWNER = "OWNER"
        const val ROLE_KASIR = "KASIR"

        @Volatile
        private var INSTANCE: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SessionManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    fun login(userId: String, username: String, fullName: String, role: String) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .putString(KEY_FULL_NAME, fullName)
            .putString(KEY_ROLE, role)
            .apply()
    }

    fun switchRole(role: String) {
        val currentName = if (role == ROLE_OWNER) "Pemilik Toko (Owner)" else "Kasir 1 (Staff)"
        val currentUsername = if (role == ROLE_OWNER) "owner" else "kasir"
        val currentUserId = if (role == ROLE_OWNER) "owner_1" else "kasir_1"
        login(currentUserId, currentUsername, currentName, role)
    }

    fun getCurrentRole(): String {
        return prefs.getString(KEY_ROLE, ROLE_OWNER) ?: ROLE_OWNER
    }

    fun isOwner(): Boolean {
        return getCurrentRole().equals(ROLE_OWNER, ignoreCase = true)
    }

    fun isKasir(): Boolean {
        return getCurrentRole().equals(ROLE_KASIR, ignoreCase = true)
    }

    fun getCurrentUserId(): String {
        return prefs.getString(KEY_USER_ID, if (isOwner()) "owner_1" else "kasir_1") ?: "owner_1"
    }

    fun getCurrentUsername(): String {
        return prefs.getString(KEY_USERNAME, if (isOwner()) "owner" else "kasir") ?: "owner"
    }

    fun getCurrentFullName(): String {
        return prefs.getString(KEY_FULL_NAME, if (isOwner()) "Pemilik Toko (Owner)" else "Kasir 1 (Staff)") ?: "Pemilik Toko (Owner)"
    }

    fun saveStoreInfo(name: String, address: String?, phone: String?) {
        prefs.edit()
            .putString(KEY_STORE_NAME, name)
            .putString(KEY_STORE_ADDRESS, address ?: "")
            .putString(KEY_STORE_PHONE, phone ?: "")
            .apply()
    }

    fun getStoreName(): String {
        return prefs.getString(KEY_STORE_NAME, "DENKASIR UMKM") ?: "DENKASIR UMKM"
    }

    fun getStoreAddress(): String {
        return prefs.getString(KEY_STORE_ADDRESS, "Jl. Pemuda No. 88, Semarang") ?: "Jl. Pemuda No. 88, Semarang"
    }

    fun getStorePhone(): String {
        return prefs.getString(KEY_STORE_PHONE, "081234567890") ?: "081234567890"
    }

    // ==========================================
    // ATURAN OWNER: STOK OPNAME & NOTIFIKASI DRAFT
    // ==========================================
    fun saveStockAlertRule(threshold: Double, autoDraft: Boolean, supplier: String, reorderQty: Double) {
        prefs.edit()
            .putFloat(KEY_MIN_STOCK_ALERT, threshold.toFloat())
            .putBoolean(KEY_AUTO_DRAFT_ENABLED, autoDraft)
            .putString(KEY_DEFAULT_SUPPLIER, supplier)
            .putFloat(KEY_AUTO_REORDER_QTY, reorderQty.toFloat())
            .apply()
    }

    fun getMinStockThreshold(): Double {
        return prefs.getFloat(KEY_MIN_STOCK_ALERT, 5.0f).toDouble()
    }

    fun isAutoDraftEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_DRAFT_ENABLED, true)
    }

    fun getDefaultSupplier(): String {
        return prefs.getString(KEY_DEFAULT_SUPPLIER, "CV. Sumber Makmur") ?: "CV. Sumber Makmur"
    }

    fun getAutoReorderQty(): Double {
        return prefs.getFloat(KEY_AUTO_REORDER_QTY, 20.0f).toDouble()
    }
}
