package com.denkasir.ui.pos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.denkasir.data.local.AppDatabase
import com.denkasir.data.local.entity.CashFlowEntity
import com.denkasir.data.local.entity.DebtEntity
import com.denkasir.data.local.entity.ProductEntity
import com.denkasir.data.local.entity.TransactionEntity
import com.denkasir.data.local.entity.TransactionItemEntity
import com.denkasir.utils.CurrencyUtils
import com.denkasir.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class PosViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val productDao = db.productDao()
    private val transactionDao = db.transactionDao()
    private val cashFlowDao = db.cashFlowDao()
    private val debtDao = db.debtDao()
    private val digitalDao = db.digitalConfigDao()

    val cartItems: StateFlow<List<CartItem>> = CartManager.cartItems

    private val _selectedCategory = MutableStateFlow("Scan")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    val allProducts = productDao.getAllProducts()

    init {
        // Keranjang kasir mulai bersih (kosong) saat aplikasi baru dijalankan
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun addToCart(product: ProductEntity, qty: Double = 1.0, phone: String? = null) {
        CartManager.addToCart(product, qty, phone)
    }

    fun updateQuantity(cartItem: CartItem, newQty: Double) {
        CartManager.updateQuantity(cartItem, newQty)
    }

    fun removeItem(cartItem: CartItem) {
        CartManager.removeItem(cartItem)
    }

    fun clearCart() {
        CartManager.clearCart()
    }

    fun getTotalItemCount(): Double {
        return CartManager.getTotalItemCount()
    }

    fun getTotalBayar(): Double {
        return CartManager.getTotalBayar()
    }

    fun addScannedBarcode(barcode: String, qty: Double = 1.0, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val product = withContext(Dispatchers.IO) {
                productDao.getProductByBarcode(barcode)
            }
            if (product != null) {
                addToCart(product, qty)
                val qtyStr = if (qty > 1.0) " (${qty.toInt()}x)" else ""
                onResult(true, "Ditambahkan: ${product.name}$qtyStr")
            } else {
                onResult(false, "Produk barcode $barcode belum terdaftar.")
            }
        }
    }

    fun checkout(
        paymentMethod: String,
        cashGiven: Double,
        customerName: String?,
        customerPhone: String? = null,
        onSuccess: (TransactionEntity, String) -> Unit
    ) {
        viewModelScope.launch {
            val currentCart = CartManager.cartItems.value
            if (currentCart.isEmpty()) return@launch

            val totalAmount = getTotalBayar()
            val change = if (paymentMethod == "CASH") maxOf(0.0, cashGiven - totalAmount) else 0.0
            val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
            val invoiceNo = "TRX-${dateFormat.format(Date())}"
            val trxId = UUID.randomUUID().toString()

            val session = SessionManager.getInstance(getApplication())
            val currentUserId = session.getCurrentUserId()
            val currentCashierName = session.getCurrentFullName()

            val hasDigital = currentCart.any { it.product.productType == "DIGITAL" }

            val transaction = TransactionEntity(
                id = trxId,
                userId = currentUserId,
                cashierName = currentCashierName,
                invoiceNumber = invoiceNo,
                totalAmount = totalAmount,
                cashGiven = cashGiven,
                changeAmount = change,
                paymentMethod = paymentMethod,
                customerName = customerName,
                status = if (hasDigital) "PROSES" else "COMPLETED"
            )

            val trxItems = currentCart.map { item ->
                val isDigital = item.product.productType == "DIGITAL"
                val initialStatus = if (isDigital) "PROSES" else "SUCCESS"

                TransactionItemEntity(
                    transactionId = trxId,
                    productId = item.product.id,
                    productName = item.product.name,
                    quantity = item.quantity,
                    costPrice = item.product.costPrice,
                    sellingPrice = item.product.sellingPrice,
                    subtotal = item.subtotal,
                    targetPhoneNumber = item.targetPhone,
                    snNumber = null,
                    digitalStatus = initialStatus
                )
            }

            withContext(Dispatchers.IO) {
                // 1. Insert Transaction & Items
                transactionDao.insertTransaction(transaction)
                transactionDao.insertTransactionItems(trxItems)

                // 2. Pemisahan Logika: Retail (Kurangi Stok Fisik) vs Digital (Potong Saldo Deposit Server)
                currentCart.forEach { item ->
                    if (item.product.productType == "RETAIL") {
                        productDao.reduceStock(item.product.id, item.quantity)
                    } else if (item.product.productType == "DIGITAL") {
                        // Potong Saldo Deposit Server H2H sebesar harga modal
                        digitalDao.deductBalance(item.product.costPrice * item.quantity)
                    }
                }

                // 3. Jurnal BKU (Kas Masuk Penjualan)
                // HANYA untuk CASH, QRIS, TRANSFER karena uang sudah lunas diterima
                if (paymentMethod in listOf("CASH", "QRIS", "TRANSFER")) {
                    cashFlowDao.insertCashFlow(
                        CashFlowEntity(
                            flowType = "IN",
                            category = "PENJUALAN",
                            amount = totalAmount,
                            description = "Penjualan Kasir No. $invoiceNo ($paymentMethod)",
                            referenceId = trxId
                        )
                    )
                } else if (paymentMethod == "KASBON") {
                    // 4. Piutang Kasbon: Uang fisik belum diterima di kas toko,
                    // JANGAN catat kas masuk di BKU untuk mencegah dobel hitung saat dilunasi nanti.
                    debtDao.insertDebt(
                        DebtEntity(
                            customerName = customerName?.ifBlank { "Pelanggan Kasbon" } ?: "Pelanggan Kasbon",
                            customerPhone = customerPhone,
                            totalDebt = totalAmount,
                            paidAmount = 0.0,
                            status = "UNPAID",
                            debtType = "PIUTANG"
                        )
                    )
                }
            }

            // Generate Responsive & Adaptive Receipt
            val receiptText = com.denkasir.utils.ReceiptFormatterUtils.formatTransactionReceipt(
                context = getApplication(),
                transaction = transaction,
                items = trxItems
            )

            clearCart()
            onSuccess(transaction, receiptText)
        }
    }
}
