package com.denkasir.ui.pos

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.denkasir.R
import com.denkasir.data.local.entity.DigitalConfigEntity
import com.denkasir.data.local.entity.ProductEntity
import com.denkasir.data.local.entity.isDigitalOutOfStock
import com.denkasir.utils.SmsGatewayHelper
import com.denkasir.ui.MainActivity
import com.denkasir.databinding.DialogPaymentMethodBinding
import com.denkasir.databinding.DialogPulsaInputBinding
import com.denkasir.databinding.FragmentPosBinding
import com.denkasir.hardware.scanner.BarcodeScannerActivity
import com.denkasir.data.local.AppDatabase
import com.denkasir.utils.CurrencyUtils
import com.denkasir.utils.SmartSearchEngine
import com.denkasir.utils.SessionManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

private data class CustomOperatorTheme(
    val brandText: String,
    val iconRes: Int,
    val bgColor: String,
    val strokeColor: String,
    val textColor: String
)

class PosFragment : Fragment() {

    private var _binding: FragmentPosBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PosViewModel by viewModels()
    private lateinit var cartAdapter: CartAdapter
    private var availableProducts: List<ProductEntity> = emptyList()
    private lateinit var smartSearchAdapter: SmartProductSearchAdapter
    private var digitalConfig: DigitalConfigEntity? = null
    private lateinit var sessionManager: SessionManager

    private var onContactPickedCallback: ((String) -> Unit)? = null

    private val barcodeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val isCashierModeAdded = result.data?.getBooleanExtra(BarcodeScannerActivity.CASHIER_MODE_ADDED, false) ?: false
            if (isCashierModeAdded) {
                val count = result.data?.getIntExtra(BarcodeScannerActivity.TOTAL_SCANNED_COUNT, 0) ?: 0
                if (count > 0) {
                    Toast.makeText(requireContext(), "$count produk masuk ke keranjang", Toast.LENGTH_SHORT).show()
                }
            } else {
                val barcode = result.data?.getStringExtra(BarcodeScannerActivity.SCANNED_BARCODE)
                val qty = result.data?.getDoubleExtra(BarcodeScannerActivity.SCANNED_QTY, 1.0) ?: 1.0
                if (!barcode.isNullOrEmpty()) {
                    viewModel.addScannedBarcode(barcode, qty) { _, message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri: Uri? = result.data?.data
            if (contactUri != null) {
                try {
                    val cursor = requireContext().contentResolver.query(
                        contactUri,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        null,
                        null,
                        null
                    )
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val rawNumber = it.getString(0)
                            val cleanNumber = sanitizePhoneNumber(rawNumber)
                            onContactPickedCallback?.invoke(cleanNumber)
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Gagal membaca kontak: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager.getInstance(requireContext())

        setupRecyclerView()
        setupCategoryTabs()
        setupSearchAndBarcode()
        setupBottomActions()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        cartAdapter = CartAdapter(
            onQuantityChanged = { item, newQty ->
                viewModel.updateQuantity(item, newQty)
            },
            onItemRemoved = { item ->
                viewModel.removeItem(item)
            }
        )
        binding.rvCartProducts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = cartAdapter
        }
    }

    private fun setupCategoryTabs() {
        binding.tabScan.setOnClickListener {
            selectCategoryTab("Scan")
            launchBarcodeScanner()
        }
        binding.tabPcs.setOnClickListener {
            selectCategoryTab("Pcs")
            showCategoryProductsDialog("Pcs")
        }
        binding.tabKg.setOnClickListener {
            selectCategoryTab("Kg")
            showCategoryProductsDialog("Kg")
        }
        binding.tabPulsa.setOnClickListener {
            selectCategoryTab("Pulsa")
            showDigitalServicesBottomSheet()
        }
    }

    private fun selectCategoryTab(category: String) {
        viewModel.setCategory(category)
        val purpleBg = ContextCompat.getColor(requireContext(), R.color.primary_purple_bg)
        val whiteBg = Color.WHITE

        binding.tabScan.setCardBackgroundColor(if (category == "Scan") purpleBg else whiteBg)
        binding.tabPcs.setCardBackgroundColor(if (category == "Pcs") purpleBg else whiteBg)
        binding.tabKg.setCardBackgroundColor(if (category == "Kg") purpleBg else whiteBg)
        binding.tabPulsa.setCardBackgroundColor(if (category == "Pulsa") purpleBg else whiteBg)
    }

    private fun setupSearchAndBarcode() {
        smartSearchAdapter = SmartProductSearchAdapter(requireContext())
        binding.etSearchProduct.threshold = 1
        binding.etSearchProduct.setAdapter(smartSearchAdapter)

        binding.btnScanBarcode.setOnClickListener {
            launchBarcodeScanner()
        }

        binding.btnHeaderQr.setOnClickListener {
            launchBarcodeScanner()
        }

        binding.etSearchProduct.setOnItemClickListener { _, _, position, _ ->
            val product = smartSearchAdapter.getItem(position)
            if (product != null) {
                if (product.productType == "DIGITAL") {
                    showDigitalServicesBottomSheet(preselected = product)
                } else {
                    viewModel.addToCart(product, 1.0)
                }
                binding.etSearchProduct.setText("")
            }
        }

        binding.etSearchProduct.setOnEditorActionListener { v, _, _ ->
            val query = v.text.toString().trim()
            if (query.isNotEmpty()) {
                val exactBarcode = availableProducts.find { it.barcode.equals(query, ignoreCase = true) }
                val filtered = SmartSearchEngine.filterProducts(query, availableProducts)
                val target = exactBarcode ?: filtered.firstOrNull()
                if (target != null) {
                    if (target.productType == "DIGITAL") {
                        showDigitalServicesBottomSheet(preselected = target)
                    } else {
                        viewModel.addToCart(target, 1.0)
                        Toast.makeText(requireContext(), "Ditambahkan: ${target.name}", Toast.LENGTH_SHORT).show()
                    }
                    binding.etSearchProduct.setText("")
                    return@setOnEditorActionListener true
                }
            }
            false
        }
    }

    private fun launchBarcodeScanner() {
        val intent = Intent(requireContext(), BarcodeScannerActivity::class.java).apply {
            putExtra(BarcodeScannerActivity.EXTRA_SCAN_MODE, BarcodeScannerActivity.MODE_CASHIER)
        }
        barcodeLauncher.launch(intent)
    }

    private fun setupBottomActions() {
        binding.btnBatal.setOnClickListener {
            if (viewModel.cartItems.value.isNotEmpty()) {
                viewModel.clearCart()
                Toast.makeText(requireContext(), "Keranjang kasir dikosongkan", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBayar.setOnClickListener {
            if (viewModel.cartItems.value.isEmpty()) {
                Toast.makeText(requireContext(), "Keranjang belanja kosong", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val digitalInCart = CartManager.getCartItemsSnapshot().filter { it.product.productType == "DIGITAL" }
            if (digitalInCart.isNotEmpty()) {
                if (!isDigitalGatewayReady()) {
                    showGatewayRequiredDialog()
                    return@setOnClickListener
                }
                val emptyStock = digitalInCart.find { it.product.isDigitalOutOfStock() }
                if (emptyStock != null) {
                    showDigitalOutOfStockPaymentDialog(emptyStock.product.name)
                    return@setOnClickListener
                }
            }
            showPaymentBottomSheet()
        }
    }


    private fun isDigitalGatewayReady(): Boolean {
        return SmsGatewayHelper.isGatewayConfigured(digitalConfig)
    }

    private fun showGatewayRequiredDialog() {
        val isOwner = sessionManager.isOwner()
        val builder = AlertDialog.Builder(requireContext())
            .setTitle("Jalur Gateway Belum Diatur")
            .setMessage(
                "Untuk melakukan transaksi produk digital (Pulsa, Paket Data, PLN, PDAM, dll), " +
                "minimal salah satu jalur pengiriman harus dikonfigurasi terlebih dahulu:\n\n" +
                "1. API H2H Server (Jalur Internet - Endpoint & API Key)\n" +
                "2. SMS Center OtomaX (Jalur SMS Seluler - Nomor Center & PIN)\n\n" +
                if (isOwner) {
                    "Sebagai OWNER, silakan atur kredensial server pada menu Pengaturan H2H & SMS Center di tab Akun."
                } else {
                    "Silakan hubungi OWNER toko untuk mengisi konfigurasi H2H atau SMS Center di menu Akun."
                }
            )
            .setIcon(R.drawable.ic_info_circle)
            .setNegativeButton("Tutup", null)

        if (isOwner) {
            builder.setPositiveButton("Buka Menu Akun") { dialog, _ ->
                dialog.dismiss()
                (activity as? MainActivity)?.navigateToAccount()
            }
        } else {
            builder.setPositiveButton("Mengerti", null)
        }
        builder.show()
    }

    private fun showDigitalOutOfStockPaymentDialog(productName: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Pembayaran Ditolak")
            .setMessage("Produk digital '$productName' saat ini dalam kondisi STOK KOSONG / GANGGUAN distributor.\n\nSilakan hapus produk ini dari keranjang belanja sebelum melanjutkan pembayaran.")
            .setIcon(R.drawable.ic_info_circle)
            .setPositiveButton("Mengerti", null)
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            AppDatabase.getDatabase(requireContext()).digitalConfigDao().getConfigFlow().collectLatest { cfg ->
                digitalConfig = cfg
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            AppDatabase.getDatabase(requireContext()).storeDao().getStoreFlow().collectLatest { store ->
                binding.tvStoreSub.text = store?.name?.uppercase() ?: SessionManager.getInstance(requireContext()).getStoreName().uppercase()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.cartItems.collectLatest { items ->
                cartAdapter.submitList(items)

                val totalCount = items.sumOf { it.quantity }
                val countText = if (totalCount % 1.0 == 0.0) totalCount.toInt().toString() else String.format("%.1f", totalCount)
                binding.tvTotalItemCount.text = countText

                val totalBayar = items.sumOf { it.subtotal }
                binding.tvTotalBayarAmount.text = CurrencyUtils.formatRupiah(totalBayar)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allProducts.collectLatest { products ->
                availableProducts = products
                withContext(Dispatchers.Default) {
                    SmartSearchEngine.preIndex(products)
                }
                smartSearchAdapter.updateProducts(products)
            }
        }
    }

    private fun showCategoryProductsDialog(category: String) {
        val items = availableProducts.filter { it.category.equals(category, ignoreCase = true) && it.productType == "RETAIL" }
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), "Tidak ada produk kategori $category", Toast.LENGTH_SHORT).show()
            return
        }

        val itemNames = items.map { "${it.name} - ${CurrencyUtils.formatRupiah(it.sellingPrice)}" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Pilih Produk ($category)")
            .setItems(itemNames) { _, index ->
                viewModel.addToCart(items[index], 1.0)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // =========================================================================
    // DIGITAL SERVICES / ISI ULANG & TAGIHAN (Image #1 & Image #2)
    // =========================================================================
    private fun showDigitalServicesBottomSheet(preselected: ProductEntity? = null) {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogPulsaInputBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true

        val allCategories = DigitalCatalog.getAllCategories()
        val isiUlangItems = allCategories.filter { it.section == DigitalSection.ISI_ULANG }
        val tagihanItems = allCategories.filter { it.section == DigitalSection.TAGIHAN }
        val digitalProducts = availableProducts.filter { it.productType == "DIGITAL" }

        // Update stats badge & auto-sync katalog digital dari API st.riungan.my.id
        fun refreshDigitalStatsBadge(count: Int, syncing: Boolean = false) {
            if (syncing) {
                dialogBinding.tvDigitalStatsBadge.text = "?? Memperbarui daftar layanan digital..."
                dialogBinding.tvDigitalStatsBadge.setTextColor(Color.parseColor("#059669"))
                return
            }
            if (!isDigitalGatewayReady()) {
                dialogBinding.tvDigitalStatsBadge.text = "?? Jalur Gateway Belum Dikonfigurasi (Atur H2H / SMS Center di Menu Akun)"
                dialogBinding.tvDigitalStatsBadge.setTextColor(Color.parseColor("#DC2626"))
            } else {
                dialogBinding.tvDigitalStatsBadge.text = if (count > 0) {
                    "? $count Layanan Digital Tersedia di Katalog Toko"
                } else {
                    "? Ketuk di sini untuk memuat daftar layanan digital"
                }
                dialogBinding.tvDigitalStatsBadge.setTextColor(Color.parseColor("#3E24E2"))
            }
        }
        refreshDigitalStatsBadge(digitalProducts.size)

        dialogBinding.tvDigitalStatsBadge.setOnClickListener {
            refreshDigitalStatsBadge(availableProducts.count { it.productType == "DIGITAL" }, syncing = true)
            viewLifecycleOwner.lifecycleScope.launch {
                val res = com.denkasir.data.repository.SyncRepository(requireContext()).syncDigitalCatalog(forceFull = true)
                res.onSuccess { summary ->
                    refreshDigitalStatsBadge(summary.totalItems, syncing = false)
                    Toast.makeText(
                        requireContext(),
                        "Katalog Layanan Digital Diperbarui: ${summary.totalItems} layanan (${summary.normalCount} Normal, ${summary.gangguanCount} Gangguan)",
                        Toast.LENGTH_SHORT
                    ).show()
                }.onFailure { err ->
                    refreshDigitalStatsBadge(availableProducts.count { it.productType == "DIGITAL" }, syncing = false)
                    Toast.makeText(requireContext(), "Gagal memperbarui katalog layanan digital.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (digitalProducts.size < 1000) {
            refreshDigitalStatsBadge(digitalProducts.size, syncing = true)
            viewLifecycleOwner.lifecycleScope.launch {
                val res = com.denkasir.data.repository.SyncRepository(requireContext()).syncDigitalCatalog(forceFull = true)
                res.onSuccess { summary ->
                    refreshDigitalStatsBadge(summary.totalItems, syncing = false)
                }.onFailure {
                    refreshDigitalStatsBadge(availableProducts.count { it.productType == "DIGITAL" }, syncing = false)
                }
            }
        } else {
            viewLifecycleOwner.lifecycleScope.launch {
                com.denkasir.data.repository.SyncRepository(requireContext()).syncDigitalCatalogIfNeeded()?.onSuccess { summary ->
                    refreshDigitalStatsBadge(summary.totalItems, syncing = false)
                }
            }
        }

        var currentCategory: DigitalCategoryItem = allCategories.first { it.id == "pulsa" }
        var selectedOperator: String = currentCategory.operators.first()
        var selectedKabupaten: String = ""
        var selectedNominal: NominalOption = NominalOption("100.000", 100000.0, 102000.0, 98500.0)
        var allNominalsForCategory: List<NominalOption> = emptyList()

        lateinit var nominalAdapter: NominalGridAdapter

        // Setup Search in Dialog Step 1
        val searchAdapter = DigitalProductSearchAdapter { pickedProduct ->
            val detectedCat = detectCategoryFromProduct(pickedProduct, allCategories)
            val detectedOp = detectOperatorFromProduct(pickedProduct) ?: detectedCat.operators.first()
            currentCategory = detectedCat
            selectedOperator = detectedOp
            navigateToServiceInput(
                category = detectedCat,
                dialogBinding = dialogBinding,
                onCategorySelected = { cat -> currentCategory = cat },
                preselectedProduct = pickedProduct,
                onSetupNominals = { cat, op, kab ->
                    selectedOperator = op
                    selectedKabupaten = kab
                    refreshServiceNominals(cat, op, kab, dialogBinding, nominalAdapter, pickedProduct) { list ->
                        allNominalsForCategory = list
                    }
                }
            )
        }
        dialogBinding.rvDigitalSearchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }

        dialogBinding.etSearchDigitalInDialog.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isNotEmpty()) {
                    dialogBinding.btnClearSearchDigital.visibility = View.VISIBLE
                    val matches = digitalProducts.filter { p ->
                        p.name.contains(query, ignoreCase = true) ||
                        (p.providerCode ?: "").contains(query, ignoreCase = true) ||
                        p.category.contains(query, ignoreCase = true)
                    }
                    searchAdapter.submitList(matches.take(50))
                    if (matches.isNotEmpty()) {
                        dialogBinding.rvDigitalSearchResults.visibility = View.VISIBLE
                        dialogBinding.tvEmptySearchResults.visibility = View.GONE
                    } else {
                        dialogBinding.rvDigitalSearchResults.visibility = View.GONE
                        dialogBinding.tvEmptySearchResults.visibility = View.VISIBLE
                    }
                    dialogBinding.tvSectionIsiUlang.visibility = View.GONE
                    dialogBinding.rvIsiUlangGrid.visibility = View.GONE
                    dialogBinding.tvSectionTagihan.visibility = View.GONE
                    dialogBinding.rvTagihanGrid.visibility = View.GONE
                } else {
                    dialogBinding.btnClearSearchDigital.visibility = View.GONE
                    dialogBinding.rvDigitalSearchResults.visibility = View.GONE
                    dialogBinding.tvEmptySearchResults.visibility = View.GONE
                    dialogBinding.tvSectionIsiUlang.visibility = View.VISIBLE
                    dialogBinding.rvIsiUlangGrid.visibility = View.VISIBLE
                    dialogBinding.tvSectionTagihan.visibility = View.VISIBLE
                    dialogBinding.rvTagihanGrid.visibility = View.VISIBLE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialogBinding.btnClearSearchDigital.setOnClickListener {
            dialogBinding.etSearchDigitalInDialog.setText("")
        }

        dialogBinding.btnShowAllCatalogDialog.setOnClickListener {
            showAllDigitalCatalogDialog { chosenProduct ->
                val detectedCat = detectCategoryFromProduct(chosenProduct, allCategories)
                val detectedOp = detectOperatorFromProduct(chosenProduct) ?: detectedCat.operators.first()
                currentCategory = detectedCat
                selectedOperator = detectedOp
                navigateToServiceInput(
                    category = detectedCat,
                    dialogBinding = dialogBinding,
                    onCategorySelected = { cat -> currentCategory = cat },
                    preselectedProduct = chosenProduct,
                    onSetupNominals = { cat, op, kab ->
                        selectedOperator = op
                        selectedKabupaten = kab
                        refreshServiceNominals(cat, op, kab, dialogBinding, nominalAdapter, chosenProduct) { list ->
                            allNominalsForCategory = list
                        }
                    }
                )
            }
        }

        dialogBinding.btnBackCategorySheet.setOnClickListener {
            dialog.dismiss()
        }

        val screenWidthDp = resources.configuration.screenWidthDp
        val categorySpanCount = if (screenWidthDp >= 600) 6 else 4
        val nominalSpanCount = when {
            screenWidthDp >= 900 -> 3
            screenWidthDp >= 600 -> 2
            else -> 1
        }

        dialogBinding.rvIsiUlangGrid.apply {
            layoutManager = GridLayoutManager(requireContext(), categorySpanCount)
            adapter = DigitalCategoryAdapter(isiUlangItems) { item ->
                currentCategory = item
                selectedOperator = item.operators.first()
                selectedKabupaten = ""
                navigateToServiceInput(
                    category = item,
                    dialogBinding = dialogBinding,
                    onCategorySelected = { cat -> currentCategory = cat },
                    onSetupNominals = { cat, op, kab ->
                        selectedOperator = op
                        selectedKabupaten = kab
                        refreshServiceNominals(cat, op, kab, dialogBinding, nominalAdapter, null) { list ->
                            allNominalsForCategory = list
                        }
                    }
                )
            }
        }

        dialogBinding.rvTagihanGrid.apply {
            layoutManager = GridLayoutManager(requireContext(), categorySpanCount)
            adapter = DigitalCategoryAdapter(tagihanItems) { item ->
                currentCategory = item
                selectedOperator = item.operators.first()
                selectedKabupaten = ""
                navigateToServiceInput(
                    category = item,
                    dialogBinding = dialogBinding,
                    onCategorySelected = { cat -> currentCategory = cat },
                    onSetupNominals = { cat, op, kab ->
                        selectedOperator = op
                        selectedKabupaten = kab
                        refreshServiceNominals(cat, op, kab, dialogBinding, nominalAdapter, null) { list ->
                            allNominalsForCategory = list
                        }
                    }
                )
            }
        }


        // Step 2 Nominal Grid Adapter
        nominalAdapter = NominalGridAdapter(
            onNominalSelected = { chosenNominal ->
                selectedNominal = chosenNominal
                if (chosenNominal.isCustom) {
                    dialogBinding.containerCustomNominal.visibility = View.VISIBLE
                    updatePriceDisplayFromCustom(dialogBinding)
                    dialogBinding.btnSubmitDigital.isEnabled = true
                    dialogBinding.btnSubmitDigital.text = "+ Tambah ke Kasir"
                    dialogBinding.btnSubmitDigital.alpha = 1.0f
                } else {
                    dialogBinding.containerCustomNominal.visibility = View.GONE
                    if (!chosenNominal.isAvailable) {
                        dialogBinding.tvPriceDisplay.text = "Stok Kosong"
                        dialogBinding.tvPriceDisplay.setTextColor(Color.parseColor("#DC2626"))
                        dialogBinding.tvMarginDisplay.text = "⚠️ Stok paket ini sedang kosong atau gangguan distributor"
                        dialogBinding.tvMarginDisplay.setTextColor(Color.parseColor("#DC2626"))
                        dialogBinding.btnSubmitDigital.isEnabled = false
                        dialogBinding.btnSubmitDigital.text = "🚫 Stok Distributor Kosong"
                        dialogBinding.btnSubmitDigital.alpha = 0.6f
                    } else {
                        dialogBinding.tvPriceDisplay.text = CurrencyUtils.formatRupiah(chosenNominal.sellingPrice)
                        dialogBinding.tvPriceDisplay.setTextColor(Color.parseColor("#16A34A"))
                        val laba = chosenNominal.sellingPrice - chosenNominal.costPrice
                        val codeInfo = chosenNominal.productEntity?.providerCode?.let { " • Kode: $it" } ?: ""
                        dialogBinding.tvMarginDisplay.text = "Modal: ${CurrencyUtils.formatRupiah(chosenNominal.costPrice)} • Laba: +${CurrencyUtils.formatRupiah(laba)}$codeInfo"
                        dialogBinding.tvMarginDisplay.setTextColor(Color.parseColor("#64748B"))
                        dialogBinding.btnSubmitDigital.isEnabled = true
                        dialogBinding.btnSubmitDigital.text = "+ Tambah ke Kasir"
                        dialogBinding.btnSubmitDigital.alpha = 1.0f
                    }
                }
            },
            onInfoClicked = { option ->
                showPackageDetailDialog(option, currentCategory, selectedOperator) { chosen ->
                    refreshServiceNominals(currentCategory, selectedOperator, selectedKabupaten, dialogBinding, nominalAdapter, chosen.productEntity) { list ->
                        allNominalsForCategory = list
                    }
                }
            }
        )

        dialogBinding.rvNominalGrid.apply {
            layoutManager = if (nominalSpanCount > 1) GridLayoutManager(requireContext(), nominalSpanCount) else LinearLayoutManager(requireContext())
            adapter = nominalAdapter
        }

        // Live Search Nominal/Package in Step 2
        dialogBinding.etSearchNominalPackage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim() ?: ""
                if (q.isNotEmpty()) {
                    dialogBinding.btnClearSearchNominal.visibility = View.VISIBLE
                    val filtered = allNominalsForCategory.filter { opt ->
                        opt.label.contains(q, ignoreCase = true) ||
                        opt.quotaLabel.contains(q, ignoreCase = true) ||
                        opt.validityLabel.contains(q, ignoreCase = true) ||
                        (opt.productEntity?.name ?: "").contains(q, ignoreCase = true) ||
                        (opt.productEntity?.providerCode ?: "").contains(q, ignoreCase = true)
                    }
                    nominalAdapter.submitList(filtered, defaultSelectIndex = 0)
                } else {
                    dialogBinding.btnClearSearchNominal.visibility = View.GONE
                    nominalAdapter.submitList(allNominalsForCategory, defaultSelectIndex = 0)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialogBinding.btnClearSearchNominal.setOnClickListener {
            dialogBinding.etSearchNominalPackage.setText("")
        }

        // Back button on Step 2 to go back to Step 1
        dialogBinding.btnBackToCategories.setOnClickListener {
            dialogBinding.layoutServiceInput.visibility = View.GONE
            dialogBinding.layoutCategorySelection.visibility = View.VISIBLE
        }

        // Contact Picker Icon Click
        dialogBinding.btnContactPicker.setOnClickListener {
            onContactPickedCallback = { pickedNumber ->
                dialogBinding.etTargetNumber.setText(pickedNumber)
                dialogBinding.etTargetNumber.setSelection(pickedNumber.length)
            }
            try {
                val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                contactPickerLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Tidak dapat membuka kontak: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        fun updateOperatorBadgeAndIcon(operatorName: String?, showBadge: Boolean = true) {
            val clean = (operatorName ?: "").lowercase().trim()
            val theme = when {
                clean.contains("by.u") || clean.contains("byu") -> {
                    CustomOperatorTheme("by.u", R.drawable.ic_operator_byu, "#E0F2FE", "#06B6D4", "#0891B2")
                }
                clean.contains("telkomsel") || clean.contains("tsel") -> {
                    CustomOperatorTheme("Telkomsel", R.drawable.ic_operator_telkomsel, "#FEE2E2", "#EF4444", "#DC2626")
                }
                clean.contains("indosat") || clean.contains("isat") -> {
                    CustomOperatorTheme("indosat", R.drawable.ic_operator_indosat, "#FEF3C7", "#F59E0B", "#D97706")
                }
                clean.contains("xl") -> {
                    CustomOperatorTheme("XL", R.drawable.ic_operator_xl, "#DBEAFE", "#3B82F6", "#1D4ED8")
                }
                clean.contains("axis") -> {
                    CustomOperatorTheme("AXIS", R.drawable.ic_operator_axis, "#F3E8FF", "#A855F7", "#7E22CE")
                }
                clean.contains("tri") || clean.contains("three") -> {
                    CustomOperatorTheme("Tri 3", R.drawable.ic_operator_tri, "#FCE7F3", "#EC4899", "#BE185D")
                }
                clean.contains("smartfren") || clean.contains("smart") -> {
                    CustomOperatorTheme("smartfren", R.drawable.ic_operator_smartfren, "#FFE4E6", "#F43F5E", "#E11D48")
                }
                else -> null
            }

            if (theme == null) {
                dialogBinding.containerDetectedOperatorBadge.visibility = View.GONE
                dialogBinding.ivSelectedOperatorLogo.visibility = View.GONE
                return
            }

            // Update Selected Operator Logo in Dropdown
            dialogBinding.ivSelectedOperatorLogo.visibility = View.VISIBLE
            dialogBinding.ivSelectedOperatorLogo.setImageResource(theme.iconRes)

            // Update Detected Operator Badge next to phone input
            if (showBadge) {
                dialogBinding.containerDetectedOperatorBadge.visibility = View.VISIBLE
                val pill = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 24f
                    setColor(Color.parseColor(theme.bgColor))
                    setStroke(2, Color.parseColor(theme.strokeColor))
                }
                dialogBinding.containerDetectedOperatorBadge.background = pill
                dialogBinding.ivDetectedOperatorLogo.setImageResource(theme.iconRes)
                dialogBinding.tvDetectedOperatorText.text = theme.brandText
                dialogBinding.tvDetectedOperatorText.setTextColor(Color.parseColor(theme.textColor))
            } else {
                dialogBinding.containerDetectedOperatorBadge.visibility = View.GONE
            }
        }

        // Operator / Provinsi Dropdown Click
        dialogBinding.containerOperatorDropdown.setOnClickListener {
            val ops = currentCategory.operators.toTypedArray()
            val titlePrompt = if (currentCategory.isRegional) "Pilih Provinsi PDAM" else "Pilih ${currentCategory.operatorLabel}"
            AlertDialog.Builder(requireContext())
                .setTitle(titlePrompt)
                .setItems(ops) { _, which ->
                    selectedOperator = ops[which]
                    dialogBinding.tvSelectedOperator.text = selectedOperator
                    updateOperatorBadgeAndIcon(selectedOperator, showBadge = false)
                    if (currentCategory.isRegional) {
                        val kabs = getKabupatenListForProvince(selectedOperator)
                        selectedKabupaten = kabs.firstOrNull() ?: ""
                        dialogBinding.tvSelectedKabupaten.text = if (selectedKabupaten.isNotEmpty()) selectedKabupaten else "Pilih Kabupaten / Kota"
                    }
                    dialogBinding.etSearchNominalPackage.setText("")
                    refreshServiceNominals(currentCategory, selectedOperator, selectedKabupaten, dialogBinding, nominalAdapter, null) { list ->
                        allNominalsForCategory = list
                    }
                }
                .show()
        }

        // Kabupaten Dropdown Click (PDAM)
        dialogBinding.containerKabupatenDropdown.setOnClickListener {
            val kabs = getKabupatenListForProvince(selectedOperator)
            if (kabs.isEmpty()) {
                Toast.makeText(requireContext(), "Belum ada data kabupaten untuk $selectedOperator", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(requireContext())
                .setTitle("Pilih Kabupaten / Kota PDAM ($selectedOperator)")
                .setItems(kabs.toTypedArray()) { _, which ->
                    selectedKabupaten = kabs[which]
                    dialogBinding.tvSelectedKabupaten.text = selectedKabupaten
                    dialogBinding.etSearchNominalPackage.setText("")
                    refreshServiceNominals(currentCategory, selectedOperator, selectedKabupaten, dialogBinding, nominalAdapter, null) { list ->
                        allNominalsForCategory = list
                    }
                }
                .show()
        }

        // Live Phone Number Operator Detection
        dialogBinding.etTargetNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s.toString().trim()
                if (currentCategory.id in listOf("pulsa", "paket_data", "paket_telp_sms", "masa_aktif", "roaming")) {
                    val detected = detectOperator(input)
                    if (detected != null) {
                        updateOperatorBadgeAndIcon(detected, showBadge = true)
                        val matchedOp = currentCategory.operators.firstOrNull { it.contains(detected, ignoreCase = true) || detected.contains(it, ignoreCase = true) }
                        if (matchedOp != null && matchedOp != selectedOperator) {
                            selectedOperator = matchedOp
                            dialogBinding.tvSelectedOperator.text = matchedOp
                            dialogBinding.etSearchNominalPackage.setText("")
                            refreshServiceNominals(currentCategory, selectedOperator, selectedKabupaten, dialogBinding, nominalAdapter, null) { list ->
                                allNominalsForCategory = list
                            }
                        }
                    } else {
                        dialogBinding.containerDetectedOperatorBadge.visibility = View.GONE
                        updateOperatorBadgeAndIcon(selectedOperator, showBadge = false)
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Custom Nominal TextWatcher
        dialogBinding.etCustomNominal.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (selectedNominal.isCustom) {
                    updatePriceDisplayFromCustom(dialogBinding)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Action Button: Tambah ke Kasir
        dialogBinding.btnSubmitDigital.setOnClickListener {
            if (!isDigitalGatewayReady()) {
                showGatewayRequiredDialog()
                return@setOnClickListener
            }

            if (!selectedNominal.isAvailable || (selectedNominal.productEntity?.isDigitalOutOfStock() == true)) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Produk Tidak Tersedia")
                    .setMessage("Maaf, layanan '${selectedNominal.label}' tidak dapat dipesan karena saat ini dalam status STOK KOSONG / Gangguan di server distributor.")
                    .setIcon(R.drawable.ic_info_circle)
                    .setPositiveButton("Pilih Layanan Lain", null)
                    .show()
                return@setOnClickListener
            }

            val target = dialogBinding.etTargetNumber.text.toString().trim()
            if (target.length < 4) {
                Toast.makeText(requireContext(), "Masukkan nomor / ID tujuan dengan benar!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (currentCategory.isRegional && selectedKabupaten.isBlank()) {
                Toast.makeText(requireContext(), "Silakan pilih Kabupaten / Kota PDAM terlebih dahulu!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val productToCart = if (selectedNominal.productEntity != null) {
                selectedNominal.productEntity!!
            } else if (selectedNominal.isCustom) {
                val customInput = dialogBinding.etCustomNominal.text.toString().toDoubleOrNull() ?: 0.0
                if (customInput <= 0) {
                    Toast.makeText(requireContext(), "Masukkan nominal yang valid!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val adminFee = if (currentCategory.section == DigitalSection.TAGIHAN) 2500.0 else 2000.0
                ProductEntity(
                    id = UUID.randomUUID().toString(),
                    name = "${currentCategory.title} $selectedOperator ${CurrencyUtils.formatRupiah(customInput)}",
                    category = currentCategory.title,
                    productType = "DIGITAL",
                    sellingPrice = customInput + adminFee,
                    costPrice = customInput,
                    providerCode = selectedOperator,
                    stock = 999.0
                )
            } else {
                ProductEntity(
                    id = UUID.randomUUID().toString(),
                    name = if (currentCategory.isRegional) "PDAM $selectedKabupaten (${selectedNominal.label})" else "${currentCategory.title} $selectedOperator ${selectedNominal.label}",
                    category = currentCategory.title,
                    productType = "DIGITAL",
                    sellingPrice = selectedNominal.sellingPrice,
                    costPrice = selectedNominal.costPrice,
                    providerCode = selectedOperator,
                    stock = 999.0
                )
            }

            val note = if (currentCategory.isRegional) {
                "$target ($selectedKabupaten - ${productToCart.providerCode ?: selectedOperator})"
            } else {
                "$target (${productToCart.providerCode ?: selectedOperator})"
            }

            viewModel.addToCart(productToCart, 1.0, note)
            dialog.dismiss()
            Toast.makeText(requireContext(), "${productToCart.name} berhasil ditambahkan ke kasir!", Toast.LENGTH_SHORT).show()
        }

        // If a preselected product was passed (e.g. from search)
        if (preselected != null) {
            val matchedCat = detectCategoryFromProduct(preselected, allCategories)
            val matchedOp = detectOperatorFromProduct(preselected) ?: matchedCat.operators.first()
            currentCategory = matchedCat
            selectedOperator = matchedOp
            navigateToServiceInput(
                category = matchedCat,
                dialogBinding = dialogBinding,
                onCategorySelected = { cat -> currentCategory = cat },
                preselectedProduct = preselected,
                onSetupNominals = { cat, op, kab ->
                    selectedOperator = op
                    selectedKabupaten = kab
                    refreshServiceNominals(cat, op, kab, dialogBinding, nominalAdapter, preselected) { list ->
                        allNominalsForCategory = list
                    }
                }
            )
        } else {
            dialogBinding.layoutCategorySelection.visibility = View.VISIBLE
            dialogBinding.layoutServiceInput.visibility = View.GONE
        }

        dialog.show()
    }


    private fun cleanKabupatenName(productName: String): String {
        if (productName.contains("Harga: Rp", ignoreCase = true)) {
            return ""
        }
        var clean = productName.replace(Regex("""^\[[^\]]+\]\s*"""), "")
        clean = clean.replace(Regex("""^(Tagihan PDAM|BAYAR TAGIHAN PDAM|BAYAR PDAM|CEK PDAM|TAGIHAN PDAM)\s*[-:]?\s*""", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("""^(BAYAR TAGIHAN PDAM|TAGIHAN PDAM|BAYAR PDAM)\s*""", RegexOption.IGNORE_CASE), "")
        return clean.trim()
    }

    private fun getKabupatenListForProvince(province: String): List<String> {
        val digitalProds = availableProducts.filter {
            it.productType == "DIGITAL" &&
            !it.name.contains("Harga: Rp", ignoreCase = true) &&
            (it.category.equals("PDAM", ignoreCase = true) || it.name.contains("PDAM", ignoreCase = true))
        }
        val provClean = province.lowercase().trim()
        val filtered = digitalProds.filter { p ->
            p.name.lowercase().contains(provClean)
        }
        return filtered.map { cleanKabupatenName(it.name) }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    private fun refreshServiceNominals(
        category: DigitalCategoryItem,
        operator: String,
        kabupaten: String,
        dialogBinding: DialogPulsaInputBinding,
        nominalAdapter: NominalGridAdapter?,
        preselectedProduct: ProductEntity?,
        onListBuilt: ((List<NominalOption>) -> Unit)? = null
    ) {
        val nominals = buildNominalOptions(category, operator, kabupaten)
        onListBuilt?.invoke(nominals)

        val defaultIdx = if (preselectedProduct != null) {
            nominals.indexOfFirst {
                it.productEntity?.id == preselectedProduct.id ||
                it.productEntity?.providerCode == preselectedProduct.providerCode
            }.let { if (it >= 0) it else 0 }
        } else {
            0
        }
        nominalAdapter?.submitList(nominals, defaultSelectIndex = defaultIdx)

        // Update Semua Paket Button
        val matchingCount = getMatchingProductsFor(category, operator, kabupaten).size
        if (matchingCount > 0) {
            dialogBinding.btnBrowseOperatorCatalog.visibility = View.VISIBLE
            dialogBinding.btnBrowseOperatorCatalog.text = "📋 Semua Paket ($matchingCount)"
            dialogBinding.btnBrowseOperatorCatalog.setOnClickListener {
                showAllProductsForOperatorDialog(category, operator, kabupaten) { chosen ->
                    refreshServiceNominals(category, operator, kabupaten, dialogBinding, nominalAdapter, chosen, onListBuilt)
                }
            }
        } else {
            dialogBinding.btnBrowseOperatorCatalog.visibility = View.GONE
        }
    }

    private fun navigateToServiceInput(
        category: DigitalCategoryItem,
        dialogBinding: DialogPulsaInputBinding,
        onCategorySelected: (DigitalCategoryItem) -> Unit,
        preselectedProduct: ProductEntity? = null,
        onSetupNominals: ((DigitalCategoryItem, String, String) -> Unit)? = null
    ) {
        onCategorySelected(category)

        dialogBinding.layoutCategorySelection.visibility = View.GONE
        dialogBinding.layoutServiceInput.visibility = View.VISIBLE

        dialogBinding.tvSelectedCategoryTitle.text = if (category.section == DigitalSection.ISI_ULANG) "Isi Ulang ${category.title}" else "Tagihan ${category.title}"
        dialogBinding.tvTargetNumberLabel.text = category.targetNumberLabel
        dialogBinding.etTargetNumber.hint = category.targetNumberHint
        dialogBinding.tvOperatorLabel.text = category.operatorLabel

        val detectedOp = if (preselectedProduct != null) {
            detectOperatorFromProduct(preselectedProduct) ?: category.operators.first()
        } else {
            category.operators.first()
        }
        dialogBinding.tvSelectedOperator.text = detectedOp
        // Operator theme and badge
        val detectedInitial = detectOperator(dialogBinding.etTargetNumber.text.toString().trim())
        if (detectedInitial != null) {
            val (brandText, iconRes, bgColor, strokeColor, textColor) = when {
                detectedInitial.contains("by.u", ignoreCase = true) || detectedInitial.contains("byu", ignoreCase = true) ->
                    CustomOperatorTheme("by.u", R.drawable.ic_operator_byu, "#E0F2FE", "#06B6D4", "#0891B2")
                detectedInitial.contains("telkomsel", ignoreCase = true) ->
                    CustomOperatorTheme("Telkomsel", R.drawable.ic_operator_telkomsel, "#FEE2E2", "#EF4444", "#DC2626")
                detectedInitial.contains("indosat", ignoreCase = true) ->
                    CustomOperatorTheme("indosat", R.drawable.ic_operator_indosat, "#FEF3C7", "#F59E0B", "#D97706")
                detectedInitial.contains("xl", ignoreCase = true) ->
                    CustomOperatorTheme("XL", R.drawable.ic_operator_xl, "#DBEAFE", "#3B82F6", "#1D4ED8")
                detectedInitial.contains("axis", ignoreCase = true) ->
                    CustomOperatorTheme("AXIS", R.drawable.ic_operator_axis, "#F3E8FF", "#A855F7", "#7E22CE")
                detectedInitial.contains("tri", ignoreCase = true) ->
                    CustomOperatorTheme("Tri 3", R.drawable.ic_operator_tri, "#FCE7F3", "#EC4899", "#BE185D")
                detectedInitial.contains("smartfren", ignoreCase = true) ->
                    CustomOperatorTheme("smartfren", R.drawable.ic_operator_smartfren, "#FFE4E6", "#F43F5E", "#E11D48")
                else -> null
            } ?: CustomOperatorTheme("Telkomsel", R.drawable.ic_operator_telkomsel, "#FEE2E2", "#EF4444", "#DC2626")

            dialogBinding.ivSelectedOperatorLogo.visibility = View.VISIBLE
            dialogBinding.ivSelectedOperatorLogo.setImageResource(iconRes)
            dialogBinding.containerDetectedOperatorBadge.visibility = View.VISIBLE
            val pill = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f
                setColor(Color.parseColor(bgColor))
                setStroke(2, Color.parseColor(strokeColor))
            }
            dialogBinding.containerDetectedOperatorBadge.background = pill
            dialogBinding.ivDetectedOperatorLogo.setImageResource(iconRes)
            dialogBinding.tvDetectedOperatorText.text = brandText
            dialogBinding.tvDetectedOperatorText.setTextColor(Color.parseColor(textColor))
        } else {
            val iconRes = when {
                detectedOp.contains("by.u", ignoreCase = true) || detectedOp.contains("byu", ignoreCase = true) -> R.drawable.ic_operator_byu
                detectedOp.contains("telkomsel", ignoreCase = true) -> R.drawable.ic_operator_telkomsel
                detectedOp.contains("indosat", ignoreCase = true) -> R.drawable.ic_operator_indosat
                detectedOp.contains("xl", ignoreCase = true) -> R.drawable.ic_operator_xl
                detectedOp.contains("axis", ignoreCase = true) -> R.drawable.ic_operator_axis
                detectedOp.contains("tri", ignoreCase = true) -> R.drawable.ic_operator_tri
                detectedOp.contains("smartfren", ignoreCase = true) -> R.drawable.ic_operator_smartfren
                else -> 0
            }
            if (iconRes != 0) {
                dialogBinding.ivSelectedOperatorLogo.visibility = View.VISIBLE
                dialogBinding.ivSelectedOperatorLogo.setImageResource(iconRes)
            } else {
                dialogBinding.ivSelectedOperatorLogo.visibility = View.GONE
            }
            dialogBinding.containerDetectedOperatorBadge.visibility = View.GONE
        }

        var initKabupaten = ""
        if (category.isRegional) {
            dialogBinding.containerKabupatenSection.visibility = View.VISIBLE
            dialogBinding.containerSearchNominal.visibility = View.GONE
            val kabs = getKabupatenListForProvince(detectedOp)
            initKabupaten = if (preselectedProduct != null) {
                cleanKabupatenName(preselectedProduct.name)
            } else {
                kabs.firstOrNull() ?: ""
            }
            dialogBinding.tvSelectedKabupaten.text = if (initKabupaten.isNotEmpty()) initKabupaten else "Pilih Kabupaten / Kota"
        } else {
            dialogBinding.containerKabupatenSection.visibility = View.GONE
            dialogBinding.containerSearchNominal.visibility = View.VISIBLE
            dialogBinding.etSearchNominalPackage.setText("")
        }

        onSetupNominals?.invoke(category, detectedOp, initKabupaten)
    }

    private fun getMatchingProductsFor(category: DigitalCategoryItem, operator: String, kabupaten: String = ""): List<ProductEntity> {
        val digitalProds = availableProducts.filter { it.productType == "DIGITAL" }
        val catTitle = category.title.lowercase().trim()
        val catId = category.id.lowercase().trim()

        if (category.isRegional || catId == "pdam") {
            val pdamProds = digitalProds.filter { p ->
                !p.name.contains("Harga: Rp", ignoreCase = true) &&
                (p.category.equals("PDAM", ignoreCase = true) || p.name.contains("PDAM", ignoreCase = true))
            }
            val provClean = operator.lowercase().trim()
            val provFiltered = pdamProds.filter { p ->
                p.name.lowercase().contains(provClean)
            }
            if (kabupaten.isNotBlank()) {
                val kabClean = kabupaten.lowercase().trim()
                val kabFiltered = provFiltered.filter { p ->
                    val cleanKab = cleanKabupatenName(p.name).lowercase()
                    cleanKab == kabClean || cleanKab.contains(kabClean) || kabClean.contains(cleanKab) || p.name.lowercase().contains(kabClean)
                }
                if (kabFiltered.isNotEmpty()) return kabFiltered.sortedBy { it.sellingPrice }
            }
            return provFiltered.sortedBy { it.sellingPrice }
        }

        val matchingCategory = digitalProds.filter { p ->
            val pCat = p.category.lowercase().trim()
            val pName = p.name.lowercase()
            when (catId) {
                "pulsa" -> (pCat == "pulsa" || pCat.contains("pulsa")) && !pName.contains("data") && !pName.contains("kuota") && !pName.contains("nelpon") && !pName.contains("sms")
                "paket_data" -> pCat.contains("data") || pCat.contains("kuota") || pCat.contains("internet") || pName.contains("data") || pName.contains("kuota") || pName.contains("internet") || pName.contains("gb")
                "paket_telp_sms" -> pCat.contains("nelpon") || pCat.contains("sms") || pName.contains("nelpon") || pName.contains("sms") || pName.contains("telepon") || pName.contains("menit")
                "listrik_pln" -> pCat.contains("pln") || pCat.contains("listrik") || pName.contains("pln") || pName.contains("token")
                "uang_elektronik" -> pCat.contains("uang") || pCat.contains("dompet") || pCat.contains("saldo") || pName.contains("dana") || pName.contains("gopay") || pName.contains("ovo") || pName.contains("shopee") || pName.contains("linkaja") || pName.contains("maxim") || pName.contains("grab") || pName.contains("toll")
                "voucher_game" -> pCat.contains("game") || pCat.contains("voucher") || pName.contains("game") || pName.contains("diamond") || pName.contains("free fire") || pName.contains("mobile legend") || pName.contains("pubg") || pName.contains("roblox") || pName.contains("genshin")
                "masa_aktif" -> pCat.contains("aktif") || pName.contains("aktif")
                "aktivasi_perdana" -> pCat.contains("perdana") || pCat.contains("aktivasi") || pName.contains("perdana") || pName.contains("aktivasi")
                "voucher_belanja" -> pCat.contains("belanja") || pName.contains("indomaret") || pName.contains("alfamart") || pName.contains("transmart")
                "bpjs", "premi_asuransi" -> pCat.contains("bpjs") || pCat.contains("asuransi") || pName.contains("bpjs") || pName.contains("asuransi")
                "telkom" -> pCat.contains("telkom") || pName.contains("telkom") || pName.contains("indihome")
                "pasca_bayar" -> pCat.contains("pasca") || pName.contains("halo") || pName.contains("matrix") || pName.contains("prioritas")
                "tv_internet" -> pCat.contains("tv") || pName.contains("indovision") || pName.contains("transvision") || pName.contains("first media")
                "angsuran_kredit" -> pCat.contains("angsuran") || pCat.contains("kredit") || pName.contains("fif") || pName.contains("baf") || pName.contains("adira")
                else -> pCat == catTitle || pCat == catId || pCat.contains(catTitle) || catTitle.contains(pCat)
            }
        }

        val opClean = operator.lowercase().trim()
        val opKeyword = when {
            opClean.contains("by.u") || opClean.contains("byu") -> "byu"
            opClean.contains("telkomsel") || opClean.contains("tsel") -> "telkomsel"
            opClean.contains("indosat") || opClean.contains("isat") -> "indosat"
            opClean.contains("xl") -> "xl"
            opClean.contains("axis") -> "axis"
            opClean.contains("tri") || opClean.contains("three") -> "tri"
            opClean.contains("smartfren") || opClean.contains("smart") -> "smartfren"
            opClean.contains("pln") -> "pln"
            opClean.contains("dana") -> "dana"
            opClean.contains("gopay") -> "gopay"
            opClean.contains("ovo") -> "ovo"
            opClean.contains("shopeepay") || opClean.contains("shopee") -> "shopee"
            opClean.contains("linkaja") -> "linkaja"
            opClean.contains("maxim") -> "maxim"
            opClean.contains("grab") -> "grab"
            opClean.contains("bpjs") -> "bpjs"
            else -> opClean.split(" ").firstOrNull() ?: opClean
        }

        val filtered = matchingCategory.filter { p ->
            val pName = p.name.lowercase()
            val pCode = (p.providerCode ?: "").lowercase()
            if (opKeyword == "dana") {
                pName.contains(Regex("\\bdana\\b")) || pCode.contains(Regex("\\bdana\\b"))
            } else if (opKeyword == "tri") {
                pName.contains("tri") || pName.contains("three") || pCode.startsWith("tri") || pCode.startsWith("three")
            } else {
                pName.contains(opKeyword) || pCode.contains(opKeyword)
            }
        }

        return if (filtered.isNotEmpty()) {
            filtered.sortedBy { it.sellingPrice }
        } else if (matchingCategory.size in 1..25) {
            matchingCategory.sortedBy { it.sellingPrice }
        } else {
            emptyList()
        }
    }

    private fun extractShortNominalLabel(product: ProductEntity): String {
        var clean = product.name
            .replace(Regex("^\\[[^\\]]+\\]\\s*"), "")
            .replace(Regex("^(Pulsa|Token PLN|Dompet Digital|Tagihan PDAM|Tagihan|Game|Voucher Game|Bebas Nominal|Paket Kuota|Paket Data|Inject Kartu Perdana)\\s*-\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^(Pulsa|Kuota Internet|Internet|Kuota|TOKEN PLN|Aktivasi|Inject)\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
        if (clean.isEmpty()) clean = CurrencyUtils.formatRupiah(product.sellingPrice)
        return if (clean.length <= 15) clean else clean.take(13) + ".."
    }

    private fun buildNominalOptions(category: DigitalCategoryItem, operator: String, kabupaten: String = ""): List<NominalOption> {
        val prods = getMatchingProductsFor(category, operator, kabupaten)
        if (prods.isNotEmpty()) {
            val options = prods.take(50).map { p ->
                DigitalPackageFormatter.createOptionFromProduct(p, category, operator)
            }.toMutableList()
            if (!category.isRegional) {
                options.add(
                    DigitalPackageFormatter.enrichOption(
                        NominalOption("Lainnya", 0.0, 0.0, 0.0, isCustom = true),
                        category,
                        operator
                    )
                )
            }
            return options
        }
        return category.defaultNominals.map { opt ->
            DigitalPackageFormatter.enrichOption(opt, category, operator)
        }
    }

    private fun showPackageDetailDialog(
        option: NominalOption,
        category: DigitalCategoryItem,
        operator: String,
        onSelect: ((NominalOption) -> Unit)? = null
    ) {
        val msg = StringBuilder().apply {
            append("Nama Layanan / Paket: ").append(option.label).append("\n\n")
            append("Kategori: ").append(category.title).append("\n")
            append("Operator / Penyedia: ").append(operator).append("\n")
            if (option.quotaLabel.isNotBlank()) {
                append("${option.column1Title}: ").append(option.quotaLabel).append("\n")
            }
            if (option.validityLabel.isNotBlank()) {
                append("${option.column2Title}: ").append(option.validityLabel).append("\n")
            }
            append("Harga Jual Kasir: ").append(CurrencyUtils.formatRupiah(option.sellingPrice)).append("\n")
            append("Harga Modal: ").append(CurrencyUtils.formatRupiah(option.costPrice)).append("\n")
            val kode = option.productEntity?.providerCode ?: "-"
            append("Kode Produk / H2H: ").append(kode)
        }.toString()

        AlertDialog.Builder(requireContext())
            .setTitle("Detail Layanan Digital")
            .setMessage(msg)
            .setPositiveButton("Pilih Paket Ini") { _, _ ->
                onSelect?.invoke(option)
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun showAllProductsForOperatorDialog(
        category: DigitalCategoryItem,
        operator: String,
        kabupaten: String = "",
        onChosen: (ProductEntity) -> Unit
    ) {
        val prods = getMatchingProductsFor(category, operator, kabupaten)
        if (prods.isEmpty()) {
            Toast.makeText(requireContext(), "Belum ada produk untuk $operator", Toast.LENGTH_SHORT).show()
            return
        }
        val items = prods.map {
            val stockTag = if (it.isDigitalOutOfStock()) " [STOK KOSONG]" else ""
            "${it.name} • ${CurrencyUtils.formatRupiah(it.sellingPrice)}$stockTag"
        }.toTypedArray()
        val titlePrompt = if (category.isRegional && kabupaten.isNotBlank()) "Daftar PDAM $kabupaten (${prods.size})" else "Daftar Layanan $operator (${prods.size})"
        AlertDialog.Builder(requireContext())
            .setTitle(titlePrompt)
            .setItems(items) { _, which ->
                onChosen(prods[which])
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun showAllDigitalCatalogDialog(onChosen: (ProductEntity) -> Unit) {
        val digitalProds = availableProducts.filter { it.productType == "DIGITAL" }
        if (digitalProds.isEmpty()) {
            Toast.makeText(requireContext(), "Belum ada produk digital di katalog", Toast.LENGTH_SHORT).show()
            return
        }

        val items = digitalProds.map {
            val stockTag = if (it.isDigitalOutOfStock()) " [STOK KOSONG]" else ""
            "[${it.category}] ${it.name} • ${CurrencyUtils.formatRupiah(it.sellingPrice)}$stockTag"
        }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Katalog Produk Digital (${digitalProds.size} Item)")
            .setItems(items) { _, which ->
                onChosen(digitalProds[which])
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun detectOperatorFromProduct(product: ProductEntity): String? {
        val name = product.name.lowercase()
        val pCode = (product.providerCode ?: "").lowercase()
        val cat = product.category.lowercase()
        return when {
            name.contains("byu") || name.contains("by u") || pCode.contains("byu") || pCode.contains("by u") -> if (cat.contains("data")) "By.U Data" else "By.U"

            name.contains("telkomsel") || pCode.contains("tsel") -> if (cat.contains("data")) "Telkomsel Data" else "Telkomsel"
            name.contains("indosat") || pCode.contains("isat") -> if (cat.contains("data")) "Indosat Data" else "Indosat Ooredoo"
            name.contains("xl") || pCode.contains("xl") -> if (cat.contains("data")) "XL Data" else "XL Axiata"
            name.contains("tri") || name.contains("three") || pCode.startsWith("tri") || pCode.startsWith("three") -> if (cat.contains("data")) "Tri Data" else "Tri (3)"
            name.contains("smartfren") || pCode.contains("smart") -> if (cat.contains("data")) "Smartfren Data" else "Smartfren"
            name.contains("axis") || pCode.contains("axis") -> if (cat.contains("data")) "Axis Data" else "Axis"
            name.contains("pln") || pCode.contains("pln") || cat.contains("pln") -> "Token PLN Prabayar"
            name.contains(Regex("\\bdana\\b")) || pCode.contains(Regex("\\bdana\\b")) -> "DANA"
            name.contains("gopay") || pCode.contains("gopay") -> "GoPay Customer"
            name.contains("ovo") || pCode.contains("ovo") -> "OVO"
            name.contains("shopee") || pCode.contains("shopee") -> "ShopeePay"
            name.contains("linkaja") || pCode.contains("linkaja") -> "LinkAja"
            name.contains("pdam") || cat.contains("pdam") -> {
                when {
                    name.contains("jawa barat") -> "Jawa Barat"
                    name.contains("jawa tengah") -> "Jawa Tengah"
                    name.contains("jawa timur") -> "Jawa Timur"
                    name.contains("jabodetabek") -> "Jabodetabek"
                    name.contains("sumatera") -> "Sumatera"
                    name.contains("sulawesi") -> "Sulawesi"
                    name.contains("kalimantan") -> "Kalimantan"
                    name.contains("bali") -> "Bali"
                    name.contains("nusa tenggara") -> "Nusa Tenggara"
                    name.contains("kepri") || name.contains("jambi") || name.contains("bengkulu") -> "Kepri, Jambi, Bengkulu"
                    name.contains("maluku") || name.contains("papua") -> "Maluku dan Papua"
                    else -> "Jawa Barat"
                }
            }
            else -> product.providerCode
        }
    }

    private fun detectCategoryFromProduct(product: ProductEntity, allCategories: List<DigitalCategoryItem>): DigitalCategoryItem {
        val pCat = product.category.lowercase().trim()
        val pName = product.name.lowercase()
        val matched = allCategories.find {
            val cTitle = it.title.lowercase().trim()
            val cId = it.id.lowercase().trim()
            pCat == cTitle || pCat == cId ||
            (cId == "pulsa" && (pCat.contains("pulsa") || pName.contains("pulsa")) && !pName.contains("data") && !pName.contains("kuota")) ||
            (cId == "paket_data" && (pCat.contains("data") || pCat.contains("kuota") || pCat.contains("internet") || pName.contains("kuota") || pName.contains("internet") || pName.contains("gb"))) ||
            (cId == "paket_telp_sms" && (pCat.contains("nelpon") || pCat.contains("sms") || pName.contains("nelpon") || pName.contains("sms"))) ||
            (cId == "listrik_pln" && (pCat.contains("pln") || pCat.contains("listrik") || pName.contains("pln") || pName.contains("token"))) ||
            (cId == "pdam" && (pCat.contains("pdam") || pCat.contains("air") || pName.contains("pdam"))) ||
            (cId == "uang_elektronik" && (pCat.contains("uang") || pCat.contains("dompet") || pCat.contains("saldo") || pName.contains("dana") || pName.contains("gopay") || pName.contains("ovo") || pName.contains("shopee"))) ||
            (cId == "voucher_game" && (pCat.contains("game") || pCat.contains("voucher") || pName.contains("game") || pName.contains("diamond"))) ||
            (cId == "bpjs" && (pCat.contains("bpjs") || pName.contains("bpjs"))) ||
            (cId == "premi_asuransi" && (pCat.contains("asuransi") || pName.contains("asuransi"))) ||
            (cId == "masa_aktif" && (pCat.contains("aktif") || pName.contains("aktif"))) ||
            (cId == "aktivasi_perdana" && (pCat.contains("perdana") || pName.contains("perdana"))) ||
            (cId == "voucher_belanja" && (pCat.contains("belanja") || pName.contains("voucher")))
        }
        return matched ?: allCategories.first()
    }

    private fun updatePriceDisplayFromCustom(dialogBinding: DialogPulsaInputBinding) {
        val customAmount = dialogBinding.etCustomNominal.text.toString().toDoubleOrNull() ?: 0.0
        val adminFee = 2500.0
        val total = customAmount + adminFee
        dialogBinding.tvPriceDisplay.text = CurrencyUtils.formatRupiah(total)
        dialogBinding.tvMarginDisplay.text = "Biaya Admin: ${CurrencyUtils.formatRupiah(adminFee)} • Modal: ${CurrencyUtils.formatRupiah(customAmount)}"
    }

    private fun sanitizePhoneNumber(raw: String): String {
        var clean = raw.replace(Regex("[^0-9+]"), "")
        if (clean.startsWith("+62")) {
            clean = "0" + clean.substring(3)
        } else if (clean.startsWith("62")) {
            clean = "0" + clean.substring(2)
        }
        return clean
    }

    private fun detectOperator(phone: String): String? {
        val clean = sanitizePhoneNumber(phone)
        return when {
            clean.startsWith("0851") -> "By.U"

            clean.startsWith("0811") || clean.startsWith("0812") || clean.startsWith("0813") ||
            clean.startsWith("0821") || clean.startsWith("0822") || clean.startsWith("0852") ||
            clean.startsWith("0853") || clean.startsWith("0823") -> "Telkomsel"

            clean.startsWith("0814") || clean.startsWith("0815") || clean.startsWith("0816") ||
            clean.startsWith("0855") || clean.startsWith("0856") || clean.startsWith("0857") ||
            clean.startsWith("0858") -> "Indosat Ooredoo"

            clean.startsWith("0817") || clean.startsWith("0818") || clean.startsWith("0819") ||
            clean.startsWith("0859") || clean.startsWith("0877") || clean.startsWith("0878") -> "XL Axiata"

            clean.startsWith("0895") || clean.startsWith("0896") || clean.startsWith("0897") ||
            clean.startsWith("0898") || clean.startsWith("0899") -> "Tri (3)"

            clean.startsWith("0881") || clean.startsWith("0882") || clean.startsWith("0883") ||
            clean.startsWith("0884") || clean.startsWith("0885") || clean.startsWith("0886") ||
            clean.startsWith("0887") || clean.startsWith("0888") || clean.startsWith("0889") -> "Smartfren"

            clean.startsWith("0831") || clean.startsWith("0832") || clean.startsWith("0833") ||
            clean.startsWith("0838") -> "Axis"

            else -> null
        }
    }

    private fun showPaymentBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val payBinding = DialogPaymentMethodBinding.inflate(layoutInflater)
        dialog.setContentView(payBinding.root)

        val totalAmount = viewModel.getTotalBayar()
        payBinding.tvDialogTotalTagihan.text = CurrencyUtils.formatRupiah(totalAmount)
        payBinding.etCashGiven.setText(totalAmount.toInt().toString())

        fun updateChange() {
            val given = payBinding.etCashGiven.text.toString().toDoubleOrNull() ?: 0.0
            val change = maxOf(0.0, given - totalAmount)
            payBinding.tvChangeAmount.text = CurrencyUtils.formatRupiah(change)
        }
        updateChange()

        payBinding.etCashGiven.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateChange()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        payBinding.btnExactAmount.setOnClickListener {
            payBinding.etCashGiven.setText(totalAmount.toInt().toString())
        }
        payBinding.btnFiftyK.setOnClickListener {
            payBinding.etCashGiven.setText("50000")
        }
        payBinding.btnHundredK.setOnClickListener {
            payBinding.etCashGiven.setText("100000")
        }

        payBinding.rgPaymentMethod.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbCash -> {
                    payBinding.tilCashGiven.visibility = View.VISIBLE
                    payBinding.quickCashLayout.visibility = View.VISIBLE
                    payBinding.changeLayout.visibility = View.VISIBLE
                    payBinding.layoutKasbonCustomerInfo.visibility = View.GONE
                }
                R.id.rbKasbon -> {
                    payBinding.tilCashGiven.visibility = View.GONE
                    payBinding.quickCashLayout.visibility = View.GONE
                    payBinding.changeLayout.visibility = View.GONE
                    payBinding.layoutKasbonCustomerInfo.visibility = View.VISIBLE
                }
                else -> {
                    payBinding.tilCashGiven.visibility = View.GONE
                    payBinding.quickCashLayout.visibility = View.GONE
                    payBinding.changeLayout.visibility = View.GONE
                    payBinding.layoutKasbonCustomerInfo.visibility = View.GONE
                }
            }
        }

        payBinding.btnConfirmPayment.setOnClickListener {
            val digitalInCart = CartManager.getCartItemsSnapshot().filter { it.product.productType == "DIGITAL" }
            if (digitalInCart.isNotEmpty()) {
                if (!isDigitalGatewayReady()) {
                    showGatewayRequiredDialog()
                    return@setOnClickListener
                }
                val emptyStock = digitalInCart.find { it.product.isDigitalOutOfStock() }
                if (emptyStock != null) {
                    showDigitalOutOfStockPaymentDialog(emptyStock.product.name)
                    return@setOnClickListener
                }
            }

            val method = when (payBinding.rgPaymentMethod.checkedRadioButtonId) {
                R.id.rbQris -> "QRIS"
                R.id.rbTransfer -> "TRANSFER"
                R.id.rbKasbon -> "KASBON"
                else -> "CASH"
            }
            val cashGiven = if (method == "CASH") {
                payBinding.etCashGiven.text.toString().toDoubleOrNull() ?: totalAmount
            } else {
                totalAmount
            }

            if (method == "CASH" && cashGiven < totalAmount) {
                Toast.makeText(requireContext(), "Uang yang diterima kurang dari total tagihan!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            var custName = "Pelanggan Warung"
            var custPhone: String? = null

            if (method == "KASBON") {
                val inputName = payBinding.etCustomerName.text.toString().trim()
                if (inputName.isEmpty()) {
                    payBinding.tilCustomerName.error = "Nama pelanggan wajib diisi untuk transaksi kasbon!"
                    Toast.makeText(requireContext(), "Silakan masukkan nama pelanggan kasbon!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                payBinding.tilCustomerName.error = null
                custName = inputName
                custPhone = payBinding.etCustomerPhone.text.toString().trim().ifEmpty { null }
            }

            // Ambil info produk digital dari keranjang sebelum checkout
            val digitalItem = CartManager.getCartItemsSnapshot().find { it.product.productType == "DIGITAL" }
            val rawPhone = digitalItem?.targetPhone
            val digitalPhone = if (rawPhone != null && rawPhone.contains("(")) {
                rawPhone.substring(0, rawPhone.indexOf("(")).trim()
            } else {
                rawPhone?.trim()
            }
            val digitalCode = digitalItem?.product?.providerCode ?: digitalItem?.product?.barcode ?: "PULSA"

            viewModel.checkout(
                paymentMethod = method,
                cashGiven = cashGiven,
                customerName = custName,
                customerPhone = custPhone
            ) { trx, receiptText ->
                dialog.dismiss()
                val intent = Intent(requireContext(), PaymentSuccessActivity::class.java).apply {
                    putExtra("INVOICE_NO", trx.invoiceNumber)
                    putExtra("TRX_ID", trx.id)
                    putExtra("RECEIPT_TEXT", receiptText)
                    putExtra("TOTAL_AMOUNT", trx.totalAmount)
                    if (digitalItem != null && !digitalPhone.isNullOrBlank()) {
                        putExtra("DIGITAL_PRODUCT_CODE", digitalCode)
                        putExtra("DIGITAL_TARGET_PHONE", digitalPhone)
                        putExtra("DIGITAL_CATEGORY", digitalItem.product.category)
                        putExtra("DIGITAL_PRODUCT_NAME", digitalItem.product.name)
                    }
                }
                startActivity(intent)
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
