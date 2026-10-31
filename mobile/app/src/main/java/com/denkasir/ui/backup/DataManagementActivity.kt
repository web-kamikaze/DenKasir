package com.denkasir.ui.backup

import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.denkasir.data.local.AppDatabase
import com.denkasir.databinding.ActivityDataManagementBinding
import com.denkasir.ui.MainActivity
import com.denkasir.utils.DataExporterImporter
import com.denkasir.utils.ImageUtils
import com.denkasir.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DataManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDataManagementBinding
    private lateinit var appDatabase: AppDatabase

    private val selectFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val path = uri.path ?: uri.toString()
                val resolvedPath = if (path.contains("primary:")) {
                    "/sdcard/" + path.substringAfter("primary:")
                } else {
                    path
                }
                SessionManager.getInstance(this).saveCustomImagesDir(resolvedPath)
                Toast.makeText(this, "Folder foto offline diatur ke: $resolvedPath", Toast.LENGTH_LONG).show()
                loadDataSummaries()
            } catch (e: Exception) {
                val fallbackPath = "/sdcard/Download/image"
                SessionManager.getInstance(this).saveCustomImagesDir(fallbackPath)
                Toast.makeText(this, "Menggunakan folder: $fallbackPath", Toast.LENGTH_SHORT).show()
                loadDataSummaries()
            }
        }
    }

    private val restoreDbLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            confirmAndRestoreDb(uri)
        }
    }

    private val importProductsLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            processImportProductsFile(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appDatabase = AppDatabase.getDatabase(this)

        setupListeners()
        loadDataSummaries()
    }

    override fun onResume() {
        super.onResume()
        loadDataSummaries()
    }

    private fun checkStoragePermissionAndProceed(onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                AlertDialog.Builder(this)
                    .setTitle("Izin Akses Berkas Diperlukan")
                    .setMessage("Untuk membaca foto produk offline di folder /sdcard/Download/image/ atau folder kustom, DenKasir memerlukan izin 'Akses Semua File'.\n\nSilakan aktifkan toggle izin pada layar pengaturan.")
                    .setPositiveButton("Buka Pengaturan") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.setData(Uri.parse("package:$packageName"))
                            startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            startActivity(intent)
                        }
                    }
                    .setNegativeButton("Batal", null)
                    .show()
                return
            }
        }
        onGranted()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnRefreshStats.setOnClickListener {
            loadDataSummaries()
            Toast.makeText(this, "Status data disegarkan", Toast.LENGTH_SHORT).show()
        }

        // Section 1: SQLite (.db & .sql)
        binding.btnBackupDb.setOnClickListener {
            backupDatabase()
        }

        binding.btnExportSql.setOnClickListener {
            exportSqlDump()
        }

        binding.btnRestoreDb.setOnClickListener {
            restoreDbLauncher.launch("*/*")
        }

        binding.btnClearAllData.setOnClickListener {
            showClearAllDataConfirmation()
        }

        // Section 2: Spreadsheet (Excel & CSV)
        binding.btnExportProductsXlsx.setOnClickListener {
            exportProducts(asXlsx = true)
        }

        binding.btnExportProductsCsv.setOnClickListener {
            exportProducts(asXlsx = false)
        }

        binding.btnImportProducts.setOnClickListener {
            importProductsLauncher.launch("*/*")
        }

        binding.btnExportSalesProfit.setOnClickListener {
            exportSalesAndProfit()
        }

        binding.btnExportCashFlow.setOnClickListener {
            exportCashFlow()
        }

        // Section 3: Gambar Offline
        binding.btnSetCustomImageFolder.setOnClickListener {
            checkStoragePermissionAndProceed { showSelectImageFolderDialog() }
        }

        binding.btnScanLocalImagesFolder.setOnClickListener {
            checkStoragePermissionAndProceed { scanAndSyncLocalImagesFolder() }
        }

        binding.btnDownloadWebImages.setOnClickListener {
            downloadAllWebImages()
        }

        binding.btnClearImageCache.setOnClickListener {
            val freed = ImageUtils.clearTemporaryCaches(this)
            Toast.makeText(this, "Berhasil membersihkan cache sementara (${ImageUtils.formatFileSize(freed)})", Toast.LENGTH_SHORT).show()
            loadDataSummaries()
        }

        // Section 4: Master Data Opsional
        binding.btnSeedMinimarketCatalog.setOnClickListener {
            showSeedCatalogConfirmation()
        }
    }

    private fun loadDataSummaries() {
        lifecycleScope.launch {
            val productCount = withContext(Dispatchers.IO) { appDatabase.productDao().getProductCount() }
            val trxCount = withContext(Dispatchers.IO) { appDatabase.transactionDao().getAllTransactionsList().size }
            val cashCount = withContext(Dispatchers.IO) { appDatabase.cashFlowDao().getCashFlowCount() }
            val debtCount = withContext(Dispatchers.IO) { appDatabase.debtDao().getDebtCount() }
            val (imageCount, imageBytes) = withContext(Dispatchers.IO) { ImageUtils.getProductImagesStorageInfo(this@DataManagementActivity) }

            binding.tvDatabaseSummary.text = "• Produk Terdaftar: $productCount item\n• Riwayat Transaksi: $trxCount nota\n• Mutasi Kas BKU: $cashCount mutasi\n• Catatan Hutang/Kasbon: $debtCount data"
            binding.tvImageStorageInfo.text = "Tersimpan: $imageCount foto (${ImageUtils.formatFileSize(imageBytes)}) di folder penyimpanan internal perangkat."
            binding.tvStoragePathInfo.text = "Lokasi Ekspor: ${DataExporterImporter.getExportDir(this@DataManagementActivity).absolutePath}"
            val folderSummary = withContext(Dispatchers.IO) { ImageUtils.getActiveImagesFolderSummary(this@DataManagementActivity) }
            binding.tvCustomFolderInfo.text = folderSummary
        }
    }

    // -------------------------------------------------------------
    // 1. BACKUP & RESTORE SQLITE
    // -------------------------------------------------------------

    private fun backupDatabase() {
        lifecycleScope.launch {
            try {
                val backupFile = withContext(Dispatchers.IO) {
                    DataExporterImporter.backupDatabaseFile(this@DataManagementActivity)
                }
                AlertDialog.Builder(this@DataManagementActivity)
                    .setTitle("Pencadangan Berhasil!")
                    .setMessage("Berkas basis data berhasil dicadangkan:\n${backupFile.name}\nUkuran: ${ImageUtils.formatFileSize(backupFile.length())}\n\nIngin membagikan/mengirimkan berkas ke WhatsApp atau Google Drive?")
                    .setPositiveButton("Bagikan Berkas") { _, _ ->
                        DataExporterImporter.shareExportedFile(this@DataManagementActivity, backupFile, "application/octet-stream", "Cadangan Database DenKasir")
                    }
                    .setNegativeButton("Selesai", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@DataManagementActivity, "Gagal backup database: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportSqlDump() {
        val progress = ProgressDialog(this).apply {
            setMessage("Mengekspor seluruh skema dan data ke SQL Script dump...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val sqlFile = withContext(Dispatchers.IO) {
                    DataExporterImporter.exportDatabaseToSqlDump(this@DataManagementActivity)
                }
                progress.dismiss()
                AlertDialog.Builder(this@DataManagementActivity)
                    .setTitle("Ekspor SQL Berhasil!")
                    .setMessage("File SQL dump berhasil dibuat:\n${sqlFile.name}\nUkuran: ${ImageUtils.formatFileSize(sqlFile.length())}")
                    .setPositiveButton("Bagikan Berkas") { _, _ ->
                        DataExporterImporter.shareExportedFile(this@DataManagementActivity, sqlFile, "text/plain", "Script SQL Dump DenKasir")
                    }
                    .setNegativeButton("Selesai", null)
                    .show()
            } catch (e: Exception) {
                progress.dismiss()
                Toast.makeText(this@DataManagementActivity, "Gagal ekspor SQL: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmAndRestoreDb(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Konfirmasi Pemulihan Data")
            .setMessage("PERINGATAN: Memulihkan database akan menimpa seluruh data kasir saat ini dengan data dari file backup. Pastikan berkas benar merupakan file .db DenKasir.\n\nLanjutkan pemulihan?")
            .setPositiveButton("Pulihkan Sekarang") { _, _ ->
                val progress = ProgressDialog(this).apply {
                    setMessage("Memeriksa integritas dan memulihkan database...")
                    setCancelable(false)
                    show()
                }

                lifecycleScope.launch {
                    val success = withContext(Dispatchers.IO) {
                        DataExporterImporter.restoreDatabaseFromDbUri(this@DataManagementActivity, uri)
                    }
                    progress.dismiss()
                    if (success) {
                        AlertDialog.Builder(this@DataManagementActivity)
                            .setTitle("Pemulihan Berhasil!")
                            .setMessage("Basis data DenKasir berhasil dipulihkan. Aplikasi akan memuat ulang data terbaru.")
                            .setPositiveButton("Buka Halaman Utama") { _, _ ->
                                val intent = Intent(this@DataManagementActivity, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                startActivity(intent)
                                finish()
                            }
                            .setCancelable(false)
                            .show()
                    } else {
                        Toast.makeText(this@DataManagementActivity, "Gagal memulihkan database. Format berkas tidak valid!", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showClearAllDataConfirmation() {
        val input = EditText(this).apply {
            hint = "Ketik RESET disini"
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("Kosongkan Seluruh Data Toko")
            .setMessage("Tindakan ini akan MENGHAPUS SEMUA produk, riwayat transaksi penjualan, arus kas, hutang, dan pembelian di perangkat ini. Akun Owner & Profil Toko tetap tersimpan.\n\nKetik kata 'RESET' di bawah untuk konfirmasi:")
            .setView(input)
            .setPositiveButton("Hapus Seluruh Data") { _, _ ->
                if (input.text.toString().trim() == "RESET") {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            appDatabase.clearAllData(keepUserAndStore = true)
                        }
                        Toast.makeText(this@DataManagementActivity, "Seluruh data toko berhasil dikosongkan!", Toast.LENGTH_LONG).show()
                        loadDataSummaries()
                    }
                } else {
                    Toast.makeText(this@DataManagementActivity, "Konfirmasi dibatalkan (kata kunci tidak sesuai).", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // -------------------------------------------------------------
    // 2. SPREADSHEET (EXCEL & CSV)
    // -------------------------------------------------------------

    private fun exportProducts(asXlsx: Boolean) {
        lifecycleScope.launch {
            try {
                val products = withContext(Dispatchers.IO) {
                    appDatabase.productDao().getAllProductsList()
                }
                if (products.isEmpty()) {
                    Toast.makeText(this@DataManagementActivity, "Tidak ada produk untuk diekspor (katalog kosong).", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val file = withContext(Dispatchers.IO) {
                    if (asXlsx) {
                        DataExporterImporter.exportProductsToXlsx(this@DataManagementActivity, products)
                    } else {
                        DataExporterImporter.exportProductsToCsv(this@DataManagementActivity, products)
                    }
                }

                val mime = if (asXlsx) "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" else "text/csv"
                AlertDialog.Builder(this@DataManagementActivity)
                    .setTitle("Ekspor Produk Selesai!")
                    .setMessage("Tersimpan ${products.size} produk ke berkas:\n${file.name}\n\nIngin membagikan berkas sekarang?")
                    .setPositiveButton("Bagikan Berkas") { _, _ ->
                        DataExporterImporter.shareExportedFile(this@DataManagementActivity, file, mime, "Daftar Produk DenKasir")
                    }
                    .setNegativeButton("Selesai", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@DataManagementActivity, "Gagal ekspor produk: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun processImportProductsFile(uri: Uri) {
        val progress = ProgressDialog(this).apply {
            setMessage("Membaca dan memproses impor berkas produk...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val (inserted, updated) = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        // Deteksi tipe berdasarkan nama atau fallback
                        val fileName = uri.lastPathSegment?.lowercase() ?: ""
                        if (fileName.endsWith(".csv")) {
                            DataExporterImporter.importProductsFromCsv(this@DataManagementActivity, stream)
                        } else {
                            try {
                                DataExporterImporter.importProductsFromXlsx(this@DataManagementActivity, stream)
                            } catch (e: Exception) {
                                // Fallback jika ekstensi csv tidak terdeteksi
                                contentResolver.openInputStream(uri)?.use { stream2 ->
                                    DataExporterImporter.importProductsFromCsv(this@DataManagementActivity, stream2)
                                } ?: Pair(0, 0)
                            }
                        }
                    } ?: Pair(0, 0)
                }

                progress.dismiss()
                AlertDialog.Builder(this@DataManagementActivity)
                    .setTitle("Impor Produk Berhasil!")
                    .setMessage("Hasil proses impor berkas:\n• Produk Baru Ditambahkan: $inserted item\n• Produk Diperbarui: $updated item")
                    .setPositiveButton("OK", null)
                    .show()
                loadDataSummaries()
            } catch (e: Exception) {
                progress.dismiss()
                Toast.makeText(this@DataManagementActivity, "Gagal impor produk: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportSalesAndProfit() {
        lifecycleScope.launch {
            try {
                val transactions = withContext(Dispatchers.IO) { appDatabase.transactionDao().getAllTransactionsList() }
                val allItems = withContext(Dispatchers.IO) { appDatabase.transactionDao().getAllTransactionItems() }

                if (transactions.isEmpty()) {
                    Toast.makeText(this@DataManagementActivity, "Belum ada transaksi penjualan untuk diekspor.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val file = withContext(Dispatchers.IO) {
                    DataExporterImporter.exportSalesAndProfitToXlsx(this@DataManagementActivity, transactions, allItems)
                }

                AlertDialog.Builder(this@DataManagementActivity)
                    .setTitle("Ekspor Penjualan & Laba Berhasil!")
                    .setMessage("Laporan riil penjualan dan laba kotor (${transactions.size} transaksi) berhasil dibuat:\n${file.name}")
                    .setPositiveButton("Bagikan Berkas") { _, _ ->
                        DataExporterImporter.shareExportedFile(this@DataManagementActivity, file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Laporan Penjualan DenKasir")
                    }
                    .setNegativeButton("Selesai", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@DataManagementActivity, "Gagal ekspor penjualan: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportCashFlow() {
        lifecycleScope.launch {
            try {
                val flows = withContext(Dispatchers.IO) { appDatabase.cashFlowDao().getAllCashFlowsList() }
                if (flows.isEmpty()) {
                    Toast.makeText(this@DataManagementActivity, "Belum ada catatan mutasi kas BKU.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val file = withContext(Dispatchers.IO) {
                    DataExporterImporter.exportCashFlowToXlsx(this@DataManagementActivity, flows)
                }

                AlertDialog.Builder(this@DataManagementActivity)
                    .setTitle("Ekspor BKU Berhasil!")
                    .setMessage("Laporan Buku Kas Umum (${flows.size} mutasi) berhasil dibuat:\n${file.name}")
                    .setPositiveButton("Bagikan Berkas") { _, _ ->
                        DataExporterImporter.shareExportedFile(this@DataManagementActivity, file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Laporan BKU DenKasir")
                    }
                    .setNegativeButton("Selesai", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@DataManagementActivity, "Gagal ekspor BKU: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // -------------------------------------------------------------
    // 3. GAMBAR PRODUK OFFLINE
    // -------------------------------------------------------------

    private fun downloadAllWebImages() {
        val progress = ProgressDialog(this).apply {
            setMessage("Mencari dan mengunduh foto produk web ke penyimpanan internal HP...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            val (success, fail) = ImageUtils.batchDownloadAllProductImages(this@DataManagementActivity) { current, total, _ ->
                progress.setMessage("Mengunduh foto produk $current dari $total...")
            }
            progress.dismiss()

            AlertDialog.Builder(this@DataManagementActivity)
                .setTitle("Pengunduhan Foto Selesai")
                .setMessage("Hasil unduh foto produk web ke memori HP:\n• Berhasil diunduh: $success foto\n• Gagal / Offline: $fail foto\n\nSeluruh foto yang berhasil diunduh kini tersimpan permanen dan 100% siap dibuka saat offline.")
                .setPositiveButton("OK", null)
                .show()

            loadDataSummaries()
        }
    }

    // -------------------------------------------------------------
    // 4. MASTER DATA OPSIONAL
    // -------------------------------------------------------------

    private fun showSeedCatalogConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Muat Katalog Minimarket?")
            .setMessage("Fitur ini akan menambahkan 3.900+ data referensi produk sembako dan minimarket populer ke daftar produk Anda.\n\nLanjutkan?")
            .setPositiveButton("Muat Katalog") { _, _ ->
                val progress = ProgressDialog(this).apply {
                    setMessage("Memuat data katalog minimarket ke basis data...")
                    setCancelable(false)
                    show()
                }

                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        appDatabase.seedProductsFromAsset(this@DataManagementActivity)
                    }
                    progress.dismiss()
                    Toast.makeText(this@DataManagementActivity, "Katalog minimarket berhasil dimuat!", Toast.LENGTH_SHORT).show()
                    loadDataSummaries()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
    private fun showSelectImageFolderDialog() {
        val options = arrayOf(
            "Gunakan Folder Standar (/sdcard/Download/image/)",
            "Gunakan Folder (/sdcard/product_images/)",
            "Pilih Folder Lewat Pemilih Berkas HP (SAF)",
            "Ketik Lokasi Folder Manual",
            "Reset ke Deteksi Otomatis"
        )
        AlertDialog.Builder(this)
            .setTitle("Pilih Folder Foto Offline")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        SessionManager.getInstance(this).saveCustomImagesDir("/sdcard/Download/image")
                        Toast.makeText(this, "Folder diatur ke: /sdcard/Download/image", Toast.LENGTH_SHORT).show()
                        loadDataSummaries()
                    }
                    1 -> {
                        SessionManager.getInstance(this).saveCustomImagesDir("/sdcard/product_images")
                        Toast.makeText(this, "Folder diatur ke: /sdcard/product_images", Toast.LENGTH_SHORT).show()
                        loadDataSummaries()
                    }
                    2 -> {
                        try {
                            selectFolderLauncher.launch(null)
                        } catch (e: Exception) {
                            showManualFolderInputDialog()
                        }
                    }
                    3 -> {
                        showManualFolderInputDialog()
                    }
                    4 -> {
                        SessionManager.getInstance(this).saveCustomImagesDir(null)
                        Toast.makeText(this, "Folder foto di-reset ke deteksi otomatis", Toast.LENGTH_SHORT).show()
                        loadDataSummaries()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showManualFolderInputDialog() {
        val input = EditText(this).apply {
            hint = "/sdcard/Download/image"
            setText(SessionManager.getInstance(this@DataManagementActivity).getCustomImagesDir() ?: "/sdcard/Download/image")
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("Ketik Jalur Folder Foto")
            .setMessage("Masukkan jalur folder lengkap tempat Anda menyimpan foto produk offline:")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val p = input.text.toString().trim()
                if (p.isNotBlank()) {
                    SessionManager.getInstance(this).saveCustomImagesDir(p)
                    Toast.makeText(this, "Folder berhasil disimpan: $p", Toast.LENGTH_SHORT).show()
                    loadDataSummaries()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun scanAndSyncLocalImagesFolder() {
        val progress = ProgressDialog(this).apply {
            setMessage("Memindai folder foto offline dan mencocokkan ke barcode produk...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            val (matched, total) = ImageUtils.syncImagesFromFolder(this@DataManagementActivity) { current, tot, match ->
                progress.setMessage("Memproses foto $current dari $tot (Cocok: $match)...")
            }
            progress.dismiss()

            AlertDialog.Builder(this@DataManagementActivity)
                .setTitle("Sinkronisasi Foto Selesai")
                .setMessage("Hasil pemindaian folder offline:\n• Berhasil Dihubungkan: $matched produk\n• Total File Foto di Folder: $total berkas\n\nFoto produk sekarang otomatis aktif offline!")
                .setPositiveButton("OK", null)
                .show()

            loadDataSummaries()
        }
    }
}
