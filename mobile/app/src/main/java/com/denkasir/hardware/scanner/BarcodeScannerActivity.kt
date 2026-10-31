package com.denkasir.hardware.scanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.denkasir.R
import com.denkasir.data.local.AppDatabase
import com.denkasir.data.local.entity.ProductEntity
import com.denkasir.databinding.ActivityBarcodeScannerBinding
import com.denkasir.utils.CurrencyUtils
import com.denkasir.utils.ImageUtils
import com.denkasir.utils.SoundFeedbackUtils
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BarcodeScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBarcodeScannerBinding
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null
    private var isTorchOn = false
    private var lastScannedCode: String? = null
    private var foundProduct: ProductEntity? = null

    // Runtime Permission Launcher for Camera
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(
                this,
                "Izin kamera diperlukan untuk memindai barcode produk",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBarcodeScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        setupListeners()
        checkCameraPermissionAndStart()
    }

    private fun checkCameraPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnFlashlight.setOnClickListener {
            toggleFlashlight()
        }

        binding.btnAddScannedToCart.setOnClickListener {
            val barcodeToSend = lastScannedCode ?: foundProduct?.barcode ?: "8991233667990"
            val resultIntent = Intent().apply {
                putExtra("SCANNED_BARCODE", barcodeToSend)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun toggleFlashlight() {
        camera?.let {
            if (it.cameraInfo.hasFlashUnit()) {
                isTorchOn = !isTorchOn
                it.cameraControl.enableTorch(isTorchOn)
                binding.btnFlashlight.setColorFilter(
                    if (isTorchOn) android.graphics.Color.YELLOW else android.graphics.Color.WHITE
                )
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                    }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcodeValue ->
                            if (barcodeValue != lastScannedCode) {
                                lastScannedCode = barcodeValue
                                runOnUiThread {
                                    // Play typical POS barcode scanner beep sound & vibration
                                    SoundFeedbackUtils.playBarcodeBeepAndVibrate(this@BarcodeScannerActivity)
                                    onBarcodeDetected(barcodeValue)
                                }
                            }
                        })
                    }

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal mengaktifkan kamera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onBarcodeDetected(barcode: String) {
        lifecycleScope.launch {
            val product = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(applicationContext).productDao().getProductByBarcode(barcode)
            }
            if (product != null) {
                foundProduct = product
                binding.tvScannedName.text = product.name
                binding.tvScannedVariant.text = "${product.category} - Barcode: $barcode"
                binding.tvScannedPrice.text = CurrencyUtils.formatRupiah(product.sellingPrice)
                val stockStr = if (product.stock % 1.0 == 0.0) product.stock.toInt().toString() else product.stock.toString()
                binding.tvScannedStock.text = "Stok: $stockStr"
                ImageUtils.loadProductImage(binding.ivProductScanThumb, product)
                binding.cardProductScanned.visibility = View.VISIBLE
            } else {
                binding.tvScannedName.text = "Produk Barcode: $barcode"
                binding.tvScannedVariant.text = "Klik '+' untuk tambah ke kasir"
                binding.tvScannedPrice.text = "Rp 0"
                binding.tvScannedStock.text = "Stok: 0"
                binding.ivProductScanThumb.setImageResource(R.drawable.ic_nav_produk)
                binding.cardProductScanned.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private class BarcodeAnalyzer(private val onBarcodeDetected: (String) -> Unit) : ImageAnalysis.Analyzer {
        private val scanner = BarcodeScanning.getClient()

        @OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val firstBarcode = barcodes.firstOrNull()?.rawValue
                        if (firstBarcode != null) {
                            onBarcodeDetected(firstBarcode)
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
}
