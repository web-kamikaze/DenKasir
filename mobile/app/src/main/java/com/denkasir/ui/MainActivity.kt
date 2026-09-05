package com.denkasir.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.denkasir.R
import com.denkasir.databinding.ActivityMainBinding
import com.denkasir.ui.account.AccountFragment
import com.denkasir.ui.bku.ReportsFragment
import com.denkasir.ui.pos.PosFragment
import com.denkasir.ui.pos.TransactionsFragment
import com.denkasir.ui.products.ProductsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, PosFragment())
                .commit()
            highlightTab("KASIR")
        }

        setupBottomNav()
    }

    private fun setupBottomNav() {
        binding.navKasir.setOnClickListener {
            loadFragment(PosFragment())
            highlightTab("KASIR")
        }
        binding.navProduk.setOnClickListener {
            loadFragment(ProductsFragment())
            highlightTab("PRODUK")
        }
        binding.navTransaksi.setOnClickListener {
            loadFragment(TransactionsFragment())
            highlightTab("TRANSAKSI")
        }
        binding.navLaporan.setOnClickListener {
            loadFragment(ReportsFragment())
            highlightTab("LAPORAN")
        }
        binding.navAkun.setOnClickListener {
            loadFragment(AccountFragment())
            highlightTab("AKUN")
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commitAllowingStateLoss()
    }

    private fun highlightTab(tab: String) {
        val activeColor = ContextCompat.getColor(this, R.color.primary_purple)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_secondary)

        binding.iconNavKasir.setColorFilter(if (tab == "KASIR") activeColor else inactiveColor)
        binding.textNavKasir.setTextColor(if (tab == "KASIR") activeColor else inactiveColor)

        binding.iconNavProduk.setColorFilter(if (tab == "PRODUK") activeColor else inactiveColor)
        binding.textNavProduk.setTextColor(if (tab == "PRODUK") activeColor else inactiveColor)

        binding.iconNavTransaksi.setColorFilter(if (tab == "TRANSAKSI") activeColor else inactiveColor)
        binding.textNavTransaksi.setTextColor(if (tab == "TRANSAKSI") activeColor else inactiveColor)

        binding.iconNavLaporan.setColorFilter(if (tab == "LAPORAN") activeColor else inactiveColor)
        binding.textNavLaporan.setTextColor(if (tab == "LAPORAN") activeColor else inactiveColor)

        binding.iconNavAkun.setColorFilter(if (tab == "AKUN") activeColor else inactiveColor)
        binding.textNavAkun.setTextColor(if (tab == "AKUN") activeColor else inactiveColor)
    }
}
