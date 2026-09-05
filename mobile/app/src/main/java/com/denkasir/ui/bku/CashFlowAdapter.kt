package com.denkasir.ui.bku

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.denkasir.R
import com.denkasir.data.local.entity.CashFlowEntity
import com.denkasir.databinding.ItemCashFlowBinding
import com.denkasir.utils.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CashFlowAdapter(
    private val onDeleteClick: (CashFlowEntity) -> Unit
) : RecyclerView.Adapter<CashFlowAdapter.CashFlowViewHolder>() {

    private val items = mutableListOf<CashFlowEntity>()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("in", "ID"))

    fun submitList(newItems: List<CashFlowEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CashFlowViewHolder {
        val binding = ItemCashFlowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CashFlowViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CashFlowViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CashFlowViewHolder(private val binding: ItemCashFlowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(flow: CashFlowEntity) {
            val context = binding.root.context
            binding.tvFlowCategory.text = flow.category
            binding.tvFlowDate.text = dateFormat.format(Date(flow.createdAt))
            binding.tvFlowDescription.text = flow.description

            if (flow.flowType == "IN") {
                binding.tvFlowBadge.text = "MASUK"
                binding.tvFlowBadge.setTextColor(Color.parseColor("#059669"))
                binding.tvFlowBadge.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_cepat)
                binding.tvFlowAmount.text = "+${CurrencyUtils.formatRupiah(flow.amount)}"
                binding.tvFlowAmount.setTextColor(Color.parseColor("#059669"))
            } else {
                binding.tvFlowBadge.text = "KELUAR"
                binding.tvFlowBadge.setTextColor(Color.parseColor("#DC2626"))
                binding.tvFlowBadge.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_selisih)
                binding.tvFlowAmount.text = "-${CurrencyUtils.formatRupiah(flow.amount)}"
                binding.tvFlowAmount.setTextColor(Color.parseColor("#DC2626"))
            }

            binding.root.setOnLongClickListener {
                onDeleteClick(flow)
                true
            }
        }
    }
}
