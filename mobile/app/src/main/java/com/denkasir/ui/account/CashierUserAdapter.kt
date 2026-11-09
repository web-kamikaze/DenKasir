package com.denkasir.ui.account

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.denkasir.data.local.entity.UserEntity
import com.denkasir.databinding.ItemCashierUserBinding

class CashierUserAdapter(
    private val onCashierSelected: (UserEntity) -> Unit
) : RecyclerView.Adapter<CashierUserAdapter.CashierViewHolder>() {

    private val items = mutableListOf<UserEntity>()

    fun submitList(newItems: List<UserEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CashierViewHolder {
        val binding = ItemCashierUserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CashierViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CashierViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CashierViewHolder(private val binding: ItemCashierUserBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: UserEntity) {
            binding.tvCashierFullName.text = user.fullName
            binding.tvCashierUsername.text = "@${user.username}"
            binding.tvCashierRoleTag.text = user.role

            binding.btnDeleteCashier.setOnClickListener {
                onCashierSelected(user)
            }

            binding.root.setOnClickListener {
                onCashierSelected(user)
            }
        }
    }
}
