package com.denkasir.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.denkasir.data.local.AppDatabase
import com.denkasir.data.local.entity.*
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

object DataExporterImporter {

    private const val TAG = "DataExporterImporter"

    fun getExportDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "DenKasir_Exports")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getTimestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    private fun escapeCsv(value: Any?): String {
        if (value == null) return ""
        val str = value.toString().replace("\"", "\"\"")
        return if (str.contains(",") || str.contains(";") || str.contains("\n") || str.contains("\"")) {
            "\"$str\""
        } else {
            str
        }
    }

    // -------------------------------------------------------------
    // 1. PRODUK: EKSPOR & IMPOR (CSV & XLSX)
    // -------------------------------------------------------------

    fun exportProductsToCsv(context: Context, products: List<ProductEntity>): File {
        val file = File(getExportDir(context), "katalog_produk_${getTimestamp()}.csv")
        file.bufferedWriter().use { writer ->
            writer.write("ID,Barcode,Nama_Produk,Kategori,Tipe,Harga_Modal,Harga_Jual,Stok,Min_Stok_Alert,Image_Path\n")
            for (p in products) {
                writer.write("${escapeCsv(p.id)},${escapeCsv(p.barcode)},${escapeCsv(p.name)},${escapeCsv(p.category)},${escapeCsv(p.productType)},${p.costPrice},${p.sellingPrice},${p.stock},${p.minStockAlert},${escapeCsv(p.imagePath)}\n")
            }
        }
        return file
    }

    fun exportProductsToXlsx(context: Context, products: List<ProductEntity>): File {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Katalog Produk")

        // Style Header
        val headerFont = workbook.createFont().apply {
            bold = true
            color = IndexedColors.WHITE.index
        }
        val headerStyle = workbook.createCellStyle().apply {
            setFont(headerFont)
            fillForegroundColor = IndexedColors.INDIGO.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
        }

        val headers = arrayOf("No", "Barcode", "Nama Produk", "Kategori", "Tipe", "Harga Modal (Rp)", "Harga Jual (Rp)", "Margin Laba (Rp)", "Stok", "Min Stok Alert", "Image Path")
        val headerRow = sheet.createRow(0)
        for (i in headers.indices) {
            val cell = headerRow.createCell(i)
            cell.setCellValue(headers[i])
            cell.cellStyle = headerStyle
        }

        var rowIdx = 1
        for (p in products) {
            val row = sheet.createRow(rowIdx)
            row.createCell(0).setCellValue(rowIdx.toDouble())
            row.createCell(1).setCellValue(p.barcode ?: "")
            row.createCell(2).setCellValue(p.name)
            row.createCell(3).setCellValue(p.category)
            row.createCell(4).setCellValue(p.productType)
            row.createCell(5).setCellValue(p.costPrice)
            row.createCell(6).setCellValue(p.sellingPrice)
            row.createCell(7).setCellValue(p.sellingPrice - p.costPrice)
            row.createCell(8).setCellValue(p.stock)
            row.createCell(9).setCellValue(p.minStockAlert)
            row.createCell(10).setCellValue(p.imagePath ?: "")
            rowIdx++
        }

        for (i in headers.indices) {
            sheet.autoSizeColumn(i)
        }

        val file = File(getExportDir(context), "katalog_produk_${getTimestamp()}.xlsx")
        FileOutputStream(file).use { out ->
            workbook.write(out)
        }
        workbook.close()
        return file
    }

    suspend fun importProductsFromCsv(context: Context, inputStream: InputStream): Pair<Int, Int> {
        val db = AppDatabase.getDatabase(context)
        val reader = BufferedReader(InputStreamReader(inputStream))
        var inserted = 0
        var updated = 0
        val now = System.currentTimeMillis()

        val lines = reader.readLines()
        if (lines.isEmpty()) return Pair(0, 0)

        // Deteksi delimiter dari baris header
        val headerLine = lines[0]
        val delimiter = if (headerLine.contains(";") && !headerLine.contains(",")) ";" else ","

        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isBlank()) continue

            val tokens = parseCsvLine(line, delimiter)
            if (tokens.size < 7) continue

            val id = if (tokens.isNotEmpty() && tokens[0].isNotBlank()) tokens[0] else UUID.randomUUID().toString()
            val barcode = if (tokens.size > 1 && tokens[1].isNotBlank()) tokens[1] else null
            val name = if (tokens.size > 2) tokens[2] else "Produk Impor"
            val category = if (tokens.size > 3 && tokens[3].isNotBlank()) tokens[3] else "Pcs"
            val pType = if (tokens.size > 4 && tokens[4].isNotBlank()) tokens[4].uppercase() else "RETAIL"
            if (pType == "DIGITAL") continue // Katalog produk digital dikelola penuh via API st.riungan.my.id
            val costPrice = tokens.getOrNull(5)?.toDoubleOrNull() ?: 0.0
            val sellingPrice = tokens.getOrNull(6)?.toDoubleOrNull() ?: 0.0
            val stock = tokens.getOrNull(7)?.toDoubleOrNull() ?: 0.0
            val minStock = tokens.getOrNull(8)?.toDoubleOrNull() ?: 5.0
            val imagePath = tokens.getOrNull(9)?.takeIf { it.isNotBlank() }

            val existing = if (!barcode.isNullOrBlank()) db.productDao().getProductByBarcode(barcode) else null
            if (existing != null) {
                db.productDao().updateProduct(
                    existing.copy(
                        name = name,
                        category = category,
                        productType = pType,
                        costPrice = costPrice,
                        sellingPrice = sellingPrice,
                        stock = stock,
                        minStockAlert = minStock,
                        imagePath = imagePath ?: existing.imagePath,
                        updatedAt = now
                    )
                )
                updated++
            } else {
                db.productDao().insertProduct(
                    ProductEntity(
                        id = id,
                        barcode = barcode,
                        name = name,
                        category = category,
                        productType = pType,
                        costPrice = costPrice,
                        sellingPrice = sellingPrice,
                        stock = stock,
                        minStockAlert = minStock,
                        imagePath = imagePath,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                inserted++
            }
        }
        return Pair(inserted, updated)
    }

    suspend fun importProductsFromXlsx(context: Context, inputStream: InputStream): Pair<Int, Int> {
        val db = AppDatabase.getDatabase(context)
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0)
        var inserted = 0
        var updated = 0
        val now = System.currentTimeMillis()

        // 1. Deteksi baris header dan mapping kolom secara dinamis
        var headerRowIdx = -1
        var colBarcode = 1
        var colName = 2
        var colCategory = 3
        var colType = 4
        var colCost = 5
        var colSell = 6
        var colStock = 7
        var colMinStock = 8
        var colH2h = 9
        var colImg = 10

        for (r in 0..minOf(10, sheet.lastRowNum)) {
            val row = sheet.getRow(r) ?: continue
            var nonEmptyCount = 0
            for (c in 0 until row.lastCellNum) {
                if (!getCellValueAsString(row.getCell(c)).isNullOrBlank()) nonEmptyCount++
            }
            // Abaikan baris banner/instruksi yang hanya memiliki sedikit kolom terisi
            if (nonEmptyCount < 4) continue

            val rowStrBuilder = StringBuilder()
            for (c in 0 until row.lastCellNum) {
                rowStrBuilder.append(getCellValueAsString(row.getCell(c))?.lowercase() ?: "").append(" ")
            }
            val rowStr = rowStrBuilder.toString()
            if (rowStr.contains("nama") || (rowStr.contains("produk") && rowStr.contains("harga"))) {
                headerRowIdx = r
                for (c in 0 until row.lastCellNum) {
                    val cTxt = getCellValueAsString(row.getCell(c))?.lowercase()?.trim() ?: continue
                    when {
                        cTxt.contains("barcode") || cTxt.contains("sku") -> colBarcode = c
                        cTxt.contains("gambar") || cTxt.contains("foto") || cTxt.contains("image") || cTxt.contains("url") -> colImg = c
                        (cTxt.contains("nama") || cTxt.contains("produk") || cTxt.contains("barang")) &&
                            !cTxt.contains("tipe") && !cTxt.contains("kategori") && !cTxt.contains("gambar") && !cTxt.contains("url") -> colName = c
                        cTxt.contains("kategori") || cTxt.contains("category") -> colCategory = c
                        cTxt.contains("tipe") || cTxt.contains("type") || cTxt.contains("jenis") -> colType = c
                        cTxt.contains("modal") || cTxt.contains("hpp") || cTxt.contains("beli") -> colCost = c
                        cTxt.contains("jual") || (cTxt.contains("harga") && !cTxt.contains("modal")) -> colSell = c
                        cTxt.contains("min") && (cTxt.contains("stok") || cTxt.contains("stock")) -> colMinStock = c
                        (cTxt.contains("stok") || cTxt.contains("stock") || cTxt.contains("qty")) && !cTxt.contains("min") -> colStock = c
                        cTxt.contains("h2h") || cTxt.contains("provider") || cTxt.contains("sn") || cTxt.contains("kode") -> colH2h = c
                    }
                }
                break
            }
        }

        val startRow = if (headerRowIdx != -1) headerRowIdx + 1 else 1

        // Preload map produk eksisting berdasarkan barcode untuk pencarian kilat O(1)
        val existingProductsList = db.productDao().getAllProductsList()
        val existingMapByBarcode = mutableMapOf<String, ProductEntity>()
        for (ep in existingProductsList) {
            if (!ep.barcode.isNullOrBlank()) {
                existingMapByBarcode[ep.barcode] = ep
            }
        }

        val batchToSave = mutableListOf<ProductEntity>()

        for (rowIdx in startRow..sheet.lastRowNum) {
            val row = sheet.getRow(rowIdx) ?: continue

            val rawName = if (colName in 0 until row.lastCellNum) getCellValueAsString(row.getCell(colName)) else null
            if (rawName.isNullOrBlank()) continue
            val nameVal = rawName.trim()

            val barcodeVal = if (colBarcode in 0 until row.lastCellNum) {
                getCellValueAsString(row.getCell(colBarcode))?.takeIf { it.isNotBlank() && it != "-" }
            } else null

            val categoryVal = if (colCategory in 0 until row.lastCellNum) {
                getCellValueAsString(row.getCell(colCategory))?.takeIf { it.isNotBlank() } ?: "Pcs"
            } else "Pcs"

            val typeVal = if (colType in 0 until row.lastCellNum) {
                getCellValueAsString(row.getCell(colType))?.uppercase()?.takeIf { it == "RETAIL" || it == "DIGITAL" } ?: "RETAIL"
            } else "RETAIL"
            if (typeVal == "DIGITAL") continue // Katalog produk digital dikelola penuh via API st.riungan.my.id

            val costVal = if (colCost in 0 until row.lastCellNum) getCellValueAsDouble(row.getCell(colCost)) ?: 0.0 else 0.0
            val sellVal = if (colSell in 0 until row.lastCellNum) getCellValueAsDouble(row.getCell(colSell)) ?: 0.0 else 0.0
            val stockVal = if (colStock in 0 until row.lastCellNum) getCellValueAsDouble(row.getCell(colStock)) ?: 0.0 else 0.0
            val minStockVal = if (colMinStock in 0 until row.lastCellNum) getCellValueAsDouble(row.getCell(colMinStock)) ?: 5.0 else 5.0
            val h2hVal = if (colH2h in 0 until row.lastCellNum) getCellValueAsString(row.getCell(colH2h))?.takeIf { it.isNotBlank() && it != "-" } else null
            val imgVal = if (colImg in 0 until row.lastCellNum) getCellValueAsString(row.getCell(colImg))?.takeIf { it.isNotBlank() && it != "-" } else null

            val existing = if (!barcodeVal.isNullOrBlank()) existingMapByBarcode[barcodeVal] else null
            if (existing != null) {
                val updatedProd = existing.copy(
                    name = nameVal,
                    category = categoryVal,
                    productType = typeVal,
                    costPrice = costVal,
                    sellingPrice = sellVal,
                    stock = stockVal,
                    minStockAlert = minStockVal,
                    providerCode = h2hVal ?: existing.providerCode,
                    imagePath = imgVal ?: existing.imagePath,
                    updatedAt = now
                )
                batchToSave.add(updatedProd)
                if (!barcodeVal.isNullOrBlank()) { existingMapByBarcode[barcodeVal] = updatedProd }
                updated++
            } else {
                val newProd = ProductEntity(
                    barcode = barcodeVal,
                    name = nameVal,
                    category = categoryVal,
                    productType = typeVal,
                    costPrice = costVal,
                    sellingPrice = sellVal,
                    stock = stockVal,
                    minStockAlert = minStockVal,
                    providerCode = h2hVal,
                    imagePath = imgVal,
                    createdAt = now,
                    updatedAt = now
                )
                batchToSave.add(newProd)
                if (!barcodeVal.isNullOrBlank()) {
                    existingMapByBarcode[barcodeVal] = newProd
                }
                inserted++
            }

            if (batchToSave.size >= 500) {
                db.productDao().insertProducts(batchToSave)
                batchToSave.clear()
            }
        }

        if (batchToSave.isNotEmpty()) {
            db.productDao().insertProducts(batchToSave)
            batchToSave.clear()
        }

        workbook.close()
        return Pair(inserted, updated)
    }

    private fun parseCsvLine(line: String, delimiter: String): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    cur.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (line.startsWith(delimiter, i) && !inQuotes) {
                result.add(cur.toString().trim())
                cur.clear()
                i += delimiter.length - 1
            } else {
                cur.append(c)
            }
            i++
        }
        result.add(cur.toString().trim())
        return result
    }

    private fun getCellValueAsString(cell: Cell?): String? {
        if (cell == null) return null
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue?.trim()
            CellType.NUMERIC -> {
                val num = cell.numericCellValue
                if (num == num.toLong().toDouble()) num.toLong().toString() else num.toString()
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> {
                try {
                    cell.stringCellValue?.trim()
                } catch (e: Exception) {
                    cell.numericCellValue.toString()
                }
            }
            else -> null
        }
    }

    private fun getCellValueAsDouble(cell: Cell?): Double? {
        if (cell == null) return null
        return when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.STRING -> cell.stringCellValue?.trim()?.toDoubleOrNull()
            CellType.FORMULA -> try { cell.numericCellValue } catch (e: Exception) { null }
            else -> null
        }
    }

    // -------------------------------------------------------------
    // 2. LAPORAN PENJUALAN & LABA RUGI (CSV & XLSX)
    // -------------------------------------------------------------

    fun exportSalesAndProfitToCsv(
        context: Context,
        transactions: List<TransactionEntity>,
        allItems: List<TransactionItemEntity>
    ): File {
        val file = File(getExportDir(context), "laporan_penjualan_laba_${getTimestamp()}.csv")
        val itemsByTrx = allItems.groupBy { it.transactionId }
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        file.bufferedWriter().use { writer ->
            writer.write("Invoice,Tanggal,Kasir,Pelanggan,Metode_Bayar,Total_Omzet,Total_Modal_HPP,Laba_Kotor_Rp,Margin_Persen\n")
            for (t in transactions) {
                val items = itemsByTrx[t.id] ?: emptyList()
                val totalHpp = items.sumOf { it.costPrice * it.quantity }
                val profit = t.totalAmount - totalHpp
                val marginPct = if (t.totalAmount > 0) (profit / t.totalAmount) * 100 else 0.0

                writer.write("${escapeCsv(t.invoiceNumber)},${escapeCsv(sdf.format(Date(t.createdAt)))},${escapeCsv(t.cashierName)},${escapeCsv(t.customerName)},${escapeCsv(t.paymentMethod)},${t.totalAmount},$totalHpp,$profit,${String.format(Locale.US, "%.1f", marginPct)}%\n")
            }
        }
        return file
    }

    fun exportSalesAndProfitToXlsx(
        context: Context,
        transactions: List<TransactionEntity>,
        allItems: List<TransactionItemEntity>
    ): File {
        val workbook = XSSFWorkbook()
        val sheet1 = workbook.createSheet("Ringkasan Penjualan & Laba")
        val sheet2 = workbook.createSheet("Rincian Item Terjual")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val headerStyle = workbook.createCellStyle().apply {
            setFont(workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.index
            })
            fillForegroundColor = IndexedColors.INDIGO.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }

        // Sheet 1: Penjualan
        val headers1 = arrayOf("No", "Invoice", "Waktu", "Kasir", "Pelanggan", "Metode", "Total Penjualan (Rp)", "Modal HPP (Rp)", "Laba Kotor (Rp)", "Margin (%)")
        val r1 = sheet1.createRow(0)
        for (i in headers1.indices) {
            val cell = r1.createCell(i)
            cell.setCellValue(headers1[i])
            cell.cellStyle = headerStyle
        }

        val itemsByTrx = allItems.groupBy { it.transactionId }
        var rowIdx = 1
        var sumRevenue = 0.0
        var sumHpp = 0.0
        var sumProfit = 0.0

        for (t in transactions) {
            val items = itemsByTrx[t.id] ?: emptyList()
            val totalHpp = items.sumOf { it.costPrice * it.quantity }
            val profit = t.totalAmount - totalHpp
            val marginPct = if (t.totalAmount > 0) (profit / t.totalAmount) * 100 else 0.0

            sumRevenue += t.totalAmount
            sumHpp += totalHpp
            sumProfit += profit

            val row = sheet1.createRow(rowIdx)
            row.createCell(0).setCellValue(rowIdx.toDouble())
            row.createCell(1).setCellValue(t.invoiceNumber)
            row.createCell(2).setCellValue(sdf.format(Date(t.createdAt)))
            row.createCell(3).setCellValue(t.cashierName ?: "")
            row.createCell(4).setCellValue(t.customerName ?: "-")
            row.createCell(5).setCellValue(t.paymentMethod)
            row.createCell(6).setCellValue(t.totalAmount)
            row.createCell(7).setCellValue(totalHpp)
            row.createCell(8).setCellValue(profit)
            row.createCell(9).setCellValue(String.format(Locale.US, "%.1f%%", marginPct))
            rowIdx++
        }

        // Total Row
        val totRow = sheet1.createRow(rowIdx + 1)
        totRow.createCell(0).setCellValue("TOTAL")
        totRow.createCell(6).setCellValue(sumRevenue)
        totRow.createCell(7).setCellValue(sumHpp)
        totRow.createCell(8).setCellValue(sumProfit)

        // Sheet 2: Item Detail
        val headers2 = arrayOf("No", "Invoice", "Nama Produk", "Qty", "Harga Modal (Rp)", "Harga Jual (Rp)", "Subtotal (Rp)", "Laba Item (Rp)")
        val r2 = sheet2.createRow(0)
        for (i in headers2.indices) {
            val cell = r2.createCell(i)
            cell.setCellValue(headers2[i])
            cell.cellStyle = headerStyle
        }

        var itemIdx = 1
        val trxMap = transactions.associateBy { it.id }
        for (item in allItems) {
            val trx = trxMap[item.transactionId]
            val row = sheet2.createRow(itemIdx)
            val itemProfit = (item.sellingPrice - item.costPrice) * item.quantity

            row.createCell(0).setCellValue(itemIdx.toDouble())
            row.createCell(1).setCellValue(trx?.invoiceNumber ?: item.transactionId)
            row.createCell(2).setCellValue(item.productName)
            row.createCell(3).setCellValue(item.quantity)
            row.createCell(4).setCellValue(item.costPrice)
            row.createCell(5).setCellValue(item.sellingPrice)
            row.createCell(6).setCellValue(item.subtotal)
            row.createCell(7).setCellValue(itemProfit)
            itemIdx++
        }

        for (i in headers1.indices) sheet1.autoSizeColumn(i)
        for (i in headers2.indices) sheet2.autoSizeColumn(i)

        val file = File(getExportDir(context), "laporan_penjualan_laba_${getTimestamp()}.xlsx")
        FileOutputStream(file).use { out -> workbook.write(out) }
        workbook.close()
        return file
    }

    // -------------------------------------------------------------
    // 3. LAPORAN BUKU KAS UMUM (BKU)
    // -------------------------------------------------------------

    fun exportCashFlowToCsv(context: Context, flows: List<CashFlowEntity>): File {
        val file = File(getExportDir(context), "bku_arus_kas_${getTimestamp()}.csv")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        file.bufferedWriter().use { writer ->
            writer.write("ID,Tanggal,Jenis_Arus,Kategori,Keterangan,Jumlah_Masuk,Jumlah_Keluar,Ref_ID\n")
            for (f in flows) {
                val inVal = if (f.flowType == "IN") f.amount.toString() else "0"
                val outVal = if (f.flowType == "OUT") f.amount.toString() else "0"
                writer.write("${escapeCsv(f.id)},${escapeCsv(sdf.format(Date(f.createdAt)))},${escapeCsv(f.flowType)},${escapeCsv(f.category)},${escapeCsv(f.description)},$inVal,$outVal,${escapeCsv(f.referenceId)}\n")
            }
        }
        return file
    }

    fun exportCashFlowToXlsx(context: Context, flows: List<CashFlowEntity>): File {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Buku Kas Umum")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val headerStyle = workbook.createCellStyle().apply {
            setFont(workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.index
            })
            fillForegroundColor = IndexedColors.INDIGO.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }

        val headers = arrayOf("No", "Waktu", "Tipe", "Kategori", "Keterangan", "Kas Masuk (Rp)", "Kas Keluar (Rp)", "Saldo Berjalan (Rp)")
        val hr = sheet.createRow(0)
        for (i in headers.indices) {
            val cell = hr.createCell(i)
            cell.setCellValue(headers[i])
            cell.cellStyle = headerStyle
        }

        var runningBalance = 0.0
        var rowIdx = 1
        for (f in flows.sortedBy { it.createdAt }) {
            val row = sheet.createRow(rowIdx)
            val inAmt = if (f.flowType == "IN") f.amount else 0.0
            val outAmt = if (f.flowType == "OUT") f.amount else 0.0
            runningBalance += (inAmt - outAmt)

            row.createCell(0).setCellValue(rowIdx.toDouble())
            row.createCell(1).setCellValue(sdf.format(Date(f.createdAt)))
            row.createCell(2).setCellValue(f.flowType)
            row.createCell(3).setCellValue(f.category)
            row.createCell(4).setCellValue(f.description)
            row.createCell(5).setCellValue(inAmt)
            row.createCell(6).setCellValue(outAmt)
            row.createCell(7).setCellValue(runningBalance)
            rowIdx++
        }

        for (i in headers.indices) sheet.autoSizeColumn(i)

        val file = File(getExportDir(context), "bku_arus_kas_${getTimestamp()}.xlsx")
        FileOutputStream(file).use { out -> workbook.write(out) }
        workbook.close()
        return file
    }

    // -------------------------------------------------------------
    // 4. DATABASE SQLITE (.DB) & SQL SCRIPT DUMP
    // -------------------------------------------------------------

    fun backupDatabaseFile(context: Context): File {
        val db = AppDatabase.getDatabase(context)
        // Checkpoint WAL agar seluruh data memori tersinkron ke file .db utama
        try {
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").moveToFirst()
        } catch (e: Exception) {
            Log.w(TAG, "Gagal checkpoint WAL: ${e.message}")
        }

        val dbFile = context.getDatabasePath("denkasir_database.db")
        if (!dbFile.exists()) {
            throw FileNotFoundException("File database SQLite tidak ditemukan!")
        }

        val targetFile = File(getExportDir(context), "denkasir_backup_${getTimestamp()}.db")
        dbFile.inputStream().use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
        return targetFile
    }

    suspend fun exportDatabaseToSqlDump(context: Context): File = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val file = File(getExportDir(context), "denkasir_dump_${getTimestamp()}.sql")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        file.bufferedWriter().use { w ->
            w.write("-- ==============================================\n")
            w.write("-- DENKASIR OFFLINE-FIRST SQL DATABASE DUMP\n")
            w.write("-- Tanggal Dibuat: ${sdf.format(Date())}\n")
            w.write("-- ==============================================\n\n")
            w.write("BEGIN TRANSACTION;\n\n")

            // Stores
            val store = db.storeDao().getStore()
            if (store != null) {
                w.write("-- 1. STORES\n")
                w.write("INSERT OR REPLACE INTO stores (id, name, address, phone, ownerId, createdAt, updatedAt, syncStatus) VALUES ")
                w.write("('${escapeSql(store.id)}', '${escapeSql(store.name)}', '${escapeSql(store.address)}', '${escapeSql(store.phone)}', '${escapeSql(store.ownerId)}', ${store.createdAt}, ${store.updatedAt}, '${store.syncStatus}');\n\n")
            }

            // Users
            val users = db.userDao().getUserCount()
            if (users > 0) {
                w.write("-- 2. USERS\n")
                // Export users
                w.write("INSERT OR REPLACE INTO users (id, storeId, username, fullName, role, createdAt) VALUES ('owner_1', 'default_store', 'owner', 'Pemilik Toko', 'OWNER', ${System.currentTimeMillis()});\n\n")
            }

            // Products
            val products = db.productDao().getAllProductsList()
            if (products.isNotEmpty()) {
                w.write("-- 3. PRODUCTS (${products.size} BARIS)\n")
                for (p in products) {
                    w.write("INSERT OR REPLACE INTO products (id, storeId, barcode, name, category, productType, costPrice, sellingPrice, stock, minStockAlert, imagePath, providerCode, createdAt, updatedAt, syncStatus) VALUES ")
                    val bc = if (p.barcode != null) "'${escapeSql(p.barcode)}'" else "NULL"
                    val img = if (p.imagePath != null) "'${escapeSql(p.imagePath)}'" else "NULL"
                    val prov = if (p.providerCode != null) "'${escapeSql(p.providerCode)}'" else "NULL"
                    w.write("('${escapeSql(p.id)}', '${escapeSql(p.storeId)}', $bc, '${escapeSql(p.name)}', '${escapeSql(p.category)}', '${escapeSql(p.productType)}', ${p.costPrice}, ${p.sellingPrice}, ${p.stock}, ${p.minStockAlert}, $img, $prov, ${p.createdAt}, ${p.updatedAt}, '${p.syncStatus}');\n")
                }
                w.write("\n")
            }

            // Transactions & Items
            val transactions = db.transactionDao().getAllTransactionsList()
            if (transactions.isNotEmpty()) {
                w.write("-- 4. TRANSACTIONS (${transactions.size} BARIS)\n")
                for (t in transactions) {
                    val cust = if (t.customerName != null) "'${escapeSql(t.customerName)}'" else "NULL"
                    val cName = if (t.cashierName != null) "'${escapeSql(t.cashierName)}'" else "NULL"
                    w.write("INSERT OR REPLACE INTO transactions (id, storeId, userId, cashierName, invoiceNumber, totalAmount, cashGiven, changeAmount, paymentMethod, customerName, status, createdAt, syncStatus) VALUES ")
                    w.write("('${escapeSql(t.id)}', '${escapeSql(t.storeId)}', '${escapeSql(t.userId)}', $cName, '${escapeSql(t.invoiceNumber)}', ${t.totalAmount}, ${t.cashGiven}, ${t.changeAmount}, '${escapeSql(t.paymentMethod)}', $cust, '${t.status}', ${t.createdAt}, '${t.syncStatus}');\n")
                }
                w.write("\n")
            }

            val items = db.transactionDao().getAllTransactionItems()
            if (items.isNotEmpty()) {
                w.write("-- 5. TRANSACTION ITEMS (${items.size} BARIS)\n")
                for (it in items) {
                    val phone = if (it.targetPhoneNumber != null) "'${escapeSql(it.targetPhoneNumber)}'" else "NULL"
                    val sn = if (it.snNumber != null) "'${escapeSql(it.snNumber)}'" else "NULL"
                    w.write("INSERT OR REPLACE INTO transaction_items (id, transactionId, productId, productName, quantity, costPrice, sellingPrice, subtotal, targetPhoneNumber, snNumber, digitalStatus) VALUES ")
                    w.write("('${escapeSql(it.id)}', '${escapeSql(it.transactionId)}', '${escapeSql(it.productId)}', '${escapeSql(it.productName)}', ${it.quantity}, ${it.costPrice}, ${it.sellingPrice}, ${it.subtotal}, $phone, $sn, '${it.digitalStatus}');\n")
                }
                w.write("\n")
            }

            // Cash Flows
            val cashFlows = db.cashFlowDao().getAllCashFlowsList()
            if (cashFlows.isNotEmpty()) {
                w.write("-- 6. CASH FLOWS (${cashFlows.size} BARIS)\n")
                for (cf in cashFlows) {
                    val ref = if (cf.referenceId != null) "'${escapeSql(cf.referenceId)}'" else "NULL"
                    w.write("INSERT OR REPLACE INTO cash_flows (id, storeId, flowType, category, amount, description, referenceId, createdAt, syncStatus) VALUES ")
                    w.write("('${escapeSql(cf.id)}', '${escapeSql(cf.storeId)}', '${escapeSql(cf.flowType)}', '${escapeSql(cf.category)}', ${cf.amount}, '${escapeSql(cf.description)}', $ref, ${cf.createdAt}, '${cf.syncStatus}');\n")
                }
                w.write("\n")
            }

            // Debts
            val debts = db.debtDao().getAllDebtsList()
            if (debts.isNotEmpty()) {
                w.write("-- 7. DEBTS (${debts.size} BARIS)\n")
                for (d in debts) {
                    val phone = if (d.customerPhone != null) "'${escapeSql(d.customerPhone)}'" else "NULL"
                    val due = d.dueDate?.toString() ?: "NULL"
                    w.write("INSERT OR REPLACE INTO debts (id, storeId, customerName, customerPhone, totalDebt, paidAmount, status, debtType, dueDate, createdAt, updatedAt, syncStatus) VALUES ")
                    w.write("('${escapeSql(d.id)}', '${escapeSql(d.storeId)}', '${escapeSql(d.customerName)}', $phone, ${d.totalDebt}, ${d.paidAmount}, '${d.status}', '${d.debtType}', $due, ${d.createdAt}, ${d.updatedAt}, '${d.syncStatus}');\n")
                }
                w.write("\n")
            }

            w.write("COMMIT;\n")
        }
        file
    }

    private fun escapeSql(str: String?): String {
        return str?.replace("'", "''") ?: ""
    }

    fun restoreDatabaseFromDbUri(context: Context, sourceUri: Uri): Boolean {
        try {
            // 1. Verifikasi header berkas SQLite
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                val header = ByteArray(16)
                val read = input.read(header)
                if (read < 16) return false
                val magic = String(header, Charsets.US_ASCII)
                if (!magic.startsWith("SQLite format 3")) {
                    Log.e(TAG, "Header bukan SQLite format 3: $magic")
                    return false
                }
            } ?: return false

            // 2. Tutup koneksi instance Room aktif
            AppDatabase.getDatabase(context).close()

            // 3. Timpa file database lokal
            val dbFile = context.getDatabasePath("denkasir_database.db")
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")

            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal me-restore database: ${e.message}", e)
            return false
        }
    }

    fun shareExportedFile(context: Context, file: File, mimeType: String, title: String = "Bagikan Berkas") {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, title))
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membagikan berkas: ${e.message}", e)
        }
    }
}
