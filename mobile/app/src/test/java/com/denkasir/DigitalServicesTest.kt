package com.denkasir

import com.denkasir.ui.pos.DigitalCatalog
import com.denkasir.ui.pos.DigitalSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitalServicesTest {

    @Test
    fun testCatalogContainsRequiredCategories() {
        val categories = DigitalCatalog.getAllCategories()
        val isiUlang = categories.filter { it.section == DigitalSection.ISI_ULANG }
        val tagihan = categories.filter { it.section == DigitalSection.TAGIHAN }

        // Verify Isi Ulang count (6 items as in Image #1)
        assertEquals(6, isiUlang.size)
        assertTrue(isiUlang.any { it.id == "pulsa" && it.badgeText == "Promo" })
        assertTrue(isiUlang.any { it.id == "paket_data" && it.badgeText == "Promo" })
        assertTrue(isiUlang.any { it.id == "uang_elektronik" })
        assertTrue(isiUlang.any { it.id == "roaming" })
        assertTrue(isiUlang.any { it.id == "travel_esim" && it.badgeText == "Baru" })
        assertTrue(isiUlang.any { it.id == "esim_lokal" && it.badgeText == "Baru" })

        // Verify Tagihan count (12 items as in Image #1)
        assertEquals(12, tagihan.size)
        assertTrue(tagihan.any { it.id == "listrik_pln" })
        assertTrue(tagihan.any { it.id == "telkom" })
        assertTrue(tagihan.any { it.id == "pasca_bayar" })
        assertTrue(tagihan.any { it.id == "pdam" })
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

        assertEquals("Telkomsel", detectOperator("+62 812 3456 7890"))
        assertEquals("Indosat Ooredoo", detectOperator("0857 1234 5678"))
        assertEquals("XL Axiata", detectOperator("0878 9988 7766"))
        assertEquals("Tri (3)", detectOperator("0896 1122 3344"))
        assertEquals("Smartfren", detectOperator("0882 5566 7788"))
        assertEquals("Axis", detectOperator("0838 1234 5678"))
    }
}