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

        // Server URL & Cloud Sync Keys
        private const val KEY_SERVER_URL = "key_server_url"
        private const val KEY_AUTH_TOKEN = "key_auth_token"
        private const val KEY_LAST_SYNC_TIME = "key_last_sync_time"

        // Stock Opname & Auto-Draft Rule Keys
        private const val KEY_MIN_STOCK_ALERT = "key_min_stock_alert"
        private const val KEY_AUTO_DRAFT_ENABLED = "key_auto_draft_enabled"
        private const val KEY_DEFAULT_SUPPLIER = "key_default_supplier"
        private const val KEY_AUTO_REORDER_QTY = "key_auto_reorder_qty"

        // Printer Receipt Layout & Paper Settings (inspired by mate.bluetoothprint)
        private const val KEY_RECEIPT_PAPER_WIDTH = "key_receipt_paper_width"
        private const val KEY_RECEIPT_FEED_LINES = "key_receipt_feed_lines"
        private const val KEY_RECEIPT_AUTO_CUT = "key_receipt_auto_cut"
        private const val KEY_RECEIPT_SHOW_QR = "key_receipt_show_qr"
        private const val KEY_RECEIPT_LAYOUT_STYLE = "key_receipt_layout_style"
        private const val KEY_RECEIPT_HEADER_SLOGAN = "key_receipt_header_slogan"
        private const val KEY_RECEIPT_FOOTER_NOTE = "key_receipt_footer_note"
        private const val KEY_CUSTOM_IMAGES_DIR = "key_custom_images_dir"

        const val ROLE_OWNER = "OWNER"
        const val ROLE_KASIR = "KASIR"

        const val LAYOUT_2_COLUMN = "2_COLUMN"
        const val LAYOUT_3_COLUMN = "3_COLUMN"

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
    // SERVER CLOUD & LOKAL SYNC CONFIG
    // ==========================================
    fun saveServerUrl(url: String) {
        val cleanUrl = if (url.endsWith("/")) url else "$url/"
        prefs.edit().putString(KEY_SERVER_URL, cleanUrl).apply()
    }

    fun getServerUrl(): String {
        return prefs.getString(KEY_SERVER_URL, "http://10.0.2.2:8000/") ?: "http://10.0.2.2:8000/"
    }

    fun saveAuthToken(token: String?) {
        prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
    }

    fun getAuthToken(): String? {
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }

    fun saveLastSyncTime(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, timestamp).apply()
    }

    fun getLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
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

    // ==========================================
    // PENGATURAN STRUK & TATA LETAK PRINTER (LAYOUT & PAPER SETTINGS)
    // ==========================================
    fun saveReceiptSettings(
        paperWidth: Int,
        feedLines: Int,
        autoCut: Boolean,
        showQr: Boolean,
        layoutStyle: String,
        headerSlogan: String,
        footerNote: String
    ) {
        prefs.edit()
            .putInt(KEY_RECEIPT_PAPER_WIDTH, paperWidth)
            .putInt(KEY_RECEIPT_FEED_LINES, feedLines)
            .putBoolean(KEY_RECEIPT_AUTO_CUT, autoCut)
            .putBoolean(KEY_RECEIPT_SHOW_QR, showQr)
            .putString(KEY_RECEIPT_LAYOUT_STYLE, layoutStyle)
            .putString(KEY_RECEIPT_HEADER_SLOGAN, headerSlogan)
            .putString(KEY_RECEIPT_FOOTER_NOTE, footerNote)
            .apply()
    }

    fun getReceiptPaperWidth(): Int {
        return prefs.getInt(KEY_RECEIPT_PAPER_WIDTH, 58)
    }

    fun getReceiptFeedLines(): Int {
        return prefs.getInt(KEY_RECEIPT_FEED_LINES, 3)
    }

    fun isReceiptAutoCut(): Boolean {
        return prefs.getBoolean(KEY_RECEIPT_AUTO_CUT, true)
    }

    fun isReceiptShowQr(): Boolean {
        return prefs.getBoolean(KEY_RECEIPT_SHOW_QR, true)
    }

    fun getReceiptLayoutStyle(): String {
        return prefs.getString(KEY_RECEIPT_LAYOUT_STYLE, LAYOUT_2_COLUMN) ?: LAYOUT_2_COLUMN
    }

    fun getReceiptHeaderSlogan(): String {
        return prefs.getString(KEY_RECEIPT_HEADER_SLOGAN, "Sistem Kasir & Pembukuan UMKM") ?: "Sistem Kasir & Pembukuan UMKM"
    }

    fun getReceiptFooterNote(): String {
        return prefs.getString(
            KEY_RECEIPT_FOOTER_NOTE,
            "Terima Kasih Atas Kunjungan Anda\nBarang dibeli tidak dapat ditukar"
        ) ?: "Terima Kasih Atas Kunjungan Anda\nBarang dibeli tidak dapat ditukar"
    }

    // ==========================================
    // FOLDER FOTO PRODUK OFFLINE KUSTOM
    // ==========================================
    fun saveCustomImagesDir(path: String?) {
        prefs.edit().putString(KEY_CUSTOM_IMAGES_DIR, path?.trim()).apply()
    }

    fun getCustomImagesDir(): String? {
        return prefs.getString(KEY_CUSTOM_IMAGES_DIR, null)?.takeIf { it.isNotBlank() }
    }

    // ==========================================
    // PREFERENSI KAMERA SCANNER (DEPAN / BELAKANG)
    // ==========================================
    fun savePreferredCameraLens(lensFacing: Int) {
        prefs.edit().putInt("key_camera_lens_facing", lensFacing).apply()
    }

    fun getPreferredCameraLens(): Int {
        // 1 = LENS_FACING_BACK, 0 = LENS_FACING_FRONT
        return prefs.getInt("key_camera_lens_facing", 1)
    }
}