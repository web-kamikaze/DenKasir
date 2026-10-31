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
import com.denkasir.utils.SessionManager
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
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var isTorchOn = false
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var currentBarcode: String? = null
    private var scannedQuantity: Int = 1
    private var lastScanTime: Long = 0L
    private var foundProduct: ProductEntity? = null
    private val SCAN_COOLDOWN_MS = 850L

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

        lensFacing = SessionManager.getInstance(this).getPreferredCameraLens()
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

        binding.btnSwitchCamera.setOnClickListener {
            switchCamera()
        }

        binding.btnAddScannedToCart.setOnClickListener {
            val barcodeToSend = currentBarcode ?: foundProduct?.barcode ?: ""
            if (barcodeToSend.isNotEmpty()) {
                val resultIntent = Intent().apply {
                    putExtra("SCANNED_BARCODE", barcodeToSend)
                    putExtra("SCANNED_QTY", scannedQuantity.toDouble())
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
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
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal mengaktifkan kamera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        try {
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
                        val now = System.currentTimeMillis()
                        if (barcodeValue == currentBarcode) {
                            if (now - lastScanTime >= SCAN_COOLDOWN_MS) {
                                lastScanTime = now
                                scannedQuantity++
                                runOnUiThread {
                                    SoundFeedbackUtils.playBarcodeBeepAndVibrate(this@BarcodeScannerActivity)
                                    updateQuantityUi()
                                }
                            }
                        } else {
                            currentBarcode = barcodeValue
                            scannedQuantity = 1
                            lastScanTime = now
                            runOnUiThread {
                                SoundFeedbackUtils.playBarcodeBeepAndVibrate(this@BarcodeScannerActivity)
                                onBarcodeDetected(barcodeValue)
                            }
                        }
                    })
                }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            if (!provider.hasCamera(cameraSelector)) {
                // Fallback if the requested lens is not available
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                }
            }

            val finalSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            if (!provider.hasCamera(finalSelector)) {
                Toast.makeText(this, "Tidak ada kamera yang tersedia pada perangkat", Toast.LENGTH_SHORT).show()
                return
            }

            provider.unbindAll()
            camera = provider.bindToLifecycle(
                this,
                finalSelector,
                preview,
                imageAnalyzer
            )

            // Adjust flashlight button visibility based on whether the active camera has a flash unit
            val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
            binding.btnFlashlight.visibility = if (hasFlash) View.VISIBLE else View.GONE
            if (!hasFlash && isTorchOn) {
                isTorchOn = false
                binding.btnFlashlight.setColorFilter(android.graphics.Color.WHITE)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal mengikat kamera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchCamera() {
        val provider = cameraProvider ?: return
        val targetFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }

        val targetSelector = CameraSelector.Builder().requireLensFacing(targetFacing).build()
        if (!provider.hasCamera(targetSelector)) {
            val label = if (targetFacing == CameraSelector.LENS_FACING_FRONT) "Kamera depan" else "Kamera belakang"
            Toast.makeText(this, "$label tidak tersedia di perangkat ini", Toast.LENGTH_SHORT).show()
            return
        }

        if (isTorchOn) {
            camera?.cameraControl?.enableTorch(false)
            isTorchOn = false
            binding.btnFlashlight.setColorFilter(android.graphics.Color.WHITE)
        }

        lensFacing = targetFacing
        SessionManager.getInstance(this).savePreferredCameraLens(lensFacing)

        binding.btnSwitchCamera.animate()
            .rotationBy(180f)
            .setDuration(250)
            .start()

        val label = if (lensFacing == CameraSelector.LENS_FACING_FRONT) "Kamera Depan" else "Kamera Belakang"
        Toast.makeText(this, "Beralih ke $label", Toast.LENGTH_SHORT).show()

        bindCameraUseCases()
    }

    private fun updateQuantityUi() {
        binding.tvScannedQtyBadge.text = "${scannedQuantity}x"
        binding.tvScannedQtyBadge.visibility = View.VISIBLE
        foundProduct?.let { product ->
            val subtotal = product.sellingPrice * scannedQuantity
            binding.tvScannedPrice.text = CurrencyUtils.formatRupiah(subtotal)
            val stockStr = if (product.stock % 1.0 == 0.0) product.stock.toInt().toString() else product.stock.toString()
            binding.tvScannedStock.text = "Stok: $stockStr"
        }
        // Subtle bounce animation on card to signify increment
        binding.cardProductScanned.animate()
            .scaleX(1.04f).scaleY(1.04f)
            .setDuration(90)
            .withEndAction {
                binding.cardProductScanned.animate().scaleX(1.0f).scaleY(1.0f).duration = 90
            }
            .start()
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
                binding.tvScannedQtyBadge.text = "${scannedQuantity}x"
                binding.tvScannedQtyBadge.visibility = View.VISIBLE
                ImageUtils.loadProductImage(binding.ivProductScanThumb, product)
                binding.cardProductScanned.visibility = View.VISIBLE
            } else {
                foundProduct = null
                binding.tvScannedName.text = "Produk Barcode: $barcode"
                binding.tvScannedVariant.text = "Klik '+' untuk tambah ke kasir"
                binding.tvScannedPrice.text = "Rp 0"
                binding.tvScannedStock.text = "Stok: 0"
                binding.tvScannedQtyBadge.text = "${scannedQuantity}x"
                binding.tvScannedQtyBadge.visibility = View.VISIBLE
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
