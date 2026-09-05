package com.denkasir.utils

import android.content.Context
import android.net.Uri
import android.widget.ImageView
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.denkasir.R
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageUtils {

    fun loadProductImage(
        imageView: ImageView,
        imagePath: String?,
        cornerRadiusDp: Int = 12,
        placeholderRes: Int = R.drawable.ic_nav_produk
    ) {
        if (!imagePath.isNullOrBlank()) {
            val file = File(imagePath)
            val loadTarget: Any = if (file.exists()) file else imagePath

            val density = imageView.context.resources.displayMetrics.density
            val cornerRadiusPx = (cornerRadiusDp * density).toInt()

            Glide.with(imageView.context)
                .load(loadTarget)
                .apply(
                    RequestOptions()
                        .transform(CenterCrop(), RoundedCorners(cornerRadiusPx))
                        .placeholder(placeholderRes)
                        .error(placeholderRes)
                )
                .into(imageView)
        } else {
            imageView.setImageResource(placeholderRes)
        }
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
            e.printStackTrace()
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
}

