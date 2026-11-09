package com.denkasir.ui.pos

import com.denkasir.data.local.entity.ProductEntity
import com.denkasir.data.local.entity.isDigitalOutOfStock
import com.denkasir.utils.CurrencyUtils

object DigitalPackageFormatter {

    data class PackageDisplayData(
        val label: String,
        val column1Title: String,
        val column1Value: String,
        val column2Title: String,
        val column2Value: String,
        val originalPrice: Double,
        val sellingPrice: Double,
        val costPrice: Double,
        val badgeText: String,
        val isCustom: Boolean,
        val productEntity: ProductEntity?
    )

        fun sanitizeProductEntity(product: ProductEntity): ProductEntity {
        var realName = product.name
        var realCost = product.costPrice
        var realSelling = product.sellingPrice
        var realCode = product.providerCode ?: ""

        if (product.name.contains("Harga: Rp", ignoreCase = true)) {
            val costMatch = Regex("(?i)Harga:\\s*Rp\\s*([0-9.,]+)").find(product.name)
            if (costMatch != null) {
                val numStr = costMatch.groupValues[1].replace(".", "").replace(",", ".")
                val parsedCost = numStr.toDoubleOrNull() ?: 0.0
                if (parsedCost > 0) {
                    realCost = parsedCost
                }
            }

            if (realCode.isNotBlank()) {
                val firstPart = realCode.split("Kode SMS:")[0].trim()
                val lines = firstPart.split("\n", "\r").map { it.trim() }.filter { it.isNotBlank() }
                if (lines.isNotEmpty()) {
                    val header = if (product.name.contains("[") && product.name.contains("]")) {
                        product.name.substring(0, product.name.indexOf("]") + 1) + " "
                    } else ""
                    realName = header + lines[0]
                }
            }
        }

        if (realCode.contains("Kode SMS:", ignoreCase = true)) {
            val codeMatch = Regex("(?i)Kode SMS:\\s*([A-Za-z0-9_-]+)").find(realCode)
            if (codeMatch != null) {
                realCode = codeMatch.groupValues[1].trim()
            }
        }

        if (realCost > 0 && (realSelling <= 2000.0 || realSelling < realCost)) {
            val margin = when {
                realCost <= 15000 -> 1800.0
                realCost <= 50000 -> 2000.0
                realCost <= 100000 -> 2500.0
                else -> 3000.0
            }
            realSelling = Math.ceil((realCost + margin) / 500.0) * 500.0
        }

        return product.copy(
            name = realName,
            costPrice = realCost,
            sellingPrice = realSelling,
            providerCode = realCode
        )
    }

    fun createOptionFromProduct(
        product: ProductEntity,
        category: DigitalCategoryItem,
        operator: String
    ): NominalOption {
        val cleanProduct = sanitizeProductEntity(product)
        val parsed = parse(
            name = cleanProduct.name,
            sellingPrice = cleanProduct.sellingPrice,
            costPrice = cleanProduct.costPrice,
            category = category,
            operator = operator,
            productEntity = cleanProduct,
            isCustom = false
        )
        val isOutOfStock = cleanProduct.isDigitalOutOfStock()
        return NominalOption(
            label = parsed.label,
            nominalValue = cleanProduct.sellingPrice,
            sellingPrice = parsed.sellingPrice,
            costPrice = parsed.costPrice,
            isCustom = false,
            productEntity = cleanProduct,
            quotaLabel = parsed.column1Value,
            validityLabel = parsed.column2Value,
            originalPrice = parsed.originalPrice,
            badgeText = if (isOutOfStock) "Stok Kosong" else parsed.badgeText,
            column1Title = parsed.column1Title,
            column2Title = parsed.column2Title,
            isAvailable = !isOutOfStock
        )
    }

    fun enrichOption(
        option: NominalOption,
        category: DigitalCategoryItem,
        operator: String
    ): NominalOption {
        if (option.isCustom) {
            return option.copy(
                label = "Input Nominal Khusus / Tagihan",
                quotaLabel = "Bebas",
                validityLabel = "-",
                originalPrice = 0.0,
                badgeText = "Manual",
                column1Title = if (category.section == DigitalSection.TAGIHAN) "Tagihan" else "Nominal",
                column2Title = "Masa berlaku"
            )
        }

        // If quotaLabel and validityLabel are already explicitly provided
        if (option.quotaLabel.isNotBlank() && option.validityLabel.isNotBlank()) {
            val orig = if (option.originalPrice > 0) option.originalPrice else calculateOriginalPrice(option.sellingPrice)
            return option.copy(originalPrice = orig)
        }

        val parsed = parse(
            name = option.productEntity?.name ?: option.label,
            sellingPrice = option.sellingPrice,
            costPrice = option.costPrice,
            category = category,
            operator = operator,
            productEntity = option.productEntity,
            isCustom = false
        )

        val isOutOfStock = option.productEntity?.isDigitalOutOfStock() ?: false
        val badge = if (isOutOfStock) "Stok Kosong" else if (option.badgeText.isNotBlank()) option.badgeText else parsed.badgeText
        return option.copy(
            label = if (option.productEntity != null) parsed.label else option.label,
            quotaLabel = if (option.quotaLabel.isNotBlank()) option.quotaLabel else parsed.column1Value,
            validityLabel = if (option.validityLabel.isNotBlank()) option.validityLabel else parsed.column2Value,
            originalPrice = if (option.originalPrice > 0) option.originalPrice else parsed.originalPrice,
            badgeText = badge,
            column1Title = if (option.column1Title.isNotBlank()) option.column1Title else parsed.column1Title,
            column2Title = if (option.column2Title.isNotBlank()) option.column2Title else parsed.column2Title,
            isAvailable = if (isOutOfStock) false else option.isAvailable
        )
    }

    fun parse(
        name: String,
        sellingPrice: Double,
        costPrice: Double,
        category: DigitalCategoryItem,
        operator: String,
        productEntity: ProductEntity?,
        isCustom: Boolean
    ): PackageDisplayData {
        val effectiveSelling = if (sellingPrice <= 0.0 && (category.section == DigitalSection.TAGIHAN || category.id.equals("pdam", ignoreCase = true))) {
            2000.0 // Biaya admin loket resmi PPOB
        } else {
            sellingPrice
        }

        if (isCustom) {
            return PackageDisplayData(
                label = "Input Nominal Khusus / Tagihan",
                column1Title = if (category.section == DigitalSection.TAGIHAN) "Tagihan" else "Nominal",
                column1Value = "Bebas",
                column2Title = "Masa berlaku",
                column2Value = "-",
                originalPrice = 0.0,
                sellingPrice = sellingPrice,
                costPrice = costPrice,
                badgeText = "Manual",
                isCustom = true,
                productEntity = productEntity
            )
        }

        val cleanName = cleanDisplayName(name, operator, category)
        val catId = category.id.lowercase()
        val isDataCategory = catId in listOf("paket_data", "roaming", "travel_esim", "esim_lokal") ||
                name.contains(Regex("(GB|MB|Kuota|Internet|Unlimited|Data)", RegexOption.IGNORE_CASE))

        // 1. Column 1 (Title & Value)
        val col1Title = when {
            isDataCategory -> "Kuota"
            catId == "pulsa" -> "Pulsa"
            catId == "listrik_pln" -> "Token"
            catId == "uang_elektronik" -> "Saldo"
            catId == "voucher_game" -> "Item"
            category.section == DigitalSection.TAGIHAN -> "Tagihan"
            else -> "Nominal"
        }

        val col1Value = extractColumn1Value(cleanName, name, effectiveSelling, col1Title, isDataCategory)

        // 2. Column 2 (Title & Value)
        val col2Title = "Masa berlaku"
        val col2Value = extractColumn2Value(cleanName, name, effectiveSelling, catId, isDataCategory)

        // 3. Strikethrough Original Price
        val origPrice = calculateOriginalPrice(effectiveSelling)

        // 4. Badge Text (Image #2 displays "Muraaah")
        val badge = "Muraaah"

        return PackageDisplayData(
            label = cleanName,
            column1Title = col1Title,
            column1Value = col1Value,
            column2Title = col2Title,
            column2Value = col2Value,
            originalPrice = origPrice,
            sellingPrice = effectiveSelling,
            costPrice = costPrice,
            badgeText = badge,
            isCustom = false,
            productEntity = productEntity
        )
    }

    private fun cleanDisplayName(raw: String, operator: String, category: DigitalCategoryItem): String {
        var res = raw
            .replace(Regex("^\\[[^\\]]+\\]\\s*"), "") // remove [ByU], [Telkomsel]
            .replace(Regex("^(Pulsa|Token PLN|Dompet Digital|Tagihan PDAM|Tagihan|Game|Voucher Game|Paket Data|Paket Kuota)\\s*-\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
        if (res.isBlank()) {
            res = "${category.title} $operator"
        }
        return res
    }

    fun extractColumn1Value(
        cleanName: String,
        fullName: String,
        price: Double,
        colTitle: String,
        isDataCategory: Boolean
    ): String {
        val target = "$cleanName $fullName"

        if (isDataCategory) {
            // Check GB / MB (e.g. 10GB, 10 GB, 1.5GB, 500MB)
            val quotaMatch = Regex("(?i)\\b(\\d+(?:[.,]\\d+)?)\\s*(GB|MB)\\b").find(target)
            if (quotaMatch != null) {
                val num = quotaMatch.groupValues[1].replace(',', '.')
                val unit = quotaMatch.groupValues[2].uppercase()
                return "$num $unit"
            }

            // Check Unlimited
            if (target.contains("unlimited", ignoreCase = true)) {
                return "Unlimited"
            }

            // Check Menit / SMS (Voice / SMS packs that may have 0 GB like in Image #2)
            val menitMatch = Regex("(?i)\\b(\\d+)\\s*(Menit|Mnt)\\b").find(target)
            if (menitMatch != null) {
                return "${menitMatch.groupValues[1]} Menit"
            }
            val smsMatch = Regex("(?i)\\b(\\d+)\\s*SMS\\b").find(target)
            if (smsMatch != null) {
                return "${smsMatch.groupValues[1]} SMS"
            }

            if (target.contains("nelpon", ignoreCase = true) || target.contains("telepon", ignoreCase = true) || target.contains("voice", ignoreCase = true)) {
                return "0 GB"
            }
        }

        // Voucher Game (Diamond / CP / UC)
        val gameMatch = Regex("(?i)\\b(\\d+)\\s*(Diamond|DM|CP|UC|Zem|Star)\\b").find(target)
        if (gameMatch != null) {
            val num = gameMatch.groupValues[1]
            val unit = gameMatch.groupValues[2].uppercase()
            return "$num $unit"
        }

        // Pulsa / Token PLN / Saldo E-Money: extract nominal number
        val kMatch = Regex("(?i)\\b(\\d{1,3})\\s*(?:k|rb|ribu)\\b").find(target)
        if (kMatch != null) {
            val n = kMatch.groupValues[1]
            return "$n.000"
        }

        val dotMatch = Regex("\\b(\\d{1,3}\\.\\d{3})\\b").find(target)
        if (dotMatch != null) {
            return dotMatch.groupValues[1]
        }

        val rawNumMatch = Regex("\\b(\\d{4,6})\\b").find(target)
        if (rawNumMatch != null) {
            val v = rawNumMatch.groupValues[1].toLongOrNull() ?: 0L
            if (v in 5000..1000000) {
                return CurrencyUtils.formatNumberOnly(v.toDouble())
            }
        }

        // If price is available, estimate nominal from selling price
        if (price > 0) {
            val approxNominal = roundToStandardNominal(price)
            if (approxNominal > 0) {
                return CurrencyUtils.formatNumberOnly(approxNominal.toDouble())
            }
        }

        return if (isDataCategory) "0 GB" else "-"
    }

    fun extractColumn2Value(
        cleanName: String,
        fullName: String,
        price: Double,
        catId: String,
        isDataCategory: Boolean
    ): String {
        val target = "$cleanName $fullName"

        // Search for days/months in name: e.g. "5 Hari", "30 Hari", "1 Bulan", "7 Hari"
        val dayMatch = Regex("(?i)\\b(\\d+)\\s*(hari|hr|day|days)\\b").find(target)
        if (dayMatch != null) {
            return "${dayMatch.groupValues[1]} hari"
        }

        val monthMatch = Regex("(?i)\\b(\\d+)\\s*(bulan|bln|month|months)\\b").find(target)
        if (monthMatch != null) {
            val months = monthMatch.groupValues[1].toIntOrNull() ?: 1
            return "${months * 30} hari"
        }

        // If category is Pulsa, validity typically scales with nominal
        if (catId == "pulsa") {
            return when {
                price <= 8000 -> "15 hari"
                price <= 25000 -> "30 hari"
                price <= 55000 -> "30 hari"
                price <= 105000 -> "60 hari"
                else -> "90 hari"
            }
        }

        // If data package and no validity specified, default is 30 hari
        if (isDataCategory) {
            return "30 hari"
        }

        // PLN, PDAM, E-Money don't expire
        if (catId in listOf("listrik_pln", "pdam", "uang_elektronik")) {
            return "Selamanya"
        }

        return "-"
    }

    fun calculateOriginalPrice(sellingPrice: Double): Double {
        if (sellingPrice <= 0) return 0.0
        // Creates realistic promotional strikethrough price like Image #2 (e.g. 12500 -> 15500)
        return when {
            sellingPrice <= 15000 -> sellingPrice + 3000.0
            sellingPrice <= 30000 -> sellingPrice + 4500.0
            sellingPrice <= 60000 -> sellingPrice + 6500.0
            sellingPrice <= 120000 -> sellingPrice + 10000.0
            else -> Math.ceil((sellingPrice * 1.12) / 1000.0) * 1000.0
        }
    }

    private fun roundToStandardNominal(price: Double): Long {
        val standard = listOf(
            5000L, 10000L, 15000L, 20000L, 25000L, 30000L, 50000L, 75000L, 100000L, 150000L, 200000L, 300000L, 500000L, 1000000L
        )
        for (std in standard.reversed()) {
            if (price >= std && price <= std * 1.25) {
                return std
            }
        }
        return (price / 1000).toLong() * 1000
    }
}
