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

    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Scan")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    val allProducts = productDao.getAllProducts()

    init {
        // Keranjang kasir mulai bersih (kosong) saat aplikasi baru dijalankan
        _cartItems.value = emptyList()
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun addToCart(product: ProductEntity, qty: Double = 1.0, phone: String? = null) {
        val current = _cartItems.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.product.id == product.id && it.targetPhone == phone }
        if (existingIndex >= 0) {
            val item = current[existingIndex]
            current[existingIndex] = item.copy(quantity = item.quantity + qty)
        } else {
            current.add(CartItem(product = product, quantity = qty, targetPhone = phone))
        }
        _cartItems.value = current
    }

    fun updateQuantity(cartItem: CartItem, newQty: Double) {
        val current = _cartItems.value.toMutableList()
        val index = current.indexOfFirst { it.product.id == cartItem.product.id && it.targetPhone == cartItem.targetPhone }
        if (index >= 0) {
            if (newQty > 0) {
                current[index] = cartItem.copy(quantity = newQty)
            } else {
                current.removeAt(index)
            }
            _cartItems.value = current
        }
    }

    fun removeItem(cartItem: CartItem) {
        val current = _cartItems.value.toMutableList()
        current.removeAll { it.product.id == cartItem.product.id && it.targetPhone == cartItem.targetPhone }
        _cartItems.value = current
    }

    fun clearCart() {
        _cartItems.value = emptyList()
    }

    fun getTotalItemCount(): Double {
        return _cartItems.value.sumOf { it.quantity }
    }

    fun getTotalBayar(): Double {
        return _cartItems.value.sumOf { it.subtotal }
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
        onSuccess: (TransactionEntity, String) -> Unit
    ) {
        viewModelScope.launch {
            val currentCart = _cartItems.value
            if (currentCart.isEmpty()) return@launch

            val totalAmount = getTotalBayar()
            val change = if (paymentMethod == "CASH") maxOf(0.0, cashGiven - totalAmount) else 0.0
            val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
            val invoiceNo = "TRX-${dateFormat.format(Date())}"
            val trxId = UUID.randomUUID().toString()

            val session = SessionManager.getInstance(getApplication())
            val currentUserId = session.getCurrentUserId()
            val currentCashierName = session.getCurrentFullName()

            val transaction = TransactionEntity(
                id = trxId,
                userId = currentUserId,
                cashierName = currentCashierName,
                invoiceNumber = invoiceNo,
                totalAmount = totalAmount,
                cashGiven = cashGiven,
                changeAmount = change,
                paymentMethod = paymentMethod,
                customerName = customerName
            )

            val trxItems = currentCart.map { item ->
                val sn = if (item.product.productType == "DIGITAL") {
                    "SN${System.currentTimeMillis().toString().takeLast(10)}"
                } else null

                TransactionItemEntity(
                    transactionId = trxId,
                    productId = item.product.id,
                    productName = item.product.name,
                    quantity = item.quantity,
                    costPrice = item.product.costPrice,
                    sellingPrice = item.product.sellingPrice,
                    subtotal = item.subtotal,
                    targetPhoneNumber = item.targetPhone,
                    snNumber = sn
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
                cashFlowDao.insertCashFlow(
                    CashFlowEntity(
                        flowType = "IN",
                        category = "PENJUALAN",
                        amount = totalAmount,
                        description = "Penjualan Kasir No. $invoiceNo (${paymentMethod})",
                        referenceId = trxId
                    )
                )

                // 4. If Kasbon / Hutang
                if (paymentMethod == "KASBON") {
                    debtDao.insertDebt(
                        DebtEntity(
                            customerName = customerName ?: "Pelanggan Warung",
                            totalDebt = totalAmount,
                            paidAmount = 0.0
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
