package com.denkasir.ui.pos

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.denkasir.R
import com.denkasir.data.local.AppDatabase
import com.denkasir.data.local.entity.CashFlowEntity
import com.denkasir.data.local.entity.PurchaseOrderEntity
import com.denkasir.data.local.entity.TransactionEntity
import com.denkasir.data.local.entity.TransactionItemEntity
import com.denkasir.databinding.DialogInputPurchaseBinding
import com.denkasir.databinding.FragmentTransactionsBinding
import com.denkasir.hardware.printer.BluetoothPrinterDriver
import com.denkasir.hardware.printer.PrinterSettingActivity
import com.denkasir.utils.CurrencyUtils
import com.denkasir.utils.DateFilterHelper
import com.denkasir.utils.DateFilterRange
import com.denkasir.utils.ReceiptFormatterUtils
import com.denkasir.utils.DigitalStatusDialogHelper
import com.denkasir.utils.ReportPeriodType
import com.denkasir.utils.SessionManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class TransactionsFragment : Fragment() {

    private var _binding: FragmentTransactionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var appDatabase: AppDatabase
    private lateinit var purchaseAdapter: PurchaseOrderAdapter
    private lateinit var transactionAdapter: TransactionAdapter

    private var activeTab = "DRAFT" // DRAFT, DIPESAN, SELESAI, KASIR
    private var cashierFilter = "ALL" // ALL, STAFF, OWNER

    // Filter Periode Waktu Transaksi Kasir
    private var currentPeriod: DateFilterRange = DateFilterHelper.getWeeklyRange()
    private var isChartExpanded = true

    private var allPurchases: List<PurchaseOrderEntity> = emptyList()
    private var allTransactions: List<TransactionEntity> = emptyList()
    private var allCashFlows: List<CashFlowEntity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appDatabase = AppDatabase.getDatabase(requireContext())

        setupAdapters()
        setupSubTabs()
        setupCashierFilters()
        setupPeriodFilters()
        setupListeners()
        observeData()

        updatePeriodChipsUI()
    }

    private fun setupAdapters() {
        purchaseAdapter = PurchaseOrderAdapter(
            onItemClick = { po ->
                showPurchaseDetailDialog(po)
            },
            onLongClick = { po ->
                showDeletePurchaseDialog(po)
            }
        )
        binding.rvPurchaseOrdersList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = purchaseAdapter
        }

        transactionAdapter = TransactionAdapter(
            onItemClick = { trx ->
                showCashierTransactionDetailDialog(trx)
            },
            onPrintClick = { trx ->
                printTransactionReceipt(trx)
            },
            onShareClick = { trx ->
                shareTransactionReceiptWhatsApp(trx)
            }
        )
        binding.rvCashierTransactionsList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = transactionAdapter
        }
    }

    private fun setupSubTabs() {
        binding.tabDraft.setOnClickListener { switchTab("DRAFT") }
        binding.tabDipesan.setOnClickListener { switchTab("DIPESAN") }
        binding.tabSelesai.setOnClickListener { switchTab("SELESAI") }
        binding.tabKasir.setOnClickListener { switchTab("KASIR") }
    }

    private fun switchTab(tab: String) {
        activeTab = tab
        val activeColor = Color.parseColor("#3E24E2")
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        binding.indicatorDraft.visibility = if (tab == "DRAFT") View.VISIBLE else View.INVISIBLE
        binding.tvTabDraftLabel.setTextColor(if (tab == "DRAFT") activeColor else inactiveColor)
        binding.tvTabDraftLabel.paint.isFakeBoldText = (tab == "DRAFT")

        binding.indicatorDipesan.visibility = if (tab == "DIPESAN") View.VISIBLE else View.INVISIBLE
        binding.tvTabDipesanLabel.setTextColor(if (tab == "DIPESAN") activeColor else inactiveColor)
        binding.tvTabDipesanLabel.paint.isFakeBoldText = (tab == "DIPESAN")

        binding.indicatorSelesai.visibility = if (tab == "SELESAI") View.VISIBLE else View.INVISIBLE
        binding.tvTabSelesaiLabel.setTextColor(if (tab == "SELESAI") activeColor else inactiveColor)
        binding.tvTabSelesaiLabel.paint.isFakeBoldText = (tab == "SELESAI")

        binding.indicatorKasir.visibility = if (tab == "KASIR") View.VISIBLE else View.INVISIBLE
        binding.tvTabKasirLabel.setTextColor(if (tab == "KASIR") activeColor else inactiveColor)
        binding.tvTabKasirLabel.paint.isFakeBoldText = (tab == "KASIR")

        if (tab == "KASIR") {
            binding.layoutCashierFilterBar.visibility = View.VISIBLE
            binding.rvPurchaseOrdersList.visibility = View.GONE
            binding.rvCashierTransactionsList.visibility = View.VISIBLE
            binding.btnAddNewPurchase.visibility = View.GONE
            filterAndDisplayCashierTransactions()
        } else {
            binding.layoutCashierFilterBar.visibility = View.GONE
            binding.rvPurchaseOrdersList.visibility = View.VISIBLE
            binding.rvCashierTransactionsList.visibility = View.GONE
            binding.btnAddNewPurchase.visibility = View.VISIBLE
            filterPurchases()
        }
    }

    private fun setupCashierFilters() {
        binding.chipFilterAllCashiers.setOnClickListener {
            setCashierFilter("ALL")
        }
        binding.chipFilterStaffOnly.setOnClickListener {
            setCashierFilter("STAFF")
        }
        binding.chipFilterOwnerOnly.setOnClickListener {
            setCashierFilter("OWNER")
        }
    }

    private fun setCashierFilter(filter: String) {
        cashierFilter = filter
        val context = requireContext()

        // Reset Chips Style
        binding.chipFilterAllCashiers.setBackgroundResource(if (filter == "ALL") R.drawable.bg_category_card_selected else R.drawable.bg_chip_unselected)
        binding.chipFilterAllCashiers.setTextColor(if (filter == "ALL") Color.WHITE else ContextCompat.getColor(context, R.color.text_secondary))
        binding.chipFilterAllCashiers.paint.isFakeBoldText = (filter == "ALL")

        binding.chipFilterStaffOnly.setBackgroundResource(if (filter == "STAFF") R.drawable.bg_category_card_selected else R.drawable.bg_chip_unselected)
        binding.chipFilterStaffOnly.setTextColor(if (filter == "STAFF") Color.WHITE else ContextCompat.getColor(context, R.color.text_secondary))
        binding.chipFilterStaffOnly.paint.isFakeBoldText = (filter == "STAFF")

        binding.chipFilterOwnerOnly.setBackgroundResource(if (filter == "OWNER") R.drawable.bg_category_card_selected else R.drawable.bg_chip_unselected)
        binding.chipFilterOwnerOnly.setTextColor(if (filter == "OWNER") Color.WHITE else ContextCompat.getColor(context, R.color.text_secondary))
        binding.chipFilterOwnerOnly.paint.isFakeBoldText = (filter == "OWNER")

        filterAndDisplayCashierTransactions()
    }

    private fun setupPeriodFilters() {
        binding.chipTrxFilterHariIni.setOnClickListener {
            applyPeriodFilter(DateFilterHelper.getTodayRange())
        }
        binding.chipTrxFilterMingguan.setOnClickListener {
            applyPeriodFilter(DateFilterHelper.getWeeklyRange())
        }
        binding.chipTrxFilterBulanan.setOnClickListener {
            applyPeriodFilter(DateFilterHelper.getMonthlyRange())
        }
        binding.chipTrxFilterKustom.setOnClickListener {
            showCustomDatePicker()
        }
        binding.chipTrxFilterSemua.setOnClickListener {
            applyPeriodFilter(DateFilterHelper.getAllTimeRange())
        }

        binding.btnToggleTrxChart.setOnClickListener {
            isChartExpanded = !isChartExpanded
            binding.trxChartView.visibility = if (isChartExpanded) View.VISIBLE else View.GONE
            binding.btnToggleTrxChart.text = if (isChartExpanded) "Sembunyikan" else "Tampilkan"
        }

        // Interaksi sentuh pada grafik kasir
        binding.trxChartView.onPointSelectedListener = { _, label, cIn, cOut ->
            binding.tvTrxActivePeriodLabel.text = "?? $label: Masuk ${CurrencyUtils.formatRupiah(cIn)} | Keluar ${CurrencyUtils.formatRupiah(cOut)}"
        }
    }

    private fun showCustomDatePicker() {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)

        val startDatePicker = DatePickerDialog(requireContext(), { _, sYear, sMonth, sDay ->
            val startCal = Calendar.getInstance().apply {
                set(sYear, sMonth, sDay, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startMs = startCal.timeInMillis

            val endDatePicker = DatePickerDialog(requireContext(), { _, eYear, eMonth, eDay ->
                val endCal = Calendar.getInstance().apply {
                    set(eYear, eMonth, eDay, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val endMs = endCal.timeInMillis

                val customRange = DateFilterHelper.getCustomRange(startMs, endMs)
                applyPeriodFilter(customRange)
            }, sYear, sMonth, sDay)

            endDatePicker.setTitle("Pilih Tanggal Selesai (Sampai)")
            endDatePicker.show()
        }, year, month, day)

        startDatePicker.setTitle("Pilih Tanggal Mulai (Dari)")
        startDatePicker.show()
    }

    private fun applyPeriodFilter(range: DateFilterRange) {
        currentPeriod = range
        updatePeriodChipsUI()
        filterAndDisplayCashierTransactions()
    }

    private fun updatePeriodChipsUI() {
        val context = requireContext()
        val activeBg = R.drawable.bg_category_card_selected
        val inactiveBg = R.drawable.bg_chip_unselected
        val activeText = Color.WHITE
        val inactiveText = ContextCompat.getColor(context, R.color.text_secondary)

        val t = currentPeriod.type
        binding.chipTrxFilterHariIni.setBackgroundResource(if (t == ReportPeriodType.HARIAN) activeBg else inactiveBg)
        binding.chipTrxFilterHariIni.setTextColor(if (t == ReportPeriodType.HARIAN) activeText else inactiveText)
        binding.chipTrxFilterHariIni.paint.isFakeBoldText = (t == ReportPeriodType.HARIAN)

        binding.chipTrxFilterMingguan.setBackgroundResource(if (t == ReportPeriodType.MINGGUAN) activeBg else inactiveBg)
        binding.chipTrxFilterMingguan.setTextColor(if (t == ReportPeriodType.MINGGUAN) activeText else inactiveText)
        binding.chipTrxFilterMingguan.paint.isFakeBoldText = (t == ReportPeriodType.MINGGUAN)

        binding.chipTrxFilterBulanan.setBackgroundResource(if (t == ReportPeriodType.BULANAN) activeBg else inactiveBg)
        binding.chipTrxFilterBulanan.setTextColor(if (t == ReportPeriodType.BULANAN) activeText else inactiveText)
        binding.chipTrxFilterBulanan.paint.isFakeBoldText = (t == ReportPeriodType.BULANAN)

        binding.chipTrxFilterKustom.setBackgroundResource(if (t == ReportPeriodType.KUSTOM) activeBg else inactiveBg)
        binding.chipTrxFilterKustom.setTextColor(if (t == ReportPeriodType.KUSTOM) activeText else inactiveText)
        binding.chipTrxFilterKustom.paint.isFakeBoldText = (t == ReportPeriodType.KUSTOM)

        binding.chipTrxFilterSemua.setBackgroundResource(if (t == ReportPeriodType.SEMUA) activeBg else inactiveBg)
        binding.chipTrxFilterSemua.setTextColor(if (t == ReportPeriodType.SEMUA) activeText else inactiveText)
        binding.chipTrxFilterSemua.paint.isFakeBoldText = (t == ReportPeriodType.SEMUA)

        binding.tvTrxActivePeriodLabel.text = "?? " + currentPeriod.detailedLabel
        binding.tvTrxChartTitle.text = "Grafik Arus Kas & Transaksi (" + currentPeriod.shortLabel + ")"
    }

    private fun filterAndDisplayCashierTransactions() {
        val range = currentPeriod

        // 1. Filter transaksi berdasarkan periode waktu & tipe kasir
        val filtered = allTransactions.filter { trx ->
            val inPeriod = trx.createdAt in range.startMs..range.endMs
            if (!inPeriod) return@filter false

            val name = trx.cashierName ?: ""
            when (cashierFilter) {
                "STAFF" -> !name.contains("Owner", ignoreCase = true) && !trx.userId.contains("owner", ignoreCase = true)
                "OWNER" -> name.contains("Owner", ignoreCase = true) || trx.userId.contains("owner", ignoreCase = true)
                else -> true
            }
        }

        transactionAdapter.submitList(filtered)
        binding.tvEmptyMessage.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE

        // 2. Update Ringkasan Omset
        val totalOmset = filtered.sumOf { it.totalAmount }
        val count = filtered.size
        binding.tvSummaryCashierTotalAmount.text = CurrencyUtils.formatRupiah(totalOmset)
        binding.tvSummaryCashierTrxCount.text = "$count Transaksi"

        binding.tvSummaryCashierFilterTitle.text = when (cashierFilter) {
            "STAFF" -> "Omset Transaksi Kasir (Staff)"
            "OWNER" -> "Omset Transaksi Pemilik (Owner)"
            else -> "Total Omset Semua Akun Kasir"
        }

        // 3. Update Grafik Arus Kas Dinamis di Tab Kasir
        val periodCashFlows = allCashFlows.filter { it.createdAt in range.startMs..range.endMs }
        val bucketResult = DateFilterHelper.buildChartBuckets(range, periodCashFlows)
        binding.trxChartView.setChartData(bucketResult.labels, bucketResult.cashIn, bucketResult.cashOut)
    }

    private fun setupListeners() {
        binding.btnAddNewPurchase.setOnClickListener {
            showAddPurchaseDialog()
        }

        binding.btnBackTransactions.setOnClickListener {
            switchTab("DRAFT")
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            appDatabase.purchaseOrderDao().getAllPurchases().collectLatest { purchases ->
                allPurchases = purchases
                if (activeTab != "KASIR") {
                    filterPurchases()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            appDatabase.transactionDao().getAllTransactions().collectLatest { trxs ->
                allTransactions = trxs
                if (activeTab == "KASIR") {
                    filterAndDisplayCashierTransactions()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            appDatabase.cashFlowDao().getAllCashFlows().collectLatest { flows ->
                allCashFlows = flows
                if (activeTab == "KASIR") {
                    filterAndDisplayCashierTransactions()
                }
            }
        }
    }

    private fun filterPurchases() {
        val statusTarget = when (activeTab) {
            "DIPESAN" -> "Dipesan"
            "SELESAI" -> "Selesai"
            else -> "Draft"
        }
        val filtered = allPurchases.filter { it.status.equals(statusTarget, ignoreCase = true) }
        purchaseAdapter.submitList(filtered)
        binding.tvEmptyMessage.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showAddPurchaseDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogInputPurchaseBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("in", "ID"))
        val todayStr = dateFormat.format(Date())

        dialogBinding.btnCancelPurchase.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSavePurchase.setOnClickListener {
            val name = dialogBinding.etPurchaseSupplierName.text.toString().trim()
            val items = dialogBinding.etPurchaseItemCount.text.toString().toIntOrNull() ?: 1
            val total = dialogBinding.etPurchaseTotalAmount.text.toString().toDoubleOrNull() ?: 0.0

            if (name.isBlank()) {
                Toast.makeText(requireContext(), "Nama supplier / distributor wajib diisi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val po = PurchaseOrderEntity(
                supplierName = name,
                dateText = todayStr,
                itemCount = items,
                totalAmount = total,
                status = "Draft"
            )

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                appDatabase.purchaseOrderDao().insertPurchase(po)
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(requireContext(), "Draft belanja berhasil ditambahkan!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun showPurchaseDetailDialog(po: PurchaseOrderEntity) {
        val statuses = arrayOf("Draft", "Dipesan", "Selesai")
        val currentIndex = statuses.indexOf(po.status).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle("Ubah Status Belanja: ${po.supplierName}")
            .setSingleChoiceItems(statuses, currentIndex) { dialog, which ->
                val newStatus = statuses[which]
                dialog.dismiss()
                if (newStatus != po.status) {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        appDatabase.purchaseOrderDao().updatePurchase(po.copy(status = newStatus))
                        if (newStatus == "Selesai") {
                            appDatabase.cashFlowDao().insertCashFlow(
                                CashFlowEntity(
                                    id = UUID.randomUUID().toString(),
                                    flowType = "OUT",
                                    category = "Belanja Stok Barang / Kulakan",
                                    amount = po.totalAmount,
                                    description = "Belanja ${po.itemCount} item dari ${po.supplierName}",
                                    referenceId = po.id,
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Status belanja diubah menjadi '$newStatus'", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun showDeletePurchaseDialog(po: PurchaseOrderEntity) {
        if (!SessionManager.getInstance(requireContext()).isOwner()) {
            Toast.makeText(requireContext(), "Hanya OWNER yang dapat menghapus transaksi belanja!", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Daftar Belanja")
            .setMessage("Yakin ingin menghapus transaksi belanja '${po.supplierName}' (${CurrencyUtils.formatRupiah(po.totalAmount)})?")
            .setPositiveButton("Hapus") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    appDatabase.purchaseOrderDao().deletePurchase(po)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Transaksi belanja dihapus", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showCashierTransactionDetailDialog(trx: TransactionEntity) {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                appDatabase.transactionDao().getTransactionItems(trx.id)
            }

            val cashierName = if (!trx.cashierName.isNullOrBlank()) {
                trx.cashierName
            } else if (trx.userId.contains("owner", ignoreCase = true)) {
                "Pemilik Toko (Owner)"
            } else {
                "Kasir 1 (Staff)"
            }

            val details = StringBuilder()
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("in", "ID"))
            details.append("No. Nota    : ${trx.invoiceNumber}\n")
            details.append("Waktu       : ${dateFormat.format(Date(trx.createdAt))}\n")
            details.append("Akun Kasir  : $cashierName\n")
            details.append("ID Pengguna : ${trx.userId}\n")
            details.append("Pelanggan   : ${trx.customerName ?: "Pelanggan Warung"}\n")
            details.append("Pembayaran  : ${trx.paymentMethod}\n")
            details.append("--------------------------------\n")

            items.forEach { item ->
                details.append("- ${item.productName}\n")
                if (!item.targetPhoneNumber.isNullOrBlank()) {
                    details.append("  Tujuan : ${item.targetPhoneNumber}\n")
                    val statusLabel = when (item.digitalStatus.uppercase()) {
                        "SUCCESS", "SUKSES" -> "?? SUKSES"
                        "GANGGUAN", "GAGAL" -> "?? GANGGUAN"
                        "PROSES", "PENDING" -> "?? PROSES (Menunggu Balasan)"
                        else -> item.digitalStatus
                    }
                    details.append("  Status : $statusLabel\n")
                }
                if (!item.snNumber.isNullOrBlank()) {
                    val snLabel = if (item.productName.contains("PLN", ignoreCase = true) || item.productName.contains("Token", ignoreCase = true)) "  Token  : " else "  SN     : "
                    details.append("$snLabel${item.snNumber}\n")
                }
                val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
                details.append("  $qtyStr x ${CurrencyUtils.formatNumberOnly(item.sellingPrice)} = ${CurrencyUtils.formatNumberOnly(item.subtotal)}\n")
            }

            details.append("--------------------------------\n")
            details.append("Total Bayar : ${CurrencyUtils.formatRupiah(trx.totalAmount)}\n")
            if (trx.paymentMethod == "CASH") {
                details.append("Uang Tunai  : ${CurrencyUtils.formatRupiah(trx.cashGiven)}\n")
                details.append("Kembalian   : ${CurrencyUtils.formatRupiah(trx.changeAmount)}\n")
            }

            val hasDigital = items.any {
                !it.targetPhoneNumber.isNullOrBlank() || it.snNumber != null ||
                it.digitalStatus in listOf("PROSES", "PENDING", "GANGGUAN", "FAILED", "SUCCESS", "SUKSES") &&
                trx.status != "COMPLETED"
            }

            val dialogBuilder = AlertDialog.Builder(requireContext())
                .setTitle("Rincian Transaksi Kasir")
                .setMessage(details.toString())
                .setPositiveButton("Tutup", null)
                .setNeutralButton("Cetak Struk") { _, _ ->
                    printTransactionReceipt(trx)
                }

            if (hasDigital) {
                dialogBuilder.setNegativeButton("?? Update Status Server") { _, _ ->
                    DigitalStatusDialogHelper.showUpdateStatusDialog(
                        context = requireContext(),
                        appDatabase = appDatabase,
                        transaction = trx,
                        scope = lifecycleScope,
                        onSuccess = { updatedTrx, _ ->
                            observeData()
                        }
                    )
                }
            }

            dialogBuilder.show()
        }
    }

    private fun printTransactionReceipt(trx: TransactionEntity) {
        val currentContext = context ?: return
        lifecycleScope.launch {
            Toast.makeText(currentContext, "Mengirim struk #" + trx.invoiceNumber + " ke printer...", Toast.LENGTH_SHORT).show()
            val items = withContext(Dispatchers.IO) {
                appDatabase.transactionDao().getTransactionItems(trx.id)
            }
            val receiptBytes = ReceiptFormatterUtils.buildEscPosReceiptBytes(currentContext, trx, items)
            val result = BluetoothPrinterDriver.printReceipt(currentContext, receiptBytes)
            when (result) {
                is BluetoothPrinterDriver.PrintResult.Success -> {
                    Toast.makeText(currentContext, "Struk #" + trx.invoiceNumber + " berhasil dicetak!", Toast.LENGTH_SHORT).show()
                }
                is BluetoothPrinterDriver.PrintResult.Error -> {
                    if (isAdded) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Gagal Mencetak Struk")
                        AlertDialog.Builder(requireContext())
                            .setTitle("Gagal Mencetak Struk")
                            .setMessage(result.message + "\n\nApakah Anda ingin membuka Pengaturan Printer Bluetooth?")
                            .setPositiveButton("Buka Pengaturan") { _, _ ->
                                startActivity(Intent(requireContext(), PrinterSettingActivity::class.java))
                            }
                            .setNegativeButton("Tutup", null)
                            .show()
                    }
                }
            }
        }
    }

    private fun shareTransactionReceiptWhatsApp(trx: TransactionEntity) {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                appDatabase.transactionDao().getTransactionItems(trx.id)
            }
            val receiptText = buildReceiptText(trx, items)
            try {
                val encoded = URLEncoder.encode(receiptText, "UTF-8")
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?text=$encoded")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "WhatsApp tidak terpasang di HP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildReceiptText(trx: TransactionEntity, items: List<TransactionItemEntity>): String {
        val currentContext = context ?: return ""
        return ReceiptFormatterUtils.formatTransactionReceipt(
            context = currentContext,
            transaction = trx,
            items = items
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
