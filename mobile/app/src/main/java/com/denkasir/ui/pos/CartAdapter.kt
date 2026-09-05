package com.denkasir.ui.pos

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.denkasir.databinding.ItemCartProductBinding
import com.denkasir.utils.CurrencyUtils
import com.denkasir.utils.ImageUtils

class CartAdapter(
    private val onQuantityChanged: (CartItem, Double) -> Unit,
    private val onItemRemoved: (CartItem) -> Unit
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    private val items = mutableListOf<CartItem>()

    fun submitList(newItems: List<CartItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = ItemCartProductBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CartViewHolder(private val binding: ItemCartProductBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(cartItem: CartItem) {
            val product = cartItem.product
            binding.tvProductName.text = if (cartItem.targetPhone != null) {
                "${product.name} (${cartItem.targetPhone})"
            } else {
                product.name
            }

            binding.tvProductUnitPrice.text = CurrencyUtils.formatRupiah(product.sellingPrice)
            
            // If whole number, format without decimal, otherwise with decimal
            val qtyText = if (cartItem.quantity % 1.0 == 0.0) {
                cartItem.quantity.toInt().toString()
            } else {
                String.format("%.1f", cartItem.quantity)
            }
            binding.tvQuantity.text = qtyText

            binding.tvSubtotalPrice.text = CurrencyUtils.formatNumberOnly(cartItem.subtotal)

            ImageUtils.loadProductImage(binding.ivProductImage, product.imagePath)

            binding.btnQtyMinus.setOnClickListener {
                if (cartItem.quantity > 1) {
                    onQuantityChanged(cartItem, cartItem.quantity - 1)
                } else {
                    onItemRemoved(cartItem)
                }
            }

            binding.btnQtyPlus.setOnClickListener {
                onQuantityChanged(cartItem, cartItem.quantity + 1)
            }

            binding.btnRemoveItem.setOnClickListener {
                onItemRemoved(cartItem)
            }
        }
    }
}

