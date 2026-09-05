package com.denkasir.ui.products

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
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

    private val scanBarcodeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val barcode = result.data?.getStringExtra("SCANNED_BARCODE")
            if (!barcode.isNullOrEmpty()) {
                if (activeBarcodeBinding != null) {
                    activeBarcodeBinding?.etProductBarcode?.setText(barcode)
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

        binding.btnAddProduct.setOnClickListener {
            showAddProductOptionsDialog()
        }

        binding.btnTopUpDeposit.setOnClickListener {
            showTopUpDepositDialog()
        }

        // 1. Observe Retail Products
        viewLifecycleOwner.lifecycleScope.launch {
            productDao.getRetailProducts().collectLatest { products ->
                productAdapter.submitList(products)
            }
        }

        // 2. Observe Digital Products (Token PLN, Pulsa, dll)
        viewLifecycleOwner.lifecycleScope.launch {
            productDao.getDigitalProducts().collectLatest { digitalProducts ->
                digitalProductAdapter.submitList(digitalProducts)
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

            binding.cardDepositBalance.visibility = View.GONE
            binding.rvProductsList.visibility = View.VISIBLE
            binding.rvDigitalProductsList.visibility = View.GONE
        }

        binding.tabDigital.setOnClickListener {
            currentTab = "DIGITAL"
            binding.tvTabDigital.setTextColor(activeColor)
            binding.tvTabDigital.setTypeface(null, android.graphics.Typeface.BOLD)
            binding.indicatorDigital.setBackgroundColor(activeColor)

            binding.tvTabRetail.setTextColor(inactiveColor)
            binding.tvTabRetail.setTypeface(null, android.graphics.Typeface.NORMAL)
            binding.indicatorRetail.setBackgroundColor(Color.TRANSPARENT)

            binding.cardDepositBalance.visibility = View.VISIBLE
            binding.rvProductsList.visibility = View.GONE
            binding.rvDigitalProductsList.visibility = View.VISIBLE
        }
    }

    private fun setupRecyclerViews() {
        // Retail Product Adapter
        productAdapter = ProductAdapter(
            onItemClick = { product ->
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
                    Toast.makeText(requireContext(), "Produk dihapus", Toast.LENGTH_SHORT).show()
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
        val options = arrayOf("Ambil Foto dari Kamera", "Pilih Foto dari Galeri")
        AlertDialog.Builder(requireContext())
            .setTitle("Unggah Foto Produk")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndCapture()
                    1 -> pickImageLauncher.launch("image/*")
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

        if (existingProduct != null) {
            formBinding.tvFormTitle.text = "Edit Produk"
            formBinding.etProductName.setText(existingProduct.name)
            formBinding.etProductBarcode.setText(existingProduct.barcode ?: "")
            formBinding.etCostPrice.setText(if (existingProduct.costPrice > 0) existingProduct.costPrice.toInt().toString() else "")
            formBinding.etSellingPrice.setText(existingProduct.sellingPrice.toInt().toString())
            formBinding.etInitialStock.setText(
                if (existingProduct.stock % 1.0 == 0.0) existingProduct.stock.toInt().toString()
                else existingProduct.stock.toString()
            )

            when (existingProduct.category) {
                "Kg" -> formBinding.rbKg.isChecked = true
                "Pulsa", "PLN", "Paket Data" -> formBinding.rbPulsa.isChecked = true
                else -> formBinding.rbPcs.isChecked = true
            }
        } else {
            formBinding.tvFormTitle.text = "Tambah Produk Baru"
            if (!prefilledBarcode.isNullOrEmpty()) {
                formBinding.etProductBarcode.setText(prefilledBarcode)
            }
            if (defaultCategory.equals("kg", ignoreCase = true)) {
                formBinding.rbKg.isChecked = true
            } else if (currentTab == "DIGITAL") {
                formBinding.rbPulsa.isChecked = true
            }
        }

        formBinding.btnScanBarcodeFromForm.setOnClickListener {
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
                Toast.makeText(requireContext(), "Nama produk tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val barcode = formBinding.etProductBarcode.text.toString().trim().ifEmpty { null }
            val costPrice = formBinding.etCostPrice.text.toString().toDoubleOrNull() ?: 0.0
            val sellingPrice = formBinding.etSellingPrice.text.toString().toDoubleOrNull() ?: 0.0
            val stock = formBinding.etInitialStock.text.toString().toDoubleOrNull() ?: 0.0

            val category = when (formBinding.rgCategory.checkedRadioButtonId) {
                R.id.rbKg -> "Kg"
                R.id.rbPulsa -> if (name.contains("PLN", ignoreCase = true)) "PLN" else "Pulsa"
                else -> "Pcs"
            }

            val productType = if (category == "Pulsa" || category == "PLN" || category == "Paket Data") "DIGITAL" else "RETAIL"

            val productToSave = ProductEntity(
                id = existingProduct?.id ?: UUID.randomUUID().toString(),
                name = name,
                barcode = barcode,
                category = category,
                productType = productType,
                costPrice = costPrice,
                sellingPrice = sellingPrice,
                stock = if (productType == "DIGITAL") 0.0 else stock, // Produk digital tidak memakai stok fisik
                imagePath = activeProductPhotoPath
            )

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(requireContext()).productDao().insertProduct(productToSave)
                }
                val msg = if (productType == "DIGITAL") "Layanan digital '$name' (Potong Saldo) berhasil disimpan!" else "Produk '$name' berhasil disimpan!"
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
