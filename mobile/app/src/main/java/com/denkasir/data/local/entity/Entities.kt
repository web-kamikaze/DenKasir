package com.denkasir.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String? = null,
    val phone: String? = null,
    val ownerId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "SYNCED"
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val storeId: String,
    val username: String,
    val fullName: String,
    val role: String = "KASIR", // OWNER or KASIR
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val storeId: String = "default_store",
    val barcode: String? = null,
    val name: String,
    val category: String = "Pcs", // Pcs, Kg, Pulsa, etc.
    val productType: String = "RETAIL", // RETAIL or DIGITAL
    val costPrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val stock: Double = 0.0,
    val minStockAlert: Double = 5.0,
    val imagePath: String? = null,
    val providerCode: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING"
)

/**
 * Aturan Bisnis: Mendeteksi apakah produk digital dalam kondisi STOK KOSONG / GANGGUAN
 */
fun ProductEntity.isDigitalOutOfStock(): Boolean {
    if (this.productType != "DIGITAL") return false
    if (this.stock <= 0.0) return true
    val combined = (this.name + " " + (this.providerCode ?: "")).uppercase()
    return combined.contains("KOSONG") || combined.contains("GANGGUAN") || combined.contains("STOK HABIS") || combined.contains("HABIS")
}

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val storeId: String = "default_store",
    val userId: String = "user_1",
    val cashierName: String? = null,
    val invoiceNumber: String,
    val totalAmount: Double,
    val cashGiven: Double = 0.0,
    val changeAmount: Double = 0.0,
    val paymentMethod: String = "CASH", // CASH, QRIS, KASBON, TRANSFER
    val customerName: String? = null,
    val status: String = "COMPLETED",
    val createdAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING"
)

@Entity(tableName = "transaction_items")
data class TransactionItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val transactionId: String,
    val productId: String,
    val productName: String,
    val quantity: Double,
    val costPrice: Double,
    val sellingPrice: Double,
    val subtotal: Double,
    val targetPhoneNumber: String? = null,
    val snNumber: String? = null,
    val digitalStatus: String = "SUCCESS"
)

@Entity(tableName = "cash_flows")
data class CashFlowEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val storeId: String = "default_store",
    val flowType: String, // IN or OUT
    val category: String, // PENJUALAN, OPERASIONAL, BELANJA_STOK, MODAL_PULSA, KASBON, etc.
    val amount: Double,
    val description: String,
    val referenceId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING"
)

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val storeId: String = "default_store",
    val customerName: String,
    val customerPhone: String? = null,
    val totalDebt: Double,
    val paidAmount: Double = 0.0,
    val status: String = "UNPAID", // UNPAID, PARTIAL, PAID
    val debtType: String = "PIUTANG", // PIUTANG (Customer Kasbon) or HUTANG (Supplier)
    val dueDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING"
)

@Entity(tableName = "bku_accounts")
data class BkuAccountEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val storeId: String = "default_store",
    val accountName: String,
    val accountType: String, // "IN" (Pemasukan) or "OUT" (Pengeluaran)
    val accountCode: String? = null,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "stock_opnames")
data class StockOpnameEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val storeId: String = "default_store",
    val productId: String,
    val systemStock: Double,
    val physicalStock: Double,
    val difference: Double,
    val reason: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING"
)

@Entity(tableName = "digital_configs")
data class DigitalConfigEntity(
    @PrimaryKey
    val id: String = "default_config",
    val providerName: String = "OtomaX H2H",
    val apiUrl: String = "https://st.riungan.my.id/api/isipulsa/h2h-order",
    val apiKey: String = "DENKASIR-H2H-LIVE",
    val memberId: String = "",
    val pinPassword: String = "",
    val balance: Double = 0.0,
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
    val trxMode: String = "API_H2H", // "API_H2H", "SMS_CENTER", "AUTO_FALLBACK"
    val smsCenterNumbers: String = "", // Comma-separated numbers e.g. "081234567890,085612345678"
    val smsTemplate: String = "[KODE].[TUJUAN].[PIN]",
    val smsDelimiter: String = ".",
    val distributorRulesJson: String = "", // JSON array of DistributorRule
    val preferredSimSlot: Int = -1, // -1 = Otomatis/Default, 0 = SIM 1, 1 = SIM 2
    val autoSendSms: Boolean = true // Langsung kirim via SmsManager di latar belakang
)

@Entity(tableName = "purchase_orders")
data class PurchaseOrderEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val storeId: String = "default_store",
    val supplierName: String,
    val dateText: String,
    val itemCount: Int,
    val totalAmount: Double,
    val status: String = "Draft", // "Draft", "Dipesan", "Selesai"
    val createdAt: Long = System.currentTimeMillis()
)