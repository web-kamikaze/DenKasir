package com.denkasir.ui.pos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.denkasir.databinding.ActivityPaymentSuccessBinding
import com.denkasir.hardware.printer.BluetoothPrinterDriver
import kotlinx.coroutines.launch
import java.net.URLEncoder

class PaymentSuccessActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentSuccessBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentSuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val invoiceNo = intent.getStringExtra("INVOICE_NO") ?: "TRX-0000"
        val receiptText = intent.getStringExtra("RECEIPT_TEXT") ?: "Struk Pembayaran DenKasir"

        binding.tvReceiptPreview.text = receiptText

        binding.btnPrintReceipt.setOnClickListener {
            lifecycleScope.launch {
                // Mock printer test or send to default paired printer
                Toast.makeText(this@PaymentSuccessActivity, "Mengirim ke Printer Bluetooth Thermal...", Toast.LENGTH_SHORT).show()
                val success = BluetoothPrinterDriver.printReceipt("00:11:22:33:44:55", receiptText)
                if (!success) {
                    Toast.makeText(this@PaymentSuccessActivity, "Printer belum terhubung, beralih ke struk WhatsApp.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnShareWhatsApp.setOnClickListener {
            try {
                val encodedMessage = URLEncoder.encode(receiptText, "UTF-8")
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?text=$encodedMessage")
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
