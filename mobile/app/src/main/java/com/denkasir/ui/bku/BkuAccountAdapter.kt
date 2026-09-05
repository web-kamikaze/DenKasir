package com.denkasir.ui.bku

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.denkasir.R
import com.denkasir.data.local.entity.BkuAccountEntity
import com.denkasir.databinding.ItemBkuAccountBinding

class BkuAccountAdapter(
    private val onDeleteClick: (BkuAccountEntity) -> Unit
) : RecyclerView.Adapter<BkuAccountAdapter.AccountViewHolder>() {

    private val items = mutableListOf<BkuAccountEntity>()

    fun submitList(newItems: List<BkuAccountEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val binding = ItemBkuAccountBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AccountViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class AccountViewHolder(private val binding: ItemBkuAccountBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(account: BkuAccountEntity) {
            val context = binding.root.context
            binding.tvAccountName.text = account.accountName
            binding.tvAccountCode.text = account.accountCode ?: "COA"

            if (account.accountType == "IN") {
                binding.tvAccountTypeBadge.text = "PEMASUKAN (IN)"
                binding.tvAccountTypeBadge.setTextColor(Color.parseColor("#059669"))
                binding.tvAccountTypeBadge.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_cepat)
            } else {
                binding.tvAccountTypeBadge.text = "PENGELUARAN (OUT)"
                binding.tvAccountTypeBadge.setTextColor(Color.parseColor("#DC2626"))
                binding.tvAccountTypeBadge.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_selisih)
            }

            if (account.isDefault) {
                binding.btnDeleteAccount.visibility = View.GONE
            } else {
                binding.btnDeleteAccount.visibility = View.VISIBLE
                binding.btnDeleteAccount.setOnClickListener {
                    onDeleteClick(account)
                }
            }
        }
    }
}