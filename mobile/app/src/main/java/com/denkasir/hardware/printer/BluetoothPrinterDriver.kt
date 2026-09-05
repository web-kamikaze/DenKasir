package com.denkasir.hardware.printer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.OutputStream
import java.util.UUID

object BluetoothPrinterDriver {
    // Standard SPP UUID for Bluetooth serial devices / printers
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // ESC/POS Commands
    private val ESC: Byte = 0x1B
    private val GS: Byte = 0x1D
    val CMD_INIT = byteArrayOf(ESC, 0x40)
    val CMD_ALIGN_CENTER = byteArrayOf(ESC, 0x61, 1)
    val CMD_ALIGN_LEFT = byteArrayOf(ESC, 0x61, 0)
    val CMD_ALIGN_RIGHT = byteArrayOf(ESC, 0x61, 2)
    val CMD_BOLD_ON = byteArrayOf(ESC, 0x45, 1)
    val CMD_BOLD_OFF = byteArrayOf(ESC, 0x45, 0)
    val CMD_FEED_AND_CUT = byteArrayOf(GS, 0x56, 66, 0)
    val CMD_LINE_FEED = byteArrayOf(0x0A)

    suspend fun printReceipt(
        deviceAddress: String,
        receiptText: String
    ): Boolean = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        var outputStream: OutputStream? = null
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return@withContext false
            val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)

            withTimeout(8000) {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket?.connect()
                outputStream = socket?.outputStream

                outputStream?.write(CMD_INIT)
                outputStream?.write(receiptText.toByteArray(Charsets.US_ASCII))
                outputStream?.write(CMD_LINE_FEED)
                outputStream?.write(CMD_LINE_FEED)
                outputStream?.write(CMD_FEED_AND_CUT)
                outputStream?.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try {
                outputStream?.close()
                socket?.close()
            } catch (_: Exception) {}
        }
    }
}
