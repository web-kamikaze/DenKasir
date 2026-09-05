package com.denkasir.ui.pos

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.denkasir.R
import com.denkasir.databinding.ItemNominalButtonBinding

class NominalGridAdapter(
    private val onNominalSelected: (NominalOption) -> Unit
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
        val binding = ItemNominalButtonBinding.inflate(
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

    inner class NominalViewHolder(private val binding: ItemNominalButtonBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(option: NominalOption, isSelected: Boolean) {
            binding.tvNominalLabel.text = option.label

            if (isSelected) {
                binding.tvNominalLabel.setBackgroundResource(R.drawable.bg_nominal_card_selected)
                binding.tvNominalLabel.setTextColor(Color.WHITE)
                binding.tvNominalLabel.setTypeface(null, Typeface.BOLD)
            } else {
                binding.tvNominalLabel.setBackgroundResource(R.drawable.bg_nominal_card_unselected)
                binding.tvNominalLabel.setTextColor(Color.parseColor("#0F172A"))
                binding.tvNominalLabel.setTypeface(null, Typeface.NORMAL)
            }

            binding.tvNominalLabel.setOnClickListener {
                val currentPos = bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    val previousIndex = selectedIndex
                    selectedIndex = currentPos
                    if (previousIndex != selectedIndex) {
                        notifyItemChanged(previousIndex)
                        notifyItemChanged(selectedIndex)
                    }
                    onNominalSelected(option)
                }
            }
        }
    }
}