package com.denkasir.ui.pos

import com.denkasir.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CartManager {

    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    @Synchronized
    fun addToCart(product: ProductEntity, qty: Double = 1.0, phone: String? = null) {
        val current = _cartItems.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.product.id == product.id && it.targetPhone == phone }
        if (existingIndex >= 0) {
            val item = current[existingIndex]
            current[existingIndex] = item.copy(quantity = item.quantity + qty)
        } else {
            current.add(CartItem(product = product, quantity = qty, targetPhone = phone))
        }
        _cartItems.value = current
    }

    @Synchronized
    fun updateQuantity(cartItem: CartItem, newQty: Double) {
        val current = _cartItems.value.toMutableList()
        val index = current.indexOfFirst { it.product.id == cartItem.product.id && it.targetPhone == cartItem.targetPhone }
        if (index >= 0) {
            if (newQty > 0) {
                current[index] = cartItem.copy(quantity = newQty)
            } else {
                current.removeAt(index)
            }
            _cartItems.value = current
        }
    }

    @Synchronized
    fun removeItem(cartItem: CartItem) {
        val current = _cartItems.value.toMutableList()
        current.removeAll { it.product.id == cartItem.product.id && it.targetPhone == cartItem.targetPhone }
        _cartItems.value = current
    }

    @Synchronized
    fun clearCart() {
        _cartItems.value = emptyList()
    }

    @Synchronized
    fun getTotalItemCount(): Double {
        return _cartItems.value.sumOf { it.quantity }
    }

    @Synchronized
    fun getTotalBayar(): Double {
        return _cartItems.value.sumOf { it.subtotal }
    }

    @Synchronized
    fun getProductQuantity(productId: String): Double {
        return _cartItems.value.filter { it.product.id == productId }.sumOf { it.quantity }
    }

    @Synchronized
    fun getCartItemsSnapshot(): List<CartItem> {
        return _cartItems.value.toList()
    }
}
