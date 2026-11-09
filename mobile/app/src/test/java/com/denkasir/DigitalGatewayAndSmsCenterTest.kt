package com.denkasir

import com.denkasir.data.local.entity.DigitalConfigEntity
import com.denkasir.utils.SmsGatewayHelper
import org.junit.Assert.*
import org.junit.Test

class DigitalGatewayAndSmsCenterTest {

    @Test
    fun testFormatSmsCommandWithBracketedTargetNumberAndCode() {
        val cmd = SmsGatewayHelper.formatSmsCommand(
            productCode = "BYU1",
            targetNumber = "085154001234 (BYU1)",
            pin = "1234",
            template = SmsGatewayHelper.TEMPLATE_DOT
        )
        assertEquals("BYU1.085154001234.1234", cmd)

        val cmdPdam = SmsGatewayHelper.formatSmsCommand(
            productCode = "CEKPDAM34",
            targetNumber = "10203040 (KABUPATEN BOGOR - CEKPDAM34)",
            pin = "1234",
            template = SmsGatewayHelper.TEMPLATE_DOT
        )
        assertEquals("CEKPDAM34.10203040.1234", cmdPdam)
    }

    @Test
    fun testFormatSmsCommandDot() {
        val cmd = SmsGatewayHelper.formatSmsCommand(
            productCode = "S10",
            targetNumber = "081234567890",
            pin = "1234",
            template = SmsGatewayHelper.TEMPLATE_DOT
        )
        assertEquals("S10.081234567890.1234", cmd)
    }

    @Test
    fun testFormatSmsCommandHash() {
        val cmd = SmsGatewayHelper.formatSmsCommand(
            productCode = "PLN20",
            targetNumber = "14123456789",
            pin = "4321",
            template = SmsGatewayHelper.TEMPLATE_HASH
        )
        assertEquals("PLN20#14123456789#4321", cmd)
    }

    @Test
    fun testFormatSmsCommandSpace() {
        val cmd = SmsGatewayHelper.formatSmsCommand(
            productCode = "DANA50",
            targetNumber = "085678901234",
            pin = "9988",
            template = SmsGatewayHelper.TEMPLATE_SPACE
        )
        assertEquals("BELI DANA50 085678901234 9988", cmd)
    }

    @Test
    fun testParseAndJoinSmsCenterList() {
        val rawCsv = "081234567890, 085678901234, +6287812345678, 081234567890"
        val parsed = SmsGatewayHelper.parseSmsCenterList(rawCsv)

        assertEquals(3, parsed.size) // deduplicated
        assertTrue(parsed.contains("081234567890"))
        assertTrue(parsed.contains("085678901234"))
        assertTrue(parsed.contains("+6287812345678"))

        val joined = SmsGatewayHelper.joinSmsCenterList(parsed)
        assertEquals("081234567890,085678901234,+6287812345678", joined)
    }

    @Test
    fun testIsValidCenterNumber() {
        assertTrue(SmsGatewayHelper.isValidCenterNumber("081234567890"))
        assertTrue(SmsGatewayHelper.isValidCenterNumber("+628567890123"))
        assertTrue(SmsGatewayHelper.isValidCenterNumber("628781234567"))

        assertFalse(SmsGatewayHelper.isValidCenterNumber(""))
        assertFalse(SmsGatewayHelper.isValidCenterNumber("12345")) // too short (<8)
        assertFalse(SmsGatewayHelper.isValidCenterNumber("abcdefghijk")) // not a number
        assertFalse(SmsGatewayHelper.isValidCenterNumber("081234567890123456789")) // too long (>16)
    }

    @Test
    fun testDigitalConfigEntityDefaultValues() {
        val cfg = DigitalConfigEntity()
        assertEquals("API_H2H", cfg.trxMode)
        assertEquals("", cfg.smsCenterNumbers)
        assertEquals("[KODE].[TUJUAN].[PIN]", cfg.smsTemplate)
        assertEquals(".", cfg.smsDelimiter)
    }

    @Test
    fun testDigitalConfigEntityMultiCenter() {
        val centers = listOf("081234567890", "085678901234", "087812345678")
        val joined = SmsGatewayHelper.joinSmsCenterList(centers)

        val cfg = DigitalConfigEntity(
            trxMode = SmsGatewayHelper.MODE_AUTO_FALLBACK,
            smsCenterNumbers = joined,
            smsTemplate = SmsGatewayHelper.TEMPLATE_DOT,
            balance = 1500000.0
        )

        assertEquals(SmsGatewayHelper.MODE_AUTO_FALLBACK, cfg.trxMode)
        val loadedCenters = SmsGatewayHelper.parseSmsCenterList(cfg.smsCenterNumbers)
        assertEquals(3, loadedCenters.size)
        assertEquals("081234567890", loadedCenters[0])
        assertEquals("085678901234", loadedCenters[1])
        assertEquals("087812345678", loadedCenters[2])
    }

    @Test
    fun testDualSimAndAutoSendConfig() {
        val cfg = DigitalConfigEntity(
            preferredSimSlot = 0, // SIM 1
            autoSendSms = true
        )
        assertEquals(0, cfg.preferredSimSlot)
        assertTrue(cfg.autoSendSms)

        val cfgSim2 = DigitalConfigEntity(
            preferredSimSlot = 1, // SIM 2
            autoSendSms = false
        )
        assertEquals(1, cfgSim2.preferredSimSlot)
        assertFalse(cfgSim2.autoSendSms)
    }

    @Test
    fun testDistributorRuleWithDualSimSlot() {
        val rules = listOf(
            com.denkasir.utils.DistributorRule(
                name = "Distributor 1 Pulsa (Telkomsel)",
                category = "Pulsa",
                centerNumber = "081111111111",
                pin = "1111",
                simSlot = 0 // Wajib SIM 1 (Telkomsel)
            ),
            com.denkasir.utils.DistributorRule(
                name = "Distributor 2 Kuota (Indosat)",
                category = "Paket Data",
                centerNumber = "085555555555",
                pin = "2222",
                simSlot = 1 // Wajib SIM 2 (Indosat)
            ),
            com.denkasir.utils.DistributorRule(
                name = "Distributor 3 PLN",
                category = "PLN",
                centerNumber = "087777777777",
                pin = "3333",
                simSlot = -1 // Ikuti default
            )
        )

        // 1. Uji Serialisasi JSON & Deserialisasi
        val json = SmsGatewayHelper.distributorRulesToJson(rules)
        val loaded = SmsGatewayHelper.parseDistributorRules(json)
        assertEquals(3, loaded.size)
        assertEquals(0, loaded[0].simSlot)
        assertEquals(1, loaded[1].simSlot)
        assertEquals(-1, loaded[2].simSlot)

        // 2. Uji Routing & Pemilihan SIM Otomatis
        val matchedPulsa = SmsGatewayHelper.findMatchingDistributor(
            rules = loaded,
            category = "Pulsa",
            defaultSimSlot = -1
        )
        assertEquals(0, matchedPulsa.simSlot) // Otomatis mengarah ke SIM 1
        assertEquals("081111111111", matchedPulsa.centerNumber)

        val matchedData = SmsGatewayHelper.findMatchingDistributor(
            rules = loaded,
            category = "Paket Data",
            defaultSimSlot = -1
        )
        assertEquals(1, matchedData.simSlot) // Otomatis mengarah ke SIM 2
        assertEquals("085555555555", matchedData.centerNumber)

        val matchedPln = SmsGatewayHelper.findMatchingDistributor(
            rules = loaded,
            category = "Listrik PLN",
            defaultSimSlot = 0 // Default toko adalah SIM 1
        )
        assertEquals(-1, matchedPln.simSlot) // Ikuti default toko
    }
}
