package com.denkasir.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import com.denkasir.data.local.entity.DigitalConfigEntity

/**
 * Aturan Rute Distributor Khusus untuk tiap kategori produk PPOB & Pulsa
 * (Contoh: Pulsa ke Distributor 1 via SIM 1 PIN 1111, Kuota ke Distributor 2 via SIM 2 PIN 2222, PLN ke Distributor 3 PIN 3333)
 */
data class DistributorRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String, // e.g. "Distributor 1 (Pulsa Murah)"
    val category: String, // e.g. "Pulsa", "Paket Data", "PLN", "PDAM", "Uang Elektronik", "Voucher Game", "SEMUA"
    val centerNumber: String, // e.g. "081234567890"
    val pin: String, // e.g. "1234" (PIN distributor ini)
    val smsTemplate: String = "[KODE].[TUJUAN].[PIN]",
    val smsDelimiter: String = ".",
    val simSlot: Int = -1 // -1 = Ikuti Konfigurasi Utama, 0 = Wajib SIM 1, 1 = Wajib SIM 2
)

data class SimCardInfo(
    val slotIndex: Int, // 0 for SIM 1, 1 for SIM 2
    val subscriptionId: Int,
    val displayName: String,
    val carrierName: String
)

sealed class DirectSmsResult {
    object Success : DirectSmsResult()
    object PermissionRequired : DirectSmsResult()
    data class Error(val message: String) : DirectSmsResult()
}

object SmsGatewayHelper {

    const val MODE_API_H2H = "API_H2H"
    const val MODE_SMS_CENTER = "SMS_CENTER"
    const val MODE_AUTO_FALLBACK = "AUTO_FALLBACK"

    const val TEMPLATE_DOT = "[KODE].[TUJUAN].[PIN]"
    const val TEMPLATE_HASH = "[KODE]#[TUJUAN]#[PIN]"
    const val TEMPLATE_SPACE = "BELI [KODE] [TUJUAN] [PIN]"

    /**
     * Memformat perintah transaksi SMS sesuai template konfigurasi distributor
     */
    fun formatSmsCommand(
        productCode: String,
        targetNumber: String,
        pin: String,
        template: String = TEMPLATE_DOT,
        delimiter: String = "."
    ): String {
        var rawCode = productCode.trim()
        if (rawCode.contains("Kode SMS:", ignoreCase = true)) {
            val m = Regex("(?i)Kode SMS:\\s*([A-Za-z0-9_-]+)").find(rawCode)
            if (m != null) rawCode = m.groupValues[1]
        }
        val cleanCode = rawCode.trim().uppercase()

        val rawTarget = if (targetNumber.contains("(")) {
            targetNumber.substring(0, targetNumber.indexOf("(")).trim()
        } else {
            targetNumber.trim()
        }
        val cleanTarget = rawTarget.replace(Regex("[^0-9+]"), "")
        val cleanPin = pin.trim()

        if (template.contains("[KODE]") && template.contains("[TUJUAN]") && template.contains("[PIN]")) {
            return template
                .replace("[KODE]", cleanCode)
                .replace("[TUJUAN]", cleanTarget)
                .replace("[PIN]", cleanPin)
        }

        return when (delimiter) {
            "#" -> "$cleanCode#$cleanTarget#$cleanPin"
            " " -> "BELI $cleanCode $cleanTarget $cleanPin"
            else -> "$cleanCode.$cleanTarget.$cleanPin"
        }
    }

    /**
     * Mem-parsing daftar nomor SMS center yang disimpan dalam format koma
     */
    fun parseSmsCenterList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(",")
            .map { it.trim().replace(Regex("[^0-9+]"), "") }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    /**
     * Menggabungkan daftar nomor SMS center menjadi string CSV
     */
    fun joinSmsCenterList(numbers: List<String>): String {
        return numbers
            .map { it.trim().replace(Regex("[^0-9+]"), "") }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(",")
    }

    /**
     * Memvalidasi apakah nomor SMS center valid (minimal 8 digit, diawali 0 atau 62 atau +62)
     */
    fun isValidCenterNumber(number: String): Boolean {
        val clean = number.trim().replace(Regex("[^0-9+]"), "")
        return clean.length in 8..16 && (clean.startsWith("0") || clean.startsWith("62") || clean.startsWith("+62"))
    }

    /**
     * Membuat Intent untuk membuka aplikasi SMS bawaan Android dengan nomor tujuan & pesan terisi
     */
    fun createSendSmsIntent(centerNumber: String, messageBody: String): Intent {
        val cleanNumber = centerNumber.trim().replace(Regex("[^0-9+]"), "")
        return Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$cleanNumber")
            putExtra("sms_body", messageBody)
        }
    }

    // ==========================================
    // DUAL SIM CARD & DIRECT BACKGROUND SMS
    // ==========================================

    /**
     * Mendeteksi kartu SIM aktif di perangkat Android (Dual SIM: SIM 1 & SIM 2)
     */
    @SuppressLint("MissingPermission")
    fun getActiveSimCards(context: Context): List<SimCardInfo> {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return emptyList()

        return try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val infoList: List<SubscriptionInfo>? = subscriptionManager?.activeSubscriptionInfoList
            infoList?.map { info ->
                val carrier = info.carrierName?.toString()?.ifBlank { null }
                    ?: info.displayName?.toString()?.ifBlank { null }
                    ?: "Operator"
                SimCardInfo(
                    slotIndex = info.simSlotIndex,
                    subscriptionId = info.subscriptionId,
                    displayName = "SIM ${info.simSlotIndex + 1} ($carrier)",
                    carrierName = carrier
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Mengirim SMS langsung di latar belakang melalui SmsManager (Direct SMS)
     * Menggunakan kartu SIM yang dipilih (SIM 1 / SIM 2)
     */
    @SuppressLint("MissingPermission")
    fun sendDirectSms(
        context: Context,
        destinationAddress: String,
        messageBody: String,
        simSlotIndex: Int = -1,
        sentIntent: PendingIntent? = null,
        deliveryIntent: PendingIntent? = null
    ): DirectSmsResult {
        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasSmsPermission) {
            return DirectSmsResult.PermissionRequired
        }

        return try {
            val cleanNumber = destinationAddress.trim().replace(Regex("[^0-9+]"), "")
            if (cleanNumber.isEmpty()) {
                return DirectSmsResult.Error("Nomor tujuan SMS Center kosong!")
            }

            val smsManager: SmsManager = getTargetSmsManager(context, simSlotIndex)
            val parts = smsManager.divideMessage(messageBody)
            if (parts.size > 1) {
                val sentIntents = sentIntent?.let { pi -> ArrayList(List(parts.size) { pi }) }
                val deliveryIntents = deliveryIntent?.let { pi -> ArrayList(List(parts.size) { pi }) }
                smsManager.sendMultipartTextMessage(cleanNumber, null, parts, sentIntents, deliveryIntents)
            } else {
                smsManager.sendTextMessage(cleanNumber, null, messageBody, sentIntent, deliveryIntent)
            }
            DirectSmsResult.Success
        } catch (e: Exception) {
            DirectSmsResult.Error(e.message ?: "Gagal mengirim SMS.")
        }
    }

    @SuppressLint("MissingPermission")
    fun getTargetSmsManager(context: Context, simSlotIndex: Int): SmsManager {
        if (simSlotIndex >= 0) {
            val simCards = getActiveSimCards(context)
            val chosenSim = simCards.find { it.slotIndex == simSlotIndex }
            if (chosenSim != null) {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java).createForSubscriptionId(chosenSim.subscriptionId)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(chosenSim.subscriptionId)
                }
            }
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    // ==========================================
    // LOGIKA MULTI-DISTRIBUTOR ROUTING
    // ==========================================

    /**
     * Mem-parsing daftar aturan distributor dari JSON String di database
     */
    fun parseDistributorRules(json: String?): List<DistributorRule> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val listType = object : TypeToken<List<DistributorRule>>() {}.type
            Gson().fromJson<List<DistributorRule>>(json, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Mengonversi daftar aturan distributor ke format JSON String untuk database
     */
    fun distributorRulesToJson(rules: List<DistributorRule>): String {
        return try {
            Gson().toJson(rules)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Logika Pintar: Mencari distributor yang bertugas untuk kategori produk tertentu
     * (Pulsa -> Dist 1 via SIM 1, Kuota -> Dist 2 via SIM 2, PLN -> Dist 3)
     */
    fun findMatchingDistributor(
        rules: List<DistributorRule>,
        category: String?,
        defaultName: String = "Server Distributor Utama",
        defaultPin: String = "",
        defaultCenter: String = "",
        defaultTemplate: String = TEMPLATE_DOT,
        defaultDelimiter: String = ".",
        defaultSimSlot: Int = -1
    ): DistributorRule {
        val cleanCat = category?.trim()?.lowercase().orEmpty()

        if (cleanCat.isNotEmpty()) {
            // 1. Pencocokan spesifik per kategori
            val matched = rules.find {
                val ruleCat = it.category.trim().lowercase()
                ruleCat == cleanCat ||
                (cleanCat.contains("pulsa") && ruleCat.contains("pulsa")) ||
                ((cleanCat.contains("data") || cleanCat.contains("kuota") || cleanCat.contains("esim") || cleanCat.contains("roaming")) && (ruleCat.contains("data") || ruleCat.contains("kuota"))) ||
                ((cleanCat.contains("pln") || cleanCat.contains("listrik")) && (ruleCat.contains("pln") || ruleCat.contains("listrik"))) ||
                ((cleanCat.contains("pdam") || cleanCat.contains("air")) && (ruleCat.contains("pdam") || ruleCat.contains("air"))) ||
                (cleanCat.contains("game") && ruleCat.contains("game")) ||
                ((cleanCat.contains("uang") || cleanCat.contains("emoney") || cleanCat.contains("dana") || cleanCat.contains("wallet")) && (ruleCat.contains("uang") || ruleCat.contains("emoney") || ruleCat.contains("wallet"))) ||
                ((cleanCat.contains("bpjs") || cleanCat.contains("asuransi")) && (ruleCat.contains("bpjs") || ruleCat.contains("asuransi"))) ||
                ((cleanCat.contains("telkom") || cleanCat.contains("indihome") || cleanCat.contains("internet") || cleanCat.contains("tv")) && (ruleCat.contains("telkom") || ruleCat.contains("internet") || ruleCat.contains("tv")))
            }

            if (matched != null) return matched

            // 2. Jika tidak ada yang spesifik, cari distributor dengan kategori 'SEMUA'
            val allRule = rules.find { it.category.equals("SEMUA", ignoreCase = true) || it.category.contains("semua", ignoreCase = true) }
            if (allRule != null) return allRule
        }

        // 3. Fallback jika tidak ada aturan khusus atau belum diatur
        val firstCenter = if (defaultCenter.contains(",")) defaultCenter.split(",").firstOrNull()?.trim().orEmpty() else defaultCenter
        return DistributorRule(
            id = "default",
            name = defaultName,
            category = category ?: "SEMUA",
            centerNumber = firstCenter,
            pin = defaultPin,
            smsTemplate = defaultTemplate,
            smsDelimiter = defaultDelimiter,
            simSlot = defaultSimSlot
        )
    }
    // ==========================================
    // VALIDASI STATUS GATEWAY DIGITAL
    // ==========================================

    /**
     * Memvalidasi apakah kredensial API H2H telah dikonfigurasi (URL dan API Key terisi)
     */
    fun isApiConfigured(config: DigitalConfigEntity?): Boolean {
        if (config == null) return false
        return config.apiUrl.isNotBlank() && config.apiKey.isNotBlank()
    }

    /**
     * Memvalidasi apakah jalur SMS Center OtomaX telah dikonfigurasi
     * (memiliki nomor SMS Center atau aturan distributor, serta PIN transaksi)
     */
    fun isSmsConfigured(config: DigitalConfigEntity?): Boolean {
        if (config == null) return false
        val hasSmsCenter = parseSmsCenterList(config.smsCenterNumbers).isNotEmpty()
        val hasDistributorRules = parseDistributorRules(config.distributorRulesJson).any { it.centerNumber.isNotBlank() }
        val hasPin = config.pinPassword.isNotBlank() || parseDistributorRules(config.distributorRulesJson).any { it.pin.isNotBlank() }
        return (hasSmsCenter || hasDistributorRules) && hasPin
    }

    /**
     * Aturan Bisnis: Produk digital boleh dipesan jika SALAH SATU jalur gateway sudah disetting:
     * - API H2H Internet aktif (URL + API Key) ATAU
     * - SMS Center OtomaX aktif (Nomor SMS Center / Rute Distributor + PIN)
     * Jika kedua jalur belum disetting, produk digital dilarang untuk dipesan / dibayar.
     */
    fun isGatewayConfigured(config: DigitalConfigEntity?): Boolean {
        return isApiConfigured(config) || isSmsConfigured(config)
    }
}
