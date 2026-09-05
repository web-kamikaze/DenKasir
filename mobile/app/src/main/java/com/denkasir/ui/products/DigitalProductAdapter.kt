package com.denkasir.ui.products

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.denkasir.data.local.entity.ProductEntity
import com.denkasir.databinding.ItemDigitalProductBinding
import com.denkasir.utils.CurrencyUtils

class DigitalProductAdapter(
    private val onItemClick: (ProductEntity) -> Unit,
    private val onDeleteClick: (ProductEntity) -> Unit
) : RecyclerView.Adapter<DigitalProductAdapter.DigitalViewHolder>() {

    private val items = mutableListOf<ProductEntity>()

    fun submitList(newItems: List<ProductEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DigitalViewHolder {
        val binding = ItemDigitalProductBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DigitalViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DigitalViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class DigitalViewHolder(private val binding: ItemDigitalProductBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: ProductEntity) {
            binding.tvDigitalName.text = product.name
            binding.badgeCategory.text = product.category
            binding.tvDigitalSellingPrice.text = "Jual: ${CurrencyUtils.formatRupiah(product.sellingPrice)}"
            binding.tvDigitalCostPrice.text = "• Modal: ${CurrencyUtils.formatRupiah(product.costPrice)}"
            
            val profit = maxOf(0.0, product.sellingPrice - product.costPrice)
            binding.tvDigitalProfit.text = "Laba per Trx: +${CurrencyUtils.formatRupiah(profit)} (Potong Saldo)"

            binding.btnDeleteDigital.setOnClickListener {
                onDeleteClick(product)
            }

            binding.root.setOnClickListener {
                onItemClick(product)
            }
        }
    }
}
