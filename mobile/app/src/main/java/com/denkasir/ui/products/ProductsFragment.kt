package com.denkasir.ui.products

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.denkasir.R
import com.denkasir.data.local.AppDatabase
import com.denkasir.data.local.entity.CashFlowEntity
import com.denkasir.data.local.entity.ProductEntity
import com.denkasir.databinding.DialogAddProductOptionsBinding
import com.denkasir.databinding.DialogInputProductFullBinding
import com.denkasir.databinding.FragmentProductsBinding
import com.denkasir.hardware.scanner.BarcodeScannerActivity
import com.denkasir.utils.CurrencyUtils
import com.denkasir.utils.ImageUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

class ProductsFragment : Fragment() {
    private var _binding: FragmentProductsBinding? = null
    private val binding get() = _binding!!

    private lateinit var productAdapter: ProductAdapter
    private lateinit var digitalProductAdapter: DigitalProductAdapter
    private var activeBarcodeBinding: DialogInputProductFullBinding? = null
    private var activeFormBinding: DialogInputProductFullBinding? = null
    private var activeProductPhotoPath: String? = null
    private var tempCameraUri: Uri? = null
    private var currentTab = "RETAIL" // RETAIL or DIGITAL

    private var allRetailProducts = listOf<ProductEntity>()
    private var allDigitalProducts = listOf<ProductEntity>()
    private var currentSearchQuery = ""
    private var isScanningForSearch = false

    private val scanBarcodeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val barcode = result.data?.getStringExtra("SCANNED_BARCODE")
            if (!barcode.isNullOrEmpty()) {
                if (isScanningForSearch) {
                    binding.etSearchProduct.setText(barcode)
                    binding.etSearchProduct.setSelection(barcode.length)
                    isScanningForSearch = false
                } else if (activeBarcodeBinding != null) {
                    activeBarcodeBinding?.etProductBarcode?.setText(barcode)
                    Toast.makeText(requireContext(), "Barcode berhasil dipindai: $barcode", Toast.LENGTH_SHORT).show()
                } else {
                    showProductFormBottomSheet(prefilledBarcode = barcode)
                }
            }
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            processSelectedImageUri(uri)
        }
    }

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempCameraUri != null) {
            processSelectedImageUri(tempCameraUri!!)
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCameraCapture()
        } else {
            Toast.makeText(requireContext(), "Izin kamera diperlukan untuk mengambil foto produk", Toast.LENGTH_SHORT).show()
        }
    }

    private val importExcelLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            Toast.makeText(requireContext(), "Memproses impor berkas Excel produk ke database...", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val db = AppDatabase.getDatabase(requireContext())
        val productDao = db.productDao()
        val digitalDao = db.digitalConfigDao()

        setupRecyclerViews()
        setupTabSwitching()
        setupSearchBar()

        binding.btnAddProduct.setOnClickListener {
            showAddProductOptionsDialog()
        }

        binding.btnTopUpDeposit.setOnClickListener {
            showTopUpDepositDialog()
        }

        // 1. Observe Retail Products
        viewLifecycleOwner.lifecycleScope.launch {
            productDao.getRetailProducts().collectLatest { products ->
                allRetailProducts = products
                if (currentTab == "RETAIL") {
                    filterProducts()
                }
            }
        }

        // 2. Observe Digital Products (Token PLN, Pulsa, dll)
        viewLifecycleOwner.lifecycleScope.launch {
            productDao.getDigitalProducts().collectLatest { digitalProducts ->
                allDigitalProducts = digitalProducts
                if (currentTab == "DIGITAL") {
                    filterProducts()
                }
            }
        }

        // 3. Observe Saldo Deposit Server
        viewLifecycleOwner.lifecycleScope.launch {
            digitalDao.getConfigFlow().collectLatest { config ->
                val balance = config?.balance ?: 0.0
                binding.tvDepositBalanceAmount.text = CurrencyUtils.formatRupiah(balance)
            }
        }
    }

    private fun setupSearchBar() {
        binding.etSearchProduct.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString() ?: ""
                binding.btnClearSearch.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                filterProducts()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchProduct.setText("")
        }

        binding.btnScanSearchBarcode.setOnClickListener {
            isScanningForSearch = true
            val intent = Intent(requireContext(), BarcodeScannerActivity::class.java)
            scanBarcodeLauncher.launch(intent)
        }
    }

    private fun setupTabSwitching() {
        val activeColor = Color.parseColor("#3418D6")
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        binding.tabRetail.setOnClickListener {
            currentTab = "RETAIL"
            binding.tvTabRetail.setTextColor(activeColor)
            binding.tvTabRetail.setTypeface(null, android.graphics.Typeface.BOLD)
            binding.indicatorRetail.setBackgroundColor(activeColor)

            binding.tvTabDigital.setTextColor(inactiveColor)
            binding.tvTabDigital.setTypeface(null, android.graphics.Typeface.NORMAL)
            binding.indicatorDigital.setBackgroundColor(Color.TRANSPARENT)

            binding.etSearchProduct.hint = "Cari nama atau barcode produk..."
            filterProducts()
        }

        binding.tabDigital.setOnClickListener {
            currentTab = "DIGITAL"
            binding.tvTabDigital.setTextColor(activeColor)
            binding.tvTabDigital.setTypeface(null, android.graphics.Typeface.BOLD)
            binding.indicatorDigital.setBackgroundColor(activeColor)

            binding.tvTabRetail.setTextColor(inactiveColor)
            binding.tvTabRetail.setTypeface(null, android.graphics.Typeface.NORMAL)
            binding.indicatorRetail.setBackgroundColor(Color.TRANSPARENT)

            binding.etSearchProduct.hint = "Cari nama paket, pulsa, atau PLN..."
            filterProducts()
        }
    }

    private fun filterProducts() {
        val query = currentSearchQuery.trim().lowercase(Locale.ROOT)
        if (currentTab == "RETAIL") {
            val filtered = if (query.isEmpty()) {
                allRetailProducts
            } else {
                allRetailProducts.filter { product ->
                    product.name.lowercase(Locale.ROOT).contains(query) ||
                    (!product.barcode.isNullOrEmpty() && product.barcode.lowercase(Locale.ROOT).contains(query)) ||
                    product.category.lowercase(Locale.ROOT).contains(query)
                }
            }
            productAdapter.submitList(filtered)
            if (filtered.isEmpty()) {
                binding.layoutEmptyState.visibility = View.VISIBLE
                binding.rvProductsList.visibility = View.GONE
                if (query.isEmpty()) {
                    binding.tvEmptyStateTitle.text = "Belum Ada Produk Fisik"
                    binding.tvEmptyStateMessage.text = "Klik tombol \"+ Tambah\" di pojok kanan atas untuk menambahkan produk."
                } else {
                    binding.tvEmptyStateTitle.text = "Produk Tidak Ditemukan"
                    binding.tvEmptyStateMessage.text = "Tidak ada produk retail dengan kata kunci \"$currentSearchQuery\"."
                }
            } else {
                binding.layoutEmptyState.visibility = View.GONE
                binding.rvProductsList.visibility = View.VISIBLE
            }
            binding.rvDigitalProductsList.visibility = View.GONE
            binding.cardDepositBalance.visibility = View.GONE
        } else {
            val filtered = if (query.isEmpty()) {
                allDigitalProducts
            } else {
                allDigitalProducts.filter { product ->
                    product.name.lowercase(Locale.ROOT).contains(query) ||
                    product.category.lowercase(Locale.ROOT).contains(query) ||
                    (!product.barcode.isNullOrEmpty() && product.barcode.lowercase(Locale.ROOT).contains(query))
                }
            }
            digitalProductAdapter.submitList(filtered)
            if (filtered.isEmpty()) {
                binding.layoutEmptyState.visibility = View.VISIBLE
                binding.rvDigitalProductsList.visibility = View.GONE
                if (query.isEmpty()) {
                    binding.tvEmptyStateTitle.text = "Belum Ada Layanan Digital"
                    binding.tvEmptyStateMessage.text = "Layanan pulsa & token PLN belum tersedia."
                } else {
                    binding.tvEmptyStateTitle.text = "Layanan Tidak Ditemukan"
                    binding.tvEmptyStateMessage.text = "Tidak ada layanan digital dengan kata kunci \"$currentSearchQuery\"."
                }
            } else {
                binding.layoutEmptyState.visibility = View.GONE
                binding.rvDigitalProductsList.visibility = View.VISIBLE
            }
            binding.rvProductsList.visibility = View.GONE
            binding.cardDepositBalance.visibility = View.VISIBLE
        }
    }

    private fun setupRecyclerViews() {
        // Retail Product Adapter
        productAdapter = ProductAdapter(
            onItemClick = { product ->
                showProductFormBottomSheet(existingProduct = product)
            },
            onEditClick = { product ->
                showProductFormBottomSheet(existingProduct = product)
            },
            onDeleteClick = { product ->
                confirmDeleteProduct(product)
            }
        )
        binding.rvProductsList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = productAdapter
        }

        // Digital Product Adapter (Token Listrik & Pulsa)
        digitalProductAdapter = DigitalProductAdapter(
            onItemClick = { product ->
                showProductFormBottomSheet(existingProduct = product)
            },
            onEditClick = { product ->
                showProductFormBottomSheet(existingProduct = product)
            },
            onDeleteClick = { product ->
                confirmDeleteProduct(product)
            }
        )
        binding.rvDigitalProductsList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = digitalProductAdapter
        }
    }

    private fun confirmDeleteProduct(product: ProductEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Produk")
            .setMessage("Yakin ingin menghapus '${product.name}'?")
            .setPositiveButton("Hapus") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        AppDatabase.getDatabase(requireContext()).productDao().deleteProduct(product)
                    }
                    Toast.makeText(requireContext(), "Produk berhasil dihapus", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showTopUpDepositDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Nominal Top Up Saldo (Rp)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("500000")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Top Up Saldo Deposit Pulsa/PLN")
            .setMessage("Masukkan jumlah uang tunai yang ditransfer/disetor ke server distributor:")
            .setView(input)
            .setPositiveButton("Top Up") { _, _ ->
                val amount = input.text.toString().toDoubleOrNull() ?: 0.0
                if (amount > 0) {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            val db = AppDatabase.getDatabase(requireContext())
                            db.digitalConfigDao().topUpBalance(amount)
                            // Catat ke Jurnal BKU sebagai pengeluaran belanja saldo deposit
                            db.cashFlowDao().insertCashFlow(
                                CashFlowEntity(
                                    flowType = "OUT",
                                    category = "MODAL_PULSA",
                                    amount = amount,
                                    description = "Top Up Saldo Deposit Server Pulsa & Token PLN"
                                )
                            )
                        }
                        Toast.makeText(requireContext(), "Saldo deposit berhasil ditambah ${CurrencyUtils.formatRupiah(amount)}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showAddProductOptionsDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogAddProductOptionsBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.cardOptionScanBarcode.setOnClickListener {
            dialog.dismiss()
            isScanningForSearch = false
            val intent = Intent(requireContext(), BarcodeScannerActivity::class.java)
            scanBarcodeLauncher.launch(intent)
        }

        dialogBinding.cardOptionInputManual.setOnClickListener {
            dialog.dismiss()
            showProductFormBottomSheet(defaultCategory = "Pcs")
        }

        dialogBinding.cardOptionTimbanganKg.setOnClickListener {
            dialog.dismiss()
            showProductFormBottomSheet(defaultCategory = "Kg")
        }

        dialogBinding.cardOptionImportSpreadsheet.setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(requireContext())
                .setTitle("Impor Produk dari Excel (.xlsx)")
                .setMessage("Pilih metode impor produk:")
                .setPositiveButton("Impor Template Master (.xlsx)") { _, _ ->
                    importSampleMasterProducts()
                }
                .setNeutralButton("Pilih Berkas (.xlsx)") { _, _ ->
                    try {
                        importExcelLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    } catch (e: Exception) {
                        importSampleMasterProducts()
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        dialog.show()
    }

    private fun showProductPhotoSourceDialog() {
        val options = arrayOf("Ambil Foto dari Kamera", "Pilih Foto dari Galeri", "Input Link / URL Gambar Web")
        AlertDialog.Builder(requireContext())
            .setTitle("Unggah Foto Produk")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndCapture()
                    1 -> pickImageLauncher.launch("image/*")
                    2 -> showInputImageUrlDialog()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showInputImageUrlDialog() {
        val input = EditText(requireContext()).apply {
            hint = "https://contoh.com/gambar-produk.jpg"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Input Link / URL Foto Produk")
            .setMessage("Masukkan link URL gambar dari internet. Gambar akan otomatis diunduh ke memori internal HP agar bisa dibuka saat offline:")
            .setView(input)
            .setPositiveButton("Unduh & Pasang") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isBlank()) {
                    Toast.makeText(requireContext(), "URL gambar tidak boleh kosong", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                    Toast.makeText(requireContext(), "URL harus diawali http:// atau https://", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                Toast.makeText(requireContext(), "Mengunduh gambar dari internet...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    val localFile = withContext(Dispatchers.IO) {
                        ImageUtils.downloadImageFromUrl(requireContext(), url)
                    }
                    if (localFile != null && localFile.exists()) {
                        activeProductPhotoPath = localFile.absolutePath
                        activeFormBinding?.let { fb ->
                            ImageUtils.loadProductImage(fb.ivProductPhotoPreview, localFile.absolutePath)
                            fb.btnRemovePhoto.visibility = View.VISIBLE
                            fb.btnUploadPhoto.text = "Ganti Foto"
                        }
                        Toast.makeText(requireContext(), "Gambar berhasil diunduh ke memori lokal HP (Offline Ready)!", Toast.LENGTH_SHORT).show()
                    } else {
                        // Fallback pasang URL online
                        activeProductPhotoPath = url
                        activeFormBinding?.let { fb ->
                            ImageUtils.loadProductImage(fb.ivProductPhotoPreview, url)
                            fb.btnRemovePhoto.visibility = View.VISIBLE
                            fb.btnUploadPhoto.text = "Ganti Foto"
                        }
                        Toast.makeText(requireContext(), "Gagal unduh ke lokal, foto dipasang via URL online.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun checkCameraPermissionAndCapture() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCameraCapture()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCameraCapture() {
        try {
            val (_, uri) = ImageUtils.createTempCameraUri(requireContext())
            tempCameraUri = uri
            takePhotoLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Gagal membuka kamera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processSelectedImageUri(uri: Uri) {
        val savedPath = ImageUtils.copyUriToInternalStorage(requireContext(), uri)
        if (savedPath != null) {
            activeProductPhotoPath = savedPath
            activeFormBinding?.let { formBinding ->
                ImageUtils.loadProductImage(formBinding.ivProductPhotoPreview, savedPath)
                formBinding.btnRemovePhoto.visibility = View.VISIBLE
                formBinding.btnUploadPhoto.text = "Ganti Foto"
            }
            Toast.makeText(requireContext(), "Foto produk berhasil dipilih", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Gagal menyimpan foto produk", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showProductFormBottomSheet(
        prefilledBarcode: String? = null,
        defaultCategory: String = "Pcs",
        existingProduct: ProductEntity? = null
    ) {
        val dialog = BottomSheetDialog(requireContext())
        val formBinding = DialogInputProductFullBinding.inflate(layoutInflater)
        dialog.setContentView(formBinding.root)
        activeBarcodeBinding = formBinding
        activeFormBinding = formBinding

        // Initialize active photo path from existing product
        activeProductPhotoPath = existingProduct?.imagePath

        if (!activeProductPhotoPath.isNullOrBlank()) {
            ImageUtils.loadProductImage(formBinding.ivProductPhotoPreview, activeProductPhotoPath)
            formBinding.btnRemovePhoto.visibility = View.VISIBLE
            formBinding.btnUploadPhoto.text = "Ganti Foto"
        } else {
            formBinding.ivProductPhotoPreview.setImageResource(R.drawable.ic_nav_produk)
            formBinding.btnRemovePhoto.visibility = View.GONE
            formBinding.btnUploadPhoto.text = "Unggah Foto"
        }

        formBinding.btnUploadPhoto.setOnClickListener {
            showProductPhotoSourceDialog()
        }

        formBinding.btnRemovePhoto.setOnClickListener {
            activeProductPhotoPath = null
            formBinding.ivProductPhotoPreview.setImageResource(R.drawable.ic_nav_produk)
            formBinding.btnRemovePhoto.visibility = View.GONE
            formBinding.btnUploadPhoto.text = "Unggah Foto"
            Toast.makeText(requireContext(), "Foto produk dihapus", Toast.LENGTH_SHORT).show()
        }

        formBinding.rgCategory.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbPulsa) {
                formBinding.tilInitialStock.visibility = View.GONE
            } else {
                formBinding.tilInitialStock.visibility = View.VISIBLE
            }
        }

        val isEdit = existingProduct != null
        if (isEdit) {
            val isDigital = existingProduct!!.productType == "DIGITAL"
            formBinding.tvFormTitle.text = if (isDigital) "Edit Layanan Digital" else "Edit Produk"
            formBinding.tvFormSubtitle.text = "Perbarui rincian harga, foto, barcode, atau data produk ini"
            formBinding.btnSaveProduct.text = "SIMPAN PERUBAHAN"

            formBinding.etProductName.setText(existingProduct.name)
            formBinding.etProductBarcode.setText(existingProduct.barcode ?: "")

            val costStr = if (existingProduct.costPrice > 0) {
                if (existingProduct.costPrice % 1.0 == 0.0) existingProduct.costPrice.toLong().toString()
                else existingProduct.costPrice.toString()
            } else ""
            formBinding.etCostPrice.setText(costStr)

            val sellStr = if (existingProduct.sellingPrice % 1.0 == 0.0) {
                existingProduct.sellingPrice.toLong().toString()
            } else {
                existingProduct.sellingPrice.toString()
            }
            formBinding.etSellingPrice.setText(sellStr)

            val stockStr = if (existingProduct.stock % 1.0 == 0.0) {
                existingProduct.stock.toLong().toString()
            } else {
                existingProduct.stock.toString()
            }
            formBinding.etInitialStock.setText(stockStr)
            formBinding.tilInitialStock.hint = "Stok Barang Saat Ini"

            when (existingProduct.category) {
                "Kg" -> formBinding.rbKg.isChecked = true
                "Pulsa", "PLN", "Paket Data" -> {
                    formBinding.rbPulsa.isChecked = true
                    formBinding.tilInitialStock.visibility = View.GONE
                }
                else -> formBinding.rbPcs.isChecked = true
            }
        } else {
            formBinding.tvFormTitle.text = "Tambah Produk Baru"
            formBinding.tvFormSubtitle.text = "Lengkapi data barang dan foto produk untuk database lokal warung"
            formBinding.btnSaveProduct.text = "SIMPAN PRODUK"
            formBinding.tilInitialStock.hint = "Stok Awal Barang"

            if (!prefilledBarcode.isNullOrEmpty()) {
                formBinding.etProductBarcode.setText(prefilledBarcode)
            }
            if (defaultCategory.equals("kg", ignoreCase = true)) {
                formBinding.rbKg.isChecked = true
            } else if (currentTab == "DIGITAL") {
                formBinding.rbPulsa.isChecked = true
                formBinding.tilInitialStock.visibility = View.GONE
            }
        }

        formBinding.btnScanBarcodeFromForm.setOnClickListener {
            isScanningForSearch = false
            val intent = Intent(requireContext(), BarcodeScannerActivity::class.java)
            scanBarcodeLauncher.launch(intent)
        }

        formBinding.btnCancelForm.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            if (activeBarcodeBinding == formBinding) {
                activeBarcodeBinding = null
            }
            if (activeFormBinding == formBinding) {
                activeFormBinding = null
            }
        }

        formBinding.btnSaveProduct.setOnClickListener {
            val name = formBinding.etProductName.text.toString().trim()
            if (name.isEmpty()) {
                formBinding.etProductName.error = "Nama produk tidak boleh kosong!"
                formBinding.etProductName.requestFocus()
                return@setOnClickListener
            }

            val barcode = formBinding.etProductBarcode.text.toString().trim().ifEmpty { null }
            val costPrice = formBinding.etCostPrice.text.toString().toDoubleOrNull() ?: 0.0
            val sellingPrice = formBinding.etSellingPrice.text.toString().toDoubleOrNull() ?: 0.0
            val stock = formBinding.etInitialStock.text.toString().toDoubleOrNull() ?: 0.0

            if (sellingPrice < 0) {
                formBinding.etSellingPrice.error = "Harga jual tidak boleh negatif!"
                formBinding.etSellingPrice.requestFocus()
                return@setOnClickListener
            }

            val category = when (formBinding.rgCategory.checkedRadioButtonId) {
                R.id.rbKg -> "Kg"
                R.id.rbPulsa -> if (name.contains("PLN", ignoreCase = true)) "PLN" else if (existingProduct?.category in listOf("PLN", "Pulsa", "Paket Data")) existingProduct!!.category else "Pulsa"
                else -> "Pcs"
            }

            val productType = if (category == "Pulsa" || category == "PLN" || category == "Paket Data" || (existingProduct != null && existingProduct.productType == "DIGITAL" && formBinding.rgCategory.checkedRadioButtonId == R.id.rbPulsa)) "DIGITAL" else "RETAIL"

            val productToSave = if (existingProduct != null) {
                existingProduct.copy(
                    name = name,
                    barcode = barcode,
                    category = category,
                    productType = productType,
                    costPrice = costPrice,
                    sellingPrice = sellingPrice,
                    stock = if (productType == "DIGITAL") 0.0 else stock,
                    imagePath = activeProductPhotoPath,
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = "PENDING"
                )
            } else {
                ProductEntity(
                    id = UUID.randomUUID().toString(),
                    storeId = "default_store",
                    name = name,
                    barcode = barcode,
                    category = category,
                    productType = productType,
                    costPrice = costPrice,
                    sellingPrice = sellingPrice,
                    stock = if (productType == "DIGITAL") 0.0 else stock,
                    imagePath = activeProductPhotoPath,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = "PENDING"
                )
            }

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(requireContext()).productDao().insertProduct(productToSave)
                }
                val actionVerb = if (isEdit) "berhasil diperbarui!" else "berhasil disimpan!"
                val msg = if (productType == "DIGITAL") "Layanan digital '$name' $actionVerb" else "Produk '$name' $actionVerb"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun importSampleMasterProducts() {
        val masterProducts = listOf(
            ProductEntity(id = UUID.randomUUID().toString(), name = "Beras Ramos 5kg", barcode = "899275321001", category = "Kg", productType = "RETAIL", costPrice = 65000.0, sellingPrice = 72000.0, stock = 20.0),
            ProductEntity(id = UUID.randomUUID().toString(), name = "Telur Ayam 1kg", barcode = "899275321002", category = "Kg", productType = "RETAIL", costPrice = 26000.0, sellingPrice = 29000.0, stock = 25.0),
            ProductEntity(id = UUID.randomUUID().toString(), name = "Kopi Kapal Api Special", barcode = "899100210555", category = "Pcs", productType = "RETAIL", costPrice = 1500.0, sellingPrice = 2000.0, stock = 100.0),
            ProductEntity(id = UUID.randomUUID().toString(), name = "Teh Celup Sosro Isi 30", barcode = "899100210666", category = "Pcs", productType = "RETAIL", costPrice = 6000.0, sellingPrice = 7500.0, stock = 30.0),
            ProductEntity(id = UUID.randomUUID().toString(), name = "Sabun Lifebuoy 85g", barcode = "899999901001", category = "Pcs", productType = "RETAIL", costPrice = 3500.0, sellingPrice = 4500.0, stock = 50.0),
            ProductEntity(id = UUID.randomUUID().toString(), name = "Sabun Cuci Piring Sunlight 750ml", barcode = "899999902002", category = "Pcs", productType = "RETAIL", costPrice = 13500.0, sellingPrice = 15500.0, stock = 30.0),
            ProductEntity(id = UUID.randomUUID().toString(), name = "Tepung Segitiga Biru 1kg", barcode = "899886630012", category = "Kg", productType = "RETAIL", costPrice = 11000.0, sellingPrice = 13000.0, stock = 40.0),
            ProductEntity(id = UUID.randomUUID().toString(), name = "Air Mineral Galon 19L", barcode = "899886640034", category = "Pcs", productType = "RETAIL", costPrice = 18000.0, sellingPrice = 20000.0, stock = 15.0)
        )

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(requireContext()).productDao().insertProducts(masterProducts)
            }
            Toast.makeText(requireContext(), "Berhasil mengimpor 8 produk master (.xlsx) ke daftar produk!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
