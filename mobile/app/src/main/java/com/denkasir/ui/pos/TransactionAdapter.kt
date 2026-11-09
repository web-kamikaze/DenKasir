package com.denkasir.ui.pos

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.denkasir.R
import com.denkasir.data.local.entity.TransactionEntity
import com.denkasir.databinding.ItemTransactionHistoryBinding
import com.denkasir.utils.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionAdapter(
    private val onItemClick: (TransactionEntity) -> Unit,
    private val onPrintClick: (TransactionEntity) -> Unit,
    private val onShareClick: (TransactionEntity) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    private val items = mutableListOf<TransactionEntity>()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("in", "ID"))

    fun submitList(newItems: List<TransactionEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class TransactionViewHolder(private val binding: ItemTransactionHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(trx: TransactionEntity) {
            val context = binding.root.context
            binding.tvTrxInvoice.text = trx.invoiceNumber
            binding.tvTrxDate.text = dateFormat.format(Date(trx.createdAt))
            val cashierDisplayName = if (!trx.cashierName.isNullOrBlank()) {
                trx.cashierName
            } else if (trx.userId.contains("owner", ignoreCase = true)) {
                "Pemilik Toko (Owner)"
            } else {
                "Kasir 1 (Staff)"
            }

            if (cashierDisplayName.contains("Owner", ignoreCase = true) || trx.userId.contains("owner", ignoreCase = true)) {
                binding.tvCashierBadge.text = "?? " + cashierDisplayName
                binding.tvCashierBadge.setTextColor(ContextCompat.getColor(context, R.color.primary_purple))
                binding.tvCashierBadge.setBackgroundResource(R.drawable.bg_badge_curah)
            } else {
                binding.tvCashierBadge.text = "?? " + cashierDisplayName
                binding.tvCashierBadge.setTextColor(android.graphics.Color.parseColor("#0284C7"))
                binding.tvCashierBadge.setBackgroundResource(R.drawable.bg_chip_selected)
            }

            binding.tvCustomerName.text = trx.customerName ?: "Pelanggan Warung"
            binding.tvTrxTotalAmount.text = CurrencyUtils.formatRupiah(trx.totalAmount)

            when (trx.status.uppercase()) {
                "PROSES", "PENDING" -> {
                    binding.badgeDigitalStatus.visibility = android.view.View.VISIBLE
                    binding.badgeDigitalStatus.text = "?? PROSES"
                    binding.badgeDigitalStatus.setTextColor(android.graphics.Color.parseColor("#B45309"))
                    binding.badgeDigitalStatus.setBackgroundResource(R.drawable.bg_badge_pending)
                }
                "SUKSES", "SUCCESS" -> {
                    binding.badgeDigitalStatus.visibility = android.view.View.VISIBLE
                    binding.badgeDigitalStatus.text = "?? SUKSES"
                    binding.badgeDigitalStatus.setTextColor(android.graphics.Color.parseColor("#059669"))
                    binding.badgeDigitalStatus.setBackgroundResource(R.drawable.bg_badge_cocok)
                }
                "GANGGUAN", "GAGAL" -> {
                    binding.badgeDigitalStatus.visibility = android.view.View.VISIBLE
                    binding.badgeDigitalStatus.text = "?? GANGGUAN"
                    binding.badgeDigitalStatus.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                    binding.badgeDigitalStatus.setBackgroundResource(R.drawable.bg_badge_selisih)
                }
                "REFUNDED", "REFUND" -> {
                    binding.badgeDigitalStatus.visibility = android.view.View.VISIBLE
                    binding.badgeDigitalStatus.text = "?? REFUND"
                    binding.badgeDigitalStatus.setTextColor(android.graphics.Color.parseColor("#64748B"))
                    binding.badgeDigitalStatus.setBackgroundResource(R.drawable.bg_badge_uncounted)
                }
                else -> {
                    binding.badgeDigitalStatus.visibility = android.view.View.GONE
                }
            }

            binding.badgePaymentMethod.text = trx.paymentMethod
            when (trx.paymentMethod.uppercase()) {
                "CASH", "TUNAI" -> {
                    binding.badgePaymentMethod.setTextColor(ContextCompat.getColor(context, R.color.primary_purple))
                    binding.badgePaymentMethod.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_curah)
                }
                "QRIS" -> {
                    binding.badgePaymentMethod.setTextColor(ContextCompat.getColor(context, R.color.green_total))
                    binding.badgePaymentMethod.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_cepat)
                }
                "TRANSFER" -> {
                    binding.badgePaymentMethod.setTextColor(android.graphics.Color.parseColor("#0284C7"))
                    binding.badgePaymentMethod.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_sesi_aktif)
                }
                "KASBON" -> {
                    binding.badgePaymentMethod.setTextColor(ContextCompat.getColor(context, R.color.red_cancel))
                    binding.badgePaymentMethod.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_selisih)
                }
            }

            binding.btnPrintTrx.setOnClickListener {
                onPrintClick(trx)
            }

            binding.btnShareWaTrx.setOnClickListener {
                onShareClick(trx)
            }

            binding.root.setOnClickListener {
                onItemClick(trx)
            }
        }
    }
}
