package com.denkasir.ui.stockopname

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.denkasir.R
import com.denkasir.databinding.ItemStockOpnameBinding
import com.denkasir.utils.CurrencyUtils
import com.denkasir.utils.ImageUtils

data class OpnameItem(
    val id: String,
    val name: String,
    val category: String = "Umum",
    val barcode: String? = null,
    val costPrice: Double = 0.0,
    val systemStock: Int,
    var physicalStock: Int? = null, // null = BELUM DIHITUNG (Uncounted)
    var reason: String? = null,
    val imagePath: String? = null
) {
    val isCounted: Boolean get() = physicalStock != null
    val safePhysicalStock: Int get() = physicalStock ?: systemStock
    val difference: Int get() = if (physicalStock != null) (physicalStock!! - systemStock) else 0
    val isMatched: Boolean get() = isCounted && difference == 0
    val hasDifference: Boolean get() = isCounted && difference != 0
    val lossValue: Double get() = if (difference < 0) (Math.abs(difference) * costPrice) else 0.0
}

class StockOpnameAdapter(
    var isOwner: Boolean = true,
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

            val barcodeText = if (!item.barcode.isNullOrBlank()) " • Barcode: ${item.barcode}" else ""
            binding.tvOpnameCategoryBarcode.text = "Kategori: ${item.category}$barcodeText"

            ImageUtils.loadProductImage(binding.ivProductThumb, item.imagePath, null, item.name)

            if (!item.isCounted) {
                // Item belum dihitung fisik
                binding.badgeStatus.text = "Belum Dihitung"
                binding.badgeStatus.setTextColor(Color.parseColor("#64748B"))
                binding.badgeStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_uncounted)

                if (isOwner) {
                    binding.tvOpnameStockDetail.text = "Stok Sistem: ${item.systemStock}  •  Fisik: Belum Dihitung"
                } else {
                    binding.tvOpnameStockDetail.text = "Belum Dihitung (Ketuk / Scan untuk hitung fisik)"
                }
            } else {
                // Item sudah dihitung
                if (!isOwner) {
                    // BLIND COUNT UNTUK KASIR: Kasir tidak melihat stok sistem atau selisih
                    binding.badgeStatus.text = "✓ Terhitung (${item.physicalStock})"
                    binding.badgeStatus.setTextColor(Color.parseColor("#0284C7"))
                    binding.badgeStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_sesi_aktif)
                    binding.tvOpnameStockDetail.text = "Fisik Riil: ${item.physicalStock} unit  •  [Blind Count Kasir]"
                } else {
                    // TAMPILAN TRANSPARAN UNTUK OWNER: Sistem, Fisik, Selisih, Nilai Kerugian
                    if (item.isMatched) {
                        binding.badgeStatus.text = "✓ Cocok"
                        binding.badgeStatus.setTextColor(Color.parseColor("#059669"))
                        binding.badgeStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_cocok)
                        binding.tvOpnameStockDetail.text = "Sistem: ${item.systemStock}  •  Fisik: ${item.physicalStock} (Cocok)"
                    } else {
                        val diffStr = if (item.difference > 0) "+${item.difference}" else "${item.difference}"
                        binding.badgeStatus.text = "⊖ Selisih $diffStr"
                        binding.badgeStatus.setTextColor(Color.parseColor("#DC2626"))
                        binding.badgeStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_selisih)

                        val lossStr = if (item.lossValue > 0) " (Loss: -${CurrencyUtils.formatRupiah(item.lossValue)})" else ""
                        val reasonText = if (!item.reason.isNullOrBlank()) " [${item.reason}]" else ""
                        binding.tvOpnameStockDetail.text = "Sistem: ${item.systemStock}  •  Fisik: ${item.physicalStock}  •  Selisih: $diffStr$lossStr$reasonText"
                    }
                }
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
