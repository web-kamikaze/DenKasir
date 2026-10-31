package com.denkasir.ui.stockopname

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.denkasir.R
import com.denkasir.data.local.AppDatabase
import com.denkasir.data.local.entity.ProductEntity
import com.denkasir.data.local.entity.PurchaseOrderEntity
import com.denkasir.data.local.entity.StockOpnameEntity
import com.denkasir.databinding.ActivityStockOpnameBinding
import com.denkasir.hardware.scanner.BarcodeScannerActivity
import com.denkasir.utils.CurrencyUtils
import com.denkasir.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class StockOpnameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStockOpnameBinding
    private lateinit var adapter: StockOpnameAdapter
    private lateinit var appDatabase: AppDatabase

    private var allItems = mutableListOf<OpnameItem>()
    private var realProducts = mutableListOf<ProductEntity>()
    private var activeFilter = "ALL" // ALL, SELISIH, COCOK

    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val barcode = result.data?.getStringExtra("SCANNED_BARCODE")
            if (!barcode.isNullOrEmpty()) {
                val matched = allItems.find { item ->
                    val prod = realProducts.find { it.id == item.id }
                    prod?.barcode == barcode
                }
                if (matched != null) {
                    showEditPhysicalStockDialog(matched)
                } else {
                    Toast.makeText(this, "Produk dengan barcode $barcode tidak ditemukan", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockOpnameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appDatabase = AppDatabase.getDatabase(this)

        setupRecyclerView()
        loadRealProducts()
        setupListeners()
    }

    private fun loadRealProducts() {
        lifecycleScope.launch {
            val products = withContext(Dispatchers.IO) {
                appDatabase.productDao().getRetailProducts().first()
            }
            realProducts.clear()
            realProducts.addAll(products)

            allItems = products.map { p ->
                OpnameItem(
                    id = p.id,
                    name = p.name,
                    systemStock = p.stock.toInt(),
                    physicalStock = p.stock.toInt(),
                    imagePath = p.imagePath
                )
            }.toMutableList()

            // If empty, add fallback sample
            if (allItems.isEmpty()) {
                allItems = mutableListOf(
                    OpnameItem(id = "1", name = "Indomie Goreng", systemStock = 20, physicalStock = 20),
                    OpnameItem(id = "2", name = "Gula Pasir 1kg", systemStock = 15, physicalStock = 14),
                    OpnameItem(id = "3", name = "Minyak Goreng 1L", systemStock = 10, physicalStock = 10),
                    OpnameItem(id = "4", name = "Susu Kental Manis", systemStock = 15, physicalStock = 16)
                )
            }

            updateMetrics()
            applyFilter()
        }
    }

    private fun setupRecyclerView() {
        adapter = StockOpnameAdapter { item ->
            showEditPhysicalStockDialog(item)
        }
        binding.rvOpnameItems.layoutManager = LinearLayoutManager(this)
        binding.rvOpnameItems.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnScanOpname.setOnClickListener {
            val intent = Intent(this, BarcodeScannerActivity::class.java)
            scanLauncher.launch(intent)
        }



        binding.chipSemua.setOnClickListener {
            activeFilter = "ALL"
            updateChipStyles()
            applyFilter()
        }

        binding.chipSelisih.setOnClickListener {
            activeFilter = "SELISIH"
            updateChipStyles()
            applyFilter()
        }

        binding.chipCocok.setOnClickListener {
            activeFilter = "COCOK"
            updateChipStyles()
            applyFilter()
        }

        binding.etSearchOpname.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSelesaikanOpname.setOnClickListener {
            finishStockOpnameAndGenerateDraft()
        }
    }

    private fun updateChipStyles() {
        val selectedBg = ContextCompat.getDrawable(this, R.drawable.bg_chip_selected)
        val unselectedBg = ContextCompat.getDrawable(this, R.drawable.bg_chip_unselected)
        val selectedTextColor = Color.WHITE
        val unselectedTextColor = ContextCompat.getColor(this, R.color.text_secondary)

        binding.chipSemua.background = if (activeFilter == "ALL") selectedBg else unselectedBg
        binding.chipSemua.setTextColor(if (activeFilter == "ALL") selectedTextColor else unselectedTextColor)

        binding.chipSelisih.background = if (activeFilter == "SELISIH") selectedBg else unselectedBg
        binding.chipSelisih.setTextColor(if (activeFilter == "SELISIH") selectedTextColor else unselectedTextColor)

        binding.chipCocok.background = if (activeFilter == "COCOK") selectedBg else unselectedBg
        binding.chipCocok.setTextColor(if (activeFilter == "COCOK") selectedTextColor else unselectedTextColor)
    }

    private fun applyFilter(query: String = binding.etSearchOpname.text.toString()) {
        val filtered = allItems.filter { item ->
            val matchQuery = item.name.contains(query, ignoreCase = true)
            val matchCategory = when (activeFilter) {
                "SELISIH" -> !item.isMatched
                "COCOK" -> item.isMatched
                else -> true
            }
            matchQuery && matchCategory
        }
        adapter.submitList(filtered)
        val nf = java.text.NumberFormat.getNumberInstance(java.util.Locale("in", "ID")); binding.tvDiperiksaCount.text = "${nf.format(filtered.size)} produk"
    }

    private fun updateMetrics() {
        val total = allItems.size
        val cocok = allItems.count { it.isMatched }
        val selisih = allItems.count { !it.isMatched }
        val progres = if (total > 0) ((total - selisih) * 100 / total) else 100

        val nf = java.text.NumberFormat.getNumberInstance(java.util.Locale("in", "ID")); binding.tvTotalProdukOpname.text = nf.format(total)
        binding.tvCocokOpname.text = nf.format(cocok)
        binding.tvSelisihOpname.text = nf.format(selisih)
        binding.tvProgresOpname.text = "$progres%"
        binding.progressOpname.progress = progres

        binding.chipSemua.text = "Semua (${nf.format(total)})"
        binding.chipSelisih.text = "Selisih ($selisih)"
        binding.chipCocok.text = "Cocok ($cocok)"
    }

    private fun showEditPhysicalStockDialog(item: OpnameItem) {
        val input = EditText(this).apply {
            setText(item.physicalStock.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle(item.name)
            .setMessage("Masukkan Jumlah Stok Fisik Riil (Stok Sistem: ${item.systemStock})")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val newPhysical = input.text.toString().toIntOrNull() ?: item.physicalStock
                item.physicalStock = newPhysical
                updateMetrics()
                applyFilter()
                Toast.makeText(this, "Stok fisik diperbarui: $newPhysical", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // =========================================================================
    // SELESAIKAN STOK OPNAME & OTOMATIS GENERATE DRAFT BELANJA DI TRANSAKSI
    // =========================================================================
    private fun finishStockOpnameAndGenerateDraft() {
        val session = SessionManager.getInstance(this)
        val minThreshold = session.getMinStockThreshold()
        val isAutoDraft = session.isAutoDraftEnabled()
        val defaultSupplier = session.getDefaultSupplier()
        val reorderQty = session.getAutoReorderQty()

        lifecycleScope.launch(Dispatchers.IO) {
            // 1. Update Real Product Stock in Database
            allItems.forEach { opItem ->
                val matchingProduct = realProducts.find { it.id == opItem.id }
                if (matchingProduct != null) {
                    val updatedProduct = matchingProduct.copy(
                        stock = opItem.physicalStock.toDouble(),
                        updatedAt = System.currentTimeMillis()
                    )
                    appDatabase.productDao().updateProduct(updatedProduct)

                    // Log Stock Opname Difference
                    val diff = (opItem.physicalStock - opItem.systemStock).toDouble()
                    appDatabase.stockOpnameDao().insertStockOpname(
                        StockOpnameEntity(
                            id = UUID.randomUUID().toString(),
                            productId = matchingProduct.id,
                            systemStock = opItem.systemStock.toDouble(),
                            physicalStock = opItem.physicalStock.toDouble(),
                            difference = diff,
                            reason = if (diff == 0.0) "Cocok" else if (diff < 0) "Selisih Kurang / Hilang" else "Selisih Lebih"
                        )
                    )
                }
            }

            // 2. Evaluasi Aturan Owner: Cari produk yang stoknya mulai kosong (<= threshold)
            val lowStockItems = allItems.filter { it.physicalStock.toDouble() <= minThreshold }

            var createdDraftPO: PurchaseOrderEntity? = null

            if (lowStockItems.isNotEmpty() && isAutoDraft) {
                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("in", "ID"))
                val dateStr = dateFormat.format(Date())

                var totalEstCost = 0.0
                lowStockItems.forEach { item ->
                    val prod = realProducts.find { it.id == item.id }
                    val cost = prod?.costPrice ?: 10000.0
                    totalEstCost += cost * reorderQty
                }

                createdDraftPO = PurchaseOrderEntity(
                    id = UUID.randomUUID().toString(),
                    supplierName = "$defaultSupplier (Auto Restock)",
                    dateText = dateStr,
                    itemCount = lowStockItems.size,
                    totalAmount = totalEstCost,
                    status = "Draft",
                    createdAt = System.currentTimeMillis()
                )

                appDatabase.purchaseOrderDao().insertPurchase(createdDraftPO)
            }

            withContext(Dispatchers.Main) {
                if (createdDraftPO != null) {
                    val summaryList = lowStockItems.joinToString("\n") { "• ${it.name} (Sisa: ${it.physicalStock} unit)" }
                    AlertDialog.Builder(this@StockOpnameActivity)
                        .setTitle("Stokopname Selesai & Draft Dibuat!")
                        .setMessage("Stok fisik berhasil disinkronisasi ke database.\n\nDitemukan ${lowStockItems.size} produk yang stoknya menipis (<= ${minThreshold.toInt()} unit):\n$summaryList\n\nSistem otomatis membuatkan 'Daftar Belanja (Draft)' di menu Transaksi senilai ${CurrencyUtils.formatRupiah(createdDraftPO.totalAmount)}.")
                        .setPositiveButton("Lihat di Transaksi") { _, _ ->
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    Toast.makeText(this@StockOpnameActivity, "Stokopname Selesai! Semua stok cocok dan aman.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }
}
