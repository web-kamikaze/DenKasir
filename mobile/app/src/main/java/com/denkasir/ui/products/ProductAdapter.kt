package com.denkasir.ui.products

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.denkasir.data.local.entity.ProductEntity
import com.denkasir.databinding.ItemProductManageBinding
import com.denkasir.utils.CurrencyUtils
import com.denkasir.utils.ImageUtils

class ProductAdapter(
    private val onItemClick: (ProductEntity) -> Unit,
    private val onDeleteClick: (ProductEntity) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    private val items = mutableListOf<ProductEntity>()

    fun submitList(newItems: List<ProductEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductManageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ProductViewHolder(private val binding: ItemProductManageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: ProductEntity) {
            binding.tvProductName.text = product.name
            binding.tvProductPrice.text = CurrencyUtils.formatRupiah(product.sellingPrice)
            
            val stockStr = if (product.stock % 1.0 == 0.0) product.stock.toInt().toString() else product.stock.toString()
            binding.tvProductStock.text = "•  Stok: $stockStr ${product.category}"

            binding.tvProductBarcode.text = if (!product.barcode.isNullOrEmpty()) {
                "Barcode: ${product.barcode}"
            } else {
                "Tipe: ${product.productType}"
            }

            ImageUtils.loadProductImage(binding.ivProductIcon, product.imagePath)

            binding.btnDeleteProduct.setOnClickListener {
                onDeleteClick(product)
            }

            binding.root.setOnClickListener {
                onItemClick(product)
            }
        }
    }
}

