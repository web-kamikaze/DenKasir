package com.denkasir.data.local.dao

import androidx.room.*
import com.denkasir.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {
    @Query("SELECT * FROM stores LIMIT 1")
    fun getStoreFlow(): Flow<StoreEntity?>

    @Query("SELECT * FROM stores LIMIT 1")
    suspend fun getStore(): StoreEntity?

    @Query("SELECT * FROM stores WHERE id = :id LIMIT 1")
    suspend fun getStoreById(id: String): StoreEntity?

    @Query("SELECT COUNT(*) FROM stores")
    suspend fun getStoreCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStore(store: StoreEntity)

    @Update
    suspend fun updateStore(store: StoreEntity)

    @Query("UPDATE stores SET name = :name, address = :address, phone = :phone, updatedAt = :time WHERE id = :storeId")
    suspend fun updateStoreProfile(storeId: String, name: String, address: String?, phone: String?, time: Long = System.currentTimeMillis())
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY role ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE role = 'KASIR' ORDER BY fullName ASC")
    fun getCashiers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE role = :role LIMIT 1")
    suspend fun getUserByRole(role: String): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Delete
    suspend fun deleteUser(user: UserEntity)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE productType = 'RETAIL' ORDER BY name ASC")
    fun getRetailProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE productType = 'DIGITAL' ORDER BY name ASC")
    fun getDigitalProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE category = :category AND productType = 'RETAIL' ORDER BY name ASC")
    fun getProductsByCategory(category: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%'")
    fun searchProducts(query: String): Flow<List<ProductEntity>>

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getProductCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("UPDATE products SET stock = stock - :quantity WHERE id = :productId")
    suspend fun reduceStock(productId: String, quantity: Double)

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProductsList(): List<ProductEntity>

    @Query("DELETE FROM products")
    suspend fun clearProducts()

    @Delete
    suspend fun deleteProduct(product: ProductEntity)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionItems(items: List<TransactionItemEntity>)

    @Query("SELECT * FROM transaction_items WHERE transactionId = :trxId")
    suspend fun getTransactionItems(trxId: String): List<TransactionItemEntity>

    @Query("SELECT * FROM transactions WHERE syncStatus = 'PENDING'")
    suspend fun getUnsyncedTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    suspend fun getAllTransactionsList(): List<TransactionEntity>

    @Query("SELECT * FROM transaction_items")
    fun getAllTransactionItemsFlow(): Flow<List<TransactionItemEntity>>

    @Query("SELECT * FROM transaction_items")
    suspend fun getAllTransactionItems(): List<TransactionItemEntity>

    @Query("SELECT SUM((sellingPrice - costPrice) * quantity) FROM transaction_items")
    fun getTotalGrossProfitFlow(): Flow<Double?>

    @Query("SELECT SUM(costPrice * quantity) FROM transaction_items")
    fun getTotalHppFlow(): Flow<Double?>

    @Query("SELECT SUM(subtotal) FROM transaction_items")
    fun getTotalRevenueFlow(): Flow<Double?>

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()

    @Query("DELETE FROM transaction_items")
    suspend fun clearTransactionItems()
}

@Dao
interface CashFlowDao {
    @Query("SELECT * FROM cash_flows ORDER BY createdAt DESC")
    fun getAllCashFlows(): Flow<List<CashFlowEntity>>

    @Query("SELECT * FROM cash_flows WHERE flowType = :type ORDER BY createdAt DESC")
    fun getCashFlowsByType(type: String): Flow<List<CashFlowEntity>>

    @Query("SELECT SUM(amount) FROM cash_flows WHERE flowType = 'IN'")
    fun getTotalCashIn(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM cash_flows WHERE flowType = 'OUT'")
    fun getTotalCashOut(): Flow<Double?>

    @Query("SELECT COUNT(*) FROM cash_flows")
    suspend fun getCashFlowCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashFlow(cashFlow: CashFlowEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashFlows(cashFlows: List<CashFlowEntity>)

    @Query("SELECT * FROM cash_flows ORDER BY createdAt DESC")
    suspend fun getAllCashFlowsList(): List<CashFlowEntity>

    @Query("SELECT SUM(amount) FROM cash_flows WHERE flowType = 'OUT' AND (category = 'OPERASIONAL' OR category = 'BEBAN_OPERASIONAL' OR category LIKE '%Operasional%' OR category LIKE '%Beban%')")
    fun getTotalOperationalExpenseFlow(): Flow<Double?>

    @Query("DELETE FROM cash_flows")
    suspend fun clearCashFlows()

    @Delete
    suspend fun deleteCashFlow(cashFlow: CashFlowEntity)
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY createdAt DESC")
    fun getAllDebts(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE debtType = :debtType ORDER BY createdAt DESC")
    fun getDebtsByType(debtType: String): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE status != 'PAID' ORDER BY createdAt DESC")
    fun getUnpaidDebts(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE debtType = :debtType AND status != 'PAID' ORDER BY createdAt DESC")
    fun getUnpaidDebtsByType(debtType: String): Flow<List<DebtEntity>>

    @Query("SELECT SUM(totalDebt - paidAmount) FROM debts WHERE debtType = 'PIUTANG' AND status != 'PAID'")
    fun getTotalRemainingPiutang(): Flow<Double?>

    @Query("SELECT SUM(totalDebt - paidAmount) FROM debts WHERE debtType = 'HUTANG' AND status != 'PAID'")
    fun getTotalRemainingHutang(): Flow<Double?>

    @Query("SELECT SUM(totalDebt - paidAmount) FROM debts WHERE status != 'PAID'")
    fun getTotalRemainingDebts(): Flow<Double?>

    @Query("SELECT COUNT(*) FROM debts")
    suspend fun getDebtCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: DebtEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebts(debts: List<DebtEntity>)

    @Update
    suspend fun updateDebt(debt: DebtEntity)

    @Query("SELECT * FROM debts ORDER BY createdAt DESC")
    suspend fun getAllDebtsList(): List<DebtEntity>

    @Query("DELETE FROM debts")
    suspend fun clearDebts()

    @Delete
    suspend fun deleteDebt(debt: DebtEntity)
}

@Dao
interface BkuAccountDao {
    @Query("SELECT * FROM bku_accounts ORDER BY accountCode ASC, accountName ASC")
    fun getAllAccounts(): Flow<List<BkuAccountEntity>>

    @Query("SELECT * FROM bku_accounts WHERE accountType = :type ORDER BY accountCode ASC, accountName ASC")
    fun getAccountsByType(type: String): Flow<List<BkuAccountEntity>>

    @Query("SELECT COUNT(*) FROM bku_accounts")
    suspend fun getAccountCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: BkuAccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<BkuAccountEntity>)

    @Delete
    suspend fun deleteAccount(account: BkuAccountEntity)
}

@Dao
interface StockOpnameDao {
    @Query("SELECT * FROM stock_opnames ORDER BY createdAt DESC")
    fun getAllStockOpnames(): Flow<List<StockOpnameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockOpname(stockOpname: StockOpnameEntity)

    @Query("DELETE FROM stock_opnames")
    suspend fun clearStockOpnames()
}

@Dao
interface DigitalConfigDao {
    @Query("SELECT * FROM digital_configs WHERE id = :id LIMIT 1")
    fun getConfigFlow(id: String = "default_config"): Flow<DigitalConfigEntity?>

    @Query("SELECT * FROM digital_configs WHERE id = :id LIMIT 1")
    suspend fun getConfig(id: String = "default_config"): DigitalConfigEntity?

    @Query("SELECT COUNT(*) FROM digital_configs")
    suspend fun getConfigCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: DigitalConfigEntity)

    @Query("UPDATE digital_configs SET balance = balance + :amount, updatedAt = :time WHERE id = 'default_config'")
    suspend fun topUpBalance(amount: Double, time: Long = System.currentTimeMillis())

    @Query("UPDATE digital_configs SET balance = balance - :amount, updatedAt = :time WHERE id = 'default_config'")
    suspend fun deductBalance(amount: Double, time: Long = System.currentTimeMillis())
}

@Dao
interface PurchaseOrderDao {
    @Query("SELECT * FROM purchase_orders ORDER BY createdAt DESC")
    fun getAllPurchases(): Flow<List<PurchaseOrderEntity>>

    @Query("SELECT * FROM purchase_orders WHERE status = :status ORDER BY createdAt DESC")
    fun getPurchasesByStatus(status: String): Flow<List<PurchaseOrderEntity>>

    @Query("SELECT COUNT(*) FROM purchase_orders")
    suspend fun getPurchaseCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: PurchaseOrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchases(purchases: List<PurchaseOrderEntity>)

    @Update
    suspend fun updatePurchase(purchase: PurchaseOrderEntity)

    @Query("DELETE FROM purchase_orders")
    suspend fun clearPurchaseOrders()

    @Delete
    suspend fun deletePurchase(purchase: PurchaseOrderEntity)
}