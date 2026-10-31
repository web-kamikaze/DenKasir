package com.denkasir.utils

import android.content.Context
import com.denkasir.data.local.entity.TransactionEntity
import com.denkasir.data.local.entity.TransactionItemEntity
import com.denkasir.hardware.printer.BluetoothPrinterDriver
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReceiptFormatterUtils {

    const val WIDTH_58MM = 32
    const val WIDTH_80MM = 48

    // ESC/POS Command Constants
    private val ESC: Byte = 0x1B
    private val GS: Byte = 0x1D

    val CMD_INIT = byteArrayOf(ESC, 0x40)
    val CMD_CODE_PAGE_CP437 = byteArrayOf(ESC, 0x74, 0x00)
    val CMD_ALIGN_CENTER = byteArrayOf(ESC, 0x61, 1)
    val CMD_ALIGN_LEFT = byteArrayOf(ESC, 0x61, 0)
    val CMD_ALIGN_RIGHT = byteArrayOf(ESC, 0x61, 2)
    val CMD_DOUBLE_SIZE = byteArrayOf(GS, 0x21, 0x11)
    val CMD_DOUBLE_WIDTH = byteArrayOf(GS, 0x21, 0x20)
    val CMD_DOUBLE_HEIGHT = byteArrayOf(GS, 0x21, 0x01)
    val CMD_NORMAL_SIZE = byteArrayOf(GS, 0x21, 0x00)
    val CMD_BOLD_ON = byteArrayOf(ESC, 0x45, 1)
    val CMD_BOLD_OFF = byteArrayOf(ESC, 0x45, 0)
    val CMD_FONT_B_SMALL = byteArrayOf(ESC, 0x4D, 1)
    val CMD_FONT_A_NORMAL = byteArrayOf(ESC, 0x4D, 0)
    val CMD_FEED_AND_CUT = byteArrayOf(GS, 0x56, 66, 0)
    val CMD_LINE_FEED = byteArrayOf(0x0A)

    private val SHORT_UNITS = setOf(
        "g", "gr", "gram", "kg", "mg", "ml", "l", "lt", "liter",
        "btl", "botol", "pcs", "pc", "bh", "buah", "dus", "box",
        "pack", "pak", "sak", "can", "tin", "cm", "m", "mm",
        "oz", "sct", "sachet", "rcg", "renceng", "bks", "bungkus",
        "butir", "btr", "lembar", "roll", "rol", "pasang", "psg",
        "cup", "gelas", "kotak", "tabung", "porsi", "strip", "tablet", "kapsul"
    )

    fun getColumnWidth(paperWidthMm: Int): Int {
        return if (paperWidthMm >= 80) WIDTH_80MM else WIDTH_58MM
    }

    fun divider(width: Int, char: Char = '-'): String {
        val count = if (width > 0) width else WIDTH_58MM
        return char.toString().repeat(count)
    }

    fun doubleDivider(width: Int): String {
        return divider(width, '=')
    }

    fun dottedDivider(width: Int): String {
        return divider(width, '.')
    }

    fun wrapText(text: String, width: Int): List<String> {
        val result = mutableListOf<String>()
        val lines = text.lines()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                result.add("")
                continue
            }
            if (line.length <= width) {
                result.add(line)
                continue
            }

            val words = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) {
                result.add("")
                continue
            }

            val rawLines = mutableListOf<MutableList<String>>()
            var currentLine = mutableListOf<String>()
            var currentLen = 0

            for (word in words) {
                if (word.length > width) {
                    if (currentLine.isNotEmpty()) {
                        rawLines.add(currentLine)
                        currentLine = mutableListOf()
                        currentLen = 0
                    }
                    var remaining = word
                    while (remaining.length > width) {
                        rawLines.add(mutableListOf(remaining.substring(0, width)))
                        remaining = remaining.substring(width)
                    }
                    if (remaining.isNotEmpty()) {
                        currentLine.add(remaining)
                        currentLen = remaining.length
                    }
                } else {
                    val addedLen = if (currentLen == 0) word.length else currentLen + 1 + word.length
                    if (addedLen <= width) {
                        currentLine.add(word)
                        currentLen = addedLen
                    } else {
                        if (currentLine.isNotEmpty()) {
                            rawLines.add(currentLine)
                        }
                        currentLine = mutableListOf(word)
                        currentLen = word.length
                    }
                }
            }
            if (currentLine.isNotEmpty()) {
                rawLines.add(currentLine)
            }

            // Lookahead orphan unit & short word balance pass
            for (i in 0 until rawLines.size - 1) {
                val prevWords = rawLines[i]
                val nextWords = rawLines[i + 1]

                if (prevWords.size > 1 && nextWords.size == 1) {
                    val orphanWord = nextWords[0]
                    val cleanOrphan = orphanWord.lowercase(Locale.ROOT).trimEnd('.', '!', '?', ',')
                    val isShortOrUnit = orphanWord.length <= 4 ||
                            SHORT_UNITS.contains(cleanOrphan) ||
                            (orphanWord.length <= 7 && (orphanWord.endsWith("!") || orphanWord.endsWith("?")))

                    val shouldBalance = isShortOrUnit || (orphanWord.length <= 8 && prevWords.size >= 3)
                    if (shouldBalance) {
                        val wordToMove = prevWords.last()
                        val newNextLen = wordToMove.length + 1 + orphanWord.length
                        if (newNextLen <= width) {
                            prevWords.removeAt(prevWords.size - 1)
                            nextWords.add(0, wordToMove)
                        }
                    }
                }
            }

            for (wordsInLine in rawLines) {
                result.add(wordsInLine.joinToString(" "))
            }
        }
        return result
    }

    /**
     * Formats label and value with a vertically aligned colon (:).
     */
    fun keyColonValue(
        label: String,
        value: String,
        width: Int,
        labelWidth: Int = if (width >= WIDTH_80MM) 14 else 11
    ): String {
        val cleanLabel = label.trim()
        val paddedLabel = cleanLabel.padEnd(labelWidth)
        val prefix = "$paddedLabel: "
        val prefixLen = prefix.length
        val valueWidth = width - prefixLen

        if (valueWidth <= 4) {
            return "$cleanLabel: $value"
        }

        val wrappedValue = wrapText(value.trim(), valueWidth)
        if (wrappedValue.isEmpty()) {
            return prefix
        }

        val sb = StringBuilder()
        sb.append(prefix).append(wrappedValue[0])
        for (i in 1 until wrappedValue.size) {
            sb.append("\n").append(" ".repeat(prefixLen)).append(wrappedValue[i])
        }
        return sb.toString()
    }

    fun centerText(text: String, width: Int): String {
        val wrappedLines = wrapText(text, width)
        val sb = StringBuilder()
        for ((index, line) in wrappedLines.withIndex()) {
            if (line.length >= width) {
                sb.append(line)
            } else {
                val pad = (width - line.length) / 2
                val rightPad = width - line.length - pad
                sb.append(" ".repeat(maxOf(0, pad)))
                sb.append(line)
                sb.append(" ".repeat(maxOf(0, rightPad)))
            }
            if (index < wrappedLines.size - 1) {
                sb.append("\n")
            }
        }
        return sb.toString()
    }

    fun twoColumns(left: String, right: String, width: Int): String {
        val availableSpace = width - right.length
        return if (left.length < availableSpace) {
            val spaceCount = width - left.length - right.length
            left + " ".repeat(maxOf(1, spaceCount)) + right
        } else if (left.length == availableSpace) {
            left + right
        } else {
            val leftWrapped = wrapText(left, maxOf(10, width - 8))
            val sb = StringBuilder()
            for (i in 0 until leftWrapped.size - 1) {
                sb.append(leftWrapped[i]).append("\n")
            }
            val lastLine = leftWrapped.last()
            val spaceCount = width - lastLine.length - right.length
            if (spaceCount >= 1) {
                sb.append(lastLine).append(" ".repeat(spaceCount)).append(right)
            } else {
                sb.append(lastLine).append("\n").append(right.padStart(width))
            }
            sb.toString()
        }
    }

    fun threeColumns(
        col1: String,
        col2: String,
        col3: String,
        width: Int,
        col1Width: Int = if (width >= WIDTH_80MM) 24 else 15,
        col2Width: Int = if (width >= WIDTH_80MM) 8 else 6
    ): String {
        val col3Width = width - col1Width - col2Width
        val col1Lines = wrapText(col1, col1Width)
        val sb = StringBuilder()

        for (i in 0 until col1Lines.size - 1) {
            sb.append(col1Lines[i].padEnd(col1Width)).append("\n")
        }

        val lastCol1 = col1Lines.lastOrNull() ?: ""
        val padded1 = lastCol1.padEnd(col1Width)
        val padded2 = col2.padStart(col2Width)
        val padded3 = col3.padStart(col3Width)
        sb.append(padded1).append(padded2).append(padded3)
        return sb.toString()
    }

    fun formatTransactionReceipt(
        context: Context,
        transaction: TransactionEntity,
        items: List<TransactionItemEntity>,
        paperWidthMm: Int = BluetoothPrinterDriver.getSavedPaperWidth(context)
    ): String {
        val session = SessionManager.getInstance(context)
        val storeName = session.getStoreName().ifBlank { "DenKasir Store" }
        val storeAddress = session.getStoreAddress()
        val storePhone = session.getStorePhone()
        val headerSlogan = session.getReceiptHeaderSlogan()
        val footerNote = session.getReceiptFooterNote()
        val layoutStyle = session.getReceiptLayoutStyle()
        val showQr = session.isReceiptShowQr()

        val width = getColumnWidth(paperWidthMm)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()

        // 1. Header Toko
        sb.append(doubleDivider(width)).append("\n")
        sb.append(centerText(storeName, width)).append("\n")
        if (headerSlogan.isNotBlank()) {
            sb.append(centerText(headerSlogan, width)).append("\n")
        }
        if (storeAddress.isNotBlank()) {
            sb.append(centerText(storeAddress, width)).append("\n")
        }
        if (storePhone.isNotBlank()) {
            sb.append(centerText("Telp: " + storePhone, width)).append("\n")
        }
        sb.append(doubleDivider(width)).append("\n")

        val lw = if (width >= WIDTH_80MM) 14 else 11
        // 2. Meta Data Transaksi dengan Titik Dua Terjajar Rapi
        sb.append(keyColonValue("No. Nota", transaction.invoiceNumber, width, lw)).append("\n")
        sb.append(keyColonValue("Tanggal", dateFormat.format(Date(transaction.createdAt)), width, lw)).append("\n")
        sb.append(keyColonValue("Kasir", transaction.cashierName ?: "Kasir", width, lw)).append("\n")
        sb.append(keyColonValue("Bayar", transaction.paymentMethod, width, lw)).append("\n")
        if (!transaction.customerName.isNullOrBlank() && transaction.customerName != "Pelanggan Warung") {
            sb.append(keyColonValue("Pelanggan", transaction.customerName, width, lw)).append("\n")
        }
        sb.append(divider(width)).append("\n")

        // 3. Daftar Item Produk (2-Kolom atau 3-Kolom Tabular)
        if (layoutStyle == SessionManager.LAYOUT_3_COLUMN) {
            sb.append(threeColumns("Item", "Qty", "Total", width)).append("\n")
            sb.append(dottedDivider(width)).append("\n")
            items.forEach { item ->
                val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() + "x" else item.quantity.toString() + "x"
                sb.append(threeColumns(item.productName, qtyStr, CurrencyUtils.formatNumberOnly(item.subtotal), width)).append("\n")
                if (!item.targetPhoneNumber.isNullOrBlank()) {
                    sb.append("  Tujuan: ").append(item.targetPhoneNumber).append("\n")
                }
                if (!item.snNumber.isNullOrBlank()) {
                    sb.append("  SN: ").append(item.snNumber).append("\n")
                }
            }
        } else {
            items.forEach { item ->
                val wrappedName = wrapText(item.productName, width)
                for (line in wrappedName) {
                    sb.append(line).append("\n")
                }
                if (!item.targetPhoneNumber.isNullOrBlank()) {
                    sb.append("  No. HP: ").append(item.targetPhoneNumber).append("\n")
                }
                if (!item.snNumber.isNullOrBlank()) {
                    sb.append("  SN: ").append(item.snNumber).append("\n")
                }
                val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
                val leftCol = "  " + qtyStr + " x " + CurrencyUtils.formatNumberOnly(item.sellingPrice)
                val rightCol = CurrencyUtils.formatNumberOnly(item.subtotal)
                sb.append(twoColumns(leftCol, rightCol, width)).append("\n")
            }
        }

        // 4. Summary Total & Pembayaran
        sb.append(divider(width)).append("\n")
        sb.append(twoColumns("TOTAL TAGIHAN", CurrencyUtils.formatRupiah(transaction.totalAmount), width)).append("\n")

        when (transaction.paymentMethod.uppercase(Locale.ROOT)) {
            "CASH" -> {
                sb.append(twoColumns("BAYAR TUNAI", CurrencyUtils.formatRupiah(transaction.cashGiven), width)).append("\n")
                sb.append(twoColumns("KEMBALIAN", CurrencyUtils.formatRupiah(transaction.changeAmount), width)).append("\n")
            }
            "KESBON", "KASBON" -> {
                sb.append(twoColumns("STATUS KASBON", "BELUM LUNAS", width)).append("\n")
            }
            "QRIS" -> {
                sb.append(twoColumns("STATUS PEMBAYARAN", "LUNAS (QRIS)", width)).append("\n")
            }
            "TRANSFER" -> {
                sb.append(twoColumns("STATUS PEMBAYARAN", "LUNAS (TRANSFER)", width)).append("\n")
            }
        }

        // 5. Footer Struk Bersih Tanpa Orphan Words
        sb.append(doubleDivider(width)).append("\n")
        val rawFooterLines = footerNote.lines()
        for (rawLine in rawFooterLines) {
            val trimmed = rawLine.trim()
            if (trimmed.isNotBlank()) {
                val wrappedLines = wrapText(trimmed, width)
                for (wLine in wrappedLines) {
                    sb.append(centerText(wLine, width)).append("\n")
                }
            }
        }
        if (showQr) {
            sb.append(centerText("[QR CODE: " + transaction.invoiceNumber + "]", width)).append("\n")
        }
        sb.append(centerText("DenKasir POS Indonesia", width)).append("\n")
        sb.append(doubleDivider(width))

        return sb.toString()
    }

    fun buildEscPosReceiptBytes(
        context: Context,
        transaction: TransactionEntity,
        items: List<TransactionItemEntity>,
        paperWidthMm: Int = BluetoothPrinterDriver.getSavedPaperWidth(context)
    ): ByteArray {
        val session = SessionManager.getInstance(context)
        val storeName = session.getStoreName().ifBlank { "DenKasir Store" }
        val storeAddress = session.getStoreAddress()
        val storePhone = session.getStorePhone()
        val headerSlogan = session.getReceiptHeaderSlogan()
        val footerNote = session.getReceiptFooterNote()
        val layoutStyle = session.getReceiptLayoutStyle()
        val showQr = session.isReceiptShowQr()
        val feedLines = session.getReceiptFeedLines()
        val autoCut = session.isReceiptAutoCut()

        val width = getColumnWidth(paperWidthMm)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val charset = Charset.forName("CP437")

        val baos = ByteArrayOutputStream()

        // 1. Init & Codepage
        baos.write(CMD_INIT)
        baos.write(CMD_CODE_PAGE_CP437)

        // 2. Header Toko (Double Size, Centered)
        baos.write(CMD_ALIGN_CENTER)
        baos.write(CMD_DOUBLE_SIZE)
        baos.write(CMD_BOLD_ON)
        baos.write((storeName + "\n").toByteArray(charset))
        baos.write(CMD_NORMAL_SIZE)
        baos.write(CMD_BOLD_OFF)

        if (headerSlogan.isNotBlank()) {
            baos.write((headerSlogan + "\n").toByteArray(charset))
        }
        if (storeAddress.isNotBlank()) {
            baos.write((storeAddress + "\n").toByteArray(charset))
        }
        if (storePhone.isNotBlank()) {
            baos.write(("Telp: " + storePhone + "\n").toByteArray(charset))
        }
        baos.write((doubleDivider(width) + "\n").toByteArray(charset))

        val lw = if (width >= WIDTH_80MM) 14 else 11
        // 3. Meta Data Transaksi dengan Kolom Aligned
        baos.write(CMD_ALIGN_LEFT)
        baos.write((keyColonValue("No. Nota", transaction.invoiceNumber, width, lw) + "\n").toByteArray(charset))
        baos.write((keyColonValue("Tanggal", dateFormat.format(Date(transaction.createdAt)), width, lw) + "\n").toByteArray(charset))
        baos.write((keyColonValue("Kasir", transaction.cashierName ?: "Kasir", width, lw) + "\n").toByteArray(charset))
        baos.write((keyColonValue("Bayar", transaction.paymentMethod, width, lw) + "\n").toByteArray(charset))
        if (!transaction.customerName.isNullOrBlank() && transaction.customerName != "Pelanggan Warung") {
            baos.write((keyColonValue("Pelanggan", transaction.customerName, width, lw) + "\n").toByteArray(charset))
        }
        baos.write((divider(width) + "\n").toByteArray(charset))

        // 4. Daftar Item Produk (2-Kolom atau 3-Kolom Tabular)
        if (layoutStyle == SessionManager.LAYOUT_3_COLUMN) {
            baos.write((threeColumns("Item", "Qty", "Total", width) + "\n").toByteArray(charset))
            baos.write((dottedDivider(width) + "\n").toByteArray(charset))
            items.forEach { item ->
                val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() + "x" else item.quantity.toString() + "x"
                baos.write((threeColumns(item.productName, qtyStr, CurrencyUtils.formatNumberOnly(item.subtotal), width) + "\n").toByteArray(charset))
                if (!item.targetPhoneNumber.isNullOrBlank()) {
                    baos.write(("  Tujuan: " + item.targetPhoneNumber + "\n").toByteArray(charset))
                }
                if (!item.snNumber.isNullOrBlank()) {
                    baos.write(("  SN: " + item.snNumber + "\n").toByteArray(charset))
                }
            }
        } else {
            items.forEach { item ->
                val wrappedName = wrapText(item.productName, width)
                for (line in wrappedName) {
                    baos.write((line + "\n").toByteArray(charset))
                }
                if (!item.targetPhoneNumber.isNullOrBlank()) {
                    baos.write(("  No. HP: " + item.targetPhoneNumber + "\n").toByteArray(charset))
                }
                if (!item.snNumber.isNullOrBlank()) {
                    baos.write(("  SN: " + item.snNumber + "\n").toByteArray(charset))
                }
                val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
                val leftCol = "  " + qtyStr + " x " + CurrencyUtils.formatNumberOnly(item.sellingPrice)
                val rightCol = CurrencyUtils.formatNumberOnly(item.subtotal)
                baos.write((twoColumns(leftCol, rightCol, width) + "\n").toByteArray(charset))
            }
        }

        // 5. Summary Total & Pembayaran
        baos.write((divider(width) + "\n").toByteArray(charset))
        baos.write(CMD_BOLD_ON)
        baos.write((twoColumns("TOTAL TAGIHAN", CurrencyUtils.formatRupiah(transaction.totalAmount), width) + "\n").toByteArray(charset))
        baos.write(CMD_BOLD_OFF)

        when (transaction.paymentMethod.uppercase(Locale.ROOT)) {
            "CASH" -> {
                baos.write((twoColumns("BAYAR TUNAI", CurrencyUtils.formatRupiah(transaction.cashGiven), width) + "\n").toByteArray(charset))
                baos.write((twoColumns("KEMBALIAN", CurrencyUtils.formatRupiah(transaction.changeAmount), width) + "\n").toByteArray(charset))
            }
            "KESBON", "KASBON" -> {
                baos.write((twoColumns("STATUS KASBON", "BELUM LUNAS", width) + "\n").toByteArray(charset))
            }
            "QRIS" -> {
                baos.write((twoColumns("STATUS PEMBAYARAN", "LUNAS (QRIS)", width) + "\n").toByteArray(charset))
            }
            "TRANSFER" -> {
                baos.write((twoColumns("STATUS PEMBAYARAN", "LUNAS (TRANSFER)", width) + "\n").toByteArray(charset))
            }
        }

        // 6. Footer Struk (Rapi tanpa orphan wrap)
        baos.write((doubleDivider(width) + "\n").toByteArray(charset))
        baos.write(CMD_ALIGN_CENTER)
        val rawFooterLines = footerNote.lines()
        for (rawLine in rawFooterLines) {
            val trimmed = rawLine.trim()
            if (trimmed.isNotBlank()) {
                val wrappedLines = wrapText(trimmed, width)
                for (wLine in wrappedLines) {
                    baos.write((wLine + "\n").toByteArray(charset))
                }
            }
        }

        // 7. Native ESC/POS QR Code
        if (showQr) {
            try {
                baos.write(CMD_LINE_FEED)
                val qrData = transaction.invoiceNumber.toByteArray(Charsets.US_ASCII)
                val len = qrData.size + 3
                val pL = (len % 256).toByte()
                val pH = (len / 256).toByte()
                // QR Model 2
                baos.write(byteArrayOf(GS, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))
                // Module Size (5 for 58mm, 7 for 80mm)
                val qrSize = if (paperWidthMm >= 80) 7.toByte() else 5.toByte()
                baos.write(byteArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, qrSize))
                // Error correction level L
                baos.write(byteArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x30))
                // Store data
                baos.write(byteArrayOf(GS, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
                baos.write(qrData)
                // Print QR
                baos.write(byteArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
                baos.write(CMD_LINE_FEED)
            } catch (_: Exception) {}
        }

        baos.write(("DenKasir POS Indonesia\n").toByteArray(charset))
        baos.write((doubleDivider(width) + "\n").toByteArray(charset))

        // 8. Feed Lines & Cut
        repeat(maxOf(1, feedLines)) {
            baos.write(CMD_LINE_FEED)
        }
        if (autoCut) {
            baos.write(CMD_FEED_AND_CUT)
        }

        return baos.toByteArray()
    }

    fun formatTestReceipt(
        context: Context,
        paperWidthMm: Int = BluetoothPrinterDriver.getSavedPaperWidth(context)
    ): String {
        val session = SessionManager.getInstance(context)
        val storeName = session.getStoreName().ifBlank { "DenKasir Official Store" }
        val storeAddress = session.getStoreAddress().ifBlank { "Sistem Kasir Offline-First" }
        val headerSlogan = session.getReceiptHeaderSlogan()
        val footerNote = session.getReceiptFooterNote()
        val layoutStyle = session.getReceiptLayoutStyle()
        val showQr = session.isReceiptShowQr()

        val width = getColumnWidth(paperWidthMm)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()

        sb.append(doubleDivider(width)).append("\n")
        sb.append(centerText(storeName, width)).append("\n")
        if (headerSlogan.isNotBlank()) {
            sb.append(centerText(headerSlogan, width)).append("\n")
        }
        sb.append(centerText(storeAddress, width)).append("\n")
        sb.append(doubleDivider(width)).append("\n")

        val lw = if (width >= WIDTH_80MM) 14 else 11
        sb.append(centerText("TES CETAK PRINTER THERMAL", width)).append("\n")
        sb.append(keyColonValue("Waktu Tes", dateFormat.format(Date()), width, lw)).append("\n")
        sb.append(keyColonValue("Status", "TERHUBUNG OK", width, lw)).append("\n")
        sb.append(keyColonValue("Ukuran", "${paperWidthMm}mm (${width} Kolom)", width, lw)).append("\n")
        sb.append(keyColonValue("Format", if (layoutStyle == SessionManager.LAYOUT_3_COLUMN) "3 Kolom Tabular" else "2 Kolom Standar", width, lw)).append("\n")
        sb.append(divider(width)).append("\n")

        if (layoutStyle == SessionManager.LAYOUT_3_COLUMN) {
            sb.append(threeColumns("Item", "Qty", "Total", width)).append("\n")
            sb.append(dottedDivider(width)).append("\n")
            sb.append(threeColumns("Indomie Goreng", "2x", "6.000", width)).append("\n")
            sb.append(threeColumns("Minyak Goreng 1L", "1x", "18.500", width)).append("\n")
        } else {
            sb.append("Indomie Goreng").append("\n")
            sb.append(twoColumns("  2 x 3.000", "6.000", width)).append("\n")
            sb.append("Minyak Goreng 1L").append("\n")
            sb.append(twoColumns("  1 x 18.500", "18.500", width)).append("\n")
        }

        sb.append(divider(width)).append("\n")
        sb.append(twoColumns("TOTAL CONTOH", "Rp 24.500", width)).append("\n")
        sb.append(twoColumns("BAYAR TUNAI", "Rp 50.000", width)).append("\n")
        sb.append(twoColumns("KEMBALIAN", "Rp 25.500", width)).append("\n")

        sb.append(doubleDivider(width)).append("\n")
        val footerLines = footerNote.lines()
        for (fLine in footerLines) {
            if (fLine.isNotBlank()) {
                sb.append(centerText(fLine, width)).append("\n")
            }
        }
        if (showQr) {
            sb.append(centerText("[QR CODE CONTOH: DENKASIR-TEST]", width)).append("\n")
        }
        sb.append(centerText("DenKasir POS Indonesia", width)).append("\n")
        sb.append(doubleDivider(width))

        return sb.toString()
    }

    fun buildEscPosTestReceiptBytes(
        context: Context,
        paperWidthMm: Int = BluetoothPrinterDriver.getSavedPaperWidth(context)
    ): ByteArray {
        val session = SessionManager.getInstance(context)
        val storeName = session.getStoreName().ifBlank { "DenKasir Official Store" }
        val storeAddress = session.getStoreAddress().ifBlank { "Sistem Kasir Offline-First" }
        val headerSlogan = session.getReceiptHeaderSlogan()
        val footerNote = session.getReceiptFooterNote()
        val layoutStyle = session.getReceiptLayoutStyle()
        val showQr = session.isReceiptShowQr()
        val feedLines = session.getReceiptFeedLines()
        val autoCut = session.isReceiptAutoCut()

        val width = getColumnWidth(paperWidthMm)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val charset = Charset.forName("CP437")

        val baos = ByteArrayOutputStream()

        baos.write(CMD_INIT)
        baos.write(CMD_CODE_PAGE_CP437)

        // Header
        baos.write(CMD_ALIGN_CENTER)
        baos.write(CMD_DOUBLE_SIZE)
        baos.write(CMD_BOLD_ON)
        baos.write((storeName + "\n").toByteArray(charset))
        baos.write(CMD_NORMAL_SIZE)
        baos.write(CMD_BOLD_OFF)

        if (headerSlogan.isNotBlank()) {
            baos.write((headerSlogan + "\n").toByteArray(charset))
        }
        baos.write((storeAddress + "\n").toByteArray(charset))
        baos.write((doubleDivider(width) + "\n").toByteArray(charset))

        // Info Test
        val lw = if (width >= WIDTH_80MM) 14 else 11
        baos.write(CMD_ALIGN_LEFT)
        baos.write(CMD_BOLD_ON)
        baos.write((centerText("TES CETAK PRINTER THERMAL", width) + "\n").toByteArray(charset))
        baos.write(CMD_BOLD_OFF)
        baos.write((keyColonValue("Waktu Tes", dateFormat.format(Date()), width, lw) + "\n").toByteArray(charset))
        baos.write((keyColonValue("Status", "TERHUBUNG OK", width, lw) + "\n").toByteArray(charset))
        baos.write((keyColonValue("Ukuran", "${paperWidthMm}mm (${width} Kolom)", width, lw) + "\n").toByteArray(charset))
        baos.write((keyColonValue("Format", if (layoutStyle == SessionManager.LAYOUT_3_COLUMN) "3 Kolom Tabular" else "2 Kolom Standar", width, lw) + "\n").toByteArray(charset))
        baos.write((divider(width) + "\n").toByteArray(charset))

        if (layoutStyle == SessionManager.LAYOUT_3_COLUMN) {
            baos.write((threeColumns("Item", "Qty", "Total", width) + "\n").toByteArray(charset))
            baos.write((dottedDivider(width) + "\n").toByteArray(charset))
            baos.write((threeColumns("Indomie Goreng", "2x", "6.000", width) + "\n").toByteArray(charset))
            baos.write((threeColumns("Minyak Goreng 1L", "1x", "18.500", width) + "\n").toByteArray(charset))
        } else {
            baos.write(("Indomie Goreng\n").toByteArray(charset))
            baos.write((twoColumns("  2 x 3.000", "6.000", width) + "\n").toByteArray(charset))
            baos.write(("Minyak Goreng 1L\n").toByteArray(charset))
            baos.write((twoColumns("  1 x 18.500", "18.500", width) + "\n").toByteArray(charset))
        }

        baos.write((divider(width) + "\n").toByteArray(charset))
        baos.write(CMD_BOLD_ON)
        baos.write((twoColumns("TOTAL CONTOH", "Rp 24.500", width) + "\n").toByteArray(charset))
        baos.write(CMD_BOLD_OFF)
        baos.write((twoColumns("BAYAR TUNAI", "Rp 50.000", width) + "\n").toByteArray(charset))
        baos.write((twoColumns("KEMBALIAN", "Rp 25.500", width) + "\n").toByteArray(charset))

        baos.write((doubleDivider(width) + "\n").toByteArray(charset))
        baos.write(CMD_ALIGN_CENTER)
        val footerLines = footerNote.lines()
        for (fLine in footerLines) {
            if (fLine.isNotBlank()) {
                baos.write((fLine + "\n").toByteArray(charset))
            }
        }

        if (showQr) {
            try {
                baos.write(CMD_LINE_FEED)
                val qrData = "DENKASIR-OFFICIAL-TEST".toByteArray(Charsets.US_ASCII)
                val len = qrData.size + 3
                val pL = (len % 256).toByte()
                val pH = (len / 256).toByte()
                baos.write(byteArrayOf(GS, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))
                val qrSize = if (paperWidthMm >= 80) 7.toByte() else 5.toByte()
                baos.write(byteArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, qrSize))
                baos.write(byteArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x30))
                baos.write(byteArrayOf(GS, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
                baos.write(qrData)
                baos.write(byteArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
                baos.write(CMD_LINE_FEED)
            } catch (_: Exception) {}
        }

        baos.write(("Printer Siap Digunakan Kasir\n").toByteArray(charset))
        baos.write(("DenKasir POS Indonesia\n").toByteArray(charset))
        baos.write((doubleDivider(width) + "\n").toByteArray(charset))

        repeat(maxOf(1, feedLines)) {
            baos.write(CMD_LINE_FEED)
        }
        if (autoCut) {
            baos.write(CMD_FEED_AND_CUT)
        }

        return baos.toByteArray()
    }
}