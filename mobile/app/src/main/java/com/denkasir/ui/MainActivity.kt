package com.denkasir.ui

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.denkasir.R
import com.denkasir.data.repository.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.denkasir.databinding.ActivityMainBinding
import com.denkasir.ui.account.AccountFragment
import com.denkasir.ui.bku.ReportsFragment
import com.denkasir.ui.pos.PosFragment
import com.denkasir.ui.pos.TransactionsFragment
import com.denkasir.ui.products.ProductsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Permissions handled
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAndRequestStoragePermissions()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, PosFragment())
                .commit()
            highlightTab("KASIR")
        }

        setupBottomNav()

        // Sinkronisasi otomatis katalog produk digital (API st.riungan.my.id) di latar belakang
        lifecycleScope.launch(Dispatchers.IO) {
            SyncRepository(applicationContext).syncDigitalCatalogIfNeeded()
        }
    }

    private fun checkAndRequestStoragePermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            val readPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            val writePerm = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (readPerm != PackageManager.PERMISSION_GRANTED || writePerm != PackageManager.PERMISSION_GRANTED) {
                storagePermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }
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

    fun navigateToAccount() {
        loadFragment(AccountFragment())
        highlightTab("AKUN")
    }

    private fun highlightTab(tab: String) {
        val activeColor = ContextCompat.getColor(this, R.color.primary_purple)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_secondary)
        val isTablet = resources.configuration.smallestScreenWidthDp >= 600

        fun styleNavItem(
            container: android.view.View,
            icon: android.widget.ImageView,
            label: android.widget.TextView,
            isSelected: Boolean
        ) {
            icon.setColorFilter(if (isSelected) activeColor else inactiveColor)
            label.setTextColor(if (isSelected) activeColor else inactiveColor)
            label.setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

            if (isTablet) {
                if (isSelected) {
                    val pill = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = 14f * resources.displayMetrics.density
                        setColor(android.graphics.Color.parseColor("#EEF2FF"))
                    }
                    container.background = pill
                } else {
                    container.background = null
                }
            }
        }

        styleNavItem(binding.navKasir, binding.iconNavKasir, binding.textNavKasir, tab == "KASIR")
        styleNavItem(binding.navProduk, binding.iconNavProduk, binding.textNavProduk, tab == "PRODUK")
        styleNavItem(binding.navTransaksi, binding.iconNavTransaksi, binding.textNavTransaksi, tab == "TRANSAKSI")
        styleNavItem(binding.navLaporan, binding.iconNavLaporan, binding.textNavLaporan, tab == "LAPORAN")
        styleNavItem(binding.navAkun, binding.iconNavAkun, binding.textNavAkun, tab == "AKUN")
    }
}
