package com.denkasir.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.denkasir.data.local.dao.*
import com.denkasir.data.local.entity.*
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Database(
    entities = [
        StoreEntity::class,
        UserEntity::class,
        ProductEntity::class,
        TransactionEntity::class,
        TransactionItemEntity::class,
        CashFlowEntity::class,
        DebtEntity::class,
        BkuAccountEntity::class,
        PurchaseOrderEntity::class,
        StockOpnameEntity::class,
        DigitalConfigEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao
    abstract fun userDao(): UserDao
    abstract fun productDao(): ProductDao
    abstract fun transactionDao(): TransactionDao
    abstract fun cashFlowDao(): CashFlowDao
    abstract fun debtDao(): DebtDao
    abstract fun bkuAccountDao(): BkuAccountDao
    abstract fun purchaseOrderDao(): PurchaseOrderDao
    abstract fun stockOpnameDao(): StockOpnameDao
    abstract fun digitalConfigDao(): DigitalConfigDao

    suspend fun ensureSeeded(context: Context) = withContext(Dispatchers.IO) {
        try {
            // 1. Initial Store Profile (Toko Bersih)
            if (storeDao().getStoreCount() == 0) {
                storeDao().insertStore(
                    StoreEntity(
                        id = "default_store",
                        name = "Toko DenKasir",
                        address = "",
                        phone = "",
                        ownerId = "owner_1"
                    )
                )
            }

            // 2. Initial Users (Akun Owner & Kasir Siap Pakai)
            if (userDao().getUserCount() == 0) {
                userDao().insertUsers(
                    listOf(
                        UserEntity(
                            id = "owner_1",
                            storeId = "default_store",
                            username = "owner",
                            fullName = "Pemilik Toko",
                            role = "OWNER"
                        ),
                        UserEntity(
                            id = "kasir_1",
                            storeId = "default_store",
                            username = "kasir",
                            fullName = "Kasir 1",
                            role = "KASIR"
                        )
                    )
                )
            }

            // 3. Initial Digital Config (Saldo Awal 0.0)
            if (digitalConfigDao().getConfigCount() == 0) {
                digitalConfigDao().saveConfig(
                    DigitalConfigEntity(
                        id = "default_config",
                        providerName = "OtomaX H2H Server",
                        balance = 0.0,
                        isActive = true
                    )
                )
            }

            // 4. Initial BKU Accounts (Chart of Accounts / Kategori Akun Standar)
            if (bkuAccountDao().getAccountCount() == 0) {
                bkuAccountDao().insertAccounts(getInitialBkuAccounts())
            }

            // 5. Data Transaksi, Arus Kas, Hutang, Pembelian, dan Produk Awal:
            // Kosong secara default agar aplikasi baru bersih dari data fiktif.
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun clearAllData(keepUserAndStore: Boolean = true) = withContext(Dispatchers.IO) {
        transactionDao().clearTransactionItems()
        transactionDao().clearTransactions()
        cashFlowDao().clearCashFlows()
        debtDao().clearDebts()
        purchaseOrderDao().clearPurchaseOrders()
        stockOpnameDao().clearStockOpnames()
        productDao().clearProducts()
        if (!keepUserAndStore) {
            digitalConfigDao().saveConfig(
                DigitalConfigEntity(
                    id = "default_config",
                    providerName = "OtomaX H2H Server",
                    balance = 0.0,
                    isActive = true
                )
            )
        }
    }

    suspend fun seedProductsFromAsset(context: Context) = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.assets.open("katalog_minimarket.json").bufferedReader().use { it.readText() }
            val jsonArray = JsonParser.parseString(jsonString).asJsonArray
            val products = mutableListOf<ProductEntity>()
            val now = System.currentTimeMillis()

            for (element in jsonArray) {
                val obj = element.asJsonObject
                val p = ProductEntity(
                    id = if (obj.has("id") && !obj.get("id").isJsonNull) obj.get("id").asString else UUID.randomUUID().toString(),
                    storeId = if (obj.has("storeId") && !obj.get("storeId").isJsonNull) obj.get("storeId").asString else "default_store",
                    barcode = if (obj.has("barcode") && !obj.get("barcode").isJsonNull) obj.get("barcode").asString else null,
                    name = if (obj.has("name") && !obj.get("name").isJsonNull) obj.get("name").asString else "Produk Retail",
                    category = if (obj.has("category") && !obj.get("category").isJsonNull) obj.get("category").asString else "Pcs",
                    productType = if (obj.has("productType") && !obj.get("productType").isJsonNull) obj.get("productType").asString else "RETAIL",
                    costPrice = if (obj.has("costPrice") && !obj.get("costPrice").isJsonNull) obj.get("costPrice").asDouble else 0.0,
                    sellingPrice = if (obj.has("sellingPrice") && !obj.get("sellingPrice").isJsonNull) obj.get("sellingPrice").asDouble else 0.0,
                    stock = if (obj.has("stock") && !obj.get("stock").isJsonNull) obj.get("stock").asDouble else 50.0,
                    minStockAlert = if (obj.has("minStockAlert") && !obj.get("minStockAlert").isJsonNull) obj.get("minStockAlert").asDouble else 5.0,
                    imagePath = if (obj.has("imagePath") && !obj.get("imagePath").isJsonNull) obj.get("imagePath").asString else null,
                    providerCode = if (obj.has("providerCode") && !obj.get("providerCode").isJsonNull) obj.get("providerCode").asString else null,
                    createdAt = now,
                    updatedAt = now,
                    syncStatus = "SYNCED"
                )
                products.add(p)
            }

            if (products.isNotEmpty()) {
                products.chunked(150).forEach { batch ->
                    productDao().insertProducts(batch)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            productDao().insertProducts(getInitialSampleProducts())
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "denkasir_database.db"
                )
                .fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            getDatabase(context).ensureSeeded(context)
                        }
                    }

                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        super.onDestructiveMigration(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            getDatabase(context).ensureSeeded(context)
                        }
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            getDatabase(context).ensureSeeded(context)
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        private fun getInitialBkuAccounts(): List<BkuAccountEntity> {
            return listOf(
                BkuAccountEntity(accountCode = "1001", accountName = "Kas Toko (Laci Kasir)", accountType = "ASET"),
                BkuAccountEntity(accountCode = "1002", accountName = "Bank BCA Toko", accountType = "ASET"),
                BkuAccountEntity(accountCode = "1003", accountName = "Saldo Deposit Server H2H Pulsa", accountType = "ASET"),
                BkuAccountEntity(accountCode = "1004", accountName = "Piutang Usaha (Kasbon Pelanggan)", accountType = "ASET"),
                BkuAccountEntity(accountCode = "1005", accountName = "Persediaan Barang Dagang", accountType = "ASET"),
                BkuAccountEntity(accountCode = "2001", accountName = "Hutang Usaha (Supplier Sembako)", accountType = "KEWAJIBAN"),
                BkuAccountEntity(accountCode = "3001", accountName = "Modal Awal Pemilik", accountType = "MODAL"),
                BkuAccountEntity(accountCode = "4001", accountName = "Pendapatan Penjualan Retail", accountType = "PENDAPATAN"),
                BkuAccountEntity(accountCode = "4002", accountName = "Pendapatan Margin PPOB & Pulsa", accountType = "PENDAPATAN"),
                BkuAccountEntity(accountCode = "5001", accountName = "Beban Pokok Penjualan (HPP Retail)", accountType = "BEBAN"),
                BkuAccountEntity(accountCode = "5002", accountName = "Beban Operasional Toko (Listrik & Air)", accountType = "BEBAN"),
                BkuAccountEntity(accountCode = "5003", accountName = "Beban Gaji Kasir", accountType = "BEBAN")
            )
        }

        private fun getInitialCashFlows(): List<CashFlowEntity> {
            val now = System.currentTimeMillis()
            return listOf(
                CashFlowEntity(
                    id = UUID.randomUUID().toString(),
                    flowType = "IN",
                    category = "PENJUALAN",
                    amount = 1850000.0,
                    description = "Penjualan Toko Shift Pagi (32 Struk)",
                    referenceId = null,
                    createdAt = now - 3600000 * 5
                ),
                CashFlowEntity(
                    id = UUID.randomUUID().toString(),
                    flowType = "IN",
                    category = "PENDAPATAN LAIN",
                    amount = 450000.0,
                    description = "Komisi Agen PPOB & Token Listrik",
                    referenceId = null,
                    createdAt = now - 3600000 * 4
                ),
                CashFlowEntity(
                    id = UUID.randomUUID().toString(),
                    flowType = "OUT",
                    category = "OPERASIONAL",
                    amount = 250000.0,
                    description = "Pembayaran Listrik Toko Bulan Ini",
                    referenceId = null,
                    createdAt = now - 3600000 * 3
                ),
                CashFlowEntity(
                    id = UUID.randomUUID().toString(),
                    flowType = "OUT",
                    category = "KULAKAN",
                    amount = 1200000.0,
                    description = "Restock Minyak Goreng & Beras (CV. Sumber Makmur)",
                    referenceId = null,
                    createdAt = now - 3600000 * 2
                ),
                CashFlowEntity(
                    id = UUID.randomUUID().toString(),
                    flowType = "IN",
                    category = "PELUNASAN HUTANG",
                    amount = 150000.0,
                    description = "Pelunasan Kasbon Bu Ratna",
                    referenceId = null,
                    createdAt = now - 3600000 * 1
                )
            )
        }

        private fun getInitialDebts(): List<DebtEntity> {
            val now = System.currentTimeMillis()
            return listOf(
                DebtEntity(
                    id = UUID.randomUUID().toString(),
                    customerName = "Bu Siti (Tetangga Toko)",
                    customerPhone = "081234567890",
                    totalDebt = 125000.0,
                    paidAmount = 0.0,
                    debtType = "PIUTANG",
                    status = "UNPAID",
                    dueDate = now + (86400000 * 7),
                    createdAt = now - 86400000 * 2
                ),
                DebtEntity(
                    id = UUID.randomUUID().toString(),
                    customerName = "Pak Joko (Ojek Pengkolan)",
                    customerPhone = "085678901234",
                    totalDebt = 45000.0,
                    paidAmount = 20000.0,
                    debtType = "PIUTANG",
                    status = "PARTIAL",
                    dueDate = now + (86400000 * 3),
                    createdAt = now - 86400000 * 1
                ),
                DebtEntity(
                    id = UUID.randomUUID().toString(),
                    customerName = "CV. Sumber Makmur (Supplier)",
                    customerPhone = "082199887766",
                    totalDebt = 1500000.0,
                    paidAmount = 500000.0,
                    debtType = "HUTANG",
                    status = "PARTIAL",
                    dueDate = now + (86400000 * 14),
                    createdAt = now - 86400000 * 5
                )
            )
        }

        private fun getInitialPurchaseOrders(): List<PurchaseOrderEntity> {
            return listOf(
                PurchaseOrderEntity(
                    id = UUID.randomUUID().toString(),
                    supplierName = "CV. Sumber Makmur",
                    dateText = "25 Mei 2025",
                    itemCount = 8,
                    totalAmount = 1250000.0,
                    status = "Draft"
                ),
                PurchaseOrderEntity(
                    id = UUID.randomUUID().toString(),
                    supplierName = "Toko Sejahtera",
                    dateText = "24 Mei 2025",
                    itemCount = 5,
                    totalAmount = 850000.0,
                    status = "Dipesan"
                ),
                PurchaseOrderEntity(
                    id = UUID.randomUUID().toString(),
                    supplierName = "Grosir Jaya",
                    dateText = "23 Mei 2025",
                    itemCount = 12,
                    totalAmount = 2150000.0,
                    status = "Selesai"
                )
            )
        }

        private fun getInitialSampleProducts(): List<ProductEntity> {
            return listOf(
                ProductEntity(
                    id = "prod_indomie_goreng",
                    name = "Indomie Mi Goreng Spesial 85g",
                    barcode = "8998866500708",
                    category = "Makanan & Mie",
                    productType = "RETAIL",
                    costPrice = 2900.0,
                    sellingPrice = 3500.0,
                    stock = 50.0,
                    imagePath = "8998866500708.jpg"
                ),
                ProductEntity(
                    id = "prod_indomie_soto",
                    name = "Indomie Kuah Rasa Soto Mie 70g",
                    barcode = "8998866200301",
                    category = "Makanan & Mie",
                    productType = "RETAIL",
                    costPrice = 2900.0,
                    sellingPrice = 3500.0,
                    stock = 40.0,
                    imagePath = "8998866200301.jpg"
                )
            )
        }
    }
}