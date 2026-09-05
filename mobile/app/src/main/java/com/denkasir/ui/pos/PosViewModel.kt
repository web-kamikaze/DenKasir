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
        // Populate default cart items matching the user's screenshot
        viewModelScope.launch {
            val defaultItems = listOf(
                CartItem(
                    product = ProductEntity(
                        id = "indomie_1",
                        name = "Indomie Goreng",
                        category = "Pcs",
                        productType = "RETAIL",
                        sellingPrice = 3000.0,
                        costPrice = 2500.0,
                        stock = 50.0
                    ),
                    quantity = 2.0
                ),
                CartItem(
                    product = ProductEntity(
                        id = "gula_1",
                        name = "Gula Pasir 1kg",
                        category = "Kg",
                        productType = "RETAIL",
                        sellingPrice = 13000.0,
                        costPrice = 11500.0,
                        stock = 30.0
                    ),
                    quantity = 1.0
                ),
                CartItem(
                    product = ProductEntity(
                        id = "minyak_1",
                        name = "Minyak Goreng 1L",
                        category = "Pcs",
                        productType = "RETAIL",
                        sellingPrice = 15500.0,
                        costPrice = 14000.0,
                        stock = 25.0
                    ),
                    quantity = 1.0
                ),
                CartItem(
                    product = ProductEntity(
                        id = "susu_1",
                        name = "Susu Kental Manis",
                        category = "Pcs",
                        productType = "RETAIL",
                        sellingPrice = 9000.0,
                        costPrice = 7500.0,
                        stock = 40.0
                    ),
                    quantity = 1.0
                )
            )
            _cartItems.value = defaultItems
        }
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

    fun addScannedBarcode(barcode: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val product = withContext(Dispatchers.IO) {
                productDao.getProductByBarcode(barcode)
            }
            if (product != null) {
                addToCart(product, 1.0)
                onResult(true, "Ditambahkan: ${product.name}")
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

            val transaction = TransactionEntity(
                id = trxId,
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

            // Generate Plain Text Receipt
            val session = SessionManager.getInstance(getApplication())
            val storeName = session.getStoreName()
            val storeAddress = session.getStoreAddress()

            val receiptBuilder = StringBuilder()
            receiptBuilder.append("================================\n")
            receiptBuilder.append("         $storeName         \n")
            receiptBuilder.append("     $storeAddress     \n")
            receiptBuilder.append("================================\n")
            receiptBuilder.append("No    : $invoiceNo\n")
            receiptBuilder.append("Waktu : ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}\n")
            receiptBuilder.append("Metode: $paymentMethod\n")
            receiptBuilder.append("--------------------------------\n")
            currentCart.forEach { item ->
                val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
                receiptBuilder.append("${item.product.name}\n")
                if (item.targetPhone != null) {
                    receiptBuilder.append("  No: ${item.targetPhone}\n")
                }
                if (item.snNumber != null) {
                    receiptBuilder.append("  SN: ${item.snNumber}\n")
                }
                receiptBuilder.append("  $qtyStr x ${CurrencyUtils.formatNumberOnly(item.product.sellingPrice)} = ${CurrencyUtils.formatNumberOnly(item.subtotal)}\n")
            }
            receiptBuilder.append("--------------------------------\n")
            receiptBuilder.append("TOTAL BAYAR : ${CurrencyUtils.formatRupiah(totalAmount)}\n")
            if (paymentMethod == "CASH") {
                receiptBuilder.append("BAYAR TUNAI : ${CurrencyUtils.formatRupiah(cashGiven)}\n")
                receiptBuilder.append("KEMBALIAN   : ${CurrencyUtils.formatRupiah(change)}\n")
            }
            receiptBuilder.append("================================\n")
            receiptBuilder.append("  Terima Kasih Atas Kunjungan  \n")
            receiptBuilder.append("          Anda di Toko         \n")
            receiptBuilder.append("================================\n")

            clearCart()
            onSuccess(transaction, receiptBuilder.toString())
        }
    }
}
