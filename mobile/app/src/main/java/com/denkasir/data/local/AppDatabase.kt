package com.denkasir.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.denkasir.data.local.dao.*
import com.denkasir.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    version = 5,
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
                            val dbInstance = getDatabase(context)
                            
                            // 1. Initial Store Profile
                            dbInstance.storeDao().insertStore(
                                StoreEntity(
                                    id = "default_store",
                                    name = "DENKASIR UMKM",
                                    address = "Jl. Pemuda No. 88, Semarang",
                                    phone = "081234567890",
                                    ownerId = "owner_1"
                                )
                            )

                            // 2. Initial Users
                            dbInstance.userDao().insertUsers(
                                listOf(
                                    UserEntity(
                                        id = "owner_1",
                                        storeId = "default_store",
                                        username = "owner",
                                        fullName = "Pemilik Toko (Owner)",
                                        role = "OWNER"
                                    ),
                                    UserEntity(
                                        id = "kasir_1",
                                        storeId = "default_store",
                                        username = "kasir",
                                        fullName = "Kasir Utama (Staff)",
                                        role = "KASIR"
                                    )
                                )
                            )

                            // 3. Initial Products
                            dbInstance.productDao().insertProducts(getInitialSampleProducts())

                            // 4. Initial Digital Config
                            dbInstance.digitalConfigDao().saveConfig(
                                DigitalConfigEntity(
                                    id = "default_config",
                                    providerName = "OtomaX H2H Server",
                                    balance = 2450000.0,
                                    isActive = true
                                )
                            )

                            // 5. Initial BKU Chart of Accounts (COA)
                            dbInstance.bkuAccountDao().insertAccounts(getInitialBkuAccounts())

                            // 6. Initial Cash Flows (BKU Records)
                            dbInstance.cashFlowDao().insertCashFlows(getInitialCashFlows())

                            // 7. Initial Debts / Receivables
                            dbInstance.debtDao().insertDebts(getInitialDebts())

                            // 8. Initial Purchase Orders (Daftar Belanja Image #2)
                            dbInstance.purchaseOrderDao().insertPurchases(getInitialPurchaseOrders())
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
                // Akun Pemasukan (IN)
                BkuAccountEntity(id = "acc_in_1", accountName = "Penjualan Kasir / Ritel", accountType = "IN", accountCode = "4-1001", isDefault = true),
                BkuAccountEntity(id = "acc_in_2", accountName = "Pendapatan Pulsa & PPOB", accountType = "IN", accountCode = "4-1002", isDefault = true),
                BkuAccountEntity(id = "acc_in_3", accountName = "Penerimaan Pelunasan Piutang", accountType = "IN", accountCode = "4-1003", isDefault = true),
                BkuAccountEntity(id = "acc_in_4", accountName = "Modal Masuk / Tambahan Kas", accountType = "IN", accountCode = "4-1004", isDefault = true),
                BkuAccountEntity(id = "acc_in_5", accountName = "Pendapatan Komisi & Lain-lain", accountType = "IN", accountCode = "4-1005", isDefault = true),

                // Akun Pengeluaran (OUT)
                BkuAccountEntity(id = "acc_out_1", accountName = "Belanja Stok Barang / Kulakan", accountType = "OUT", accountCode = "5-1001", isDefault = true),
                BkuAccountEntity(id = "acc_out_2", accountName = "Top Up Saldo Deposit H2H Pulsa", accountType = "OUT", accountCode = "5-1002", isDefault = true),
                BkuAccountEntity(id = "acc_out_3", accountName = "Biaya Listrik, Air & Internet Toko", accountType = "OUT", accountCode = "5-1003", isDefault = true),
                BkuAccountEntity(id = "acc_out_4", accountName = "Gaji & Uang Makan Karyawan", accountType = "OUT", accountCode = "5-1004", isDefault = true),
                BkuAccountEntity(id = "acc_out_5", accountName = "Sewa Tempat / Bangunan Toko", accountType = "OUT", accountCode = "5-1005", isDefault = true),
                BkuAccountEntity(id = "acc_out_6", accountName = "Biaya Transportasi & Logistik", accountType = "OUT", accountCode = "5-1006", isDefault = true),
                BkuAccountEntity(id = "acc_out_7", accountName = "Pembayaran Hutang Supplier", accountType = "OUT", accountCode = "5-1007", isDefault = true),
                BkuAccountEntity(id = "acc_out_8", accountName = "Biaya Operasional & Perlengkapan", accountType = "OUT", accountCode = "5-1008", isDefault = true)
            )
        }

        private fun getInitialCashFlows(): List<CashFlowEntity> {
            val now = System.currentTimeMillis()
            val day = 86400000L
            return listOf(
                CashFlowEntity(id = UUID.randomUUID().toString(), flowType = "IN", category = "Modal Masuk / Tambahan Kas", amount = 5000000.0, description = "Saldo Kas Awal Toko", createdAt = now - 6 * day),
                CashFlowEntity(id = UUID.randomUUID().toString(), flowType = "OUT", category = "Belanja Stok Barang / Kulakan", amount = 2200000.0, description = "Kulakan Sembako Grosir", createdAt = now - 5 * day),
                CashFlowEntity(id = UUID.randomUUID().toString(), flowType = "IN", category = "Penjualan Kasir / Ritel", amount = 3750000.0, description = "Total Penjualan Kasir Shift Pagi", createdAt = now - 4 * day),
                CashFlowEntity(id = UUID.randomUUID().toString(), flowType = "OUT", category = "Top Up Saldo Deposit H2H Pulsa", amount = 1000000.0, description = "Deposit Saldo Pulsa Server", createdAt = now - 3 * day),
                CashFlowEntity(id = UUID.randomUUID().toString(), flowType = "IN", category = "Pendapatan Pulsa & PPOB", amount = 1250000.0, description = "Penjualan Pulsa & Token PLN", createdAt = now - 2 * day),
                CashFlowEntity(id = UUID.randomUUID().toString(), flowType = "OUT", category = "Biaya Listrik, Air & Internet Toko", amount = 350000.0, description = "Bayar Tagihan Listrik Toko", createdAt = now - 1 * day),
                CashFlowEntity(id = UUID.randomUUID().toString(), flowType = "IN", category = "Penjualan Kasir / Ritel", amount = 4200000.0, description = "Omset Penjualan Kasir Hari Ini", createdAt = now)
            )
        }

        private fun getInitialDebts(): List<DebtEntity> {
            val now = System.currentTimeMillis()
            return listOf(
                DebtEntity(
                    id = UUID.randomUUID().toString(),
                    customerName = "Ibu Siti (Warga RT 03)",
                    customerPhone = "081234567891",
                    totalDebt = 150000.0,
                    paidAmount = 50000.0,
                    status = "PARTIAL",
                    debtType = "PIUTANG",
                    createdAt = now - 3 * 86400000L
                ),
                DebtEntity(
                    id = UUID.randomUUID().toString(),
                    customerName = "Pak Joko (Bengkel Depan)",
                    customerPhone = "081987654321",
                    totalDebt = 85000.0,
                    paidAmount = 0.0,
                    status = "UNPAID",
                    debtType = "PIUTANG",
                    createdAt = now - 1 * 86400000L
                ),
                DebtEntity(
                    id = UUID.randomUUID().toString(),
                    customerName = "Distributor Sembako Jaya",
                    customerPhone = "082112233445",
                    totalDebt = 750000.0,
                    paidAmount = 0.0,
                    status = "UNPAID",
                    debtType = "HUTANG",
                    createdAt = now - 2 * 86400000L
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
                    id = UUID.randomUUID().toString(),
                    name = "Indomie Goreng",
                    barcode = "8998866200213",
                    category = "Pcs",
                    productType = "RETAIL",
                    costPrice = 2500.0,
                    sellingPrice = 3000.0,
                    stock = 50.0
                ),
                ProductEntity(
                    id = UUID.randomUUID().toString(),
                    name = "Gula Pasir 1kg",
                    barcode = "8991002100012",
                    category = "Kg",
                    productType = "RETAIL",
                    costPrice = 11500.0,
                    sellingPrice = 13000.0,
                    stock = 30.0
                ),
                ProductEntity(
                    id = UUID.randomUUID().toString(),
                    name = "Minyak Goreng 1L",
                    barcode = "8992003100025",
                    category = "Pcs",
                    productType = "RETAIL",
                    costPrice = 14000.0,
                    sellingPrice = 15500.0,
                    stock = 25.0
                ),
                ProductEntity(
                    id = UUID.randomUUID().toString(),
                    name = "Susu Kental Manis",
                    barcode = "8993004100037",
                    category = "Pcs",
                    productType = "RETAIL",
                    costPrice = 7500.0,
                    sellingPrice = 9000.0,
                    stock = 40.0
                ),
                ProductEntity(
                    id = UUID.randomUUID().toString(),
                    name = "Token Listrik PLN 20.000",
                    barcode = null,
                    category = "PLN",
                    productType = "DIGITAL",
                    costPrice = 20250.0,
                    sellingPrice = 22500.0,
                    providerCode = "PLN20",
                    stock = 0.0
                ),
                ProductEntity(
                    id = UUID.randomUUID().toString(),
                    name = "Token Listrik PLN 50.000",
                    barcode = null,
                    category = "PLN",
                    productType = "DIGITAL",
                    costPrice = 50250.0,
                    sellingPrice = 52500.0,
                    providerCode = "PLN50",
                    stock = 0.0
                ),
                ProductEntity(
                    id = UUID.randomUUID().toString(),
                    name = "Pulsa Telkomsel 10.000",
                    barcode = null,
                    category = "Pulsa",
                    productType = "DIGITAL",
                    costPrice = 10200.0,
                    sellingPrice = 12000.0,
                    providerCode = "TSEL10",
                    stock = 0.0
                ),
                ProductEntity(
                    id = UUID.randomUUID().toString(),
                    name = "Pulsa Indosat 10.000",
                    barcode = null,
                    category = "Pulsa",
                    productType = "DIGITAL",
                    costPrice = 10150.0,
                    sellingPrice = 12000.0,
                    providerCode = "ISAT10",
                    stock = 0.0
                ),
                ProductEntity(
                    id = UUID.randomUUID().toString(),
                    name = "Paket Data Telkomsel 5GB",
                    barcode = null,
                    category = "Paket Data",
                    productType = "DIGITAL",
                    costPrice = 25000.0,
                    sellingPrice = 28000.0,
                    providerCode = "DATA_TSEL5",
                    stock = 0.0
                )
            )
        }
    }
}