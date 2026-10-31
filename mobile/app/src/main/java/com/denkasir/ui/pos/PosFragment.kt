package com.denkasir.ui.pos

import android.app.Activity
import android.content.Intent
import android.graphics.Color
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
import com.denkasir.data.local.entity.ProductEntity
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

class PosFragment : Fragment() {

    private var _binding: FragmentPosBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PosViewModel by viewModels()
    private lateinit var cartAdapter: CartAdapter
    private var availableProducts: List<ProductEntity> = emptyList()
    private lateinit var smartSearchAdapter: SmartProductSearchAdapter

    private var onContactPickedCallback: ((String) -> Unit)? = null

    private val barcodeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val barcode = result.data?.getStringExtra("SCANNED_BARCODE")
            val qty = result.data?.getDoubleExtra("SCANNED_QTY", 1.0) ?: 1.0
            if (!barcode.isNullOrEmpty()) {
                viewModel.addScannedBarcode(barcode, qty) { _, message ->
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
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
        val intent = Intent(requireContext(), BarcodeScannerActivity::class.java)
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
            showPaymentBottomSheet()
        }
    }

    private fun observeViewModel() {
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

        // Ensure bottom sheet expands nicely
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true

        val allCategories = DigitalCatalog.getAllCategories()
        val isiUlangItems = allCategories.filter { it.section == DigitalSection.ISI_ULANG }
        val tagihanItems = allCategories.filter { it.section == DigitalSection.TAGIHAN }

        var currentCategory: DigitalCategoryItem = allCategories.first { it.id == "pulsa" }
        var selectedOperator: String = currentCategory.operators.first()
        var selectedNominal: NominalOption = currentCategory.defaultNominals.let { list ->
            if (list.size >= 5) list[4] else list.first() // default to 100.000 for pulsa as in Image #2
        }

        // Setup Step 1 Adapters (Image #1: 5 Columns Grid)
        val isiUlangAdapter = DigitalCategoryAdapter { item ->
            navigateToServiceInput(item, dialogBinding, onCategorySelected = { cat ->
                currentCategory = cat
                selectedOperator = cat.operators.first()
            })
        }
        val tagihanAdapter = DigitalCategoryAdapter { item ->
            navigateToServiceInput(item, dialogBinding, onCategorySelected = { cat ->
                currentCategory = cat
                selectedOperator = cat.operators.first()
            })
        }

        dialogBinding.rvIsiUlangGrid.apply {
            layoutManager = GridLayoutManager(requireContext(), 5)
            adapter = isiUlangAdapter
        }
        isiUlangAdapter.submitList(isiUlangItems)

        dialogBinding.rvTagihanGrid.apply {
            layoutManager = GridLayoutManager(requireContext(), 5)
            adapter = tagihanAdapter
        }
        tagihanAdapter.submitList(tagihanItems)

        // Close / Back button on Step 1 (Image #1)
        dialogBinding.btnBackCategorySheet.setOnClickListener {
            dialog.dismiss()
        }

        // Setup Step 2 Nominal Grid Adapter (Image #2)
        val nominalAdapter = NominalGridAdapter { chosenNominal ->
            selectedNominal = chosenNominal
            if (chosenNominal.isCustom) {
                dialogBinding.containerCustomNominal.visibility = View.VISIBLE
                updatePriceDisplayFromCustom(dialogBinding)
            } else {
                dialogBinding.containerCustomNominal.visibility = View.GONE
                dialogBinding.tvPriceDisplay.text = CurrencyUtils.formatRupiah(chosenNominal.sellingPrice)
                val laba = chosenNominal.sellingPrice - chosenNominal.costPrice
                dialogBinding.tvMarginDisplay.text = "Modal: ${CurrencyUtils.formatRupiah(chosenNominal.costPrice)} • Laba: +${CurrencyUtils.formatRupiah(laba)}"
            }
        }

        dialogBinding.rvNominalGrid.apply {
            layoutManager = GridLayoutManager(requireContext(), 4)
            adapter = nominalAdapter
        }

        // Back button on Step 2 (Image #2) to go back to Step 1 (Image #1)
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

        // Operator Dropdown Click
        dialogBinding.containerOperatorDropdown.setOnClickListener {
            val ops = currentCategory.operators.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Pilih ${currentCategory.operatorLabel}")
                .setItems(ops) { _, which ->
                    selectedOperator = ops[which]
                    dialogBinding.tvSelectedOperator.text = selectedOperator
                }
                .show()
        }

        // Live Phone Number Operator Detection
        dialogBinding.etTargetNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s.toString().trim()
                if (currentCategory.id == "pulsa" || currentCategory.id == "paket_data" || currentCategory.id == "esim_lokal") {
                    val detected = detectOperator(input)
                    if (detected != null && currentCategory.operators.contains(detected)) {
                        selectedOperator = detected
                        dialogBinding.tvSelectedOperator.text = detected
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
            val target = dialogBinding.etTargetNumber.text.toString().trim()
            if (target.length < 4) {
                Toast.makeText(requireContext(), "Masukkan nomor / ID tujuan dengan benar!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val finalSellingPrice: Double
            val finalCostPrice: Double
            val itemTitle: String

            if (selectedNominal.isCustom) {
                val customInput = dialogBinding.etCustomNominal.text.toString().toDoubleOrNull() ?: 0.0
                if (customInput <= 0) {
                    Toast.makeText(requireContext(), "Masukkan nominal yang valid!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val adminFee = if (currentCategory.section == DigitalSection.TAGIHAN) 2500.0 else 2000.0
                finalSellingPrice = customInput + adminFee
                finalCostPrice = customInput
                itemTitle = "${currentCategory.title} $selectedOperator ${CurrencyUtils.formatRupiah(customInput)}"
            } else {
                finalSellingPrice = selectedNominal.sellingPrice
                finalCostPrice = selectedNominal.costPrice
                itemTitle = "${currentCategory.title} $selectedOperator ${selectedNominal.label}"
            }

            val digitalProduct = ProductEntity(
                id = UUID.randomUUID().toString(),
                name = itemTitle,
                category = currentCategory.title,
                productType = "DIGITAL",
                sellingPrice = finalSellingPrice,
                costPrice = finalCostPrice,
                providerCode = selectedOperator,
                stock = 0.0
            )

            val note = "$target ($selectedOperator)"
            viewModel.addToCart(digitalProduct, 1.0, note)
            dialog.dismiss()
            Toast.makeText(requireContext(), "$itemTitle berhasil ditambahkan ke kasir!", Toast.LENGTH_SHORT).show()
        }

        // If a preselected product was passed (e.g. from search)
        if (preselected != null) {
            val matchedCat = allCategories.find { 
                it.title.equals(preselected.category, ignoreCase = true) || 
                it.id.equals(preselected.category, ignoreCase = true) ||
                preselected.name.contains(it.title, ignoreCase = true)
            } ?: allCategories.first()
            
            navigateToServiceInput(matchedCat, dialogBinding, onCategorySelected = { cat ->
                currentCategory = cat
                selectedOperator = preselected.providerCode ?: cat.operators.first()
            })
        } else {
            dialogBinding.layoutCategorySelection.visibility = View.VISIBLE
            dialogBinding.layoutServiceInput.visibility = View.GONE
        }

        dialog.show()
    }

    private fun navigateToServiceInput(
        category: DigitalCategoryItem,
        dialogBinding: DialogPulsaInputBinding,
        onCategorySelected: (DigitalCategoryItem) -> Unit
    ) {
        onCategorySelected(category)

        dialogBinding.layoutCategorySelection.visibility = View.GONE
        dialogBinding.layoutServiceInput.visibility = View.VISIBLE

        // Update titles and hints
        dialogBinding.tvSelectedCategoryTitle.text = if (category.section == DigitalSection.ISI_ULANG) "Isi Ulang ${category.title}" else "Tagihan ${category.title}"
        dialogBinding.tvTargetNumberLabel.text = category.targetNumberLabel
        dialogBinding.etTargetNumber.hint = category.targetNumberHint
        dialogBinding.tvOperatorLabel.text = category.operatorLabel
        dialogBinding.tvSelectedOperator.text = category.operators.first()

        // Set Nominal list: Default to 100.000 (index 4) for pulsa if available
        val nominalAdapter = dialogBinding.rvNominalGrid.adapter as? NominalGridAdapter
        val defaultIdx = if (category.id == "pulsa" && category.defaultNominals.size > 4) 4 else 0
        nominalAdapter?.submitList(category.defaultNominals, defaultSelectIndex = defaultIdx)
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
                }
                else -> {
                    payBinding.tilCashGiven.visibility = View.GONE
                    payBinding.quickCashLayout.visibility = View.GONE
                    payBinding.changeLayout.visibility = View.GONE
                }
            }
        }

        payBinding.btnConfirmPayment.setOnClickListener {
            val method = when (payBinding.rgPaymentMethod.checkedRadioButtonId) {
                R.id.rbQris -> "QRIS"
                R.id.rbKasbon -> "KASBON"
                else -> "CASH"
            }
            val cashGiven = payBinding.etCashGiven.text.toString().toDoubleOrNull() ?: totalAmount

            if (method == "CASH" && cashGiven < totalAmount) {
                Toast.makeText(requireContext(), "Uang yang diterima kurang dari total tagihan!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.checkout(
                paymentMethod = method,
                cashGiven = cashGiven,
                customerName = "Pelanggan Warung"
            ) { trx, receiptText ->
                dialog.dismiss()
                val intent = Intent(requireContext(), PaymentSuccessActivity::class.java).apply {
                    putExtra("INVOICE_NO", trx.invoiceNumber)
                    putExtra("RECEIPT_TEXT", receiptText)
                    putExtra("TOTAL_AMOUNT", trx.totalAmount)
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