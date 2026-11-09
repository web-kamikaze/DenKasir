package com.denkasir.data.repository

import android.content.Context
import com.denkasir.data.local.AppDatabase
import com.denkasir.data.local.entity.*
import com.denkasir.data.remote.api.ApiClient
import com.denkasir.data.remote.dto.*
import com.denkasir.utils.SessionManager
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SyncRepository(private val context: Context) {

    companion object {
        private val digitalSyncMutex = Mutex()
        private const val AUTO_CHECK_INTERVAL_MS = 60 * 60 * 1000L // Cek perubahan setiap 1 jam (ringan via ?since=)
    }

    private val db = AppDatabase.getDatabase(context)
    private val sessionManager = SessionManager.getInstance(context)

    /**
     * Sinkronisasi otomatis katalog produk digital (hanya produk DIGITAL) dari API:
     * https://st.riungan.my.id/api/isipulsa/kasir-catalog
     * - Tidak menyentuh produk RETAIL sama sekali.
     * - Mendukung delta check 24 jam via parameter ?since=last_updated_at.
     * - Mempertahankan harga jual kustom pemilik toko jika syncStatus == "CUSTOM_PRICE" dan masih >= harga modal + 500.
     */
    suspend fun syncDigitalCatalog(forceFull: Boolean = false): Result<DigitalCatalogSyncSummary> = withContext(Dispatchers.IO) {
        digitalSyncMutex.withLock {
            try {
                val catalogUrl = sessionManager.getDigitalCatalogApiUrl()
                val margin = sessionManager.getDigitalDefaultMargin()
                val currentDigitalCount = db.productDao().getDigitalProductCount()
                val lastUpdatedIso = sessionManager.getDigitalCatalogLastUpdated()

                // Gunakan parameter since hanya jika bukan forceFull dan katalog lokal sudah terisi
                val sinceParam = if (!forceFull && currentDigitalCount >= 1000 && !lastUpdatedIso.isNullOrBlank()) {
                    lastUpdatedIso
                } else {
                    null
                }

                val api = ApiClient.getApi(sessionManager.getServerUrl())
                val response = api.fetchDigitalCatalogFromUrl(
                    fullUrl = catalogUrl,
                    since = sinceParam,
                    margin = margin
                )

                if (!response.isSuccessful || response.body() == null) {
                    return@withLock Result.failure(
                        Exception("Gagal memuat pembaruan katalog layanan digital (${response.code()})")
                    )
                }

                val body = response.body()!!
                val now = System.currentTimeMillis()
                sessionManager.saveDigitalCatalogLastCheckMs(now)

                // Jika server menyatakan belum ada perubahan (masih dalam siklus 24 jam yang sama)
                if (body.status == "not_modified" || body.upToDate) {
                    return@withLock Result.success(
                        DigitalCatalogSyncSummary(
                            upToDate = true,
                            totalItems = currentDigitalCount,
                            normalCount = currentDigitalCount,
                            gangguanCount = 0,
                            lastUpdatedAt = body.lastUpdatedAt ?: lastUpdatedIso,
                            expiresAt = body.expiresAt
                        )
                    )
                }

                if (body.items.isEmpty()) {
                    return@withLock Result.failure(Exception("Data katalog layanan digital kosong."))
                }

                // Ambil produk digital yang sudah ada untuk mempertahankan harga jual manual (CUSTOM_PRICE)
                val existingDigitals = db.productDao().getDigitalProductsList().associateBy { it.id }

                val newDigitalEntities = body.items.map { item ->
                    val existing = existingDigitals[item.id]
                    val apiCost = item.costPrice.coerceAtLeast(0.0)
                    val apiRecommendedSell = if (item.recommendedSellingPrice > 0) {
                        item.recommendedSellingPrice
                    } else if (item.sellingPrice > 0) {
                        item.sellingPrice
                    } else {
                        apiCost + margin
                    }

                    // Jika pemilik toko pernah mengubah harga jual manual & masih di atas harga modal terbaru, pertahankan
                    val isCustomPrice = existing != null && existing.syncStatus == "CUSTOM_PRICE" && existing.sellingPrice >= (apiCost + 500.0)
                    val finalSellingPrice = if (isCustomPrice) existing!!.sellingPrice else apiRecommendedSell
                    val finalSyncStatus = if (isCustomPrice) "CUSTOM_PRICE" else "SYNCED"
                    val finalStock = if (item.isGangguan || item.stock <= 0.0) 0.0 else 999.0

                    ProductEntity(
                        id = item.id,
                        storeId = existing?.storeId ?: "default_store",
                        barcode = null,
                        name = item.name,
                        category = item.category.ifBlank { "Pulsa" },
                        productType = "DIGITAL",
                        costPrice = apiCost,
                        sellingPrice = finalSellingPrice,
                        stock = finalStock,
                        minStockAlert = 0.0,
                        imagePath = null,
                        providerCode = item.providerCode,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                        syncStatus = finalSyncStatus
                    )
                }

                // Simpan atomik dalam transaksi Room (Hanya menyentuh productType == 'DIGITAL')
                db.withTransaction {
                    db.productDao().deleteLegacyDigitalProducts()
                    newDigitalEntities.chunked(500).forEach { batch ->
                        db.productDao().insertProducts(batch)
                    }
                }

                if (!body.lastUpdatedAt.isNullOrBlank()) {
                    sessionManager.saveDigitalCatalogLastUpdated(body.lastUpdatedAt)
                }

                Result.success(
                    DigitalCatalogSyncSummary(
                        upToDate = false,
                        totalItems = newDigitalEntities.size,
                        normalCount = body.totalNormal,
                        gangguanCount = body.totalGangguan,
                        lastUpdatedAt = body.lastUpdatedAt,
                        expiresAt = body.expiresAt
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Dipanggil otomatis saat aplikasi dibuka / tab digital dibuka.
     * Hanya melakukan pengecekan ke server jika katalog digital lokal masih sedikit (< 1000)
     * atau sudah melewati interval pengecekan otomatis.
     */
    suspend fun syncDigitalCatalogIfNeeded(): Result<DigitalCatalogSyncSummary>? = withContext(Dispatchers.IO) {
        val currentDigitalCount = db.productDao().getDigitalProductCount()
        val lastCheck = sessionManager.getDigitalCatalogLastCheckMs()
        val now = System.currentTimeMillis()
        if (currentDigitalCount < 1000 || (now - lastCheck) >= AUTO_CHECK_INTERVAL_MS) {
            syncDigitalCatalog(forceFull = currentDigitalCount < 1000)
        } else {
            null
        }
    }

    suspend fun performFullSync(): Result<SyncPushResponseDto> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = sessionManager.getServerUrl()
            val api = ApiClient.getApi(baseUrl)

            // Pastikan Token didapatkan (Login jika belum ada token)
            suspend fun obtainFreshToken(): String? {
                val username = sessionManager.getCurrentUsername()
                val password = if (sessionManager.isOwner()) sessionManager.getOwnerPassword() else "kasir123"
                val loginRes = api.login(LoginRequestDto(username, password))
                return if (loginRes.isSuccessful && loginRes.body() != null) {
                    val newToken = loginRes.body()!!.accessToken
                    sessionManager.saveAuthToken(newToken)
                    newToken
                } else {
                    sessionManager.saveAuthToken(null)
                    null
                }
            }

            var token = sessionManager.getAuthToken()
            if (token.isNullOrEmpty()) {
                token = obtainFreshToken()
                if (token.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("Gagal autentikasi ke server cloud: Periksa kredensial akun"))
                }
            }

            var bearerToken = "Bearer " + token
            val storeId = "default_store"
            val now = System.currentTimeMillis()

            // ==========================================
            // 1. KUMPULKAN DATA LOKAL UNTUK DI-PUSH
            // ==========================================
            // Sinkronkan juga katalog produk digital dari st.riungan.my.id
            syncDigitalCatalog(forceFull = false)

            val products = db.productDao().getAllProducts().first().filter { !it.id.startsWith("isipulsa_") }.map {
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

            // Eksekusi Push ke Cloud (dengan auto-retry jika 401 Token Expired)
            var pushRes = api.pushSync(bearerToken, pushPayload)
            if (pushRes.code() == 401) {
                val fresh = obtainFreshToken()
                if (!fresh.isNullOrEmpty()) {
                    token = fresh
                    bearerToken = "Bearer " + token
                    pushRes = api.pushSync(bearerToken, pushPayload)
                }
            }
            if (!pushRes.isSuccessful || pushRes.body() == null) {
                if (pushRes.code() == 401) sessionManager.saveAuthToken(null)
                return@withContext Result.failure(Exception("Gagal Push Sync: ${pushRes.message()} (${pushRes.code()})"))
            }

            // ==========================================
            // 2. TARIK DELTA TERBARU DARI CLOUD (PULL)
            // ==========================================
            val lastSynced = sessionManager.getLastSyncTime()
            var pullRes = api.pullSync(bearerToken, SyncPullRequestDto(storeId, lastSynced))
            if (pullRes.code() == 401) {
                val fresh = obtainFreshToken()
                if (!fresh.isNullOrEmpty()) {
                    token = fresh
                    bearerToken = "Bearer " + token
                    pullRes = api.pullSync(bearerToken, SyncPullRequestDto(storeId, lastSynced))
                }
            }
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
