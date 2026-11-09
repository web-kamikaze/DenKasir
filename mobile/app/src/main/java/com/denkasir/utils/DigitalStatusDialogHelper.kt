package com.denkasir.utils

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.denkasir.R
import com.denkasir.data.local.AppDatabase
import com.denkasir.data.local.entity.CashFlowEntity
import com.denkasir.data.local.entity.TransactionEntity
import com.denkasir.data.local.entity.TransactionItemEntity
import com.denkasir.databinding.DialogUpdateDigitalStatusBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object DigitalStatusDialogHelper {

    fun showUpdateStatusDialog(
        context: Context,
        appDatabase: AppDatabase,
        transaction: TransactionEntity,
        scope: CoroutineScope,
        onSuccess: ((TransactionEntity, String) -> Unit)? = null
    ) {
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                appDatabase.transactionDao().getTransactionItems(transaction.id)
            }

            val digitalItem = items.find {
                !it.targetPhoneNumber.isNullOrBlank() || it.snNumber != null ||
                it.digitalStatus in listOf("PROSES", "PENDING", "GANGGUAN", "FAILED", "SUCCESS", "SUKSES") &&
                transaction.status != "COMPLETED"
            } ?: items.firstOrNull { !it.targetPhoneNumber.isNullOrBlank() }

            if (digitalItem == null) {
                Toast.makeText(context, "Transaksi ini tidak memiliki produk digital / PPOB.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val binding = DialogUpdateDigitalStatusBinding.inflate(LayoutInflater.from(context))
            val dialog = AlertDialog.Builder(context)
                .setView(binding.root)
                .setCancelable(true)
                .create()

            binding.tvDialogInvoiceNo.text = "No. Nota: ${transaction.invoiceNumber}"
            binding.tvDialogProductName.text = digitalItem.productName
            binding.tvDialogTargetNumber.text = "Tujuan: ${digitalItem.targetPhoneNumber ?: "-"}"

            // Status Saat Ini
            when (transaction.status.uppercase()) {
                "PROSES", "PENDING" -> {
                    binding.tvDialogCurrentStatus.text = "?? PROSES"
                    binding.tvDialogCurrentStatus.setTextColor(android.graphics.Color.parseColor("#B45309"))
                    binding.tvDialogCurrentStatus.setBackgroundResource(R.drawable.bg_badge_pending)
                }
                "SUKSES", "SUCCESS" -> {
                    binding.tvDialogCurrentStatus.text = "?? SUKSES"
                    binding.tvDialogCurrentStatus.setTextColor(android.graphics.Color.parseColor("#059669"))
                    binding.tvDialogCurrentStatus.setBackgroundResource(R.drawable.bg_badge_cocok)
                }
                "GANGGUAN", "GAGAL" -> {
                    binding.tvDialogCurrentStatus.text = "?? GANGGUAN"
                    binding.tvDialogCurrentStatus.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                    binding.tvDialogCurrentStatus.setBackgroundResource(R.drawable.bg_badge_selisih)
                }
                else -> {
                    binding.tvDialogCurrentStatus.text = transaction.status
                }
            }

            if (!digitalItem.snNumber.isNullOrBlank() && !digitalItem.snNumber.startsWith("GANGGUAN")) {
                binding.etSnInput.setText(digitalItem.snNumber)
            }

            // Checklist Refund Kas toko
            binding.cbRefundKas.text = "Kembalikan Uang Kas (Refund ${CurrencyUtils.formatRupiah(transaction.totalAmount)} ke Pelanggan)"
            val refundDepositAmount = digitalItem.costPrice * digitalItem.quantity
            binding.cbRefundDeposit.text = "Kembalikan Saldo Deposit Server Pulsa (${CurrencyUtils.formatRupiah(refundDepositAmount)})"

            // Toggle Tampilan Antara Sukses vs Gangguan
            binding.rgStatusSelection.setOnCheckedChangeListener { _, checkedId ->
                if (checkedId == R.id.rbStatusSukses) {
                    binding.layoutSuksesContainer.visibility = View.VISIBLE
                    binding.layoutGangguanContainer.visibility = View.GONE
                } else {
                    binding.layoutSuksesContainer.visibility = View.GONE
                    binding.layoutGangguanContainer.visibility = View.VISIBLE
                }
            }

            // Tempel dari Clipboard SMS
            binding.btnPasteSms.setOnClickListener {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    if (clipboard.hasPrimaryClip() && clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true) {
                        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            // Cerdas: Jika paste berupa balasan SMS panjang, cari kata kunci 'SN:' atau ambil langsung
                            val extractedSn = extractSnFromSms(text)
                            binding.etSnInput.setText(extractedSn)
                            Toast.makeText(context, "SN berhasil ditempel!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Papan klip (clipboard) kosong.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Gagal membaca clipboard: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            binding.btnCancelUpdate.setOnClickListener {
                dialog.dismiss()
            }

            binding.btnSaveStatus.setOnClickListener {
                val isSukses = binding.rbStatusSukses.isChecked

                if (isSukses) {
                    val sn = binding.etSnInput.text.toString().trim()
                    if (sn.isEmpty()) {
                        binding.tilSnInput.error = "Nomor SN / Token wajib diisi!"
                        return@setOnClickListener
                    }
                    binding.tilSnInput.error = null

                    scope.launch {
                        withContext(Dispatchers.IO) {
                            appDatabase.transactionDao().updateDigitalItemStatus(
                                itemId = digitalItem.id,
                                status = "SUCCESS",
                                sn = sn
                            )
                            appDatabase.transactionDao().updateTransactionStatus(
                                trxId = transaction.id,
                                status = "SUKSES"
                            )
                        }

                        val updatedTrx = withContext(Dispatchers.IO) {
                            appDatabase.transactionDao().getTransactionById(transaction.id)
                        } ?: transaction.copy(status = "SUKSES")

                        val updatedItems = withContext(Dispatchers.IO) {
                            appDatabase.transactionDao().getTransactionItems(transaction.id)
                        }

                        val receiptText = ReceiptFormatterUtils.formatTransactionReceipt(
                            context = context,
                            transaction = updatedTrx,
                            items = updatedItems
                        )

                        dialog.dismiss()
                        Toast.makeText(context, "Status PPOB diperbarui: SUKSES (SN: $sn)", Toast.LENGTH_LONG).show()
                        onSuccess?.invoke(updatedTrx, receiptText)
                    }
                } else {
                    // Kasus GANGGUAN / GAGAL
                    val reason = when (binding.rgAlasanGangguan.checkedRadioButtonId) {
                        R.id.rbNomorSalah -> "Nomor Tujuan Salah / Hangus"
                        R.id.rbStokHabis -> "Stok / Nominal Kosong"
                        else -> "Server Distributor Gangguan / Cut Off"
                    }

                    val shouldRefundKas = binding.cbRefundKas.isChecked
                    val shouldRefundDeposit = binding.cbRefundDeposit.isChecked

                    scope.launch {
                        withContext(Dispatchers.IO) {
                            // 1. Update status transaksi & item
                            appDatabase.transactionDao().updateDigitalItemStatus(
                                itemId = digitalItem.id,
                                status = "GANGGUAN",
                                sn = "GANGGUAN: $reason"
                            )
                            appDatabase.transactionDao().updateTransactionStatus(
                                trxId = transaction.id,
                                status = "GANGGUAN"
                            )

                            // 2. Refund Kas Toko (Jurnal BKU Kas Keluar)
                            if (shouldRefundKas) {
                                appDatabase.cashFlowDao().insertCashFlow(
                                    CashFlowEntity(
                                        flowType = "OUT",
                                        category = "REFUND",
                                        amount = transaction.totalAmount,
                                        description = "Refund Pulsa #${transaction.invoiceNumber} ($reason)",
                                        referenceId = transaction.id
                                    )
                                )
                            }

                            // 3. Kembalikan Saldo Deposit Server Pulsa
                            if (shouldRefundDeposit) {
                                appDatabase.digitalConfigDao().topUpBalance(refundDepositAmount)
                            }
                        }

                        val updatedTrx = withContext(Dispatchers.IO) {
                            appDatabase.transactionDao().getTransactionById(transaction.id)
                        } ?: transaction.copy(status = "GANGGUAN")

                        val updatedItems = withContext(Dispatchers.IO) {
                            appDatabase.transactionDao().getTransactionItems(transaction.id)
                        }

                        val receiptText = ReceiptFormatterUtils.formatTransactionReceipt(
                            context = context,
                            transaction = updatedTrx,
                            items = updatedItems
                        )

                        dialog.dismiss()
                        Toast.makeText(context, "Transaksi ditandai GANGGUAN. Kas Toko & Saldo Deposit telah disesuaikan.", Toast.LENGTH_LONG).show()
                        onSuccess?.invoke(updatedTrx, receiptText)
                    }
                }
            }

            dialog.show()
        }
    }

    /**
     * Mengekstrak kode SN / Token PLN dari teks SMS balasan distributor secara cerdas.
     * Contoh SMS balasan:
     * - "Trx S10 ke 081234567890 SUKSES. SN: 03120192837482. Sisa Saldo: Rp 1.500.000" -> 03120192837482
     * - "PLN20 ke 14123456789 SUKSES. SN/TOKEN: 1234-5678-9012-3456-7890/PLN/20.0" -> 1234-5678-9012-3456-7890
     */
    fun extractSnFromSms(smsText: String): String {
        val trimmed = smsText.trim()
        val snRegex = Regex("""(?:SN|TOKEN|SN\/TOKEN)\s*[:=]\s*([A-Za-z0-9\-\/]+)""", RegexOption.IGNORE_CASE)
        val match = snRegex.find(trimmed)
        if (match != null && match.groupValues.size > 1) {
            val candidate = match.groupValues[1].trim()
            // Jika token PLN berbentuk 1234-5678-9012-3456-7890/PLN/..., ambil nomor 20-digit tokennya
            val tokenPlnRegex = Regex("""(\d{4}-\d{4}-\d{4}-\d{4}-\d{4})""")
            val plnMatch = tokenPlnRegex.find(candidate)
            if (plnMatch != null) {
                return plnMatch.value
            }
            return candidate.split("/").first().trim()
        }

        // Cari pola 20-digit token PLN langsung (1234-5678-9012-3456-7890)
        val directPln = Regex("""\b(\d{4}-\d{4}-\d{4}-\d{4}-\d{4})\b""").find(trimmed)
        if (directPln != null) {
            return directPln.value
        }

        // Jika teks pendek (< 40 karakter), gunakan langsung sebagai nomor SN
        return if (trimmed.length <= 40 && !trimmed.contains("\n")) trimmed else trimmed.take(30)
    }
}
