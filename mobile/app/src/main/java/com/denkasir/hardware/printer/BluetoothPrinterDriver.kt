package com.denkasir.hardware.printer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.denkasir.utils.ReceiptFormatterUtils
import com.denkasir.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object BluetoothPrinterDriver {
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private const val PREFS_NAME = "denkasir_prefs"
    const val KEY_PRINTER_MAC = "printer_mac"
    const val KEY_PRINTER_NAME = "printer_name"
    const val KEY_PAPER_WIDTH = "printer_paper_width"
    private const val PREF_KEY_WORKING_METHOD = "printer_conn_method_"

    private const val METHOD_REFLECTION_PORT_1 = 0
    private const val METHOD_SPP_SECURE = 1
    private const val METHOD_SPP_INSECURE = 2
    private const val METHOD_INSECURE_REFLECTION_PORT_1 = 3

    sealed class PrintResult {
        object Success : PrintResult()
        data class Error(val message: String) : PrintResult()
    }

    data class BluetoothDeviceInfo(
        val name: String,
        val address: String,
        val isSaved: Boolean = false
    )

    private val printMutex = Mutex()

    @Volatile
    private var activeSocket: BluetoothSocket? = null
    @Volatile
    private var activeOutputStream: OutputStream? = null
    @Volatile
    private var activeMacAddress: String? = null

    private val methodCache = ConcurrentHashMap<String, Int>()

    fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    fun hasBluetoothPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val adapter = getBluetoothAdapter(context) ?: return false
        return adapter.isEnabled
    }

    fun getSavedPrinterMac(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PRINTER_MAC, null)?.takeIf { it.isNotBlank() && it != "00:11:22:33:44:55" }
    }

    fun getSavedPrinterName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PRINTER_NAME, null)
    }

    fun getSavedPaperWidth(context: Context): Int {
        val session = SessionManager.getInstance(context)
        return session.getReceiptPaperWidth()
    }

    fun savePrinter(context: Context, macAddress: String, name: String? = null, paperWidth: Int = 58) {
        if (activeMacAddress != null && !activeMacAddress.equals(macAddress, ignoreCase = true)) {
            disconnect()
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_PRINTER_MAC, macAddress)
            if (name != null) putString(KEY_PRINTER_NAME, name)
            putInt(KEY_PAPER_WIDTH, paperWidth)
            apply()
        }
        val session = SessionManager.getInstance(context)
        session.saveReceiptSettings(
            paperWidth = paperWidth,
            feedLines = session.getReceiptFeedLines(),
            autoCut = session.isReceiptAutoCut(),
            showQr = session.isReceiptShowQr(),
            layoutStyle = session.getReceiptLayoutStyle(),
            headerSlogan = session.getReceiptHeaderSlogan(),
            footerNote = session.getReceiptFooterNote()
        )
    }

    fun clearSavedPrinter(context: Context) {
        disconnect()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove(KEY_PRINTER_MAC)
            remove(KEY_PRINTER_NAME)
            apply()
        }
    }

    fun getPairedDevices(context: Context): List<BluetoothDeviceInfo> {
        val adapter = getBluetoothAdapter(context) ?: return emptyList()
        if (!hasBluetoothPermission(context) || !adapter.isEnabled) return emptyList()

        return try {
            val savedMac = getSavedPrinterMac(context)
            adapter.bondedDevices.map { device ->
                val name = try { device.name ?: "Unknown Device" } catch (_: SecurityException) { "Unknown Device" }
                val address = device.address
                BluetoothDeviceInfo(
                    name = name,
                    address = address,
                    isSaved = (address.equals(savedMac, ignoreCase = true))
                )
            }.sortedWith(compareByDescending<BluetoothDeviceInfo> { it.isSaved }.thenBy { it.name })
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    private fun getCachedMethod(context: Context, macAddress: String): Int {
        methodCache[macAddress]?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getInt(PREF_KEY_WORKING_METHOD + macAddress, -1)
        if (saved != -1) {
            methodCache[macAddress] = saved
            return saved
        }
        return METHOD_REFLECTION_PORT_1
    }

    private fun saveCachedMethod(context: Context, macAddress: String, method: Int) {
        methodCache[macAddress] = method
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(PREF_KEY_WORKING_METHOD + macAddress, method).apply()
    }

    private fun trySingleConnection(device: BluetoothDevice, method: Int): BluetoothSocket {
        val socket = when (method) {
            METHOD_REFLECTION_PORT_1 -> {
                val methodRef = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                methodRef.invoke(device, 1) as BluetoothSocket
            }
            METHOD_INSECURE_REFLECTION_PORT_1 -> {
                val methodRef = device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                methodRef.invoke(device, 1) as BluetoothSocket
            }
            METHOD_SPP_INSECURE -> {
                device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            }
            else -> {
                device.createRfcommSocketToServiceRecord(SPP_UUID)
            }
        }
        socket.connect()
        return socket
    }

    private suspend fun connectSocketWithOptimizedFallback(
        context: Context,
        device: BluetoothDevice
    ): Pair<BluetoothSocket, Int> = withContext(Dispatchers.IO) {
        val mac = device.address
        val preferredMethod = getCachedMethod(context, mac)

        val methodsToTry = mutableListOf<Int>()
        methodsToTry.add(preferredMethod)
        listOf(METHOD_REFLECTION_PORT_1, METHOD_SPP_SECURE, METHOD_SPP_INSECURE, METHOD_INSECURE_REFLECTION_PORT_1).forEach { m ->
            if (m !in methodsToTry) methodsToTry.add(m)
        }

        var lastException: Exception? = null
        for (method in methodsToTry) {
            var tempSocket: BluetoothSocket? = null
            try {
                tempSocket = trySingleConnection(device, method)
                if (tempSocket.isConnected) {
                    saveCachedMethod(context, mac, method)
                    return@withContext Pair(tempSocket, method)
                }
            } catch (e: Exception) {
                lastException = e
                try {
                    tempSocket?.close()
                } catch (_: Exception) {}
            }
            delay(40)
        }
        throw lastException ?: IOException("Gagal menghubungkan RFCOMM Bluetooth Socket ke " + device.name)
    }

    fun disconnect() {
        try {
            activeOutputStream?.flush()
            activeOutputStream?.close()
        } catch (_: Exception) {}
        try {
            activeSocket?.close()
        } catch (_: Exception) {}
        activeOutputStream = null
        activeSocket = null
        activeMacAddress = null
    }

    suspend fun warmupConnection(context: Context, targetMac: String? = null) = withContext(Dispatchers.IO) {
        val macToUse = targetMac ?: getSavedPrinterMac(context) ?: return@withContext
        if (!hasBluetoothPermission(context) || !isBluetoothEnabled(context)) return@withContext

        printMutex.withLock {
            try {
                if (activeSocket?.isConnected == true && activeMacAddress.equals(macToUse, ignoreCase = true)) {
                    return@withLock
                }
                disconnect()

                val adapter = getBluetoothAdapter(context) ?: return@withLock
                try {
                    if (adapter.isDiscovering) {
                        adapter.cancelDiscovery()
                    }
                } catch (_: SecurityException) {}

                val device = adapter.getRemoteDevice(macToUse)
                withTimeout(4000) {
                    val (socket, _) = connectSocketWithOptimizedFallback(context, device)
                    activeSocket = socket
                    activeOutputStream = socket.outputStream
                    activeMacAddress = macToUse
                }
            } catch (_: Exception) {
                disconnect()
            }
        }
    }

    suspend fun printReceipt(
        context: Context,
        receiptBytes: ByteArray,
        targetMac: String? = null
    ): PrintResult = withContext(Dispatchers.IO) {
        val adapter = getBluetoothAdapter(context)
            ?: return@withContext PrintResult.Error("Perangkat tidak mendukung Bluetooth.")

        if (!hasBluetoothPermission(context)) {
            return@withContext PrintResult.Error("Izin Bluetooth belum diberikan.")
        }
        if (!adapter.isEnabled) {
            return@withContext PrintResult.Error("Bluetooth di HP dalam keadaan MATI. Silakan nyalakan Bluetooth.")
        }

        val macToUse = targetMac ?: getSavedPrinterMac(context)
            ?: return@withContext PrintResult.Error("Belum ada printer yang dipilih. Silakan atur printer terlebih dahulu.")

        printMutex.withLock {
            try {
                withTimeout(10000) {
                    var out = activeOutputStream
                    val sock = activeSocket

                    if (sock != null && sock.isConnected && out != null && activeMacAddress.equals(macToUse, ignoreCase = true)) {
                        try {
                            out.write(receiptBytes)
                            out.flush()
                            delay(80)
                            return@withTimeout
                        } catch (_: IOException) {
                            disconnect()
                        }
                    }

                    disconnect()
                    try {
                        if (adapter.isDiscovering) {
                            adapter.cancelDiscovery()
                            delay(40)
                        }
                    } catch (_: SecurityException) {}

                    val device: BluetoothDevice = adapter.getRemoteDevice(macToUse)
                    val (freshSocket, _) = connectSocketWithOptimizedFallback(context, device)
                    val freshOut = freshSocket.outputStream
                    activeSocket = freshSocket
                    activeOutputStream = freshOut
                    activeMacAddress = macToUse

                    freshOut.write(receiptBytes)
                    freshOut.flush()
                    delay(80)
                }

                PrintResult.Success
            } catch (e: TimeoutCancellationException) {
                disconnect()
                PrintResult.Error("Koneksi ke printer timeout (10 detik). Pastikan printer dalam jarak dekat dan baterai cukup.")
            } catch (e: SecurityException) {
                disconnect()
                PrintResult.Error("Akses Bluetooth ditolak oleh keamanan Android.")
            } catch (e: Exception) {
                e.printStackTrace()
                disconnect()
                PrintResult.Error("Gagal mencetak ke printer: " + (e.message ?: "Printer tidak merespon"))
            }
        }
    }

    suspend fun printReceipt(
        context: Context,
        receiptText: String,
        targetMac: String? = null
    ): PrintResult = withContext(Dispatchers.IO) {
        val session = SessionManager.getInstance(context)
        val feedLines = session.getReceiptFeedLines()
        val autoCut = session.isReceiptAutoCut()
        val charset = Charset.forName("CP437")

        val baos = java.io.ByteArrayOutputStream()
        baos.write(ReceiptFormatterUtils.CMD_INIT)
        baos.write(ReceiptFormatterUtils.CMD_CODE_PAGE_CP437)
        val sanitized = sanitizeText(receiptText)
        baos.write(sanitized.toByteArray(charset))

        repeat(maxOf(1, feedLines)) {
            baos.write(ReceiptFormatterUtils.CMD_LINE_FEED)
        }
        if (autoCut) {
            baos.write(ReceiptFormatterUtils.CMD_FEED_AND_CUT)
        }

        return@withContext printReceipt(context, baos.toByteArray(), targetMac)
    }

    suspend fun printReceipt(
        deviceAddress: String,
        receiptText: String
    ): Boolean = withContext(Dispatchers.IO) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@withContext false
        if (deviceAddress.isBlank() || deviceAddress == "00:11:22:33:44:55") return@withContext false
        printMutex.withLock {
            try {
                try {
                    if (adapter.isDiscovering) {
                        adapter.cancelDiscovery()
                        delay(40)
                    }
                } catch (_: SecurityException) {}

                val device: BluetoothDevice = adapter.getRemoteDevice(deviceAddress)
                withTimeout(8000) {
                    var socket: BluetoothSocket? = null
                    var out: OutputStream? = null
                    try {
                        val method = methodCache[deviceAddress] ?: METHOD_REFLECTION_PORT_1
                        try {
                            socket = trySingleConnection(device, method)
                        } catch (_: Exception) {
                            socket = trySingleConnection(device, METHOD_SPP_SECURE)
                        }
                        out = socket?.outputStream
                        out?.write(ReceiptFormatterUtils.CMD_INIT)
                        out?.write(ReceiptFormatterUtils.CMD_CODE_PAGE_CP437)
                        out?.write(sanitizeText(receiptText).toByteArray(Charset.forName("CP437")))
                        out?.write(ReceiptFormatterUtils.CMD_LINE_FEED)
                        out?.write(ReceiptFormatterUtils.CMD_LINE_FEED)
                        out?.write(ReceiptFormatterUtils.CMD_FEED_AND_CUT)
                        out?.flush()
                        delay(80)
                    } finally {
                        try { out?.close() } catch (_: Exception) {}
                        try { socket?.close() } catch (_: Exception) {}
                    }
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun printTestReceipt(context: Context, targetMac: String? = null): PrintResult {
        val testBytes = ReceiptFormatterUtils.buildEscPosTestReceiptBytes(context)
        return printReceipt(context, testBytes, targetMac)
    }

    private fun sanitizeText(text: String): String {
        return text.replace("â€’", "-")
            .replace("â€“", "-")
            .replace("â€”", "-")
            .replace("â†’", "->")
            .replace("â€¢", "*")
            .replace("â„¢", "TM")
            .replace("Â©", "(C)")
            .replace("Â®", "(R)")
            .replace("â‚¬", "EUR")
            .replace("Â¥", "YEN")
    }
}
