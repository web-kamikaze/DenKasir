package com.denkasir.ui.pos

import com.denkasir.data.local.entity.ProductEntity

data class CartItem(
    val product: ProductEntity,
    var quantity: Double = 1.0,
    var targetPhone: String? = null,
    var snNumber: String? = null
) {
    val subtotal: Double
        get() = product.sellingPrice * quantity
}
