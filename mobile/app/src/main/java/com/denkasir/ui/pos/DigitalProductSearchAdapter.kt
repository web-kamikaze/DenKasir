package com.denkasir.ui.pos

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.denkasir.data.local.entity.ProductEntity
import com.denkasir.data.local.entity.isDigitalOutOfStock
import com.denkasir.databinding.ItemDigitalSearchResultBinding
import com.denkasir.utils.CurrencyUtils

class DigitalProductSearchAdapter(
    private val onProductClicked: (ProductEntity) -> Unit
) : RecyclerView.Adapter<DigitalProductSearchAdapter.ViewHolder>() {

    private val items = mutableListOf<ProductEntity>()

    fun submitList(newItems: List<ProductEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDigitalSearchResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemDigitalSearchResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: ProductEntity) {
            val isOutOfStock = product.isDigitalOutOfStock()
            binding.tvItemName.text = product.name
            if (isOutOfStock) {
                binding.tvItemCategoryBadge.text = "${product.category.uppercase()} • KOSONG"
                binding.tvItemCategoryBadge.setTextColor(Color.parseColor("#DC2626"))
                binding.root.alpha = 0.6f
                binding.tvItemSellingPrice.text = "Stok Kosong"
                binding.tvItemSellingPrice.setTextColor(Color.parseColor("#DC2626"))
            } else {
                binding.tvItemCategoryBadge.text = product.category.uppercase()
                binding.tvItemCategoryBadge.setTextColor(Color.parseColor("#3E24E2"))
                binding.root.alpha = 1.0f
                binding.tvItemSellingPrice.text = CurrencyUtils.formatRupiah(product.sellingPrice)
                binding.tvItemSellingPrice.setTextColor(Color.parseColor("#00A86B"))
            }
            binding.tvItemCode.text = "Kode: ${product.providerCode ?: product.barcode ?: "-"}"
            binding.tvItemCostPrice.text = "Modal: ${CurrencyUtils.formatRupiah(product.costPrice)}"

            binding.root.setOnClickListener {
                onProductClicked(product)
            }
        }
    }
}
