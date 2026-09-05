package com.denkasir.ui.pos

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.denkasir.data.local.entity.ProductEntity
import com.denkasir.databinding.ItemDigitalDenomCardBinding
import com.denkasir.utils.CurrencyUtils

class DenominationAdapter(
    private val onItemSelected: (ProductEntity) -> Unit
) : RecyclerView.Adapter<DenominationAdapter.DenomViewHolder>() {

    private val items = mutableListOf<ProductEntity>()
    private var selectedIndex = 0

    fun submitList(newItems: List<ProductEntity>) {
        items.clear()
        items.addAll(newItems)
        selectedIndex = 0
        notifyDataSetChanged()
        if (items.isNotEmpty()) {
            onItemSelected(items[0])
        }
    }

    fun getSelectedItem(): ProductEntity? {
        return if (selectedIndex in items.indices) items[selectedIndex] else items.firstOrNull()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DenomViewHolder {
        val binding = ItemDigitalDenomCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DenomViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DenomViewHolder, position: Int) {
        holder.bind(items[position], position == selectedIndex)
    }

    override fun getItemCount(): Int = items.size

    inner class DenomViewHolder(private val binding: ItemDigitalDenomCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: ProductEntity, isSelected: Boolean) {
            binding.tvDenomTitle.text = product.name
            binding.tvDenomSubtitle.text = "Modal: ${CurrencyUtils.formatRupiah(product.costPrice)} • Potong Saldo"
            binding.tvDenomPrice.text = CurrencyUtils.formatRupiah(product.sellingPrice)

            if (isSelected) {
                binding.cardDenom.setCardBackgroundColor(Color.parseColor("#EEF2FF"))
                binding.tvDenomTitle.setTextColor(Color.parseColor("#3E24E2"))
            } else {
                binding.cardDenom.setCardBackgroundColor(Color.WHITE)
                binding.tvDenomTitle.setTextColor(Color.parseColor("#0F172A"))
            }

            binding.root.setOnClickListener {
                val previousIndex = selectedIndex
                selectedIndex = adapterPosition
                notifyItemChanged(previousIndex)
                notifyItemChanged(selectedIndex)
                onItemSelected(product)
            }
        }
    }
}
