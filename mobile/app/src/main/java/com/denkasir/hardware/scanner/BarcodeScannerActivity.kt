package com.denkasir.hardware.scanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
import com.denkasir.ui.pos.CartManager
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

    companion object {
        const val EXTRA_SCAN_MODE = "EXTRA_SCAN_MODE"
        const val MODE_CASHIER = "MODE_CASHIER"
        const val MODE_DEFAULT = "MODE_DEFAULT"
        const val CASHIER_MODE_ADDED = "CASHIER_MODE_ADDED"
        const val TOTAL_SCANNED_COUNT = "TOTAL_SCANNED_COUNT"
        const val SCANNED_BARCODE = "SCANNED_BARCODE"
        const val SCANNED_QTY = "SCANNED_QTY"
    }

    private lateinit var binding: ActivityBarcodeScannerBinding
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var isTorchOn = false
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var isCashierMode: Boolean = false
    private var sessionScannedCount: Int = 0

    private var currentBarcode: String? = null
    private var scannedQuantity: Int = 1
    private var lastScanTime: Long = 0L
    private var foundProduct: ProductEntity? = null

    private val SCAN_COOLDOWN_SAME_MS = 1000L
    private val SCAN_COOLDOWN_DIFF_MS = 650L

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

        isCashierMode = intent.getStringExtra(EXTRA_SCAN_MODE) == MODE_CASHIER
        binding.cardProductScanned.visibility = View.GONE

        if (isCashierMode) {
            binding.tvHeaderTitle.text = "Kasir - Scan Barcode"
            binding.btnDoneScanning.visibility = View.VISIBLE
            updateHeaderCartSummary()
        } else {
            binding.tvHeaderTitle.text = "Scan Barcode"
            binding.btnDoneScanning.visibility = View.GONE
            binding.tvInstruction.text = "Arahkan barcode ke dalam area kotak"
        }

        lensFacing = SessionManager.getInstance(this).getPreferredCameraLens()
        cameraExecutor = Executors.newSingleThreadExecutor()
        setupListeners()
        setupBackHandling()
        checkCameraPermissionAndStart()
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleExit()
            }
        })
    }

    private fun handleExit() {
        if (isCashierMode) {
            val resultIntent = Intent().apply {
                putExtra(CASHIER_MODE_ADDED, true)
                putExtra(TOTAL_SCANNED_COUNT, sessionScannedCount)
            }
            setResult(Activity.RESULT_OK, resultIntent)
        }
        finish()
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
            handleExit()
        }

        binding.btnDoneScanning.setOnClickListener {
            handleExit()
        }

        binding.btnFlashlight.setOnClickListener {
            toggleFlashlight()
        }

        binding.btnSwitchCamera.setOnClickListener {
            switchCamera()
        }

        binding.btnAddScannedToCart.setOnClickListener {
            if (isCashierMode) {
                foundProduct?.let { product ->
                    CartManager.addToCart(product, 1.0)
                    sessionScannedCount++
                    SoundFeedbackUtils.playBarcodeBeepAndVibrate(this@BarcodeScannerActivity)
                    updateScannedProductCard(product)
                    updateHeaderCartSummary()
                }
            } else {
                val barcodeToSend = currentBarcode ?: foundProduct?.barcode ?: ""
                if (barcodeToSend.isNotEmpty()) {
                    val resultIntent = Intent().apply {
                        putExtra(SCANNED_BARCODE, barcodeToSend)
                        putExtra(SCANNED_QTY, scannedQuantity.toDouble())
                    }
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }
            }
        }
    }

    private fun updateHeaderCartSummary() {
        if (!isCashierMode) return
        val totalCount = CartManager.getTotalItemCount()
        val totalBayar = CartManager.getTotalBayar()
        val countInt = totalCount.toInt()

        binding.btnDoneScanning.text = if (countInt > 0) "Selesai ($countInt)" else "Selesai"
        if (countInt > 0) {
            binding.tvInstruction.text = "Keranjang: $countInt item (${CurrencyUtils.formatRupiah(totalBayar)})"
        } else {
            binding.tvInstruction.text = "Arahkan barcode ke kotak (Otomatis ditambahkan)"
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
                        val isSame = (barcodeValue == currentBarcode)
                        val requiredCooldown = if (isSame) SCAN_COOLDOWN_SAME_MS else SCAN_COOLDOWN_DIFF_MS

                        if (now - lastScanTime < requiredCooldown) {
                            return@BarcodeAnalyzer
                        }

                        lastScanTime = now
                        currentBarcode = barcodeValue

                        if (isCashierMode) {
                            processBarcodeInCashierMode(barcodeValue)
                        } else {
                            processBarcodeInDefaultMode(barcodeValue, isSame)
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

    private fun processBarcodeInCashierMode(barcode: String) {
        lifecycleScope.launch {
            val product = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(applicationContext).productDao().getProductByBarcode(barcode)
            }
            withContext(Dispatchers.Main) {
                if (product != null) {
                    foundProduct = product
                    CartManager.addToCart(product, 1.0)
                    sessionScannedCount++
                    SoundFeedbackUtils.playBarcodeBeepAndVibrate(this@BarcodeScannerActivity)
                    updateScannedProductCard(product)
                    updateHeaderCartSummary()
                } else {
                    showUnregisteredBarcode(barcode)
                }
            }
        }
    }

    private fun processBarcodeInDefaultMode(barcode: String, isSame: Boolean) {
        lifecycleScope.launch {
            if (isSame) {
                scannedQuantity++
                withContext(Dispatchers.Main) {
                    SoundFeedbackUtils.playBarcodeBeepAndVibrate(this@BarcodeScannerActivity)
                    updateQuantityUiDefault()
                }
            } else {
                scannedQuantity = 1
                val product = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(applicationContext).productDao().getProductByBarcode(barcode)
                }
                withContext(Dispatchers.Main) {
                    SoundFeedbackUtils.playBarcodeBeepAndVibrate(this@BarcodeScannerActivity)
                    if (product != null) {
                        foundProduct = product
                        updateScannedProductCard(product)
                    } else {
                        showUnregisteredBarcode(barcode)
                    }
                }
            }
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

    private fun updateQuantityUiDefault() {
        binding.tvScannedQtyBadge.text = "${scannedQuantity}x"
        binding.tvScannedQtyBadge.visibility = View.VISIBLE
        foundProduct?.let { product ->
            val subtotal = product.sellingPrice * scannedQuantity
            binding.tvScannedPrice.text = CurrencyUtils.formatRupiah(subtotal)
            val stockStr = if (product.stock % 1.0 == 0.0) product.stock.toInt().toString() else product.stock.toString()
            binding.tvScannedStock.text = "Stok: $stockStr"
        }
        binding.cardProductScanned.animate()
            .scaleX(1.04f).scaleY(1.04f)
            .setDuration(90)
            .withEndAction {
                binding.cardProductScanned.animate().scaleX(1.0f).scaleY(1.0f).duration = 90
            }
            .start()
    }

    private fun updateScannedProductCard(product: ProductEntity) {
        binding.tvScannedName.text = product.name
        binding.tvScannedPrice.text = CurrencyUtils.formatRupiah(product.sellingPrice)
        val stockStr = if (product.stock % 1.0 == 0.0) product.stock.toInt().toString() else product.stock.toString()
        binding.tvScannedStock.text = "Stok: $stockStr"

        if (isCashierMode) {
            val inCartQty = CartManager.getProductQuantity(product.id)
            val qtyText = if (inCartQty % 1.0 == 0.0) inCartQty.toInt().toString() else inCartQty.toString()
            binding.tvScannedVariant.text = "${product.category} • Sudah otomatis ditambahkan"
            binding.tvScannedQtyBadge.text = "${qtyText}x di kasir"
            binding.tvScannedQtyBadge.visibility = View.VISIBLE
            binding.btnAddScannedToCart.visibility = View.VISIBLE
        } else {
            binding.tvScannedVariant.text = "${product.category} - Barcode: ${product.barcode}"
            binding.tvScannedQtyBadge.text = "${scannedQuantity}x"
            binding.tvScannedQtyBadge.visibility = View.VISIBLE
            binding.btnAddScannedToCart.visibility = View.VISIBLE
        }

        ImageUtils.loadProductImage(binding.ivProductScanThumb, product)
        binding.cardProductScanned.visibility = View.VISIBLE

        binding.cardProductScanned.animate()
            .scaleX(1.04f).scaleY(1.04f)
            .setDuration(90)
            .withEndAction {
                binding.cardProductScanned.animate().scaleX(1.0f).scaleY(1.0f).duration = 90
            }
            .start()
    }

    private fun showUnregisteredBarcode(barcode: String) {
        foundProduct = null
        binding.tvScannedName.text = "Barcode: $barcode"
        binding.tvScannedVariant.text = "Produk belum terdaftar di katalog"
        binding.tvScannedPrice.text = "Rp 0"
        binding.tvScannedStock.text = "Stok: 0"
        binding.tvScannedQtyBadge.visibility = View.GONE
        if (isCashierMode) {
            binding.btnAddScannedToCart.visibility = View.GONE
        } else {
            binding.btnAddScannedToCart.visibility = View.VISIBLE
        }
        binding.ivProductScanThumb.setImageResource(R.drawable.ic_nav_produk)
        binding.cardProductScanned.visibility = View.VISIBLE
        Toast.makeText(this, "Barcode $barcode belum terdaftar", Toast.LENGTH_SHORT).show()
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
