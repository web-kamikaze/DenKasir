package com.denkasir.hardware.printer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.denkasir.R

class PrinterSettingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Pilih Printer Bluetooth"

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Perangkat tidak memiliki Bluetooth", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 101)
            return
        }

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
        val deviceList = mutableListOf<String>()
        val deviceAddresses = mutableListOf<String>()

        pairedDevices?.forEach { device ->
            deviceList.add("${device.name} (${device.address})")
            deviceAddresses.add(device.address)
        }

        if (deviceList.isEmpty()) {
            Toast.makeText(this, "Tidak ada printer bluetooth yang ter-pairing", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Pilih Printer Thermal")
            .setAdapter(adapter) { _, which ->
                val chosenAddress = deviceAddresses[which]
                getSharedPreferences("denkasir_prefs", MODE_PRIVATE).edit()
                    .putString("printer_mac", chosenAddress)
                    .apply()
                Toast.makeText(this, "Printer dipilih: ${deviceList[which]}", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }
}
