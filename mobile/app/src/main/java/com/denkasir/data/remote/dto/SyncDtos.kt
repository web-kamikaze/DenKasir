package com.denkasir.data.remote.dto

import com.google.gson.annotations.SerializedName

// ==========================================
// DTO AUTH
// ==========================================
data class LoginRequestDto(
    val username: String,
    val password: String
)

data class LoginResponseDto(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("user_id") val userId: String,
    val username: String,
    @SerializedName("full_name") val fullName: String,
    val role: String,
    @SerializedName("store_id") val storeId: String,
    @SerializedName("store_name") val storeName: String?
)

// ==========================================
// DTO ENTITAS SYNC
// ==========================================
data class ProductSyncDto(
    val id: String,
    @SerializedName("store_id") val storeId: String = "default_store",
    val barcode: String?,
    val name: String,
    val category: String,
    @SerializedName("product_type") val productType: String,
    @SerializedName("cost_price") val costPrice: Double,
    @SerializedName("selling_price") val sellingPrice: Double,
    val stock: Double,
    @SerializedName("min_stock_alert") val minStockAlert: Double,
    @SerializedName("image_path") val imagePath: String?,
    @SerializedName("provider_code") val providerCode: String?
)

data class TransactionItemSyncDto(
    val id: String,
    @SerializedName("product_id") val productId: String,
    @SerializedName("product_name") val productName: String,
    val quantity: Double,
    @SerializedName("cost_price") val costPrice: Double,
    @SerializedName("selling_price") val sellingPrice: Double,
    val subtotal: Double,
    @SerializedName("target_phone_number") val targetPhoneNumber: String?,
    @SerializedName("sn_number") val snNumber: String?,
    @SerializedName("digital_status") val digitalStatus: String
)

data class TransactionSyncDto(
    val id: String,
    @SerializedName("store_id") val storeId: String = "default_store",
    @SerializedName("user_id") val userId: String = "user_1",
    @SerializedName("invoice_number") val invoiceNumber: String,
    @SerializedName("total_amount") val totalAmount: Double,
    @SerializedName("cash_given") val cashGiven: Double,
    @SerializedName("change_amount") val changeAmount: Double,
    @SerializedName("payment_method") val paymentMethod: String,
    @SerializedName("customer_name") val customerName: String?,
    val status: String,
    val items: List<TransactionItemSyncDto>
)

data class CashFlowSyncDto(
    val id: String,
    @SerializedName("store_id") val storeId: String = "default_store",
    @SerializedName("flow_type") val flowType: String,
    val category: String,
    val amount: Double,
    val description: String,
    @SerializedName("reference_id") val referenceId: String?
)

data class DebtSyncDto(
    val id: String,
    @SerializedName("store_id") val storeId: String = "default_store",
    @SerializedName("customer_name") val customerName: String,
    @SerializedName("customer_phone") val customerPhone: String?,
    @SerializedName("total_debt") val totalDebt: Double,
    @SerializedName("paid_amount") val paidAmount: Double,
    val status: String,
    @SerializedName("debt_type") val debtType: String,
    @SerializedName("due_date") val dueDate: Long?
)

data class StockOpnameSyncDto(
    val id: String,
    @SerializedName("store_id") val storeId: String = "default_store",
    @SerializedName("product_id") val productId: String,
    @SerializedName("system_stock") val systemStock: Double,
    @SerializedName("physical_stock") val physicalStock: Double,
    val difference: Double,
    val reason: String?
)

data class PurchaseOrderSyncDto(
    val id: String,
    @SerializedName("store_id") val storeId: String = "default_store",
    @SerializedName("supplier_name") val supplierName: String,
    @SerializedName("date_text") val dateText: String,
    @SerializedName("item_count") val itemCount: Int,
    @SerializedName("total_amount") val totalAmount: Double,
    val status: String
)

// ==========================================
// DTO PUSH & PULL PAYLOADS
// ==========================================
data class SyncPushRequestDto(
    @SerializedName("store_id") val storeId: String,
    @SerializedName("client_timestamp") val clientTimestamp: Long,
    val products: List<ProductSyncDto> = emptyList(),
    val transactions: List<TransactionSyncDto> = emptyList(),
    @SerializedName("cash_flows") val cashFlows: List<CashFlowSyncDto> = emptyList(),
    val debts: List<DebtSyncDto> = emptyList(),
    @SerializedName("stock_opnames") val stockOpnames: List<StockOpnameSyncDto> = emptyList(),
    @SerializedName("purchase_orders") val purchaseOrders: List<PurchaseOrderSyncDto> = emptyList()
)

data class SyncPushResponseDto(
    val status: String,
    @SerializedName("server_timestamp") val serverTimestamp: Long,
    @SerializedName("synced_counts") val syncedCounts: Map<String, Int>,
    val errors: List<String> = emptyList()
)

data class SyncPullRequestDto(
    @SerializedName("store_id") val storeId: String,
    @SerializedName("last_synced_at") val lastSyncedAt: Long
)

data class SyncPullResponseDto(
    val status: String,
    @SerializedName("server_timestamp") val serverTimestamp: Long,
    val products: List<ProductSyncDto> = emptyList(),
    val transactions: List<TransactionSyncDto> = emptyList(),
    @SerializedName("cash_flows") val cashFlows: List<CashFlowSyncDto> = emptyList(),
    val debts: List<DebtSyncDto> = emptyList(),
    @SerializedName("stock_opnames") val stockOpnames: List<StockOpnameSyncDto> = emptyList(),
    @SerializedName("purchase_orders") val purchaseOrders: List<PurchaseOrderSyncDto> = emptyList()
)


// ==========================================
// DTO KATALOG PRODUK DIGITAL API (st.riungan.my.id)
// ==========================================
data class DigitalCatalogApiItemDto(
    val id: String,
    @SerializedName("voucher_id") val voucherId: Int = 0,
    @SerializedName("provider_code") val providerCode: String? = null,
    val name: String,
    @SerializedName("nominal_label") val nominalLabel: String? = null,
    @SerializedName("operator_name") val operatorName: String? = null,
    val category: String = "Pulsa",
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("product_type") val productType: String = "DIGITAL",
    @SerializedName("cost_price") val costPrice: Double = 0.0,
    @SerializedName("selling_price") val sellingPrice: Double = 0.0,
    @SerializedName("recommended_selling_price") val recommendedSellingPrice: Double = 0.0,
    val stock: Double = 999.0,
    @SerializedName("is_gangguan") val isGangguan: Boolean = false,
    @SerializedName("status_label") val statusLabel: String? = null,
    val description: String? = null
)

data class DigitalCatalogApiResponseDto(
    val status: String,
    @SerializedName("up_to_date") val upToDate: Boolean = false,
    @SerializedName("last_updated_at") val lastUpdatedAt: String? = null,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("is_expired") val isExpired: Boolean = false,
    @SerializedName("total_items") val totalItems: Int = 0,
    @SerializedName("total_normal") val totalNormal: Int = 0,
    @SerializedName("total_gangguan") val totalGangguan: Int = 0,
    val items: List<DigitalCatalogApiItemDto> = emptyList()
)

data class DigitalCatalogSyncSummary(
    val upToDate: Boolean,
    val totalItems: Int,
    val normalCount: Int,
    val gangguanCount: Int,
    val lastUpdatedAt: String?,
    val expiresAt: String?
)

// ==========================================
// DTO TRANSAKSI H2H GATEWAY
// ==========================================
data class H2hOrderRequestDto(
    @SerializedName("product_code") val productCode: String,
    @SerializedName("target_number") val targetNumber: String,
    @SerializedName("id_plgn") val idPlgn: String? = null,
    @SerializedName("client_trx_id") val clientTrxId: String? = null,
    @SerializedName("pin") val pin: String? = null,
    @SerializedName("sms_center") val smsCenter: String? = null,
    @SerializedName("api_key") val apiKey: String? = null
)

data class H2hOrderResponseDto(
    val status: String,
    val message: String,
    @SerializedName("trx_id") val trxId: String? = null,
    @SerializedName("product_code") val productCode: String? = null,
    @SerializedName("voucher_id") val voucherId: Int = 0,
    @SerializedName("product_name") val productName: String? = null,
    @SerializedName("target_number") val targetNumber: String? = null,
    @SerializedName("sn_number") val snNumber: String? = null,
    @SerializedName("sms_command") val smsCommand: String? = null,
    @SerializedName("sms_center") val smsCenter: String? = null,
    val price: Double = 0.0,
    val channel: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)
