package com.denkasir.ui.stockopname

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.denkasir.R
import com.denkasir.databinding.ItemStockOpnameBinding
import com.denkasir.utils.ImageUtils

data class OpnameItem(
    val id: String,
    val name: String,
    val systemStock: Int,
    var physicalStock: Int,
    val imagePath: String? = null
) {
    val difference: Int get() = physicalStock - systemStock
    val isMatched: Boolean get() = difference == 0
}

class StockOpnameAdapter(
    private val onItemClick: (OpnameItem) -> Unit
) : RecyclerView.Adapter<StockOpnameAdapter.OpnameViewHolder>() {

    private val items = mutableListOf<OpnameItem>()

    fun submitList(newItems: List<OpnameItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OpnameViewHolder {
        val binding = ItemStockOpnameBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OpnameViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OpnameViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class OpnameViewHolder(private val binding: ItemStockOpnameBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: OpnameItem) {
            val context = binding.root.context
            binding.tvOpnameProductName.text = item.name
            binding.tvOpnameStockDetail.text = "Stok Sistem: ${item.systemStock}  •  Stok Fisik: ${item.physicalStock}"

            ImageUtils.loadProductImage(binding.ivProductThumb, item.imagePath, null, item.name)

            if (item.isMatched) {
                binding.badgeStatus.text = "✓ Cocok"
                binding.badgeStatus.setTextColor(Color.parseColor("#059669"))
                binding.badgeStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_cocok)
            } else {
                val diffStr = if (item.difference > 0) "+${item.difference}" else "${item.difference}"
                binding.badgeStatus.text = "⊖ Selisih $diffStr"
                binding.badgeStatus.setTextColor(Color.parseColor("#DC2626"))
                binding.badgeStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_selisih)
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
