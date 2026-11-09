package com.denkasir.ui.pos

import android.graphics.Color
import android.graphics.Paint
import android.widget.Toast
import com.denkasir.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.denkasir.databinding.ItemDigitalPackageCardBinding
import com.denkasir.utils.CurrencyUtils

class NominalGridAdapter(
    private val onNominalSelected: (NominalOption) -> Unit,
    private val onInfoClicked: ((NominalOption) -> Unit)? = null
) : RecyclerView.Adapter<NominalGridAdapter.NominalViewHolder>() {

    private val items = mutableListOf<NominalOption>()
    private var selectedIndex = 0

    fun submitList(newItems: List<NominalOption>, defaultSelectIndex: Int = 0) {
        items.clear()
        items.addAll(newItems)
        selectedIndex = if (defaultSelectIndex in items.indices) defaultSelectIndex else 0
        notifyDataSetChanged()
        if (items.isNotEmpty() && selectedIndex in items.indices) {
            onNominalSelected(items[selectedIndex])
        }
    }

    fun getSelectedItem(): NominalOption? {
        return if (selectedIndex in items.indices) items[selectedIndex] else items.firstOrNull()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NominalViewHolder {
        val binding = ItemDigitalPackageCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NominalViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NominalViewHolder, position: Int) {
        holder.bind(items[position], position == selectedIndex)
    }

    override fun getItemCount(): Int = items.size

    inner class NominalViewHolder(private val binding: ItemDigitalPackageCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(option: NominalOption, isSelected: Boolean) {
            // 1. Badge Top-Left
            if (option.badgeText.isNotBlank()) {
                binding.tvBadge.visibility = View.VISIBLE
                binding.tvBadge.text = option.badgeText
                if (!option.isAvailable) {
                    binding.tvBadge.setBackgroundResource(R.drawable.bg_badge_out_of_stock)
                } else {
                    binding.tvBadge.setBackgroundResource(R.drawable.bg_badge_muraaah)
                }
            } else {
                binding.tvBadge.visibility = View.GONE
            }

            // 2. Column 1 (Kuota / Pulsa / Saldo)
            binding.tvCol1Title.text = option.column1Title.ifEmpty { "Kuota" }
            binding.tvCol1Value.text = option.quotaLabel.ifEmpty { option.label }

            // 3. Column 2 (Masa berlaku)
            binding.tvCol2Title.text = option.column2Title.ifEmpty { "Masa berlaku" }
            binding.tvCol2Value.text = option.validityLabel.ifEmpty { "-" }

            // 4. Column 3 (Harga Coret & Jual)
            if (option.isCustom) {
                binding.tvOriginalPrice.visibility = View.GONE
                binding.tvSellingPrice.text = "Nominal Bebas"
                binding.tvSellingPrice.setTextColor(Color.parseColor("#3E24E2"))
            } else {
                if (option.originalPrice > option.sellingPrice && option.isAvailable) {
                    binding.tvOriginalPrice.visibility = View.VISIBLE
                    binding.tvOriginalPrice.text = CurrencyUtils.formatRupiah(option.originalPrice)
                    binding.tvOriginalPrice.paintFlags = binding.tvOriginalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    binding.tvOriginalPrice.visibility = View.GONE
                }
                if (!option.isAvailable) {
                    binding.tvSellingPrice.text = "Stok Kosong"
                    binding.tvSellingPrice.setTextColor(Color.parseColor("#DC2626"))
                } else {
                    binding.tvSellingPrice.text = CurrencyUtils.formatRupiah(option.sellingPrice)
                    binding.tvSellingPrice.setTextColor(Color.parseColor("#16A34A")) // green bold
                }
            }

            // 5. Bottom Bar
            binding.tvPackageName.text = option.label

            // 6. Selected vs Unselected Styling (TETAP PUTIH!)
            val density = binding.root.context.resources.displayMetrics.density
            if (isSelected) {
                binding.cardPackage.strokeColor = Color.parseColor("#3E24E2")
                binding.cardPackage.strokeWidth = (2.2f * density).toInt().coerceAtLeast(4)
                binding.cardPackage.setCardBackgroundColor(Color.parseColor("#F8F7FF"))
                binding.cardPackage.cardElevation = 4f * density
                binding.layoutBottomBar.setBackgroundColor(Color.parseColor("#EEF2FF"))
            } else {
                binding.cardPackage.strokeColor = Color.parseColor("#E2E8F0")
                binding.cardPackage.strokeWidth = (1f * density).toInt().coerceAtLeast(2)
                binding.cardPackage.setCardBackgroundColor(Color.WHITE)
                binding.cardPackage.cardElevation = 1.5f * density
                binding.layoutBottomBar.setBackgroundColor(Color.parseColor("#F8FAFC"))
            }

            // Dim card if not available
            binding.root.alpha = if (option.isAvailable) 1.0f else 0.55f

            // Card click listener
            binding.root.setOnClickListener {
                val currentPos = bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    val previousIndex = selectedIndex
                    selectedIndex = currentPos
                    if (previousIndex != selectedIndex) {
                        notifyItemChanged(previousIndex)
                        notifyItemChanged(selectedIndex)
                    }
                    onNominalSelected(option)
                    if (!option.isAvailable) {
                        Toast.makeText(
                            binding.root.context,
                            "⚠️ Stok paket ini sedang KOSONG / GANGGUAN dari distributor.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            // Info icon click listener
            binding.btnInfoIcon.setOnClickListener {
                onInfoClicked?.invoke(option)
            }
        }
    }
}
