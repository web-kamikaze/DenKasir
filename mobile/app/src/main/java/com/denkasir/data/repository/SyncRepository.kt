package com.denkasir.data.repository

import android.content.Context
import com.denkasir.data.local.AppDatabase
import com.denkasir.data.local.entity.*
import com.denkasir.data.remote.api.ApiClient
import com.denkasir.data.remote.dto.*
import com.denkasir.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class SyncRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val sessionManager = SessionManager.getInstance(context)

    suspend fun performFullSync(): Result<SyncPushResponseDto> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = sessionManager.getServerUrl()
            val api = ApiClient.getApi(baseUrl)

            // Pastikan Token didapatkan (Login jika belum ada token)
            var token = sessionManager.getAuthToken()
            if (token.isNullOrEmpty()) {
                val username = sessionManager.getCurrentUsername()
                val password = if (sessionManager.isOwner()) "admin123" else "kasir123"
                val loginRes = api.login(LoginRequestDto(username, password))
                if (loginRes.isSuccessful && loginRes.body() != null) {
                    token = loginRes.body()!!.accessToken
                    sessionManager.saveAuthToken(token)
                } else {
                    return@withContext Result.failure(Exception("Gagal autentikasi ke server: ${loginRes.message()} (${loginRes.code()})"))
                }
            }

            val bearerToken = "Bearer $token"
            val storeId = "default_store"
            val now = System.currentTimeMillis()

            // ==========================================
            // 1. KUMPULKAN DATA LOKAL UNTUK DI-PUSH
            // ==========================================
            val products = db.productDao().getAllProducts().first().map {
                ProductSyncDto(
                    id = it.id,
                    storeId = it.storeId,
                    barcode = it.barcode,
                    name = it.name,
                    category = it.category,
                    productType = it.productType,
                    costPrice = it.costPrice,
                    sellingPrice = it.sellingPrice,
                    stock = it.stock,
                    minStockAlert = it.minStockAlert,
                    imagePath = it.imagePath,
                    providerCode = it.providerCode
                )
            }

            val transactions = db.transactionDao().getAllTransactions().first().map { t ->
                val items = db.transactionDao().getTransactionItems(t.id).map { itm ->
                    TransactionItemSyncDto(
                        id = itm.id,
                        productId = itm.productId,
                        productName = itm.productName,
                        quantity = itm.quantity,
                        costPrice = itm.costPrice,
                        sellingPrice = itm.sellingPrice,
                        subtotal = itm.subtotal,
                        targetPhoneNumber = itm.targetPhoneNumber,
                        snNumber = itm.snNumber,
                        digitalStatus = itm.digitalStatus
                    )
                }
                TransactionSyncDto(
                    id = t.id,
                    storeId = t.storeId,
                    userId = t.userId,
                    invoiceNumber = t.invoiceNumber,
                    totalAmount = t.totalAmount,
                    cashGiven = t.cashGiven,
                    changeAmount = t.changeAmount,
                    paymentMethod = t.paymentMethod,
                    customerName = t.customerName,
                    status = t.status,
                    items = items
                )
            }

            val cashFlows = db.cashFlowDao().getAllCashFlows().first().map {
                CashFlowSyncDto(
                    id = it.id,
                    storeId = it.storeId,
                    flowType = it.flowType,
                    category = it.category,
                    amount = it.amount,
                    description = it.description,
                    referenceId = it.referenceId
                )
            }

            val debts = db.debtDao().getAllDebts().first().map {
                DebtSyncDto(
                    id = it.id,
                    storeId = it.storeId,
                    customerName = it.customerName,
                    customerPhone = it.customerPhone,
                    totalDebt = it.totalDebt,
                    paidAmount = it.paidAmount,
                    status = it.status,
                    debtType = it.debtType,
                    dueDate = it.dueDate
                )
            }

            val stockOpnames = db.stockOpnameDao().getAllStockOpnames().first().map {
                StockOpnameSyncDto(
                    id = it.id,
                    storeId = it.storeId,
                    productId = it.productId,
                    systemStock = it.systemStock,
                    physicalStock = it.physicalStock,
                    difference = it.difference,
                    reason = it.reason
                )
            }

            val purchaseOrders = db.purchaseOrderDao().getAllPurchases().first().map {
                PurchaseOrderSyncDto(
                    id = it.id,
                    storeId = it.storeId,
                    supplierName = it.supplierName,
                    dateText = it.dateText,
                    itemCount = it.itemCount,
                    totalAmount = it.totalAmount,
                    status = it.status
                )
            }

            val pushPayload = SyncPushRequestDto(
                storeId = storeId,
                clientTimestamp = now,
                products = products,
                transactions = transactions,
                cashFlows = cashFlows,
                debts = debts,
                stockOpnames = stockOpnames,
                purchaseOrders = purchaseOrders
            )

            // Eksekusi Push ke Cloud
            val pushRes = api.pushSync(bearerToken, pushPayload)
            if (!pushRes.isSuccessful || pushRes.body() == null) {
                return@withContext Result.failure(Exception("Gagal Push Sync: ${pushRes.message()} (${pushRes.code()})"))
            }

            // ==========================================
            // 2. TARIK DELTA TERBARU DARI CLOUD (PULL)
            // ==========================================
            val lastSynced = sessionManager.getLastSyncTime()
            val pullRes = api.pullSync(bearerToken, SyncPullRequestDto(storeId, lastSynced))
            if (pullRes.isSuccessful && pullRes.body() != null) {
                val pullBody = pullRes.body()!!
                // Masukkan produk yang ditarik dari server
                for (p in pullBody.products) {
                    val entity = ProductEntity(
                        id = p.id,
                        storeId = p.storeId,
                        barcode = p.barcode,
                        name = p.name,
                        category = p.category,
                        productType = p.productType,
                        costPrice = p.costPrice,
                        sellingPrice = p.sellingPrice,
                        stock = p.stock,
                        minStockAlert = p.minStockAlert,
                        imagePath = p.imagePath,
                        providerCode = p.providerCode,
                        updatedAt = pullBody.serverTimestamp,
                        syncStatus = "SYNCED"
                    )
                    db.productDao().insertProduct(entity)
                }

                sessionManager.saveLastSyncTime(pullBody.serverTimestamp)
            }

            Result.success(pushRes.body()!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
