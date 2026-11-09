package com.denkasir.ui.bku

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
import com.denkasir.data.local.entity.BkuAccountEntity
import com.denkasir.data.local.entity.CashFlowEntity
import com.denkasir.data.local.entity.DebtEntity
import com.denkasir.data.local.entity.TransactionEntity
import com.denkasir.data.local.entity.TransactionItemEntity
import com.denkasir.databinding.*
import com.denkasir.hardware.printer.PrinterSettingActivity
import com.denkasir.ui.stockopname.StockOpnameActivity
import com.denkasir.utils.CurrencyUtils
import com.denkasir.utils.DataExporterImporter
import com.denkasir.utils.DateFilterHelper
import com.denkasir.utils.DateFilterRange
import com.denkasir.utils.ReportPeriodType
import com.denkasir.utils.SessionManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    private lateinit var appDatabase: AppDatabase
    private lateinit var cashFlowAdapter: CashFlowAdapter
    private lateinit var debtAdapter: DebtAdapter
    private lateinit var bkuAccountAdapter: BkuAccountAdapter

    private var latestTotalRevenue = 0.0
    private var latestTotalHpp = 0.0
    private var latestGrossProfit = 0.0

    private var currentSubTab = "RINGKASAN"
    private var cashFlowFilter = "ALL"
    private var debtTypeFilter = "PIUTANG"
    private var accountTypeFilter = "ALL"

    // Filter Periode Waktu Laporan: Harian, Mingguan (default 7 hari), Bulanan, Kustom, Semua
    private var currentPeriod: DateFilterRange = DateFilterHelper.getWeeklyRange()

    private var allCashFlows: List<CashFlowEntity> = emptyList()
    private var allTransactions: List<TransactionEntity> = emptyList()
    private var allTransactionItems: List<TransactionItemEntity> = emptyList()
    private var allDebts: List<DebtEntity> = emptyList()
    private var allAccounts: List<BkuAccountEntity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appDatabase = AppDatabase.getDatabase(requireContext())

        setupAdapters()
        setupSubTabs()
        setupPeriodFilters()
        setupListeners()
        observeData()

        updatePeriodChipsUI()
    }

    private fun setupAdapters() {
        cashFlowAdapter = CashFlowAdapter(
            onDeleteClick = { flow ->
                showDeleteConfirmDialog("Hapus Catatan Kas", "Yakin ingin menghapus mutasi '${flow.description}'?") {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        appDatabase.cashFlowDao().deleteCashFlow(flow)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Catatan kas dihapus", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
        binding.rvCashFlowList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = cashFlowAdapter
        }

        debtAdapter = DebtAdapter(
            onPayClick = { debt ->
                showPayDebtDialog(debt)
            },
            onRemindClick = { debt ->
                sendWhatsAppReminder(debt)
            },
            onDeleteClick = { debt ->
                showDeleteConfirmDialog("Hapus Data Hutang", "Hapus catatan atas nama '${debt.customerName}'?") {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        appDatabase.debtDao().deleteDebt(debt)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Catatan hutang/piutang dihapus", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
        binding.rvDebtsList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = debtAdapter
        }

        bkuAccountAdapter = BkuAccountAdapter(
            onDeleteClick = { account ->
                showDeleteConfirmDialog("Hapus Akun BKU", "Hapus akun '${account.accountName}' (${account.accountCode})?") {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        appDatabase.bkuAccountDao().deleteAccount(account)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Akun BKU dihapus", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
        binding.rvBkuAccountsList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = bkuAccountAdapter
        }
    }

    private fun setupSubTabs() {
        binding.tabRingkasan.setOnClickListener { switchSubTab("RINGKASAN") }
        binding.tabKas.setOnClickListener { switchSubTab("KAS") }
        binding.tabHutangPiutang.setOnClickListener { switchSubTab("HUTANG") }
        binding.tabAkunBku.setOnClickListener { switchSubTab("AKUN") }
    }

    private fun switchSubTab(tab: String) {
        currentSubTab = tab
        val activeColor = Color.parseColor("#3E24E2")
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        binding.indicatorRingkasan.visibility = if (tab == "RINGKASAN") View.VISIBLE else View.INVISIBLE
        binding.tvTabRingkasanLabel.setTextColor(if (tab == "RINGKASAN") activeColor else inactiveColor)
        binding.tvTabRingkasanLabel.paint.isFakeBoldText = (tab == "RINGKASAN")

        binding.indicatorKas.visibility = if (tab == "KAS") View.VISIBLE else View.INVISIBLE
        binding.tvTabKasLabel.setTextColor(if (tab == "KAS") activeColor else inactiveColor)
        binding.tvTabKasLabel.paint.isFakeBoldText = (tab == "KAS")

        binding.indicatorHutang.visibility = if (tab == "HUTANG") View.VISIBLE else View.INVISIBLE
        binding.tvTabHutangLabel.setTextColor(if (tab == "HUTANG") activeColor else inactiveColor)
        binding.tvTabHutangLabel.paint.isFakeBoldText = (tab == "HUTANG")

        binding.indicatorAkun.visibility = if (tab == "AKUN") View.VISIBLE else View.INVISIBLE
        binding.tvTabAkunLabel.setTextColor(if (tab == "AKUN") activeColor else inactiveColor)
        binding.tvTabAkunLabel.paint.isFakeBoldText = (tab == "AKUN")

        binding.layoutRingkasan.visibility = if (tab == "RINGKASAN") View.VISIBLE else View.GONE
        binding.layoutBukuKas.visibility = if (tab == "KAS") View.VISIBLE else View.GONE
        binding.layoutHutangPiutang.visibility = if (tab == "HUTANG") View.VISIBLE else View.GONE
        binding.layoutKelolaAkun.visibility = if (tab == "AKUN") View.VISIBLE else View.GONE

        // Bar filter waktu hanya muncul pada Ringkasan & Buku Kas
        binding.layoutDateFilterBar.visibility = if (tab == "RINGKASAN" || tab == "KAS") View.VISIBLE else View.GONE
    }

    private fun setupPeriodFilters() {
        binding.chipFilterHarian.setOnClickListener {
            applyPeriodFilter(DateFilterHelper.getTodayRange())
        }
        binding.chipFilterMingguan.setOnClickListener {
            applyPeriodFilter(DateFilterHelper.getWeeklyRange())
        }
        binding.chipFilterBulanan.setOnClickListener {
            applyPeriodFilter(DateFilterHelper.getMonthlyRange())
        }
        binding.chipFilterKustom.setOnClickListener {
            showCustomDatePicker()
        }
        binding.chipFilterSemua.setOnClickListener {
            applyPeriodFilter(DateFilterHelper.getAllTimeRange())
        }
    }

    private fun showCustomDatePicker() {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)

        // 1. Dialog Pilih Tanggal Mulai
        val startDatePicker = DatePickerDialog(requireContext(), { _, sYear, sMonth, sDay ->
            val startCal = Calendar.getInstance().apply {
                set(sYear, sMonth, sDay, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startMs = startCal.timeInMillis

            // 2. Dialog Pilih Tanggal Selesai
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
        recalculateAllMetrics()
    }

    private fun updatePeriodChipsUI() {
        val context = requireContext()
        val activeBg = R.drawable.bg_chip_selected
        val inactiveBg = R.drawable.bg_chip_unselected
        val activeText = Color.WHITE
        val inactiveText = ContextCompat.getColor(context, R.color.text_secondary)

        val t = currentPeriod.type
        binding.chipFilterHarian.setBackgroundResource(if (t == ReportPeriodType.HARIAN) activeBg else inactiveBg)
        binding.chipFilterHarian.setTextColor(if (t == ReportPeriodType.HARIAN) activeText else inactiveText)
        binding.chipFilterHarian.paint.isFakeBoldText = (t == ReportPeriodType.HARIAN)

        binding.chipFilterMingguan.setBackgroundResource(if (t == ReportPeriodType.MINGGUAN) activeBg else inactiveBg)
        binding.chipFilterMingguan.setTextColor(if (t == ReportPeriodType.MINGGUAN) activeText else inactiveText)
        binding.chipFilterMingguan.paint.isFakeBoldText = (t == ReportPeriodType.MINGGUAN)

        binding.chipFilterBulanan.setBackgroundResource(if (t == ReportPeriodType.BULANAN) activeBg else inactiveBg)
        binding.chipFilterBulanan.setTextColor(if (t == ReportPeriodType.BULANAN) activeText else inactiveText)
        binding.chipFilterBulanan.paint.isFakeBoldText = (t == ReportPeriodType.BULANAN)

        binding.chipFilterKustom.setBackgroundResource(if (t == ReportPeriodType.KUSTOM) activeBg else inactiveBg)
        binding.chipFilterKustom.setTextColor(if (t == ReportPeriodType.KUSTOM) activeText else inactiveText)
        binding.chipFilterKustom.paint.isFakeBoldText = (t == ReportPeriodType.KUSTOM)

        binding.chipFilterSemua.setBackgroundResource(if (t == ReportPeriodType.SEMUA) activeBg else inactiveBg)
        binding.chipFilterSemua.setTextColor(if (t == ReportPeriodType.SEMUA) activeText else inactiveText)
        binding.chipFilterSemua.paint.isFakeBoldText = (t == ReportPeriodType.SEMUA)

        binding.tvActiveDateRangeLabel.text = "?? " + currentPeriod.detailedLabel
        binding.tvPeriode.text = currentPeriod.detailedLabel
        binding.tvChartTitle.text = "Grafik Arus Kas (" + currentPeriod.shortLabel + ")"
    }

    private fun setupListeners() {
        binding.btnHeaderAddCash.setOnClickListener {
            showInputCashFlowDialog()
        }

        binding.btnQuickCashIn.setOnClickListener {
            showInputCashFlowDialog(defaultType = "IN")
        }
        binding.btnQuickCashOut.setOnClickListener {
            showInputCashFlowDialog(defaultType = "OUT")
        }

        binding.btnAddNewCashFlow.setOnClickListener {
            showInputCashFlowDialog()
        }
        binding.btnFilterAllCash.setOnClickListener {
            cashFlowFilter = "ALL"
            updateCashFlowFilterUI()
        }
        binding.btnFilterInCash.setOnClickListener {
            cashFlowFilter = "IN"
            updateCashFlowFilterUI()
        }
        binding.btnFilterOutCash.setOnClickListener {
            cashFlowFilter = "OUT"
            updateCashFlowFilterUI()
        }

        binding.btnTogglePiutang.setOnClickListener {
            debtTypeFilter = "PIUTANG"
            updateDebtTypeUI()
        }
        binding.btnToggleHutang.setOnClickListener {
            debtTypeFilter = "HUTANG"
            updateDebtTypeUI()
        }
        binding.btnAddNewDebt.setOnClickListener {
            showInputDebtDialog(debtTypeFilter)
        }

        binding.btnAddNewBkuAccount.setOnClickListener {
            showCreateAccountDialog()
        }
        binding.btnFilterAccAll.setOnClickListener {
            accountTypeFilter = "ALL"
            updateAccountFilterUI()
        }
        binding.btnFilterAccIn.setOnClickListener {
            accountTypeFilter = "IN"
            updateAccountFilterUI()
        }
        binding.btnFilterAccOut.setOnClickListener {
            accountTypeFilter = "OUT"
            updateAccountFilterUI()
        }

        binding.cardDownloadPdf.setOnClickListener {
            exportBkuReport()
        }
        binding.cardPrinterStruk.setOnClickListener {
            startActivity(Intent(requireContext(), PrinterSettingActivity::class.java))
        }
        binding.btnBukaStokOpname.setOnClickListener {
            startActivity(Intent(requireContext(), StockOpnameActivity::class.java))
        }

        // Interaksi sentuh pada grafik untuk melihat nominal detail
        binding.chartView.onPointSelectedListener = { _, label, cIn, cOut ->
            binding.tvActiveDateRangeLabel.text = "?? $label: Masuk ${CurrencyUtils.formatRupiah(cIn)} | Keluar ${CurrencyUtils.formatRupiah(cOut)}"
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            appDatabase.storeDao().getStoreFlow().collectLatest { store ->
                binding.tvStoreHeaderSub.text = store?.name?.uppercase() ?: SessionManager.getInstance(requireContext()).getStoreName().uppercase()
            }
        }

        // 1. Amati Transaksi Penjualan
        viewLifecycleOwner.lifecycleScope.launch {
            appDatabase.transactionDao().getAllTransactions().collectLatest { trxs ->
                allTransactions = trxs
                recalculateAllMetrics()
            }
        }

        // 2. Amati Rincian Item Penjualan
        viewLifecycleOwner.lifecycleScope.launch {
            appDatabase.transactionDao().getAllTransactionItemsFlow().collectLatest { items ->
                allTransactionItems = items
                recalculateAllMetrics()
            }
        }

        // 3. Amati Buku Kas Umum (Mutasi Kas BKU)
        viewLifecycleOwner.lifecycleScope.launch {
            appDatabase.cashFlowDao().getAllCashFlows().collectLatest { flows ->
                allCashFlows = flows
                recalculateAllMetrics()
            }
        }

        // 4. Amati Catatan Hutang & Piutang
        viewLifecycleOwner.lifecycleScope.launch {
            appDatabase.debtDao().getAllDebts().collectLatest { debts ->
                allDebts = debts
                filterAndSubmitDebts()
            }
        }

        // 5. Amati Master Rekening/Akun BKU
        viewLifecycleOwner.lifecycleScope.launch {
            appDatabase.bkuAccountDao().getAllAccounts().collectLatest { accounts ->
                allAccounts = accounts
                filterAndSubmitAccounts()
            }
        }
    }

    /**
     * Menghitung ulang seluruh metrik keuangan secara dinamis sesuai periode yang dipilih.
     */
    private fun recalculateAllMetrics() {
        val range = currentPeriod

        // 1. Filter Penjualan & Laba Kotor berdasarkan Tanggal Transaksi
        val periodTrxIds = allTransactions
            .filter { it.createdAt in range.startMs..range.endMs }
            .map { it.id }
            .toSet()

        val periodItems = allTransactionItems.filter { it.transactionId in periodTrxIds }
        latestTotalRevenue = periodItems.sumOf { it.subtotal }
        latestTotalHpp = periodItems.sumOf { it.costPrice * it.quantity }
        latestGrossProfit = latestTotalRevenue - latestTotalHpp

        binding.tvOmzetValue.text = CurrencyUtils.formatRupiah(latestTotalRevenue)
        binding.tvHppValue.text = "- " + CurrencyUtils.formatRupiah(latestTotalHpp)
        binding.tvGrossProfitValue.text = (if (latestGrossProfit >= 0) "+ " else "") + CurrencyUtils.formatRupiah(latestGrossProfit)

        // 2. Filter Mutasi Arus Kas berdasarkan Periode
        val periodCashFlows = allCashFlows.filter { it.createdAt in range.startMs..range.endMs }
        val totalIn = periodCashFlows.filter { it.flowType == "IN" }.sumOf { it.amount }
        val totalOut = periodCashFlows.filter { it.flowType == "OUT" }.sumOf { it.amount }
        val netCash = totalIn - totalOut

        binding.tvKasMasukValue.text = CurrencyUtils.formatRupiah(totalIn)
        binding.tvKasKeluarValue.text = CurrencyUtils.formatRupiah(totalOut)
        binding.tvLabaBersihValue.text = CurrencyUtils.formatRupiah(netCash)
        binding.tvLabaBersihValue.setTextColor(if (netCash >= 0) Color.parseColor("#10B981") else Color.parseColor("#EF4444"))

        // 3. Perhitungan Beban Operasional & Laba Bersih Riil (P&L)
        val opExpenses = periodCashFlows.filter {
            it.flowType == "OUT" && (
                it.category.contains("OPERASIONAL", ignoreCase = true) ||
                it.category.contains("BEBAN", ignoreCase = true) ||
                it.category.contains("LISTRIK", ignoreCase = true) ||
                it.category.contains("GAJI", ignoreCase = true) ||
                it.category.contains("SEWA", ignoreCase = true) ||
                it.category.contains("SELISIH STOK", ignoreCase = true) ||
                it.category.contains("PENYUSUTAN", ignoreCase = true)
            )
        }.sumOf { it.amount }

        val realNetProfit = latestGrossProfit - opExpenses
        val marginPct = if (latestTotalRevenue > 0) (latestGrossProfit / latestTotalRevenue) * 100.0 else 0.0

        binding.tvOperationalExpenseValue.text = "- " + CurrencyUtils.formatRupiah(opExpenses)
        binding.tvRealNetProfitValue.text = (if (realNetProfit >= 0) "+ " else "") + CurrencyUtils.formatRupiah(realNetProfit)
        binding.tvRealNetProfitValue.setTextColor(if (realNetProfit >= 0) Color.parseColor("#059669") else Color.parseColor("#DC2626"))
        binding.tvMarginPercentBadge.text = String.format(Locale.US, "Margin: %.1f%%", marginPct)

        // 4. Update Grafik Arus Kas Dinamis Real-Time
        val bucketResult = DateFilterHelper.buildChartBuckets(range, periodCashFlows)
        binding.chartView.setChartData(bucketResult.labels, bucketResult.cashIn, bucketResult.cashOut)

        // 5. Update Daftar Riwayat Mutasi Buku Kas
        filterAndSubmitCashFlows()
    }

    private fun filterAndSubmitCashFlows() {
        val range = currentPeriod
        val periodFlows = allCashFlows.filter { it.createdAt in range.startMs..range.endMs }

        val filtered = when (cashFlowFilter) {
            "IN" -> periodFlows.filter { it.flowType == "IN" }
            "OUT" -> periodFlows.filter { it.flowType == "OUT" }
            else -> periodFlows
        }
        cashFlowAdapter.submitList(filtered)
        binding.tvEmptyCashFlow.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateCashFlowFilterUI() {
        val activeBg = Color.parseColor("#3E24E2")
        val activeText = Color.WHITE

        binding.btnFilterAllCash.setBackgroundColor(if (cashFlowFilter == "ALL") activeBg else Color.parseColor("#EBEDFE"))
        binding.btnFilterAllCash.setTextColor(if (cashFlowFilter == "ALL") activeText else Color.parseColor("#3E24E2"))

        binding.btnFilterInCash.setBackgroundColor(if (cashFlowFilter == "IN") Color.parseColor("#059669") else Color.parseColor("#E6F8F0"))
        binding.btnFilterInCash.setTextColor(if (cashFlowFilter == "IN") activeText else Color.parseColor("#059669"))

        binding.btnFilterOutCash.setBackgroundColor(if (cashFlowFilter == "OUT") Color.parseColor("#DC2626") else Color.parseColor("#FEF2F2"))
        binding.btnFilterOutCash.setTextColor(if (cashFlowFilter == "OUT") activeText else Color.parseColor("#DC2626"))

        filterAndSubmitCashFlows()
    }

    private fun filterAndSubmitDebts() {
        val filtered = allDebts.filter { it.debtType.equals(debtTypeFilter, ignoreCase = true) }
        debtAdapter.submitList(filtered)
        binding.tvEmptyDebts.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE

        val unpaidTotal = filtered.filter { it.status != "PAID" }.sumOf { maxOf(0.0, it.totalDebt - it.paidAmount) }
        val label = if (debtTypeFilter == "PIUTANG") "Total Sisa Piutang Aktif" else "Total Sisa Hutang Supplier"
        binding.tvDebtSummaryLabel.text = label
        binding.tvDebtSummaryTotal.text = CurrencyUtils.formatRupiah(unpaidTotal)
    }

    private fun updateDebtTypeUI() {
        val activeBg = Color.parseColor("#3E24E2")
        val activeText = Color.WHITE
        val inactiveBg = Color.parseColor("#EBEDFE")
        val inactiveText = Color.parseColor("#334155")

        if (debtTypeFilter == "PIUTANG") {
            binding.btnTogglePiutang.setBackgroundColor(activeBg)
            binding.btnTogglePiutang.setTextColor(activeText)
            binding.btnToggleHutang.setBackgroundColor(inactiveBg)
            binding.btnToggleHutang.setTextColor(inactiveText)
        } else {
            binding.btnToggleHutang.setBackgroundColor(activeBg)
            binding.btnToggleHutang.setTextColor(activeText)
            binding.btnTogglePiutang.setBackgroundColor(inactiveBg)
            binding.btnTogglePiutang.setTextColor(inactiveText)
        }
        filterAndSubmitDebts()
    }

    private fun filterAndSubmitAccounts() {
        val filtered = when (accountTypeFilter) {
            "IN" -> allAccounts.filter { it.accountType == "IN" }
            "OUT" -> allAccounts.filter { it.accountType == "OUT" }
            else -> allAccounts
        }
        bkuAccountAdapter.submitList(filtered)
    }

    private fun updateAccountFilterUI() {
        val activeBg = Color.parseColor("#3E24E2")
        val activeText = Color.WHITE

        binding.btnFilterAccAll.setBackgroundColor(if (accountTypeFilter == "ALL") activeBg else Color.parseColor("#EBEDFE"))
        binding.btnFilterAccAll.setTextColor(if (accountTypeFilter == "ALL") activeText else Color.parseColor("#3E24E2"))

        binding.btnFilterAccIn.setBackgroundColor(if (accountTypeFilter == "IN") Color.parseColor("#059669") else Color.parseColor("#E6F8F0"))
        binding.btnFilterAccIn.setTextColor(if (accountTypeFilter == "IN") activeText else Color.parseColor("#059669"))

        binding.btnFilterAccOut.setBackgroundColor(if (accountTypeFilter == "OUT") Color.parseColor("#DC2626") else Color.parseColor("#FEF2F2"))
        binding.btnFilterAccOut.setTextColor(if (accountTypeFilter == "OUT") activeText else Color.parseColor("#DC2626"))

        filterAndSubmitAccounts()
    }

    private fun showInputCashFlowDialog(defaultType: String = "IN") {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogInputCashFlowBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        var selectedType = defaultType
        var selectedAccountName = if (defaultType == "IN") "Penjualan Kasir / Ritel" else "Belanja Stok Barang / Kulakan"

        dialogBinding.rbFlowIn.isChecked = (defaultType == "IN")
        dialogBinding.rbFlowOut.isChecked = (defaultType == "OUT")
        dialogBinding.tvSelectedAccountName.text = selectedAccountName

        dialogBinding.rgFlowType.setOnCheckedChangeListener { _, checkedId ->
            selectedType = if (checkedId == R.id.rbFlowIn) "IN" else "OUT"
            val available = allAccounts.filter { it.accountType == selectedType }
            selectedAccountName = available.firstOrNull()?.accountName ?: if (selectedType == "IN") "Pemasukan Lain-lain" else "Pengeluaran Operasional"
            dialogBinding.tvSelectedAccountName.text = selectedAccountName
        }

        dialogBinding.containerSelectAccount.setOnClickListener {
            val accountsForType = allAccounts.filter { it.accountType == selectedType }
            val accountNames = accountsForType.map { "${it.accountName} (${it.accountCode ?: "COA"})" }.toTypedArray()

            AlertDialog.Builder(requireContext())
                .setTitle("Pilih Akun BKU (${if (selectedType == "IN") "Pemasukan" else "Pengeluaran"})")
                .setItems(accountNames) { _, index ->
                    selectedAccountName = accountsForType[index].accountName
                    dialogBinding.tvSelectedAccountName.text = selectedAccountName
                }
                .setPositiveButton("+ Buat Akun Baru") { _, _ ->
                    dialog.dismiss()
                    showCreateAccountDialog(selectedType)
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        dialogBinding.btnCancelCashFlow.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveCashFlow.setOnClickListener {
            val amount = dialogBinding.etFlowAmount.text.toString().toDoubleOrNull() ?: 0.0
            val description = dialogBinding.etFlowDescription.text.toString().trim()

            if (amount <= 0) {
                Toast.makeText(requireContext(), "Masukkan nominal kas yang valid!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val desc = if (description.isNotEmpty()) description else selectedAccountName

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val flow = CashFlowEntity(
                    id = UUID.randomUUID().toString(),
                    flowType = selectedType,
                    category = selectedAccountName,
                    amount = amount,
                    description = desc,
                    createdAt = System.currentTimeMillis()
                )
                appDatabase.cashFlowDao().insertCashFlow(flow)
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(requireContext(), "Mutasi kas '${selectedAccountName}' sebesar ${CurrencyUtils.formatRupiah(amount)} berhasil dicatat!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun showInputDebtDialog(defaultDebtType: String = "PIUTANG") {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogInputDebtBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        var selectedDebtType = defaultDebtType
        dialogBinding.rbPiutang.isChecked = (defaultDebtType == "PIUTANG")
        dialogBinding.rbHutang.isChecked = (defaultDebtType == "HUTANG")
        dialogBinding.tvPartyNameLabel.text = if (defaultDebtType == "PIUTANG") "Nama Pelanggan / Peminjam" else "Nama Supplier / Pemasok"

        dialogBinding.rgDebtType.setOnCheckedChangeListener { _, checkedId ->
            selectedDebtType = if (checkedId == R.id.rbPiutang) "PIUTANG" else "HUTANG"
            dialogBinding.tvPartyNameLabel.text = if (selectedDebtType == "PIUTANG") "Nama Pelanggan / Peminjam" else "Nama Supplier / Pemasok"
        }

        dialogBinding.btnCancelDebt.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveDebt.setOnClickListener {
            val name = dialogBinding.etPartyName.text.toString().trim()
            val phone = dialogBinding.etPartyPhone.text.toString().trim()
            val total = dialogBinding.etTotalDebtAmount.text.toString().toDoubleOrNull() ?: 0.0

            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "Nama wajib diisi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (total <= 0) {
                Toast.makeText(requireContext(), "Masukkan total nominal yang valid!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val newDebt = DebtEntity(
                    id = UUID.randomUUID().toString(),
                    customerName = name,
                    customerPhone = phone,
                    totalDebt = total,
                    paidAmount = 0.0,
                    status = "UNPAID",
                    debtType = selectedDebtType,
                    createdAt = System.currentTimeMillis()
                )
                appDatabase.debtDao().insertDebt(newDebt)
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(requireContext(), "Catatan ${if (selectedDebtType == "PIUTANG") "Piutang" else "Hutang"} '$name' berhasil disimpan!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun showPayDebtDialog(debt: DebtEntity) {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogPayDebtBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val remaining = maxOf(0.0, debt.totalDebt - debt.paidAmount)
        val isPiutang = debt.debtType.equals("PIUTANG", ignoreCase = true)

        dialogBinding.tvPayDebtTitle.text = if (isPiutang) "Pelunasan Piutang Kasbon" else "Pembayaran Hutang Supplier"
        dialogBinding.tvTargetPartyName.text = debt.customerName
        dialogBinding.tvRemainingAmountDisplay.text = CurrencyUtils.formatRupiah(remaining)
        dialogBinding.etPaymentAmount.setText(remaining.toInt().toString())

        dialogBinding.btnQuickPayFull.setOnClickListener {
            dialogBinding.etPaymentAmount.setText(remaining.toInt().toString())
        }

        dialogBinding.btnCancelPayment.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirmPayment.setOnClickListener {
            val payAmount = dialogBinding.etPaymentAmount.text.toString().toDoubleOrNull() ?: 0.0
            if (payAmount <= 0) {
                Toast.makeText(requireContext(), "Masukkan nominal pembayaran yang valid!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newPaid = debt.paidAmount + payAmount
            val newStatus = if (newPaid >= debt.totalDebt) "PAID" else "PARTIAL"

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val updatedDebt = debt.copy(
                    paidAmount = newPaid,
                    status = newStatus,
                    updatedAt = System.currentTimeMillis()
                )
                appDatabase.debtDao().updateDebt(updatedDebt)

                val flow = CashFlowEntity(
                    id = UUID.randomUUID().toString(),
                    flowType = if (isPiutang) "IN" else "OUT",
                    category = if (isPiutang) "Penerimaan Pelunasan Piutang" else "Pembayaran Hutang Supplier",
                    amount = payAmount,
                    description = "Pembayaran ${if (isPiutang) "Piutang" else "Hutang"} an. ${debt.customerName}",
                    referenceId = debt.id,
                    createdAt = System.currentTimeMillis()
                )
                appDatabase.cashFlowDao().insertCashFlow(flow)

                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(requireContext(), "Pembayaran ${CurrencyUtils.formatRupiah(payAmount)} dicatat ke BKU!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun showCreateAccountDialog(defaultType: String = "IN") {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogCreateAccountBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.rbAccIn.isChecked = (defaultType == "IN")
        dialogBinding.rbAccOut.isChecked = (defaultType == "OUT")

        dialogBinding.btnCancelCreateAccount.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveAccount.setOnClickListener {
            val name = dialogBinding.etAccountName.text.toString().trim()
            val code = dialogBinding.etAccountCode.text.toString().trim()
            val type = if (dialogBinding.rbAccIn.isChecked) "IN" else "OUT"

            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "Nama akun wajib diisi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val accCode = if (code.isNotEmpty()) code else if (type == "IN") "4-${(1000..9999).random()}" else "5-${(1000..9999).random()}"

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val account = BkuAccountEntity(
                    id = UUID.randomUUID().toString(),
                    accountName = name,
                    accountType = type,
                    accountCode = accCode,
                    isDefault = false,
                    createdAt = System.currentTimeMillis()
                )
                appDatabase.bkuAccountDao().insertAccount(account)
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(requireContext(), "Akun BKU '$name' ($accCode) berhasil dibuat!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun sendWhatsAppReminder(debt: DebtEntity) {
        val phone = debt.customerPhone
        if (phone.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Nomor HP pelanggan tidak tersedia", Toast.LENGTH_SHORT).show()
            return
        }

        val remaining = maxOf(0.0, debt.totalDebt - debt.paidAmount)
        val storeName = SessionManager.getInstance(requireContext()).getStoreName()
        val message = "Halo ${debt.customerName},\n\nKami menginformasikan catatan tagihan kasbon di *$storeName* dengan sisa pembayaran sebesar *${CurrencyUtils.formatRupiah(remaining)}*.\n\nTerima kasih atas kerja samanya."

        var cleanPhone = phone.replace(Regex("[^0-9+]"), "")
        if (cleanPhone.startsWith("0")) {
            cleanPhone = "62" + cleanPhone.substring(1)
        } else if (cleanPhone.startsWith("+62")) {
            cleanPhone = cleanPhone.substring(1)
        }

        try {
            val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${URLEncoder.encode(message, "UTF-8")}"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Gagal membuka WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportBkuReport() {
        val options = arrayOf(
            "Ekspor Penjualan Periode Ini (${currentPeriod.shortLabel}) (.xlsx)",
            "Ekspor Semua Riwayat Penjualan (.xlsx)",
            "Ekspor Mutasi Kas BKU (.xlsx)",
            "Buka Pusat Pengelolaan Data Lengkap"
        )
        AlertDialog.Builder(requireContext())
            .setTitle("Ekspor Laporan & Pengelolaan Data")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportSalesProfitReport(asXlsx = true, filterByActivePeriod = true)
                    1 -> exportSalesProfitReport(asXlsx = true, filterByActivePeriod = false)
                    2 -> exportBkuSpreadsheet(filterByActivePeriod = true)
                    3 -> startActivity(Intent(requireContext(), com.denkasir.ui.backup.DataManagementActivity::class.java))
                }
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun exportSalesProfitReport(asXlsx: Boolean, filterByActivePeriod: Boolean = false) {
        lifecycleScope.launch {
            try {
                val transactions = if (filterByActivePeriod) {
                    allTransactions.filter { it.createdAt in currentPeriod.startMs..currentPeriod.endMs }
                } else {
                    withContext(Dispatchers.IO) { appDatabase.transactionDao().getAllTransactionsList() }
                }

                val periodTrxIds = transactions.map { it.id }.toSet()
                val allItems = withContext(Dispatchers.IO) { appDatabase.transactionDao().getAllTransactionItems() }
                    .filter { if (filterByActivePeriod) it.transactionId in periodTrxIds else true }

                if (transactions.isEmpty()) {
                    Toast.makeText(requireContext(), "Tidak ada transaksi penjualan pada periode ini untuk diekspor.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val file = withContext(Dispatchers.IO) {
                    if (asXlsx) {
                        DataExporterImporter.exportSalesAndProfitToXlsx(requireContext(), transactions, allItems)
                    } else {
                        DataExporterImporter.exportSalesAndProfitToCsv(requireContext(), transactions, allItems)
                    }
                }

                val mime = if (asXlsx) "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" else "text/csv"
                AlertDialog.Builder(requireContext())
                    .setTitle("Ekspor Selesai!")
                    .setMessage("Berkas berhasil disimpan di:\n${file.name}\n\nIngin membagikan laporan sekarang?")
                    .setPositiveButton("Bagikan Berkas") { _, _ ->
                        DataExporterImporter.shareExportedFile(requireContext(), file, mime, "Laporan Penjualan & Laba DenKasir")
                    }
                    .setNegativeButton("Selesai", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal ekspor: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportBkuSpreadsheet(filterByActivePeriod: Boolean = false) {
        lifecycleScope.launch {
            try {
                val exportFlows = if (filterByActivePeriod) {
                    allCashFlows.filter { it.createdAt in currentPeriod.startMs..currentPeriod.endMs }
                } else {
                    allCashFlows
                }

                if (exportFlows.isEmpty()) {
                    Toast.makeText(requireContext(), "Belum ada catatan mutasi kas BKU pada periode ini.", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val file = withContext(Dispatchers.IO) {
                    DataExporterImporter.exportCashFlowToXlsx(requireContext(), exportFlows)
                }
                AlertDialog.Builder(requireContext())
                    .setTitle("Ekspor BKU Berhasil!")
                    .setMessage("Berkas berhasil disimpan:\n${file.name}\n\nIngin membagikan laporan sekarang?")
                    .setPositiveButton("Bagikan Berkas") { _, _ ->
                        DataExporterImporter.shareExportedFile(requireContext(), file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Laporan Buku Kas Umum")
                    }
                    .setNegativeButton("Selesai", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal ekspor BKU: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDeleteConfirmDialog(title: String, message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Hapus") { _, _ -> onConfirm() }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
