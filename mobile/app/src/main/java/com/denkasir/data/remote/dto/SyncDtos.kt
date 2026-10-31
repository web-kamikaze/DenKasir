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
