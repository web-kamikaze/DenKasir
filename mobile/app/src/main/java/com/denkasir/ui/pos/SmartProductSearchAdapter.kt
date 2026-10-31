package com.denkasir.ui.pos

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import com.denkasir.data.local.entity.ProductEntity
import com.denkasir.utils.CurrencyUtils
import com.denkasir.utils.SmartSearchEngine

class SmartProductSearchAdapter(
    private val context: Context,
    private var allProducts: List<ProductEntity> = emptyList()
) : BaseAdapter(), Filterable {

    private var filteredProducts: List<ProductEntity> = emptyList()

    fun updateProducts(products: List<ProductEntity>) {
        this.allProducts = products
        this.filteredProducts = emptyList()
        notifyDataSetChanged()
    }

    override fun getCount(): Int = filteredProducts.size

    override fun getItem(position: Int): ProductEntity? = filteredProducts.getOrNull(position)

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(
            android.R.layout.simple_list_item_2,
            parent,
            false
        )
        val product = getItem(position) ?: return view

        val text1 = view.findViewById<TextView>(android.R.id.text1)
        val text2 = view.findViewById<TextView>(android.R.id.text2)

        text1?.setText(product.name)
        val stockStr = if (product.stock % 1.0 == 0.0) product.stock.toInt().toString() else product.stock.toString()
        val barcodeInfo = if (!product.barcode.isNullOrBlank()) " • ${product.barcode}" else ""
        text2?.setText("${product.category}$barcodeInfo • ${CurrencyUtils.formatRupiah(product.sellingPrice)} (Stok: $stockStr)")

        return view
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                val query = constraint?.toString()?.trim() ?: ""
                val filtered = if (query.isEmpty()) {
                    emptyList()
                } else {
                    SmartSearchEngine.filterProducts(query, allProducts, maxResults = 40)
                }
                results.values = filtered
                results.count = filtered.size
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredProducts = (results?.values as? List<ProductEntity>) ?: emptyList()
                if (results != null && results.count > 0) {
                    notifyDataSetChanged()
                } else {
                    notifyDataSetInvalidated()
                }
            }

            override fun convertResultToString(resultValue: Any?): CharSequence {
                return (resultValue as? ProductEntity)?.name ?: ""
            }
        }
    }
}