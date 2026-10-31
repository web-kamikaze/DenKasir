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

class PaymentSuccessActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentSuccessBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentSuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val receiptText = intent.getStringExtra("RECEIPT_TEXT") ?: "Struk Pembayaran DenKasir"

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

        binding.btnNewTransaction.setOnClickListener {
            finish()
        }
    }
}
