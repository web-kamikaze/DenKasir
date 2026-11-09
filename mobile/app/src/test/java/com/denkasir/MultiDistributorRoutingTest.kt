package com.denkasir

import com.denkasir.data.local.entity.DigitalConfigEntity
import com.denkasir.utils.DistributorRule
import com.denkasir.utils.SmsGatewayHelper
import org.junit.Assert.*
import org.junit.Test

/**
 * Pengujian Unit untuk Logika Unik Perutean Multi-Distributor PPOB & Pulsa:
 * Distributor 1: Pulsa (PIN 1111, Center 081111111111)
 * Distributor 2: Kuota / Paket Data (PIN 2222, Center 082222222222)
 * Distributor 3: Token PLN / Listrik (PIN 3333, Center 083333333333)
 */
class MultiDistributorRoutingTest {

    @Test
    fun testJsonSerializationAndDeserialization() {
        val rules = listOf(
            DistributorRule(
                id = "dist-1",
                name = "Distributor 1 (Pulsa Murah)",
                category = "Pulsa",
                centerNumber = "081111111111",
                pin = "1111",
                smsTemplate = SmsGatewayHelper.TEMPLATE_DOT,
                smsDelimiter = "."
            ),
            DistributorRule(
                id = "dist-2",
                name = "Distributor 2 (Kuota Jumbo)",
                category = "Paket Data",
                centerNumber = "082222222222",
                pin = "2222",
                smsTemplate = SmsGatewayHelper.TEMPLATE_HASH,
                smsDelimiter = "#"
            ),
            DistributorRule(
                id = "dist-3",
                name = "Distributor 3 (Server Listrik PLN)",
                category = "PLN",
                centerNumber = "083333333333",
                pin = "3333",
                smsTemplate = SmsGatewayHelper.TEMPLATE_DOT,
                smsDelimiter = "."
            )
        )

        val json = SmsGatewayHelper.distributorRulesToJson(rules)
        assertTrue(json.contains("Distributor 1"))
        assertTrue(json.contains("1111"))
        assertTrue(json.contains("082222222222"))
        assertTrue(json.contains("PLN"))

        val parsed = SmsGatewayHelper.parseDistributorRules(json)
        assertEquals(3, parsed.size)
        assertEquals("Distributor 1 (Pulsa Murah)", parsed[0].name)
        assertEquals("1111", parsed[0].pin)
        assertEquals("081111111111", parsed[0].centerNumber)
        assertEquals("Distributor 2 (Kuota Jumbo)", parsed[1].name)
        assertEquals("2222", parsed[1].pin)
        assertEquals("Distributor 3 (Server Listrik PLN)", parsed[2].name)
        assertEquals("3333", parsed[2].pin)
    }

    @Test
    fun testUserScenarioThreeDifferentDistributorsAndPins() {
        val rules = listOf(
            DistributorRule(
                id = "dist-1",
                name = "Distributor 1 Pulsa",
                category = "Pulsa",
                centerNumber = "081111111111",
                pin = "1111",
                smsTemplate = SmsGatewayHelper.TEMPLATE_DOT,
                smsDelimiter = "."
            ),
            DistributorRule(
                id = "dist-2",
                name = "Distributor 2 Kuota Data",
                category = "Paket Data",
                centerNumber = "082222222222",
                pin = "2222",
                smsTemplate = SmsGatewayHelper.TEMPLATE_DOT,
                smsDelimiter = "."
            ),
            DistributorRule(
                id = "dist-3",
                name = "Distributor 3 Token PLN",
                category = "PLN",
                centerNumber = "083333333333",
                pin = "3333",
                smsTemplate = SmsGatewayHelper.TEMPLATE_HASH,
                smsDelimiter = "#"
            )
        )

        // 1. Transaksi Pulsa -> Harus terarahkan ke Distributor 1 (Center 081111111111 & PIN 1111)
        val matchedPulsa = SmsGatewayHelper.findMatchingDistributor(
            rules = rules,
            category = "Pulsa",
            defaultName = "Default Server",
            defaultPin = "9999",
            defaultCenter = "089999999999"
        )
        assertEquals("Distributor 1 Pulsa", matchedPulsa.name)
        assertEquals("081111111111", matchedPulsa.centerNumber)
        assertEquals("1111", matchedPulsa.pin)

        val pulsaCommand = SmsGatewayHelper.formatSmsCommand("S10", "081234567890", matchedPulsa.pin, matchedPulsa.smsTemplate, matchedPulsa.smsDelimiter)
        assertEquals("S10.081234567890.1111", pulsaCommand)

        // 2. Transaksi Kuota Data -> Harus terarahkan ke Distributor 2 (Center 082222222222 & PIN 2222)
        val matchedData = SmsGatewayHelper.findMatchingDistributor(
            rules = rules,
            category = "Paket Data",
            defaultName = "Default Server",
            defaultPin = "9999",
            defaultCenter = "089999999999"
        )
        assertEquals("Distributor 2 Kuota Data", matchedData.name)
        assertEquals("082222222222", matchedData.centerNumber)
        assertEquals("2222", matchedData.pin)

        val dataCommand = SmsGatewayHelper.formatSmsCommand("DATA10", "085678901234", matchedData.pin, matchedData.smsTemplate, matchedData.smsDelimiter)
        assertEquals("DATA10.085678901234.2222", dataCommand)

        // 3. Transaksi Token Listrik PLN -> Harus terarahkan ke Distributor 3 (Center 083333333333 & PIN 3333, Format Pagar #)
        val matchedPln = SmsGatewayHelper.findMatchingDistributor(
            rules = rules,
            category = "Listrik PLN",
            defaultName = "Default Server",
            defaultPin = "9999",
            defaultCenter = "089999999999"
        )
        assertEquals("Distributor 3 Token PLN", matchedPln.name)
        assertEquals("083333333333", matchedPln.centerNumber)
        assertEquals("3333", matchedPln.pin)

        val plnCommand = SmsGatewayHelper.formatSmsCommand("PLN20", "14123456789", matchedPln.pin, matchedPln.smsTemplate, matchedPln.smsDelimiter)
        assertEquals("PLN20#14123456789#3333", plnCommand)
    }

    @Test
    fun testFallbackToDefaultWhenNoMatchingCategory() {
        val rules = listOf(
            DistributorRule(
                id = "dist-1",
                name = "Distributor 1 Pulsa",
                category = "Pulsa",
                centerNumber = "081111111111",
                pin = "1111"
            )
        )

        // Kategori PDAM tidak terdaftar di rute khusus -> Fallback ke default
        val matchedPdam = SmsGatewayHelper.findMatchingDistributor(
            rules = rules,
            category = "Air PDAM",
            defaultName = "Server Distributor Cadangan",
            defaultPin = "0000",
            defaultCenter = "081234567890,085678901234"
        )

        assertEquals("default", matchedPdam.id)
        assertEquals("Server Distributor Cadangan", matchedPdam.name)
        assertEquals("081234567890", matchedPdam.centerNumber) // Mengambil nomor center default pertama
        assertEquals("0000", matchedPdam.pin)
    }

    @Test
    fun testWildcardSemuaCategoryRule() {
        val rules = listOf(
            DistributorRule(
                id = "dist-pulsa",
                name = "Distributor Pulsa",
                category = "Pulsa",
                centerNumber = "081111111111",
                pin = "1111"
            ),
            DistributorRule(
                id = "dist-all",
                name = "Distributor Serba Ada",
                category = "SEMUA",
                centerNumber = "087777777777",
                pin = "7777"
            )
        )

        // Game tidak ada rute khusus -> Harus jatuh ke Distributor Serba Ada ('SEMUA')
        val matchedGame = SmsGatewayHelper.findMatchingDistributor(
            rules = rules,
            category = "Voucher Game",
            defaultName = "Default",
            defaultPin = "0000",
            defaultCenter = "081234567890"
        )

        assertEquals("Distributor Serba Ada", matchedGame.name)
        assertEquals("087777777777", matchedGame.centerNumber)
        assertEquals("7777", matchedGame.pin)
    }

    @Test
    fun testEmptyJsonReturnsEmptyListSafely() {
        assertTrue(SmsGatewayHelper.parseDistributorRules(null).isEmpty())
        assertTrue(SmsGatewayHelper.parseDistributorRules("").isEmpty())
        assertTrue(SmsGatewayHelper.parseDistributorRules("invalid json").isEmpty())
    }

    @Test
    fun testDigitalConfigEntityStoresDistributorRulesJson() {
        val rules = listOf(
            DistributorRule(name = "Dist 1", category = "Pulsa", centerNumber = "081111", pin = "1234")
        )
        val json = SmsGatewayHelper.distributorRulesToJson(rules)
        val config = DigitalConfigEntity(
            trxMode = SmsGatewayHelper.MODE_SMS_CENTER,
            distributorRulesJson = json
        )

        assertEquals(json, config.distributorRulesJson)
        val parsed = SmsGatewayHelper.parseDistributorRules(config.distributorRulesJson)
        assertEquals(1, parsed.size)
        assertEquals("Dist 1", parsed[0].name)
    }
}
