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
    val isCustom: Boolean = false,
    val productEntity: com.denkasir.data.local.entity.ProductEntity? = null,
    val quotaLabel: String = "",
    val validityLabel: String = "",
    val originalPrice: Double = 0.0,
    val badgeText: String = "Muraaah",
    val column1Title: String = "Kuota",
    val column2Title: String = "Masa berlaku",
    val isAvailable: Boolean = true
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
    val defaultNominals: List<NominalOption> = defaultPulseNominals(),
    val isRegional: Boolean = false
)

fun defaultPulseNominals(): List<NominalOption> = listOf(
    NominalOption("5.000", 5000.0, 7000.0, 5200.0, quotaLabel = "5.000", validityLabel = "15 hari", originalPrice = 10000.0, badgeText = "Muraaah", column1Title = "Pulsa"),
    NominalOption("10.000", 10000.0, 12000.0, 10200.0, quotaLabel = "10.000", validityLabel = "30 hari", originalPrice = 15000.0, badgeText = "Muraaah", column1Title = "Pulsa"),
    NominalOption("20.000", 20000.0, 22000.0, 20200.0, quotaLabel = "20.000", validityLabel = "30 hari", originalPrice = 25000.0, badgeText = "Muraaah", column1Title = "Pulsa"),
    NominalOption("50.000", 50000.0, 52000.0, 49800.0, quotaLabel = "50.000", validityLabel = "30 hari", originalPrice = 55000.0, badgeText = "Muraaah", column1Title = "Pulsa"),
    NominalOption("100.000", 100000.0, 102000.0, 98500.0, quotaLabel = "100.000", validityLabel = "60 hari", originalPrice = 108000.0, badgeText = "Muraaah", column1Title = "Pulsa"),
    NominalOption("Lainnya", 0.0, 0.0, 0.0, isCustom = true, quotaLabel = "Bebas", validityLabel = "-", badgeText = "Manual", column1Title = "Pulsa")
)

object DigitalCatalog {

    fun getAllCategories(): List<DigitalCategoryItem> = listOf(
        // ================= ISI ULANG (Sesuai Data Master & Scraper) =================
        DigitalCategoryItem(
            id = "pulsa",
            title = "Pulsa",
            iconRes = R.drawable.ic_digital_pulsa,
            badgeText = "Promo",
            isPromoBadge = true,
            section = DigitalSection.ISI_ULANG,
            targetNumberLabel = "Nomor Ponsel Tujuan",
            targetNumberHint = "081234567890",
            operators = listOf("Telkomsel", "Indosat Ooredoo", "XL Axiata", "Tri (3)", "Smartfren", "Axis", "By.U")
        ),
        DigitalCategoryItem(
            id = "paket_data",
            title = "Paket Data",
            iconRes = R.drawable.ic_digital_paket_data,
            badgeText = "Promo",
            isPromoBadge = true,
            section = DigitalSection.ISI_ULANG,
            targetNumberLabel = "Nomor Ponsel Tujuan",
            targetNumberHint = "081234567890",
            operators = listOf("Telkomsel Data", "Indosat Data", "XL Data", "Tri Data", "Smartfren Data", "Axis Data", "By.U Data"),
            defaultNominals = listOf(
                NominalOption("1 GB / 30 Hari", 1000.0, 15000.0, 13000.0, quotaLabel = "1 GB", validityLabel = "30 hari", originalPrice = 18000.0),
                NominalOption("3 GB / 30 Hari", 3000.0, 25000.0, 23000.0, quotaLabel = "3 GB", validityLabel = "30 hari", originalPrice = 29000.0),
                NominalOption("5 GB / 30 Hari", 5000.0, 38000.0, 35000.0, quotaLabel = "5 GB", validityLabel = "30 hari", originalPrice = 43000.0),
                NominalOption("10 GB / 30 Hari", 10000.0, 58000.0, 55000.0, quotaLabel = "10 GB", validityLabel = "30 hari", originalPrice = 65000.0),
                NominalOption("25 GB / 30 Hari", 25000.0, 95000.0, 90000.0, quotaLabel = "25 GB", validityLabel = "30 hari", originalPrice = 105000.0),
                NominalOption("Lainnya", 0.0, 0.0, 0.0, isCustom = true)
            )
        ),
        DigitalCategoryItem(
            id = "paket_telp_sms",
            title = "Paket Nelpon & SMS",
            iconRes = R.drawable.ic_phone_contact,
            badgeText = "Hemat",
            isPromoBadge = true,
            section = DigitalSection.ISI_ULANG,
            targetNumberLabel = "Nomor Ponsel Tujuan",
            targetNumberHint = "081234567890",
            operators = listOf("Telkomsel Nelpon/SMS", "Indosat Nelpon/SMS", "XL Nelpon/SMS", "Tri Nelpon/SMS", "Axis Nelpon/SMS")
        ),
        DigitalCategoryItem(
            id = "listrik_pln",
            title = "Listrik PLN",
            iconRes = R.drawable.ic_digital_listrik_pln,
            badgeText = "Token",
            isPromoBadge = false,
            section = DigitalSection.ISI_ULANG,
            targetNumberLabel = "No. Meter / ID Pelanggan",
            targetNumberHint = "Contoh: 14123456789 atau 52123456789",
            operatorLabel = "Jenis Layanan Listrik",
            operators = listOf("Token PLN Prabayar", "Tagihan Listrik Pascabayar"),
            defaultNominals = listOf(
                NominalOption("20.000", 20000.0, 22500.0, 20250.0, quotaLabel = "20.000", validityLabel = "Selamanya", originalPrice = 25000.0, column1Title = "Token"),
                NominalOption("50.000", 50000.0, 52500.0, 50250.0, quotaLabel = "50.000", validityLabel = "Selamanya", originalPrice = 56000.0, column1Title = "Token"),
                NominalOption("100.000", 100000.0, 102500.0, 100250.0, quotaLabel = "100.000", validityLabel = "Selamanya", originalPrice = 107000.0, column1Title = "Token"),
                NominalOption("200.000", 200000.0, 202500.0, 200250.0, quotaLabel = "200.000", validityLabel = "Selamanya", originalPrice = 208000.0, column1Title = "Token"),
                NominalOption("500.000", 500000.0, 502500.0, 500250.0, quotaLabel = "500.000", validityLabel = "Selamanya", originalPrice = 510000.0, column1Title = "Token"),
                NominalOption("Lainnya", 0.0, 0.0, 0.0, isCustom = true, quotaLabel = "Bebas", validityLabel = "Selamanya", column1Title = "Token")
            )
        ),
        DigitalCategoryItem(
            id = "uang_elektronik",
            title = "Uang Elektronik",
            iconRes = R.drawable.ic_digital_uang_elektronik,
            badgeText = "E-Wallet",
            isPromoBadge = false,
            section = DigitalSection.ISI_ULANG,
            targetNumberLabel = "Nomor Akun / HP E-Money",
            targetNumberHint = "081234567890",
            operatorLabel = "Penyedia E-Money",
            operators = listOf("DANA", "GoPay Penumpang", "GoPay Driver", "OVO", "ShopeePay", "LinkAja", "Maxim Driver", "Grab Driver", "Mandiri e-Toll"),
            defaultNominals = listOf(
                NominalOption("10.000", 10000.0, 12000.0, 10500.0, quotaLabel = "10.000", validityLabel = "Selamanya", originalPrice = 14000.0, column1Title = "Saldo"),
                NominalOption("20.000", 20000.0, 22000.0, 20500.0, quotaLabel = "20.000", validityLabel = "Selamanya", originalPrice = 24000.0, column1Title = "Saldo"),
                NominalOption("50.000", 50000.0, 52000.0, 50500.0, quotaLabel = "50.000", validityLabel = "Selamanya", originalPrice = 55000.0, column1Title = "Saldo"),
                NominalOption("100.000", 100000.0, 102000.0, 100500.0, quotaLabel = "100.000", validityLabel = "Selamanya", originalPrice = 106000.0, column1Title = "Saldo"),
                NominalOption("200.000", 200000.0, 202000.0, 200500.0, quotaLabel = "200.000", validityLabel = "Selamanya", originalPrice = 207000.0, column1Title = "Saldo"),
                NominalOption("Lainnya", 0.0, 0.0, 0.0, isCustom = true)
            )
        ),
        DigitalCategoryItem(
            id = "voucher_game",
            title = "Voucher Game",
            iconRes = R.drawable.ic_gamepad,
            badgeText = "Populer",
            isPromoBadge = true,
            section = DigitalSection.ISI_ULANG,
            targetNumberLabel = "User ID Game",
            targetNumberHint = "Contoh: 12345678 (Zone 2001)",
            operatorLabel = "Pilih Game",
            operators = listOf("Mobile Legends", "Free Fire", "PUBG Mobile", "Genshin Impact", "Roblox", "Steam Wallet", "Gemscool", "Higgs Domino", "Call of Duty Mobile")
        ),
        DigitalCategoryItem(
            id = "masa_aktif",
            title = "Masa Aktif",
            iconRes = R.drawable.ic_history,
            section = DigitalSection.ISI_ULANG,
            targetNumberLabel = "Nomor Ponsel Tujuan",
            targetNumberHint = "081234567890",
            operators = listOf("Three (Tri)", "Telkomsel", "Indosat", "XL", "Axis")
        ),
        DigitalCategoryItem(
            id = "aktivasi_perdana",
            title = "Aktivasi Perdana",
            iconRes = R.drawable.ic_barcode,
            section = DigitalSection.ISI_ULANG,
            targetNumberLabel = "Nomor Seri / Barcode Fisik",
            targetNumberHint = "Scan / input nomor seri fisik",
            operatorLabel = "Operator / Voucher",
            operators = listOf("Three", "Axis", "Telkomsel", "Indosat", "Smartfren", "XL")
        ),
        DigitalCategoryItem(
            id = "voucher_belanja",
            title = "Voucher Belanja",
            iconRes = R.drawable.ic_spreadsheet_file,
            section = DigitalSection.ISI_ULANG,
            targetNumberLabel = "Nomor HP Penerima Voucher",
            targetNumberHint = "081234567890",
            operatorLabel = "Penyedia Voucher",
            operators = listOf("Indomaret", "Alfamart", "Transmart")
        ),
        DigitalCategoryItem(
            id = "roaming",
            title = "Roaming",
            iconRes = R.drawable.ic_digital_roaming,
            section = DigitalSection.ISI_ULANG,
            targetNumberLabel = "Nomor HP Roaming",
            targetNumberHint = "081234567890",
            operators = listOf("Telkomsel RoaMAX", "Indosat PassPord", "XL Pass Luar Negeri", "Tri Roaming Pass")
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
            operators = listOf("Japan & Korea eSIM", "Singapore & Malaysia eSIM", "Europe 33 Countries eSIM", "USA & Canada eSIM", "Global eSIM")
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
            operators = listOf("Smartfren eSIM", "XL Axiata eSIM", "Indosat IM3 eSIM", "Telkomsel eSIM")
        ),

        // ================= TAGIHAN (Sesuai Data Master & Format Wilayah) =================
        DigitalCategoryItem(
            id = "pdam",
            title = "PDAM",
            iconRes = R.drawable.ic_digital_pdam,
            badgeText = "Resmi",
            isPromoBadge = false,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Nomor Pelanggan PDAM",
            targetNumberHint = "Masukkan ID Pelanggan PDAM (contoh: 0102030405)",
            operatorLabel = "Pilih Provinsi / Wilayah",
            operators = listOf("Jawa Barat", "Jawa Tengah", "Jawa Timur", "Jabodetabek", "Sumatera", "Sulawesi", "Kalimantan", "Bali", "Nusa Tenggara", "Kepri, Jambi, Bengkulu", "Maluku dan Papua"),
            isRegional = true
        ),
        DigitalCategoryItem(
            id = "bpjs",
            title = "BPJS",
            iconRes = R.drawable.ic_digital_premi_asuransi,
            badgeText = "Resmi",
            isPromoBadge = false,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Nomor Peserta BPJS",
            targetNumberHint = "13 digit nomor kartu BPJS",
            operatorLabel = "Jenis Layanan BPJS",
            operators = listOf("BPJS Kesehatan", "BPJS Ketenagakerjaan")
        ),
        DigitalCategoryItem(
            id = "tagihan",
            title = "Tagihan Lainnya",
            iconRes = R.drawable.ic_cash_register,
            badgeText = null,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Nomor Tagihan / ID Pelanggan",
            targetNumberHint = "Nomor kontrak / ID pelanggan",
            operatorLabel = "Penyedia Tagihan",
            operators = listOf("Telkom / IndiHome", "XL/Axis Cuanku", "Multifinance / Angsuran", "TV Berlangganan")
        ),
        DigitalCategoryItem(
            id = "telkom",
            title = "Telkom & IndiHome",
            iconRes = R.drawable.ic_digital_telkom,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Nomor Telepon / Internet",
            targetNumberHint = "02112345678",
            operators = listOf("Telkom PSTN", "IndiHome", "Speedy")
        ),
        DigitalCategoryItem(
            id = "pasca_bayar",
            title = "Pasca Bayar",
            iconRes = R.drawable.ic_digital_pasca_bayar,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Nomor HP Pasca Bayar",
            targetNumberHint = "081234567890",
            operators = listOf("Kartu Halo", "Matrix Ooredoo", "XL Prioritas", "Smartfren Postpaid")
        ),
        DigitalCategoryItem(
            id = "tv_internet",
            title = "TV Kabel & Internet",
            iconRes = R.drawable.ic_digital_tv_internet,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Nomor Pelanggan TV",
            targetNumberHint = "1012345678",
            operators = listOf("Indovision / MNC Vision", "Transvision", "First Media", "MyRepublic", "Biznet Home")
        ),
        DigitalCategoryItem(
            id = "angsuran_kredit",
            title = "Angsuran Kredit",
            iconRes = R.drawable.ic_digital_angsuran_kredit,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Nomor Kontrak Kredit",
            targetNumberHint = "Contoh: 0123456789",
            operators = listOf("FIF Group", "WOM Finance", "BAF", "Adira Finance", "Mega Finance", "Home Credit")
        ),
        DigitalCategoryItem(
            id = "kartu_kredit",
            title = "Kartu Kredit",
            iconRes = R.drawable.ic_digital_kartu_kredit,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Nomor Kartu Kredit",
            targetNumberHint = "16 digit nomor kartu",
            operators = listOf("BCA", "Mandiri", "BNI", "BRI", "CIMB Niaga", "Bank Mega")
        ),
        DigitalCategoryItem(
            id = "biaya_pendidikan",
            title = "Biaya Pendidikan",
            iconRes = R.drawable.ic_digital_biaya_pendidikan,
            badgeText = "Promo",
            isPromoBadge = true,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Nomor Pembayaran Mahasiswa",
            targetNumberHint = "Nomor induk mahasiswa / SPP",
            operators = listOf("Universitas Terbuka", "SPP Sekolah", "Bimbel Ruangguru")
        ),
        DigitalCategoryItem(
            id = "pgn",
            title = "PGN Gas",
            iconRes = R.drawable.ic_digital_pgn,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Nomor Pelanggan Gas",
            targetNumberHint = "Nomor ID pelanggan PGN",
            operators = listOf("PGN Pelanggan Rumah Tangga", "PGN Bisnis")
        ),
        DigitalCategoryItem(
            id = "ipl",
            title = "IPL & Lingkungan",
            iconRes = R.drawable.ic_digital_ipl,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Nomor Unit / Rumah",
            targetNumberHint = "Blok / Nomor rumah",
            operators = listOf("Iuran Pengelolaan Lingkungan Apartemen", "Iuran Komplek Perumahan")
        ),
        DigitalCategoryItem(
            id = "e_invoicing",
            title = "E-Invoicing Pajak",
            iconRes = R.drawable.ic_digital_e_invoicing,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Kode Billing Pajak",
            targetNumberHint = "15 digit kode e-billing",
            operators = listOf("Pajak DJP Online", "PBB Daerah", "SAMSAT PKB")
        ),
        DigitalCategoryItem(
            id = "premi_asuransi",
            title = "Premi Asuransi",
            iconRes = R.drawable.ic_digital_premi_asuransi,
            section = DigitalSection.TAGIHAN,
            targetNumberLabel = "Nomor Polis Asuransi",
            targetNumberHint = "Nomor polis asuransi terdaftar",
            operators = listOf("Prudential", "Allianz", "AXA Mandiri", "Manulife", "Generali")
        )
    )
}
