package com.denkasir

import com.denkasir.data.local.entity.ProductEntity
import com.denkasir.data.local.entity.isDigitalOutOfStock
import com.denkasir.ui.pos.DigitalCatalog
import com.denkasir.ui.pos.DigitalSection
import com.denkasir.ui.pos.NominalOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitalServicesTest {

    @Test
    fun testCatalogContainsRequiredCategories() {
        val categories = DigitalCatalog.getAllCategories()
        val isiUlang = categories.filter { it.section == DigitalSection.ISI_ULANG }
        val tagihan = categories.filter { it.section == DigitalSection.TAGIHAN }

        // Verify Isi Ulang count (now contains real master categories)
        assertTrue(isiUlang.size >= 6)
        assertTrue(isiUlang.any { it.id == "pulsa" && it.badgeText == "Promo" })
        assertTrue(isiUlang.any { it.id == "paket_data" && it.badgeText == "Promo" })
        assertTrue(isiUlang.any { it.id == "paket_telp_sms" })
        assertTrue(isiUlang.any { it.id == "listrik_pln" })
        assertTrue(isiUlang.any { it.id == "uang_elektronik" })
        assertTrue(isiUlang.any { it.id == "voucher_game" })
        assertTrue(isiUlang.any { it.id == "roaming" })
        assertTrue(isiUlang.any { it.id == "travel_esim" && it.badgeText == "Baru" })
        assertTrue(isiUlang.any { it.id == "esim_lokal" && it.badgeText == "Baru" })

        // Verify Tagihan count
        assertTrue(tagihan.size >= 12)
        assertTrue(tagihan.any { it.id == "pdam" && it.isRegional })
        assertTrue(tagihan.any { it.id == "bpjs" })
        assertTrue(tagihan.any { it.id == "tagihan" })
        assertTrue(tagihan.any { it.id == "telkom" })
        assertTrue(tagihan.any { it.id == "pasca_bayar" })
        assertTrue(tagihan.any { it.id == "tv_internet" })
        assertTrue(tagihan.any { it.id == "angsuran_kredit" })
        assertTrue(tagihan.any { it.id == "kartu_kredit" })
        assertTrue(tagihan.any { it.id == "biaya_pendidikan" && it.badgeText == "Promo" })
        assertTrue(tagihan.any { it.id == "pgn" })
        assertTrue(tagihan.any { it.id == "ipl" })
        assertTrue(tagihan.any { it.id == "e_invoicing" })
        assertTrue(tagihan.any { it.id == "premi_asuransi" })
    }

    @Test
    fun testPulsaDenominations() {
        val pulsa = DigitalCatalog.getAllCategories().first { it.id == "pulsa" }
        assertNotNull(pulsa)
        val nominalLabels = pulsa.defaultNominals.map { it.label }
        assertEquals(listOf("5.000", "10.000", "20.000", "50.000", "100.000", "Lainnya"), nominalLabels)
    }

    @Test
    fun testPhoneNumberSanitizationAndOperatorDetection() {
        fun sanitizePhoneNumber(raw: String): String {
            var clean = raw.replace(Regex("[^0-9+]"), "")
            if (clean.startsWith("+62")) {
                clean = "0" + clean.substring(3)
            } else if (clean.startsWith("62")) {
                clean = "0" + clean.substring(2)
            }
            return clean
        }

        fun detectOperator(phone: String): String? {
            val clean = sanitizePhoneNumber(phone)
            return when {
                clean.startsWith("0851") -> "By.U"

                clean.startsWith("0811") || clean.startsWith("0812") || clean.startsWith("0813") ||
                clean.startsWith("0821") || clean.startsWith("0822") || clean.startsWith("0852") ||
                clean.startsWith("0853") || clean.startsWith("0823") -> "Telkomsel"

                clean.startsWith("0814") || clean.startsWith("0815") || clean.startsWith("0816") ||
                clean.startsWith("0855") || clean.startsWith("0856") || clean.startsWith("0857") ||
                clean.startsWith("0858") -> "Indosat Ooredoo"

                clean.startsWith("0817") || clean.startsWith("0818") || clean.startsWith("0819") ||
                clean.startsWith("0859") || clean.startsWith("0877") || clean.startsWith("0878") -> "XL Axiata"

                clean.startsWith("0895") || clean.startsWith("0896") || clean.startsWith("0897") ||
                clean.startsWith("0898") || clean.startsWith("0899") -> "Tri (3)"

                clean.startsWith("0881") || clean.startsWith("0882") || clean.startsWith("0883") ||
                clean.startsWith("0884") || clean.startsWith("0885") || clean.startsWith("0886") ||
                clean.startsWith("0887") || clean.startsWith("0888") || clean.startsWith("0889") -> "Smartfren"

                clean.startsWith("0831") || clean.startsWith("0832") || clean.startsWith("0833") ||
                clean.startsWith("0838") -> "Axis"

                else -> null
            }
        }

        assertEquals("081234567890", sanitizePhoneNumber("+62 812-3456-7890"))
        assertEquals("081234567890", sanitizePhoneNumber("6281234567890"))
        assertEquals("081234567890", sanitizePhoneNumber("081234567890"))

        assertEquals("By.U", detectOperator("0851 5400 1234"))
        assertEquals("Telkomsel", detectOperator("+62 812 3456 7890"))
        assertEquals("Indosat Ooredoo", detectOperator("0857 1234 5678"))
        assertEquals("XL Axiata", detectOperator("0878 9988 7766"))
        assertEquals("Tri (3)", detectOperator("0896 1122 3344"))
        assertEquals("Smartfren", detectOperator("0882 5566 7788"))
        assertEquals("Axis", detectOperator("0838 1234 5678"))
    }

    @Test
    fun testDynamicNominalOptionsFromImportedProducts() {
        val importedProds = listOf(
            ProductEntity(
                name = "[Pulsa Telkomsel] Pulsa 5,000",
                category = "Pulsa",
                productType = "DIGITAL",
                sellingPrice = 7000.0,
                costPrice = 5180.0,
                providerCode = "TREG5"
            ),
            ProductEntity(
                name = "[Pulsa Telkomsel] Pulsa 10,000",
                category = "Pulsa",
                productType = "DIGITAL",
                sellingPrice = 12000.0,
                costPrice = 10200.0,
                providerCode = "TREG10"
            ),
            ProductEntity(
                name = "[Pulsa Indosat] Pulsa 5,000",
                category = "Pulsa",
                productType = "DIGITAL",
                sellingPrice = 7500.0,
                costPrice = 5200.0,
                providerCode = "IDREG5"
            ),
            ProductEntity(
                name = "[Token PLN] TOKEN PLN 20RB",
                category = "Listrik PLN",
                productType = "DIGITAL",
                sellingPrice = 23500.0,
                costPrice = 21870.0,
                providerCode = "PLN20"
            )
        )

        val tselProds = importedProds.filter {
            it.category == "Pulsa" && it.name.contains("Telkomsel")
        }
        assertEquals(2, tselProds.size)
        assertEquals("TREG5", tselProds[0].providerCode)
        assertEquals("TREG10", tselProds[1].providerCode)

        val options = tselProds.map { p ->
            NominalOption(
                label = p.name.replace("[Pulsa Telkomsel] Pulsa ", ""),
                nominalValue = p.sellingPrice,
                sellingPrice = p.sellingPrice,
                costPrice = p.costPrice,
                productEntity = p
            )
        }

        assertEquals(2, options.size)
        assertEquals("5,000", options[0].label)
        assertEquals(7000.0, options[0].sellingPrice, 0.01)
        assertEquals("TREG5", options[0].productEntity?.providerCode)
        assertEquals("10,000", options[1].label)
        assertEquals(12000.0, options[1].sellingPrice, 0.01)
        assertEquals("TREG10", options[1].productEntity?.providerCode)
    }

    @Test
    fun testPdamRegionalExtractionAndFilter() {
        fun cleanKabupatenName(name: String): String {
            var clean = name.replace(Regex("^\\[Tagihan PDAM [^\\]]+\\]\\s*"), "")
            clean = clean.replace(Regex("^(Tagihan PDAM|BAYAR TAGIHAN PDAM|BAYAR PDAM|CEK PDAM|TAGIHAN PDAM)\\s*[-:]?\\s*", RegexOption.IGNORE_CASE), "")
            clean = clean.replace(Regex("^(BAYAR TAGIHAN PDAM|TAGIHAN PDAM|BAYAR PDAM)\\s*", RegexOption.IGNORE_CASE), "")
            return clean.trim()
        }

        val pdamList = listOf(
            ProductEntity(
                name = "[Tagihan PDAM PDAM Jawa Barat] Tagihan PDAM - BAYAR TAGIHAN PDAM KAB KARAWANG",
                category = "PDAM",
                productType = "DIGITAL",
                sellingPrice = 2000.0,
                costPrice = 1800.0,
                providerCode = "BYRPDAM39"
            ),
            ProductEntity(
                name = "[Tagihan PDAM PDAM Jawa Barat] Tagihan PDAM - KOTA DEPOK",
                category = "PDAM",
                productType = "DIGITAL",
                sellingPrice = 2000.0,
                costPrice = 1800.0,
                providerCode = "CEKPDAM77"
            ),
            ProductEntity(
                name = "[Tagihan PDAM PDAM Jawa Tengah] Tagihan PDAM - KAB BANYUMAS",
                category = "PDAM",
                productType = "DIGITAL",
                sellingPrice = 2000.0,
                costPrice = 1800.0,
                providerCode = "BYRPDAM10"
            )
        )

        val jabarKabs = pdamList.filter { it.name.contains("Jawa Barat") }
            .map { cleanKabupatenName(it.name) }
        assertEquals(listOf("KAB KARAWANG", "KOTA DEPOK"), jabarKabs)

        val depokProd = pdamList.find { cleanKabupatenName(it.name) == "KOTA DEPOK" }
        assertNotNull(depokProd)
        assertEquals("CEKPDAM77", depokProd?.providerCode)
    }

    @Test
    fun testLiveNominalSearchFiltering() {
        val sampleOptions = listOf(
            NominalOption("Data ByU 10GB 5 Hari", 10000.0, 15000.0, 13000.0, quotaLabel = "10 GB", validityLabel = "5 hari"),
            NominalOption("Data ByU 25GB 30 Hari", 25000.0, 35000.0, 32000.0, quotaLabel = "25 GB", validityLabel = "30 hari"),
            NominalOption("Pulsa Telkomsel 50.000", 50000.0, 52000.0, 49800.0, quotaLabel = "50.000", validityLabel = "30 hari")
        )

        // Filter query "10GB"
        val q1 = "10GB"
        val filtered1 = sampleOptions.filter {
            it.label.contains(q1, ignoreCase = true) || it.quotaLabel.contains(q1, ignoreCase = true)
        }
        assertEquals(1, filtered1.size)
        assertEquals("Data ByU 10GB 5 Hari", filtered1.first().label)

        // Filter query "30 Hari"
        val q2 = "30 Hari"
        val filtered2 = sampleOptions.filter {
            it.label.contains(q2, ignoreCase = true) || it.validityLabel.contains(q2, ignoreCase = true)
        }
        assertEquals(2, filtered2.size)
    }

    @Test
    fun testDigitalCatalogApiMappingAndGangguanStatus() {
        val normalDto = com.denkasir.data.remote.dto.DigitalCatalogApiItemDto(
            id = "isipulsa_431",
            voucherId = 431,
            providerCode = "PLN20",
            name = "[PLN] Token PLN 20.000",
            category = "PLN",
            productType = "DIGITAL",
            costPrice = 20100.0,
            sellingPrice = 22500.0,
            recommendedSellingPrice = 22500.0,
            stock = 999.0,
            isGangguan = false
        )

        val gangguanDto = com.denkasir.data.remote.dto.DigitalCatalogApiItemDto(
            id = "isipulsa_3",
            voucherId = 3,
            providerCode = "TREG20",
            name = "[Telkomsel] 20.000",
            category = "Pulsa",
            productType = "DIGITAL",
            costPrice = 20150.0,
            sellingPrice = 22500.0,
            recommendedSellingPrice = 22500.0,
            stock = 0.0,
            isGangguan = true
        )

        val normalEntity = ProductEntity(
            id = normalDto.id,
            name = normalDto.name,
            category = normalDto.category,
            productType = "DIGITAL",
            costPrice = normalDto.costPrice,
            sellingPrice = normalDto.recommendedSellingPrice,
            stock = if (normalDto.isGangguan || normalDto.stock <= 0.0) 0.0 else 999.0,
            providerCode = normalDto.providerCode
        )

        val gangguanEntity = ProductEntity(
            id = gangguanDto.id,
            name = gangguanDto.name,
            category = gangguanDto.category,
            productType = "DIGITAL",
            costPrice = gangguanDto.costPrice,
            sellingPrice = gangguanDto.recommendedSellingPrice,
            stock = if (gangguanDto.isGangguan || gangguanDto.stock <= 0.0) 0.0 else 999.0,
            providerCode = gangguanDto.providerCode
        )

        assertFalse(normalEntity.isDigitalOutOfStock())
        assertTrue(gangguanEntity.isDigitalOutOfStock())
        assertEquals("PLN20", normalEntity.providerCode)
        assertEquals("TREG20", gangguanEntity.providerCode)
    }
}
