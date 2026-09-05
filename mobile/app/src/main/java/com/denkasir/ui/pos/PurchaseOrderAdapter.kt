package com.denkasir.ui.pos

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.denkasir.data.local.entity.PurchaseOrderEntity
import com.denkasir.databinding.ItemPurchaseOrderBinding
import com.denkasir.utils.CurrencyUtils

class PurchaseOrderAdapter(
    private val onItemClick: (PurchaseOrderEntity) -> Unit,
    private val onLongClick: (PurchaseOrderEntity) -> Unit
) : RecyclerView.Adapter<PurchaseOrderAdapter.PurchaseViewHolder>() {

    private val items = mutableListOf<PurchaseOrderEntity>()

    fun submitList(newItems: List<PurchaseOrderEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PurchaseViewHolder {
        val binding = ItemPurchaseOrderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PurchaseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PurchaseViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PurchaseViewHolder(private val binding: ItemPurchaseOrderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(po: PurchaseOrderEntity) {
            binding.tvSupplierName.text = po.supplierName
            binding.tvPurchaseDate.text = po.dateText
            binding.tvPurchaseItemCount.text = "${po.itemCount} item"
            binding.tvPurchaseTotalAmount.text = CurrencyUtils.formatRupiah(po.totalAmount)
            binding.tvPurchaseStatus.text = po.status

            when (po.status.lowercase()) {
                "selesai" -> {
                    binding.tvPurchaseStatus.setTextColor(Color.parseColor("#059669"))
                }
                "dipesan" -> {
                    binding.tvPurchaseStatus.setTextColor(Color.parseColor("#4338CA"))
                }
                else -> {
                    binding.tvPurchaseStatus.setTextColor(Color.parseColor("#64748B"))
                }
            }

            binding.root.setOnClickListener {
                onItemClick(po)
            }

            binding.root.setOnLongClickListener {
                onLongClick(po)
                true
            }
        }
    }
}
