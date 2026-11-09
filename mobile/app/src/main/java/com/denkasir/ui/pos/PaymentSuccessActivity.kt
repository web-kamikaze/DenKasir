package com.denkasir.ui.pos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.denkasir.databinding.ActivityPaymentSuccessBinding
import com.denkasir.hardware.printer.BluetoothPrinterDriver
import com.denkasir.hardware.printer.PrinterSettingActivity
import com.denkasir.utils.SoundFeedbackUtils
import kotlinx.coroutines.launch
import java.net.URLEncoder
import android.view.View
import com.denkasir.data.local.AppDatabase
import com.denkasir.utils.SmsGatewayHelper
import com.denkasir.utils.DigitalStatusDialogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PaymentSuccessActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentSuccessBinding
    private var receiptText: String = "Struk Pembayaran DenKasir"
    private var pendingSmsAction: (() -> Unit)? = null

    private val smsPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[android.Manifest.permission.SEND_SMS] ?: false
        if (smsGranted) {
            pendingSmsAction?.invoke()
        } else {
            Toast.makeText(this, "Izin SMS tidak diberikan. Membuka aplikasi SMS bawaan.", Toast.LENGTH_SHORT).show()
        }
        pendingSmsAction = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentSuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        receiptText = intent.getStringExtra("RECEIPT_TEXT") ?: "Struk Pembayaran DenKasir"

        binding.tvReceiptPreview.typeface = android.graphics.Typeface.MONOSPACE
        binding.tvReceiptPreview.text = receiptText

        // Pre-warm printer connection in background for instantaneous printing response
        lifecycleScope.launch {
            BluetoothPrinterDriver.warmupConnection(this@PaymentSuccessActivity)
        }

        binding.btnPrintReceipt.setOnClickListener {
            SoundFeedbackUtils.playBarcodeBeepAndVibrate(this@PaymentSuccessActivity)
            binding.btnPrintReceipt.isEnabled = false
            binding.btnPrintReceipt.text = "Sedang Mencetak..."
            
            lifecycleScope.launch {
                try {
                    Toast.makeText(this@PaymentSuccessActivity, "Mencetak ke printer...", Toast.LENGTH_SHORT).show()
                    val result = BluetoothPrinterDriver.printReceipt(this@PaymentSuccessActivity, receiptText)
                    when (result) {
                        is BluetoothPrinterDriver.PrintResult.Success -> {
                            Toast.makeText(this@PaymentSuccessActivity, "Struk berhasil dicetak!", Toast.LENGTH_SHORT).show()
                        }
                        is BluetoothPrinterDriver.PrintResult.Error -> {
                            AlertDialog.Builder(this@PaymentSuccessActivity)
                                .setTitle("Gagal Mencetak Struk")
                                .setMessage(result.message + "\n\nApakah Anda ingin membuka Pengaturan Printer Bluetooth?")
                                .setPositiveButton("Buka Pengaturan") { _, _ ->
                                    startActivity(Intent(this@PaymentSuccessActivity, PrinterSettingActivity::class.java))
                                }
                                .setNegativeButton("Tutup", null)
                                .show()
                        }
                    }
                } finally {
                    binding.btnPrintReceipt.isEnabled = true
                    binding.btnPrintReceipt.text = "Cetak Struk Bluetooth"
                }
            }
        }

        binding.btnShareWhatsApp.setOnClickListener {
            try {
                val encodedMessage = URLEncoder.encode(receiptText, "UTF-8")
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?text=" + encodedMessage)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Aplikasi WhatsApp tidak ditemukan", Toast.LENGTH_SHORT).show()
            }
        }

        // Dukungan Transaksi PPOB via Multi-Distributor SMS Center & PIN Routing
        val digitalCode = intent.getStringExtra("DIGITAL_PRODUCT_CODE")
        val rawPhone = intent.getStringExtra("DIGITAL_TARGET_PHONE")
        val digitalPhone = if (rawPhone != null && rawPhone.contains("(")) {
            rawPhone.substring(0, rawPhone.indexOf("(")).trim()
        } else {
            rawPhone?.trim()
        }
        val digitalCategory = intent.getStringExtra("DIGITAL_CATEGORY")
        val trxId = intent.getStringExtra("TRX_ID")

        if (!digitalCode.isNullOrBlank() && !digitalPhone.isNullOrBlank()) {
            binding.btnUpdateDigitalStatus.visibility = View.VISIBLE
            binding.btnUpdateDigitalStatus.setOnClickListener {
                lifecycleScope.launch {
                    val db = AppDatabase.getDatabase(this@PaymentSuccessActivity)
                    val trx = if (!trxId.isNullOrBlank()) {
                        withContext(Dispatchers.IO) {
                            db.transactionDao().getTransactionById(trxId)
                        }
                    } else null

                    if (trx != null) {
                        DigitalStatusDialogHelper.showUpdateStatusDialog(
                            context = this@PaymentSuccessActivity,
                            appDatabase = db,
                            transaction = trx,
                            scope = lifecycleScope,
                            onSuccess = { updatedTrx, newReceiptText ->
                                receiptText = newReceiptText
                                binding.tvReceiptPreview.text = newReceiptText
                            }
                        )
                    } else {
                        Toast.makeText(this@PaymentSuccessActivity, "Status transaksi dapat diperbarui melalui menu Transaksi Kasir.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        if (!digitalCode.isNullOrBlank() && !digitalPhone.isNullOrBlank()) {
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(this@PaymentSuccessActivity)
                val config = db.digitalConfigDao().getConfig()
                val rules = SmsGatewayHelper.parseDistributorRules(config?.distributorRulesJson)
                val centers = SmsGatewayHelper.parseSmsCenterList(config?.smsCenterNumbers)
                val defaultPin = config?.pinPassword?.trim().orEmpty()
                val defaultTemplate = config?.smsTemplate ?: SmsGatewayHelper.TEMPLATE_DOT
                val defaultDelimiter = config?.smsDelimiter ?: "."
                val trxMode = config?.trxMode ?: SmsGatewayHelper.MODE_API_H2H

                val effectiveApiUrl = if (!config?.apiUrl.isNullOrBlank()) config!!.apiUrl else "https://st.riungan.my.id/api/isipulsa/h2h-order"
                val effectiveApiKey = if (!config?.apiKey.isNullOrBlank()) config!!.apiKey else "DENKASIR-H2H-LIVE"

                // =========================================================================
                // 1. JALUR UTAMA: API H2H KE SERVER (st.riungan.my.id / SMS Center Terpusat)
                // =========================================================================
                if (trxMode == SmsGatewayHelper.MODE_API_H2H || trxMode == SmsGatewayHelper.MODE_AUTO_FALLBACK) {
                    withContext(Dispatchers.Main) {
                        binding.btnSendSmsCenter.visibility = View.VISIBLE
                        binding.btnSendSmsCenter.text = "? Memproses ke Server..."
                        binding.btnSendSmsCenter.isEnabled = false
                    }

                    try {
                        val api = com.denkasir.data.remote.api.ApiClient.getApi(com.denkasir.utils.SessionManager.getInstance(this@PaymentSuccessActivity).getServerUrl())
                        val payload = com.denkasir.data.remote.dto.H2hOrderRequestDto(
                            productCode = digitalCode,
                            targetNumber = digitalPhone,
                            clientTrxId = trxId,
                            pin = defaultPin,
                            apiKey = effectiveApiKey
                        )

                        val resp = api.postH2hOrder(
                            fullUrl = effectiveApiUrl,
                            apiKey = effectiveApiKey,
                            payload = payload
                        )

                        if (resp.isSuccessful && resp.body() != null && resp.body()!!.status == "SUCCESS") {
                            val h2hRes = resp.body()!!
                            val sn = h2hRes.snNumber ?: "SN${System.currentTimeMillis().toString().takeLast(8)}"

                            // Update item transaksi & cetak struk dengan SN / Token resmi
                            if (!trxId.isNullOrBlank()) {
                                db.transactionDao().updateItemDigitalResult(trxId, sn, "SUCCESS")
                            }

                            withContext(Dispatchers.Main) {
                                binding.btnSendSmsCenter.visibility = View.VISIBLE
                                binding.btnSendSmsCenter.isEnabled = true
                                binding.btnSendSmsCenter.text = "? Transaksi Berhasil (SN: $sn)"
                                binding.btnSendSmsCenter.setBackgroundColor(android.graphics.Color.parseColor("#10B981"))

                                val updatedReceipt = if (!receiptText.contains("SN/TOKEN:")) {
                                    "$receiptText\nSN/TOKEN: $sn\nStatus: SUKSES (Server)"
                                } else {
                                    receiptText
                                }
                                receiptText = updatedReceipt
                                binding.tvReceiptPreview.text = updatedReceipt
                                Toast.makeText(this@PaymentSuccessActivity, "Layanan digital berhasil diproses!", Toast.LENGTH_LONG).show()
                            }
                            return@launch
                        } else {
                            val errMsg = resp.body()?.message ?: "Gagal respon server HTTP ${resp.code()}"
                            if (trxMode == SmsGatewayHelper.MODE_API_H2H) {
                                withContext(Dispatchers.Main) {
                                    binding.btnSendSmsCenter.visibility = View.VISIBLE
                                    binding.btnSendSmsCenter.isEnabled = true
                                    binding.btnSendSmsCenter.text = "?? Server Gagal - Coba Lagi"
                                    binding.btnSendSmsCenter.setBackgroundColor(android.graphics.Color.parseColor("#DC2626"))
                                    binding.btnSendSmsCenter.setOnClickListener {
                                        Toast.makeText(this@PaymentSuccessActivity, errMsg, Toast.LENGTH_LONG).show()
                                    }
                                }
                                return@launch
                            }
                            // Jika MODE_AUTO_FALLBACK, otomatis lanjut ke pengiriman SMS seluler lokal di bawah
                        }
                    } catch (e: Exception) {
                        if (trxMode == SmsGatewayHelper.MODE_API_H2H) {
                            withContext(Dispatchers.Main) {
                                binding.btnSendSmsCenter.visibility = View.VISIBLE
                                binding.btnSendSmsCenter.isEnabled = true
                                binding.btnSendSmsCenter.text = "?? Koneksi Server Timeout"
                                binding.btnSendSmsCenter.setBackgroundColor(android.graphics.Color.parseColor("#DC2626"))
                            }
                            return@launch
                        }
                    }
                }

                // Logika Pintar: Cari distributor yang bertugas untuk kategori ini (Pulsa -> Dist 1 PIN 1111, Data -> Dist 2 PIN 2222, PLN -> Dist 3 PIN 3333)
                val matchedDistributor = SmsGatewayHelper.findMatchingDistributor(
                    rules = rules,
                    category = digitalCategory,
                    defaultName = "Server Distributor Utama",
                    defaultPin = defaultPin,
                    defaultCenter = if (centers.isNotEmpty()) centers.first() else "",
                    defaultTemplate = defaultTemplate,
                    defaultDelimiter = defaultDelimiter
                )

                val effectiveCenter = matchedDistributor.centerNumber.ifEmpty { if (centers.isNotEmpty()) centers.first() else "" }
                val effectivePin = matchedDistributor.pin.ifEmpty { defaultPin }

                if (effectiveCenter.isNotEmpty() && effectivePin.isNotEmpty()) {
                    val smsMessage = SmsGatewayHelper.formatSmsCommand(
                        productCode = digitalCode,
                        targetNumber = digitalPhone,
                        pin = effectivePin,
                        template = matchedDistributor.smsTemplate,
                        delimiter = matchedDistributor.smsDelimiter
                    )

                    val effectiveSimSlot = if (matchedDistributor.simSlot >= 0) matchedDistributor.simSlot else (config?.preferredSimSlot ?: -1)
                    val isAutoSend = config?.autoSendSms ?: true
                    val simLabel = when (effectiveSimSlot) {
                        0 -> " (SIM 1)"
                        1 -> " (SIM 2)"
                        else -> ""
                    }
                    val distName = if (matchedDistributor.id != "default") matchedDistributor.name else "Server Pulsa"

                    withContext(Dispatchers.Main) {
                        binding.btnSendSmsCenter.visibility = View.VISIBLE
                        if (isAutoSend) {
                            binding.btnSendSmsCenter.text = "?? Kirim SMS Otomatis ke $distName$simLabel"
                        } else {
                            binding.btnSendSmsCenter.text = "Kirim SMS ke $distName$simLabel"
                        }

                        fun executeSendSms(targetCenter: String) {
                            if (isAutoSend) {
                                val sendAction = {
                                    val result = SmsGatewayHelper.sendDirectSms(
                                        context = this@PaymentSuccessActivity,
                                        destinationAddress = targetCenter,
                                        messageBody = smsMessage,
                                        simSlotIndex = effectiveSimSlot
                                    )
                                    when (result) {
                                        is com.denkasir.utils.DirectSmsResult.Success -> {
                                            Toast.makeText(
                                                this@PaymentSuccessActivity,
                                                "Berhasil mengirim SMS ke $distName$simLabel!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        is com.denkasir.utils.DirectSmsResult.PermissionRequired -> {
                                            pendingSmsAction = { executeSendSms(targetCenter) }
                                            smsPermissionLauncher.launch(
                                                arrayOf(
                                                    android.Manifest.permission.SEND_SMS,
                                                    android.Manifest.permission.READ_PHONE_STATE
                                                )
                                            )
                                        }
                                        is com.denkasir.utils.DirectSmsResult.Error -> {
                                            Toast.makeText(this@PaymentSuccessActivity, "Gagal auto SMS: ${result.message}", Toast.LENGTH_SHORT).show()
                                            val smsIntent = SmsGatewayHelper.createSendSmsIntent(targetCenter, smsMessage)
                                            startActivity(smsIntent)
                                        }
                                    }
                                }

                                if (androidx.core.content.ContextCompat.checkSelfPermission(this@PaymentSuccessActivity, android.Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    sendAction()
                                } else {
                                    pendingSmsAction = sendAction
                                    smsPermissionLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.SEND_SMS,
                                            android.Manifest.permission.READ_PHONE_STATE
                                        )
                                    )
                                }
                            } else {
                                val smsIntent = SmsGatewayHelper.createSendSmsIntent(targetCenter, smsMessage)
                                startActivity(smsIntent)
                            }
                        }

                        binding.btnSendSmsCenter.setOnClickListener {
                            if (matchedDistributor.id != "default" || centers.size <= 1) {
                                executeSendSms(effectiveCenter)
                            } else {
                                val centerOptions = centers.map { "Center: $it" }.toTypedArray()
                                AlertDialog.Builder(this@PaymentSuccessActivity)
                                    .setTitle("Pilih SMS Center Distributor")
                                    .setItems(centerOptions) { _, which ->
                                        val chosenCenter = centers[which]
                                        executeSendSms(chosenCenter)
                                    }
                                    .setNegativeButton("Batal", null)
                                    .show()
                            }
                        }
                    }
                }
            }
        }

        binding.btnNewTransaction.setOnClickListener {
            finish()
        }
    }
}
