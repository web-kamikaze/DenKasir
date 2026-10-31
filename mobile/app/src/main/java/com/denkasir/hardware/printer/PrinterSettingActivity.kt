package com.denkasir.hardware.printer

import android.Manifest
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.denkasir.R
import com.denkasir.databinding.ActivityPrinterSettingBinding
import com.denkasir.databinding.ItemBluetoothDeviceBinding
import com.denkasir.utils.ReceiptFormatterUtils
import com.denkasir.utils.SessionManager
import kotlinx.coroutines.launch

class PrinterSettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrinterSettingBinding
    private lateinit var deviceAdapter: BluetoothDeviceAdapter
    private lateinit var session: SessionManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            loadPairedDevices()
        } else {
            Toast.makeText(this, "Izin Bluetooth diperlukan untuk mencari dan menghubungkan printer", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrinterSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager.getInstance(this)

        setupRecyclerView()
        setupListeners()
        loadSavedReceiptSettings()
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateActivePrinterUI()
        loadPairedDevices()
    }

    private fun setupRecyclerView() {
        deviceAdapter = BluetoothDeviceAdapter { selectedDevice ->
            val paperWidth = if (binding.rbPaper80.isChecked) 80 else 58
            BluetoothPrinterDriver.savePrinter(
                this,
                selectedDevice.address,
                selectedDevice.name,
                paperWidth
            )
            Toast.makeText(this, "Printer aktif: " + selectedDevice.name, Toast.LENGTH_SHORT).show()
            updateActivePrinterUI()
            loadPairedDevices()
        }
        binding.rvPairedDevices.apply {
            layoutManager = LinearLayoutManager(this@PrinterSettingActivity)
            adapter = deviceAdapter
        }
    }

    private fun loadSavedReceiptSettings() {
        val paperWidth = session.getReceiptPaperWidth()
        if (paperWidth >= 80) {
            binding.rbPaper80.isChecked = true
        } else {
            binding.rbPaper58.isChecked = true
        }

        val layoutStyle = session.getReceiptLayoutStyle()
        if (layoutStyle == SessionManager.LAYOUT_3_COLUMN) {
            binding.rbLayout3Col.isChecked = true
        } else {
            binding.rbLayout2Col.isChecked = true
        }

        when (session.getReceiptFeedLines()) {
            2 -> binding.rbFeed2.isChecked = true
            4 -> binding.rbFeed4.isChecked = true
            5 -> binding.rbFeed5.isChecked = true
            else -> binding.rbFeed3.isChecked = true
        }

        binding.switchAutoCut.isChecked = session.isReceiptAutoCut()
        binding.switchShowQr.isChecked = session.isReceiptShowQr()
        binding.etHeaderSlogan.setText(session.getReceiptHeaderSlogan())
        binding.etFooterNote.setText(session.getReceiptFooterNote())
    }

    private fun saveCurrentReceiptSettings(showToast: Boolean = true) {
        val paperWidth = if (binding.rbPaper80.isChecked) 80 else 58
        val layoutStyle = if (binding.rbLayout3Col.isChecked) SessionManager.LAYOUT_3_COLUMN else SessionManager.LAYOUT_2_COLUMN
        val feedLines = when {
            binding.rbFeed2.isChecked -> 2
            binding.rbFeed4.isChecked -> 4
            binding.rbFeed5.isChecked -> 5
            else -> 3
        }
        val autoCut = binding.switchAutoCut.isChecked
        val showQr = binding.switchShowQr.isChecked
        val headerSlogan = binding.etHeaderSlogan.text?.toString()?.trim() ?: ""
        val footerNote = binding.etFooterNote.text?.toString()?.trim() ?: ""

        session.saveReceiptSettings(
            paperWidth = paperWidth,
            feedLines = feedLines,
            autoCut = autoCut,
            showQr = showQr,
            layoutStyle = layoutStyle,
            headerSlogan = headerSlogan,
            footerNote = footerNote
        )

        val currentMac = BluetoothPrinterDriver.getSavedPrinterMac(this)
        val currentName = BluetoothPrinterDriver.getSavedPrinterName(this)
        if (currentMac != null) {
            BluetoothPrinterDriver.savePrinter(this, currentMac, currentName, paperWidth)
        }

        if (showToast) {
            Toast.makeText(this, "Pengaturan format struk berhasil disimpan!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnRefresh.setOnClickListener {
            checkAndRequestPermissions()
        }

        binding.btnOpenBluetoothSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal membuka Pengaturan Bluetooth", Toast.LENGTH_SHORT).show()
            }
        }

        binding.rgPaperWidth.setOnCheckedChangeListener { _, _ ->
            saveCurrentReceiptSettings(showToast = false)
        }

        binding.btnSaveLayoutSettings.setOnClickListener {
            saveCurrentReceiptSettings(showToast = true)
        }

        binding.btnPreviewReceipt.setOnClickListener {
            saveCurrentReceiptSettings(showToast = false)
            showReceiptPreviewDialog()
        }

        binding.btnDisconnect.setOnClickListener {
            BluetoothPrinterDriver.clearSavedPrinter(this)
            Toast.makeText(this, "Printer kasir telah diputuskan", Toast.LENGTH_SHORT).show()
            updateActivePrinterUI()
            loadPairedDevices()
        }

        binding.btnTestPrint.setOnClickListener {
            saveCurrentReceiptSettings(showToast = false)
            runTestPrint()
        }
    }

    private fun showReceiptPreviewDialog() {
        val paperWidth = session.getReceiptPaperWidth()
        val previewText = ReceiptFormatterUtils.formatTestReceipt(this, paperWidth)

        val textView = TextView(this).apply {
            text = previewText
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(32, 24, 32, 24)
            setTextColor(ContextCompat.getColor(this@PrinterSettingActivity, R.color.text_primary))
        }

        val scrollView = ScrollView(this).apply {
            addView(textView)
        }

        AlertDialog.Builder(this)
            .setTitle("Pratinjau Struk (" + paperWidth + " mm)")
            .setView(scrollView)
            .setPositiveButton("Tutup", null)
            .setNeutralButton("Uji Cetak") { _, _ ->
                runTestPrint()
            }
            .show()
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            if (!BluetoothPrinterDriver.hasBluetoothPermission(this)) {
                permissionLauncher.launch(permissions)
                return
            }
        }
        loadPairedDevices()
    }

    private fun updateActivePrinterUI() {
        val savedMac = BluetoothPrinterDriver.getSavedPrinterMac(this)
        val savedName = BluetoothPrinterDriver.getSavedPrinterName(this)

        if (savedMac != null) {
            binding.tvActivePrinterName.text = savedName ?: "Printer Thermal Bluetooth"
            binding.tvActivePrinterMac.text = "MAC: " + savedMac
            binding.tvActiveStatusBadge.text = "TERHUBUNG"
            binding.tvActiveStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.green_total))
            binding.tvActiveStatusBadge.setBackgroundResource(R.drawable.bg_badge_cocok)
            binding.btnTestPrint.isEnabled = true
            binding.btnDisconnect.visibility = View.VISIBLE
        } else {
            binding.tvActivePrinterName.text = "Belum Ada Printer Terpilih"
            binding.tvActivePrinterMac.text = "Pilih salah satu printer terpasang di bawah ini"
            binding.tvActiveStatusBadge.text = "BELUM DIATUR"
            binding.tvActiveStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.tvActiveStatusBadge.setBackgroundResource(R.drawable.bg_chip_unselected)
            binding.btnTestPrint.isEnabled = false
            binding.btnDisconnect.visibility = View.GONE
        }
    }

    private fun loadPairedDevices() {
        if (!BluetoothPrinterDriver.isBluetoothEnabled(this)) {
            binding.layoutEmptyDevices.visibility = View.VISIBLE
            binding.rvPairedDevices.visibility = View.GONE
            Toast.makeText(this, "Bluetooth HP belum aktif. Nyalakan Bluetooth untuk melihat printer.", Toast.LENGTH_SHORT).show()
            return
        }

        val devices = BluetoothPrinterDriver.getPairedDevices(this)
        if (devices.isEmpty()) {
            binding.layoutEmptyDevices.visibility = View.VISIBLE
            binding.rvPairedDevices.visibility = View.GONE
        } else {
            binding.layoutEmptyDevices.visibility = View.GONE
            binding.rvPairedDevices.visibility = View.VISIBLE
            deviceAdapter.submitList(devices)
        }
    }

    private fun runTestPrint() {
        binding.btnTestPrint.isEnabled = false
        binding.btnTestPrint.text = "Sedang Mencetak..."
        Toast.makeText(this, "Mengirim format uji coba ESC/POS ke printer...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val result = BluetoothPrinterDriver.printTestReceipt(this@PrinterSettingActivity)
                when (result) {
                    is BluetoothPrinterDriver.PrintResult.Success -> {
                        Toast.makeText(this@PrinterSettingActivity, "Uji coba cetak ESC/POS berhasil!", Toast.LENGTH_LONG).show()
                    }
                    is BluetoothPrinterDriver.PrintResult.Error -> {
                        AlertDialog.Builder(this@PrinterSettingActivity)
                            .setTitle("Uji Coba Cetak Gagal")
                            .setMessage(result.message + "\n\nPastikan:\n1. Printer Bluetooth sudah dinyalakan\n2. Kertas kasir terpasang dengan benar\n3. Lampu indikator printer siap/standby")
                            .setPositiveButton("Mengerti", null)
                            .show()
                    }
                }
            } finally {
                binding.btnTestPrint.isEnabled = true
                binding.btnTestPrint.text = "Uji Coba Cetak"
            }
        }
    }

    private class BluetoothDeviceAdapter(
        private val onSelectClick: (BluetoothPrinterDriver.BluetoothDeviceInfo) -> Unit
    ) : RecyclerView.Adapter<BluetoothDeviceAdapter.ViewHolder>() {

        private val items = mutableListOf<BluetoothPrinterDriver.BluetoothDeviceInfo>()

        fun submitList(newList: List<BluetoothPrinterDriver.BluetoothDeviceInfo>) {
            items.clear()
            items.addAll(newList)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemBluetoothDeviceBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(private val binding: ItemBluetoothDeviceBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(device: BluetoothPrinterDriver.BluetoothDeviceInfo) {
                binding.tvDeviceName.text = device.name
                binding.tvDeviceAddress.text = device.address

                if (device.isSaved) {
                    binding.btnSelectDevice.text = "Aktif"
                    binding.btnSelectDevice.isEnabled = false
                    binding.btnSelectDevice.setBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.green_total))
                } else {
                    binding.btnSelectDevice.text = "Pilih"
                    binding.btnSelectDevice.isEnabled = true
                    binding.btnSelectDevice.setBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.purple_button))
                }

                binding.btnSelectDevice.setOnClickListener {
                    onSelectClick(device)
                }

                binding.root.setOnClickListener {
                    onSelectClick(device)
                }
            }
        }
    }
}
