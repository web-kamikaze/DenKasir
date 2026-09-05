package com.denkasir.ui.pos

import com.denkasir.R

enum class DigitalSection {
    ISI_ULANG,
    TAGIHAN
}

data class NominalOption(
    val label: String,
    val nominalValue: Double,
    val sellingPrice: Double,
    val costPrice: Double,
    val isCustom: Boolean = false
)

data class DigitalCategoryItem(
    val id: String,
    val title: String,
    val iconRes: Int,
    val badgeText: String? = null,
    val isPromoBadge: Boolean = true,
    val section: DigitalSection,
    val targetNumberLabel: String = "Nomor Tujuan",
    val targetNumberHint: String = "081234567890",
    val operatorLabel: String = "Operator",
    val operators: List<String> = listOf("Telkomsel", "Indosat Ooredoo", "XL Axiata", "Tri (3)", "Smartfren", "Axis"),
    val defaultNominals: List<NominalOption> = defaultPulseNominals()
)

fun defaultPulseNominals(): List<NominalOption> = listOf(
    NominalOption("5.000", 5000.0, 7000.0, 5200.0),
    NominalOption("10.000", 10000.0, 12000.0, 10200.0),
    NominalOption("20.000", 20000.0, 22000.0, 20200.0),
    NominalOption("50.000", 50000.0, 52000.0, 49800.0),
    NominalOption("100.000", 100000.0, 102000.0, 98500.0),
    NominalOption("Lainnya", 0.0, 0.0, 0.0, isCustom = true)
)

object DigitalCatalog {

    fun getAllCategories(): List<DigitalCategoryItem> = listOf(
        // ================= ISI ULANG (Image #1) =================
        DigitalCategoryItem(
            id = "pulsa",
            title = "Pulsa",
            iconRes = R.drawable.ic_digital_pulsa,
            badgeText = "Promo",
            isPromoBadge = true,
            section = DigitalSection.ISI_ULANG,
            targetNumberLabel = "Nomor Tujuan",
            targetNumberHint = "081234567890",
            operators = listOf("Telkomsel", "Indosat Ooredoo", "XL Axiata", "Tri (3)", "Smartfren", "Axis")
        ),
        DigitalCategoryItem(
            id = "paket_data",
            title = "Paket Data",
            iconRes = R.drawable.ic_digital_paket_data,
            badgeText = "Promo",
            isPromoBadge = true,
            section = DigitalSection.ISI_ULANG,
            targetNumberLabel = "Nomor Tujuan",
            targetNumberHint = "081234567890",
            operators = listOf("Telkomsel Data", "Indosat Data Freedom", "XL Data Xtra", "Tri Data AON", "Smartfren Unlimited", "Axis Bronet"),
            defaultNominals = listOf(
                NominalOption("1 GB / 30 Hari", 1000.0, 15000.0, 13000.0),
                NominalOption("3 GB / 30 Hari", 3000.0, 25000.0, 23000.0),
                NominalOption("5 GB / 30 Hari", 5000.0, 38000.0, 35000.0),
                NominalOption("10 GB / 30 Hari", 10000.0, 58000.0, 55000.0),
                NominalOption("25 GB / 30 Hari", 25000.0, 95000.0, 90000.0),
                NominalOption("Lainnya", 0.0, 0.0, 0.0, isCustom = true)
            )
        ),
        DigitalCategoryItem(
            id = "uang_elektronik",
            title = "Uang Elektronik",
            iconRes = R.drawable.ic_digital_uang_elektronik,
            badgeText = null,
            section = DigitalSection.ISI_ULANG,
            targetNumberLabel = "Nomor Akun / HP E-Money",
            targetNumberHint = "081234567890",
            operatorLabel = "Penyedia E-Money",
            operators = listOf("DANA", "GoPay Customer", "GoPay Driver", "OVO", "ShopeePay", "LinkAja", "Maxim Driver", "Grab Driver"),
            defaultNominals = listOf(
                NominalOption("10.000", 10000.0, 12000.0, 10500.0),
                NominalOption("20.000", 20000.0, 22000.0, 20500.0),
                NominalOption("50.000", 50000.0, 52000.0, 50500.0),
                NominalOption("100.000", 100000.0, 102000.0, 100500.0),
                NominalOption("200.000", 200000.0, 202000.0, 200500.0),
                NominalOption("Lainnya", 0.0, 0.0, 0.0, isCustom = true)
            )
        ),
        DigitalCategoryItem(
            id = "roaming",
            title = "Roaming",
            iconRes = R.drawable.ic_digital_roaming,
            badgeText = null,
            section = DigitalSection.ISI_ULANG,
            targetNumberLabel = "Nomor HP Roaming",
            targetNumberHint = "081234567890",
            operators = listOf("Telkomsel RoaMAX", "Indosat PassPord", "XL Pass Luar Negeri", "Tri Roaming Pass"),
            defaultNominals = listOf(
                NominalOption("Singapura/Malaysia 3 Hari", 3000.0, 50000.0, 47000.0),
                NominalOption("Asia Pasifik 7 Hari", 7000.0, 120000.0, 115000.0),
                NominalOption("Umroh / Haji 12 Hari", 12000.0, 275000.0, 265000.0),
                NominalOption("Global Worldwide 30 Hari", 30000.0, 390000.0, 375000.0),
                NominalOption("Lainnya", 0.0, 0.0, 0.0, isCustom = true)
            )
        ),
        DigitalCategoryItem(
            id = "travel_esim",
            title = "Travel ESIM",
            iconRes = R.drawable.ic_digital_travel_esim,
            badgeText = "Baru",
            isPromoBadge = false,
            section = DigitalSection.ISI_ULANG,
            targetNumberLabel = "Email / No HP Pembeli",
            targetNumberHint = "081234567890",
            operatorLabel = "Negara Tujuan",
            operators = listOf("Japan & Korea eSIM", "Singapore & Malaysia eSIM", "Europe 33 Countries eSIM", "USA & Canada eSIM", "Global eSIM"),
            defaultNominals = listOf(
                NominalOption("5 GB / 7 Hari", 5000.0, 85000.0, 78000.0),
                NominalOption("10 GB / 15 Hari", 10000.0, 145000.0, 135000.0),
                NominalOption("20 GB / 30 Hari", 20000.0, 225000.0, 210000.0),
                NominalOption("Unlimited 7 Hari", 70000.0, 120000.0, 110000.0),
                NominalOption("Lainnya", 0.0, 0.0, 0.0, isCustom = true)
            )
        ),
        DigitalCategoryItem(
            id = "esim_lokal",
            title = "ESIM Lokal",
            iconRes = R.drawable.ic_digital_esim_lokal,
            badgeText = "Baru",
            isPromoBadge = false,
            section = DigitalSection.ISI_ULANG,
            targetNumberLabel = "Nomor KTP / NIK & No HP",
            targetNumberHint = "081234567890",
            operators = listOf("Smartfren eSIM", "XL Axiata eSIM", "Indosat IM3 eSIM", "Telkomsel eSIM"),
            defaultNominals = listOf(
                NominalOption("Starter 15 GB / 30 Hari", 15000.0, 45000.0, 40000.0),
                NominalOption("Freedom 30 GB / 30 Hari", 30000.0, 75000.0, 70000.0),
                NominalOption("Unlimited Nonstop 30 Hari", 50000.0, 95000.0, 88000.0),
                NominalOption("Lainnya", 0.0, 0.0, 0.0, isCustom = true)
            )
        ),

        // ================= TAGIHAN (Image #1) =================
        DigitalCategoryItem(
            id = "listrik_pln",
            title = "Listrik PLN",
            iconRes = R.drawable.ic_digital_listrik_pln,
            badgeText = null,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Nomor Meter / ID Pelanggan",
            targetNumberHint = "Contoh: 14123456789",
            operatorLabel = "Jenis Layanan PLN",
            operators = listOf("PLN Prabayar (Token)", "PLN Pascabayar (Tagihan Bulanan)", "PLN Non Taglis"),
            defaultNominals = listOf(
                NominalOption("20.000", 20000.0, 22500.0, 20250.0),
                NominalOption("50.000", 50000.0, 52500.0, 50250.0),
                NominalOption("100.000", 100000.0, 102500.0, 100250.0),
                NominalOption("200.000", 200000.0, 202500.0, 200250.0),
                NominalOption("500.000", 500000.0, 502500.0, 500250.0),
                NominalOption("Lainnya", 0.0, 0.0, 0.0, isCustom = true)
            )
        ),
        DigitalCategoryItem(
            id = "telkom",
            title = "Telkom",
            iconRes = R.drawable.ic_digital_telkom,
            badgeText = null,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Nomor Telepon / ID IndiHome",
            targetNumberHint = "Contoh: 0218765432 / 1234567890",
            operatorLabel = "Layanan Telkom",
            operators = listOf("Telkom IndiHome", "Telepon Rumah PSTN"),
            defaultNominals = listOf(
                NominalOption("Cek & Bayar Tagihan", 0.0, 3000.0, 1000.0, isCustom = true)
            )
        ),
        DigitalCategoryItem(
            id = "pasca_bayar",
            title = "Pasca Bayar",
            iconRes = R.drawable.ic_digital_pasca_bayar,
            badgeText = null,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Nomor HP Pasca Bayar",
            targetNumberHint = "08112345678",
            operators = listOf("Telkomsel Halo", "Indosat Postpaid (Matrix)", "XL Prioritas", "Smartfren Postpaid", "Tri Postpaid"),
            defaultNominals = listOf(
                NominalOption("Bayar Tagihan Bulanan", 0.0, 3000.0, 1000.0, isCustom = true)
            )
        ),
        DigitalCategoryItem(
            id = "pdam",
            title = "PDAM",
            iconRes = R.drawable.ic_digital_pdam,
            badgeText = null,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Nomor Pelanggan Air PDAM",
            targetNumberHint = "Contoh: 0102030405",
            operatorLabel = "Wilayah PDAM",
            operators = listOf("PDAM DKI Jakarta PAM JAYA", "PDAM Tirta Moedal Semarang", "PDAM Surabaya Surya Sembada", "PDAM Kota Bandung Tirtawening", "PDAM Kab Bogor Tirta Kahuripan", "PDAM Wilayah Lainnya"),
            defaultNominals = listOf(
                NominalOption("Bayar Tagihan Air", 0.0, 3500.0, 1500.0, isCustom = true)
            )
        ),
        DigitalCategoryItem(
            id = "tv_internet",
            title = "TV Kabel & Internet",
            iconRes = R.drawable.ic_digital_tv_internet,
            badgeText = null,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Nomor Pelanggan TV / Internet",
            targetNumberHint = "Contoh: 1002345678",
            operatorLabel = "Penyedia Internet / TV",
            operators = listOf("IndiHome", "First Media", "MyRepublic", "Biznet Home", "MNC Vision / Play", "Transvision", "K-Vision"),
            defaultNominals = listOf(
                NominalOption("Bayar Tagihan Bulanan", 0.0, 3500.0, 1500.0, isCustom = true)
            )
        ),
        DigitalCategoryItem(
            id = "angsuran_kredit",
            title = "Angsuran Kredit",
            iconRes = R.drawable.ic_digital_angsuran_kredit,
            badgeText = null,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Nomor Kontrak Cicilan",
            targetNumberHint = "Contoh: 2021008899",
            operatorLabel = "Perusahaan Leasing / Multifinance",
            operators = listOf("FIFGROUP", "BAF (Bussan Auto Finance)", "Adira Finance", "WOM Finance", "Mega Auto Finance", "OTO Kredit Motor/Mobil", "Home Credit", "Kredit Plus"),
            defaultNominals = listOf(
                NominalOption("Bayar Cicilan Angsuran", 0.0, 4000.0, 1500.0, isCustom = true)
            )
        ),
        DigitalCategoryItem(
            id = "kartu_kredit",
            title = "Kartu Kredit",
            iconRes = R.drawable.ic_digital_kartu_kredit,
            badgeText = null,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "16 Digit Nomor Kartu Kredit",
            targetNumberHint = "Contoh: 4111222233334444",
            operatorLabel = "Bank Penerbit Kartu",
            operators = listOf("BCA Credit Card", "Mandiri Kartu Kredit", "BRI Card", "BNI Credit Card", "CIMB Niaga Card", "Bank Mega Card", "Permata Bank Card"),
            defaultNominals = listOf(
                NominalOption("Bayar Tagihan Kartu Kredit", 0.0, 7500.0, 5000.0, isCustom = true)
            )
        ),
        DigitalCategoryItem(
            id = "biaya_pendidikan",
            title = "Biaya Pendidikan",
            iconRes = R.drawable.ic_digital_biaya_pendidikan,
            badgeText = "Promo",
            isPromoBadge = true,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Nomor Induk Siswa / Mahasiswa / Kode Bayar",
            targetNumberHint = "Contoh: 2023019900",
            operatorLabel = "Institusi Pendidikan",
            operators = listOf("Universitas Terbuka (UT)", "SPP Sekolah & Yayasan", "Universitas Gadjah Mada (UGM)", "Universitas Indonesia (UI)", "Institusi Bimbel & Kursus"),
            defaultNominals = listOf(
                NominalOption("Bayar Uang Kuliah / SPP", 0.0, 3000.0, 1000.0, isCustom = true)
            )
        ),
        DigitalCategoryItem(
            id = "pgn",
            title = "PGN",
            iconRes = R.drawable.ic_digital_pgn,
            badgeText = null,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Nomor Pelanggan Gas PGN",
            targetNumberHint = "Contoh: 1000123456",
            operatorLabel = "Penyedia Gas",
            operators = listOf("PGN (Perusahaan Gas Negara)", "Pertagas Niaga"),
            defaultNominals = listOf(
                NominalOption("Bayar Tagihan Gas PGN", 0.0, 3000.0, 1000.0, isCustom = true)
            )
        ),
        DigitalCategoryItem(
            id = "ipl",
            title = "IPL",
            iconRes = R.drawable.ic_digital_ipl,
            badgeText = null,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Nomor Unit / Rumah / ID Penghuni",
            targetNumberHint = "Contoh: TOWER-A-1205",
            operatorLabel = "Pengelola Kawasan / Properti",
            operators = listOf("IPL Apartemen & Real Estate", "Iuran Pengelolaan Lingkungan Perumahan", "Iuran Keamanan & Kebersihan RW"),
            defaultNominals = listOf(
                NominalOption("Bayar Iuran Pengelolaan Lingkungan", 0.0, 2500.0, 1000.0, isCustom = true)
            )
        ),
        DigitalCategoryItem(
            id = "e_invoicing",
            title = "E-Invoicing",
            iconRes = R.drawable.ic_digital_e_invoicing,
            badgeText = null,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Kode Billing / ID Transaksi",
            targetNumberHint = "Contoh: 012345678901234",
            operatorLabel = "Jenis E-Billing",
            operators = listOf("E-Billing Pajak DJP", "Penerimaan Negara Bukan Pajak (PNBP)", "Bea Cukai Cukai & Impor", "SAMSAT Pajak Kendaraan"),
            defaultNominals = listOf(
                NominalOption("Bayar E-Billing Resmi", 0.0, 3500.0, 1500.0, isCustom = true)
            )
        ),
        DigitalCategoryItem(
            id = "premi_asuransi",
            title = "Premi Asuransi",
            iconRes = R.drawable.ic_digital_premi_asuransi,
            badgeText = null,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Nomor Polis / Nomor VA Asuransi",
            targetNumberHint = "Contoh: 0001234567890",
            operatorLabel = "Perusahaan Asuransi / Jaminan",
            operators = listOf("BPJS Kesehatan", "BPJS Ketenagakerjaan", "Prudential Life Assurance", "Allianz Indonesia", "AXA Mandiri Financial", "AIA Financial", "Manulife Indonesia"),
            defaultNominals = listOf(
                NominalOption("Bayar Iuran / Premi Asuransi", 0.0, 3000.0, 1000.0, isCustom = true)
            )
        )
    )
}