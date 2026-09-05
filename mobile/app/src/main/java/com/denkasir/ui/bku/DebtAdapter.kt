package com.denkasir.ui.bku

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.denkasir.R
import com.denkasir.data.local.entity.DebtEntity
import com.denkasir.databinding.ItemDebtBinding
import com.denkasir.utils.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebtAdapter(
    private val onPayClick: (DebtEntity) -> Unit,
    private val onRemindClick: (DebtEntity) -> Unit,
    private val onDeleteClick: (DebtEntity) -> Unit
) : RecyclerView.Adapter<DebtAdapter.DebtViewHolder>() {

    private val items = mutableListOf<DebtEntity>()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("in", "ID"))

    fun submitList(newItems: List<DebtEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DebtViewHolder {
        val binding = ItemDebtBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DebtViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DebtViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class DebtViewHolder(private val binding: ItemDebtBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(debt: DebtEntity) {
            val context = binding.root.context
            binding.tvCustomerOrSupplier.text = debt.customerName
            binding.tvDebtDate.text = "Tanggal: ${dateFormat.format(Date(debt.createdAt))}"

            val remaining = maxOf(0.0, debt.totalDebt - debt.paidAmount)
            binding.tvRemainingDebt.text = CurrencyUtils.formatRupiah(remaining)

            if (debt.status == "PAID" || remaining == 0.0) {
                binding.badgeDebtStatus.text = "LUNAS"
                binding.badgeDebtStatus.setTextColor(Color.parseColor("#059669"))
                binding.badgeDebtStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_cepat)
                binding.btnPayDebt.visibility = android.view.View.GONE
            } else if (debt.paidAmount > 0) {
                binding.badgeDebtStatus.text = "DICICIL"
                binding.badgeDebtStatus.setTextColor(Color.parseColor("#D97706"))
                binding.badgeDebtStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_curah)
                binding.btnPayDebt.visibility = android.view.View.VISIBLE
            } else {
                binding.badgeDebtStatus.text = "BELUM LUNAS"
                binding.badgeDebtStatus.setTextColor(Color.parseColor("#DC2626"))
                binding.badgeDebtStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_selisih)
                binding.btnPayDebt.visibility = android.view.View.VISIBLE
            }

            binding.btnPayDebt.setOnClickListener {
                onPayClick(debt)
            }

            binding.btnRemindWa.setOnClickListener {
                onRemindClick(debt)
            }

            binding.root.setOnLongClickListener {
                onDeleteClick(debt)
                true
            }
        }
    }
}
