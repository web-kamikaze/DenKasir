package com.denkasir.ui.pos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.denkasir.R
import com.denkasir.databinding.ItemDigitalCategoryGridBinding

class DigitalCategoryAdapter(
    private val onCategoryClicked: (DigitalCategoryItem) -> Unit
) : RecyclerView.Adapter<DigitalCategoryAdapter.CategoryViewHolder>() {

    private val items = mutableListOf<DigitalCategoryItem>()

    constructor(initialItems: List<DigitalCategoryItem>, onCategoryClicked: (DigitalCategoryItem) -> Unit) : this(onCategoryClicked) {
        items.addAll(initialItems)
    }

    fun submitList(newItems: List<DigitalCategoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemDigitalCategoryGridBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CategoryViewHolder(private val binding: ItemDigitalCategoryGridBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DigitalCategoryItem) {
            binding.tvCategoryTitle.text = item.title
            binding.ivCategoryIcon.setImageResource(item.iconRes)

            if (!item.badgeText.isNullOrEmpty()) {
                binding.tvCategoryBadge.visibility = View.VISIBLE
                binding.tvCategoryBadge.text = item.badgeText
                if (item.isPromoBadge) {
                    binding.tvCategoryBadge.setBackgroundResource(R.drawable.bg_badge_promo)
                } else {
                    binding.tvCategoryBadge.setBackgroundResource(R.drawable.bg_badge_baru)
                }
            } else {
                binding.tvCategoryBadge.visibility = View.INVISIBLE
            }

            binding.root.setOnClickListener {
                onCategoryClicked(item)
            }
        }
    }
}
