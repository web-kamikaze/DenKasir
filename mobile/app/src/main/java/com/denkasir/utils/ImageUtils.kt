package com.denkasir.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.ImageView
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.denkasir.R
import com.denkasir.data.local.entity.ProductEntity
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

object ImageUtils {

    private const val TAG = "ImageUtils"

    fun getProductFallbackIcon(
        category: String?,
        productName: String?,
        productType: String? = null
    ): Int {
        val cat = category?.trim()?.uppercase(Locale.ROOT) ?: ""
        val name = productName?.trim()?.uppercase(Locale.ROOT) ?: ""
        val type = productType?.trim()?.uppercase(Locale.ROOT) ?: ""

        if (type == "DIGITAL" || cat.contains("DIGITAL") || cat.contains("PLN") || name.contains("PLN") || name.contains("LISTRIK")) {
            if (name.contains("PLN") || name.contains("LISTRIK") || cat.contains("PLN")) return R.drawable.ic_digital_listrik_pln
            if (cat.contains("E-MONEY") || name.contains("GOPAY") || name.contains("OVO") || name.contains("DANA") || name.contains("SHOPEEPAY")) return R.drawable.ic_digital_uang_elektronik
            if (name.contains("PAKET DATA") || name.contains("KUOTA") || name.contains("GB")) return R.drawable.ic_digital_paket_data
            return R.drawable.ic_digital_pulsa
        }
        if (cat.contains("PULSA") || name.contains("PULSA") || name.contains("TELKOMSEL") || name.contains("INDOSAT") || name.contains("XL") || name.contains("TRI") || name.contains("AXIS") || name.contains("SMARTFREN")) {
            return R.drawable.ic_digital_pulsa
        }
        if (cat.contains("PAKET DATA") || (cat.contains("DATA") && !cat.contains("DAPUR")) || name.contains("PAKET DATA") || name.contains("KUOTA") || name.contains("GB")) {
            return R.drawable.ic_digital_paket_data
        }
        if (cat.contains("PDAM") || (cat.contains("AIR") && type == "DIGITAL") || name.contains("PDAM")) {
            return R.drawable.ic_digital_pdam
        }
        if (cat.contains("E-MONEY") || cat.contains("UANG") || cat.contains("DOMPET") || name.contains("GOPAY") || name.contains("OVO") || name.contains("DANA") || name.contains("SHOPEEPAY")) {
            return R.drawable.ic_digital_uang_elektronik
        }
        if (cat.contains("SEGAR") || cat.contains("BEKU") || cat == "KG" || cat.contains("CURAH") || name.contains("BERAS") || name.contains("GULA") || name.contains("TEPUNG") || name.contains("TELUR") || name.contains("DAGING") || name.contains("AYAM") || name.contains("IKAN") || name.contains("BUAH") || name.contains("SAYUR")) {
            return R.drawable.ic_kg
        }
        if (cat.contains("MAKANAN") || cat.contains("MINUMAN") || cat.contains("PERSONAL") || cat.contains("TUBUH") || cat.contains("IBU") || cat.contains("ANAK") || cat.contains("LIFESTYLE") || cat.contains("RUMAH") || cat.contains("DAPUR") || cat.contains("KESEHATAN") || cat.contains("PET") || cat.contains("HEWAN") || cat == "PCS" || name.contains("INDOMIE") || name.contains("MIE") || name.contains("MINYAK") || name.contains("SABUN") || name.contains("KOPI") || name.contains("SUSU") || name.contains("KECAP")) {
            return R.drawable.ic_pcs
        }
        return R.drawable.ic_nav_produk
    }

    fun normalizeDirectory(path: String?): File? {
        if (path.isNullOrBlank()) return null
        val direct = File(path.trim())
        if (direct.exists() && direct.isDirectory) return direct

        val extPath = Environment.getExternalStorageDirectory().absolutePath
        val alt1 = if (path.startsWith("/sdcard")) {
            path.replaceFirst("/sdcard", extPath)
        } else if (path.startsWith(extPath)) {
            path.replaceFirst(extPath, "/sdcard")
        } else {
            null
        }
        if (alt1 != null) {
            val fAlt = File(alt1.trim())
            if (fAlt.exists() && fAlt.isDirectory) return fAlt
        }
        return null
    }

    fun getResolvedImagesDirectories(context: Context): List<File> {
        val dirs = mutableListOf<File>()
        val customPath = SessionManager.getInstance(context).getCustomImagesDir()
        if (!customPath.isNullOrBlank()) {
            normalizeDirectory(customPath)?.let { dirs.add(it) }
        }
        dirs.add(File(context.filesDir, "product_images"))
        val extDir = Environment.getExternalStorageDirectory()
        val candidates = listOf(
            File(extDir, "image"),
            File("/sdcard/image"),
            File(extDir, "Download/image"),
            File("/sdcard/Download/image"),
            File(extDir, "product_images"),
            File("/sdcard/product_images"),
            File(extDir, "Download/product_images"),
            File(extDir, "Pictures/image"),
            File("/sdcard/Pictures/image"),
            File(extDir, "Pictures/product_images"),
            File(extDir, "DCIM/image")
        )
        for (cand in candidates) {
            if (cand.exists() && cand.isDirectory && !dirs.any { it.absolutePath == cand.absolutePath }) {
                dirs.add(cand)
            }
        }
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let {
            if (it.exists() && it.isDirectory && !dirs.any { d -> d.absolutePath == it.absolutePath }) dirs.add(it)
        }
        return dirs
    }

    fun findProductImageTarget(
        context: Context,
        imagePath: String?,
        barcode: String?,
        productId: String? = null
    ): Any? {
        val searchDirs = getResolvedImagesDirectories(context)

        // 1. Jika ada barcode, cari file offline lokal {barcode}.jpg / {barcode}.png terlebih dahulu
        if (!barcode.isNullOrBlank()) {
            val cleanBc = barcode.trim()
            val extensions = listOf(".jpg", ".png", ".jpeg", ".webp")
            for (dir in searchDirs) {
                if (!dir.exists() || !dir.isDirectory) continue
                for (ext in extensions) {
                    val cand = File(dir, "$cleanBc$ext")
                    if (cand.exists() && cand.length() > 0) {
                        return cand
                    }
                }
            }
        }

        // 2. Jika imagePath adalah uri content:// atau file://
        if (!imagePath.isNullOrBlank()) {
            if (imagePath.startsWith("content://") || imagePath.startsWith("file://")) {
                return Uri.parse(imagePath)
            }

            // Cek jika path lokal absolut langsung
            val directFile = File(imagePath)
            if (directFile.isAbsolute && directFile.exists() && directFile.length() > 0) {
                return directFile
            }

            // Cek nama file lokal di dalam direktori pencarian
            val fileName = directFile.name
            if (fileName.isNotBlank() && fileName != "-") {
                for (dir in searchDirs) {
                    if (!dir.exists() || !dir.isDirectory) continue
                    val cand = File(dir, fileName)
                    if (cand.exists() && cand.length() > 0) {
                        return cand
                    }
                }
            }

            // 3. Jika imagePath adalah URL internet
            if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
                return imagePath
            }
        }

        // 4. Cek ID Produk
        if (!productId.isNullOrBlank()) {
            val cleanId = productId.trim()
            for (dir in searchDirs) {
                if (!dir.exists() || !dir.isDirectory) continue
                val cand = File(dir, "$cleanId.jpg")
                if (cand.exists() && cand.length() > 0) return cand
            }
        }

        return null
    }

    fun loadProductImage(
        imageView: ImageView,
        imagePath: String?,
        category: String? = null,
        productName: String? = null,
        productType: String? = null,
        barcode: String? = null,
        productId: String? = null,
        cornerRadiusDp: Int = 12
    ) {
        val context = imageView.context
        val fallbackIcon = getProductFallbackIcon(category, productName, productType)
        val density = context.resources.displayMetrics.density
        val cornerRadiusPx = (cornerRadiusDp * density).toInt()

        val targetLoad = findProductImageTarget(context, imagePath, barcode, productId)

        if (targetLoad != null) {
            Glide.with(context)
                .load(targetLoad)
                .apply(
                    RequestOptions()
                        .transform(CenterCrop(), RoundedCorners(cornerRadiusPx))
                        .placeholder(fallbackIcon)
                        .error(fallbackIcon)
                )
                .into(imageView)
        } else {
            imageView.setImageResource(fallbackIcon)
        }
    }

    fun loadProductImage(
        imageView: ImageView,
        product: ProductEntity?,
        cornerRadiusDp: Int = 12
    ) {
        if (product == null) {
            imageView.setImageResource(R.drawable.ic_nav_produk)
            return
        }
        loadProductImage(
            imageView = imageView,
            imagePath = product.imagePath,
            category = product.category,
            productName = product.name,
            productType = product.productType,
            barcode = product.barcode,
            productId = product.id,
            cornerRadiusDp = cornerRadiusDp
        )
    }

    fun copyUriToInternalStorage(context: Context, sourceUri: Uri): String? {
        return try {
            val imagesDir = File(context.filesDir, "product_images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }
            val targetFile = File(imagesDir, "prod_${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            targetFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Gagal copy gambar: ${e.message}", e)
            null
        }
    }

    fun createTempCameraUri(context: Context): Pair<File, Uri> {
        val cacheDir = File(context.cacheDir, "camera_pictures")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val tempFile = File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            tempFile
        )
        return Pair(tempFile, uri)
    }
    fun downloadImageFromUrl(
        context: Context,
        imageUrl: String,
        targetFileName: String? = null
    ): File? {
        return try {
            if (!imageUrl.startsWith("http://", ignoreCase = true) && !imageUrl.startsWith("https://", ignoreCase = true)) {
                return null
            }
            val imagesDir = File(context.filesDir, "product_images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }
            val fileName = targetFileName ?: "prod_${UUID.randomUUID()}.jpg"
            val targetFile = File(imagesDir, fileName)

            val url = java.net.URL(imageUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android DenKasir POS)")
            connection.connect()

            if (connection.responseCode in 200..299) {
                connection.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (targetFile.exists() && targetFile.length() > 0) {
                    targetFile
                } else {
                    targetFile.delete()
                    null
                }
            } else {
                Log.w(TAG, "Gagal unduh gambar HTTP ${connection.responseCode} dari $imageUrl")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception saat unduh gambar dari $imageUrl: ${e.message}", e)
            null
        }
    }

    suspend fun batchDownloadAllProductImages(
        context: Context,
        onProgress: ((current: Int, total: Int, successCount: Int) -> Unit)? = null
    ): Pair<Int, Int> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val db = com.denkasir.data.local.AppDatabase.getDatabase(context)
        val allProducts = db.productDao().getAllProductsList()
        val urlProducts = allProducts.filter { 
            !it.imagePath.isNullOrBlank() && 
            (it.imagePath.startsWith("http://", ignoreCase = true) || it.imagePath.startsWith("https://", ignoreCase = true))
        }

        var successCount = 0
        var failCount = 0
        val total = urlProducts.size

        urlProducts.forEachIndexed { index, product ->
            val safeName = "prod_${product.id.take(8)}_${System.currentTimeMillis() % 10000}.jpg"
            val localFile = downloadImageFromUrl(context, product.imagePath!!, safeName)
            if (localFile != null) {
                val updatedProduct = product.copy(
                    imagePath = localFile.absolutePath,
                    updatedAt = System.currentTimeMillis()
                )
                db.productDao().updateProduct(updatedProduct)
                successCount++
            } else {
                failCount++
            }
            onProgress?.invoke(index + 1, total, successCount)
        }

        Pair(successCount, failCount)
    }

    fun getProductImagesStorageInfo(context: Context): Pair<Int, Long> {
        val imagesDir = File(context.filesDir, "product_images")
        if (!imagesDir.exists() || !imagesDir.isDirectory) {
            return Pair(0, 0L)
        }
        val files = imagesDir.listFiles() ?: return Pair(0, 0L)
        var totalBytes = 0L
        var validFiles = 0
        for (f in files) {
            if (f.isFile) {
                validFiles++
                totalBytes += f.length()
            }
        }
        return Pair(validFiles, totalBytes)
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }

    fun clearTemporaryCaches(context: Context): Long {
        var freedBytes = 0L
        try {
            val cameraDir = File(context.cacheDir, "camera_pictures")
            if (cameraDir.exists()) {
                cameraDir.listFiles()?.forEach { file ->
                    freedBytes += file.length()
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membersihkan cache kamera: ${e.message}", e)
        }
        return freedBytes
    }
    fun resolveActiveImagesDirectory(context: Context): File {
        val custom = SessionManager.getInstance(context).getCustomImagesDir()
        if (!custom.isNullOrBlank()) {
            normalizeDirectory(custom)?.let { return it }
        }

        val extDir = Environment.getExternalStorageDirectory()
        val candidates = listOf(
            File(extDir, "image"),
            File("/sdcard/image"),
            File(extDir, "Download/image"),
            File("/sdcard/Download/image"),
            File(extDir, "product_images"),
            File("/sdcard/product_images"),
            File(extDir, "Download/product_images"),
            File(extDir, "Pictures/image"),
            File("/sdcard/Pictures/image"),
            File(extDir, "Pictures/product_images"),
            File(context.filesDir, "product_images")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory } ?: File(extDir, "image")
    }

    suspend fun syncImagesFromFolder(
        context: Context,
        folderPath: String? = null,
        onProgress: ((current: Int, total: Int, matched: Int) -> Unit)? = null
    ): Pair<Int, Int> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val targetFolder = if (!folderPath.isNullOrBlank()) {
            normalizeDirectory(folderPath) ?: resolveActiveImagesDirectory(context)
        } else {
            resolveActiveImagesDirectory(context)
        }

        if (!targetFolder.exists() || !targetFolder.isDirectory) {
            return@withContext Pair(0, 0)
        }

        val imageExtensions = setOf("jpg", "jpeg", "png", "webp")
        val allFiles = targetFolder.listFiles { file ->
            file.isFile && file.extension.lowercase(Locale.ROOT) in imageExtensions
        }?.toList() ?: emptyList()

        if (allFiles.isEmpty()) {
            return@withContext Pair(0, 0)
        }

        val db = com.denkasir.data.local.AppDatabase.getDatabase(context)
        val allProducts = db.productDao().getAllProductsList()
        val productMapByBarcode = mutableMapOf<String, ProductEntity>()
        for (p in allProducts) {
            if (!p.barcode.isNullOrBlank()) {
                productMapByBarcode[p.barcode.trim().uppercase(Locale.ROOT)] = p
            }
            productMapByBarcode[p.id.trim().uppercase(Locale.ROOT)] = p
        }

        var matchedCount = 0
        val batchUpdates = mutableListOf<ProductEntity>()
        val now = System.currentTimeMillis()

        allFiles.forEachIndexed { index, file ->
            val baseName = file.nameWithoutExtension.trim().uppercase(Locale.ROOT)
            val product = productMapByBarcode[baseName]
            if (product != null) {
                val updated = product.copy(
                    imagePath = file.absolutePath,
                    updatedAt = now
                )
                batchUpdates.add(updated)
                productMapByBarcode[baseName] = updated
                matchedCount++
            }
            if (batchUpdates.size >= 500) {
                db.productDao().insertProducts(batchUpdates)
                batchUpdates.clear()
            }
            if ((index + 1) % 200 == 0 || index + 1 == allFiles.size) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onProgress?.invoke(index + 1, allFiles.size, matchedCount)
                }
            }
        }

        if (batchUpdates.isNotEmpty()) {
            db.productDao().insertProducts(batchUpdates)
            batchUpdates.clear()
        }

        Pair(matchedCount, allFiles.size)
    }

    fun getActiveImagesFolderSummary(context: Context): String {
        val custom = SessionManager.getInstance(context).getCustomImagesDir()
        val activeDir = resolveActiveImagesDirectory(context)

        val imageExtensions = setOf("jpg", "jpeg", "png", "webp")
        val count = if (activeDir.exists() && activeDir.isDirectory) {
            activeDir.listFiles { file ->
                file.isFile && file.extension.lowercase(Locale.ROOT) in imageExtensions
            }?.size ?: 0
        } else {
            0
        }

        val label = if (!custom.isNullOrBlank()) "Folder Khusus: ${activeDir.absolutePath}" else "Folder Otomatis: ${activeDir.absolutePath}"
        return "$label ($count foto offline)"
    }
}
