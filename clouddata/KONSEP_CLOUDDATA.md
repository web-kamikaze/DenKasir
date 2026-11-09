# KONSEP BISNIS, ARSITEKTUR MULTI-TENANT & KEAMANAN ANTI-SCRAPING
## DenKasir Cloud Data (DaaS: Database as a Service)

Dokumen ini berisi cetak biru (*blueprint*) konseptual mengenai pemisahan produk, strategi monetisasi bulanan, arsitektur multi-tenant ala Alfamart/Indomaret, serta sistem keamanan anti-scraping berstandar tinggi (ala Alibaba/Taobao).

---

## 1. Latar Belakang & Model Bisnis

### 1.1. Penyakit Utama Digitalisasi Toko Ritel/Kelontong
Mayoritas pemilik toko kelontong, sembako, dan minimarket mandiri di Indonesia enggan atau gagal beralih ke aplikasi kasir modern bukan karena software-nya sulit, melainkan karena **beban awal input master data barang**:
- Rata-rata toko kelontong memiliki **3.000 hingga 9.000+ jenis barang (SKU)**.
- Mengetik nama barang, mencari/memotret gambar, menentukan kategori, dan mendaftarkan barcode secara manual membutuhkan waktu berminggu-minggu hingga berbulan-bulan, atau biaya operator jutaan rupiah.
- Akhirnya pedagang menyerah dan kembali ke pembukuan manual/kalkulator.

### 1.2. Solusi DenKasir: Pemisahan Bisnis Mandiri
DenKasir memecah model bisnis menjadi dua pilar produk yang dapat disewa secara mandiri maupun bundling:

```
+-------------------------------------------------------------+
|                        DENKASIR ECOSYSTEM                   |
+------------------------------+------------------------------+
|   PILAR 1: APLIKASI KASIR    |    PILAR 2: DATABASE CLOUD   |
|   (Software POS Engine)      |    (DaaS & Master Catalog)   |
|   Rp 45.000 / bulan          |    Rp 20.000 / bulan         |
+------------------------------+------------------------------+
| - 100% Offline-First (Room)  | - Akses 9.000+ SKU Produk    |
| - Cetak Struk 58/80mm        | - Foto Produk Siap Pakai     |
| - Scan Barcode Kamera & SPP  | - Barcode Standar Pabrikan   |
| - Laporan Kas & Laba-Rugi    | - Update Rutin Varian Baru   |
| - Operasional Kasir Harian   | - Multi-Tenant Auto Sync     |
+------------------------------+------------------------------+
|            TOTAL PAKET BUNDLING: Rp 65.000 / BULAN          |
|      (Jauh di bawah kompetitor Rp 150.000 - Rp 300.000)      |
+-------------------------------------------------------------+
```

### 1.3. Nilai Jual & Target Pelanggan
1. **Sewa Aplikasi Kasir (Rp 45.000/bulan)**:
   - Target: Toko pakaian, bengkel, warkop, jasa, kuliner (yang memiliki produk unik sendiri dan tidak memerlukan barcode pabrik).
2. **Sewa Database Cloud Produk (Rp 20.000/bulan)**:
   - Target: Toko kelontong, minimarket, toko sembako, toko kosmetik/atk.
   - Slogan: *"Toko Anda Buka & Siap Jualan dalam 5 Menit. Cukup Scan Barcode, Nama & Foto Muncul Otomatis. Anda Hanya Perlu Ketik Stok & Harga Jual Toko."*
3. **Margin & Skalabilitas**:
   - Master katalog dan aset gambar hanya disimpan satu kali di server pusat (sangat hemat storage dan bandwidth VPS).
   - Biaya infrastruktur server sangat rendah (~Rp 300rb–500rb/bulan) untuk melayani ribuan toko.
   - Tingkat retensi tinggi (*very low churn rate*) karena memindahkan data inventori toko ke aplikasi lain sangat menyulitkan pedagang.

---

## 2. Arsitektur Multi-Tenant: Pola Alfamart / Indomaret

Untuk memastikan 1 master database pusat dapat digunakan oleh ribuan toko dengan nilai stok, harga modal, dan harga jual yang berbeda-beda, sistem menerapkan pemisahan logis antara **Identitas Barang** dan **Kepemilikan Barang**.

### 2.1. Lapisan Data: Global Master vs Toko Privat

```
                     +---------------------------------------+
                     |    CLOUD: GLOBAL MASTER CATALOG       |
                     |  (Universal, Read-Only, Shared 1x)    |
                     +---------------------------------------+
                     | - Barcode (Unique Key)                |
                     | - Nama Resmi Produk                   |
                     | - Kategori & Merk / Pabrik            |
                     | - Path Gambar Thumbnail               |
                     | - HET / Rekomendasi Harga Nasional    |
                     +---------------------------------------+
                                         |
                                         | Referensi Barcode
                                         v
         +-------------------------------+-------------------------------+
         |                                                               |
+---------------------------------+             +---------------------------------+
|   INVENTORI TOKO A (Garut)      |             |   INVENTORI TOKO B (Jakarta)    |
|   (Private Tenant Database)     |             |   (Private Tenant Database)     |
+---------------------------------+             +---------------------------------+
| - Store ID : TOKO_001           |             | - Store ID : TOKO_002           |
| - Barcode  : 8998866627764      |             | - Barcode  : 8998866627764      |
| - Stok Fisik   : 120 pcs        |             | - Stok Fisik   : 45 pcs         |
| - Harga Modal  : Rp 2.900       |             | - Harga Modal  : Rp 3.100       |
| - Harga Jual   : Rp 3.500       |             | - Harga Jual   : Rp 3.800       |
+---------------------------------+             +---------------------------------+
```

### 2.2. Keunggulan Arsitektur Ini
1. **Zero Redundancy**: 1.000 toko tidak menduplikasi teks dan foto 9.000 produk di cloud. Penyimpanan server sangat efisien.
2. **Kemandirian Toko 100%**: Toko A di daerah pelosok bebas menaikkan harga jual karena biaya kirim ekspedisi tinggi, tanpa mempengaruhi toko lain di perkotaan.
3. **Isolasi Data**: Transaksi dan stok di Toko A tidak pernah bocor atau menggeser stok di Toko B.
4. **Rekomendasi Harga Otomatis (Smart Pricing Suggestion)**: Saat kasir scan barcode baru, sistem menampilkan saran harga pasaran (misal Rp 3.500 - Rp 4.000) untuk mempermudah pemilik toko baru menentukan margin keuntungan.

### 2.3. Alur Pengalaman Pengguna (User Journey di Kasir)
1. **Toko Baru Registrasi**: Inventori toko lokal di HP kasir berstatus **0 produk**.
2. **Barang Masuk (Kulakan Datang)**:
   - Kasir scan barcode produk menggunakan kamera atau barcode scanner.
   - Aplikasi menanyakan ke API Cloud: `GET /catalog/product/{barcode}`.
   - Layar kasir menampilkan Nama Produk, Kategori, dan Foto secara instan.
3. **Input Cepat (Hanya 3 Kolom)**:
   - Kasir mengisi: **Harga Modal (Beli)**, **Harga Jual**, dan **Jumlah Stok Masuk**.
   - Klik **[Simpan ke Toko Saya]**.
4. **Siap Transaksi Offline**:
   - Produk tersebut resmi tercatat di database lokal HP kasir (Room Database).
   - Transaksi penjualan harian berjalan 100% offline tanpa delay jaringan.

---

## 3. Sistem Registrasi & Autentikasi No-Hassle

Pedagang pasar/kelontong enggan mengisi formulir pendaftaran panjang dengan email dan password rumit.

### 3.1. Alur Pendaftaran Sederhana
1. Pengguna memasukkan **Nomor Handphone (WhatsApp)**.
2. Server mengirimkan **Kode Verifikasi OTP 4/6 digit** via WhatsApp API resmi / SMS.
3. Pengguna memasukkan OTP, lalu mengisi **Nama Toko** dan **Kota/Kabupaten**.
4. Toko langsung aktif dan siap digunakan.

### 3.2. Aspek Keamanan Akun
- **1 Nomor HP = 1 Akun Toko Utama**.
- **Hardware Device Binding**: Akun dikunci pada *Hardware Android ID* HP kasir tersebut menggunakan Android Keystore.
- Akun tidak dapat digunakan secara paralel di emulator atau puluhan perangkat sekaligus untuk scraping data.

---

## 4. Sistem Keamanan Anti-Scraping Berlapis (Standar Alibaba / Taobao)

Katalog 9.000+ produk, barcode, dan foto adalah aset intelektual (*Intellectual Property*) bernilai tinggi. Sistem mengadopsi mekanisme proteksi ala Taobao/Alibaba untuk mencegah bot/kompetitor menyedot database.

### 4.1. Prinsip Utama: Penghapusan Endpoint Bulk-Dump
- Server **dilarang menyediakan endpoint massal** seperti `GET /products?limit=10000`.
- Data hanya diberikan secara **On-Demand**: 1 barcode per request saat kasir benar-benar melakukan scanning fisik.

### 4.2. Deteksi Anomali Kecepatan (50 Produk dalam Waktu Singkat)
- **Kapasitas Manusia Normal**: Kasir terlatih membutuhkan waktu minimal 2 hingga 3 detik per barang (mengambil barang, membidik barcode, menaruh barang). Memindai 50 barang butuh waktu minimal 100 - 150 detik.
- **Ambang Batas Keamanan**: Jika sebuah akun meminta 50 produk dalam durasi di bawah 30 detik:
  - Aliran data API seketika dihentikan sementara.
  - Sistem server memicu tantangan interaktif: **TRIGGER CAPTCHA HIGH-SECURITY**.

### 4.3. Menghancurkan Bot Layanan 2Captcha & Auto-Screenshot
Layanan pemecah captcha seperti 2Captcha / CapMonster mengandalkan dua metode: OCR Vision AI pada screenshot atau human farm di mana screenshot dikirim ke pekerja manusia.

Sistem DenKasir mematahkan keduanya melalui 4 pilar pertahanan:

```
+-----------------------------------------------------------------------------+
|               ARSITEKTUR PERTAHANAN ANTI-2CAPTCHA & BOT                     |
+-----------------------------------------------------------------------------+
| [1] Android FLAG_SECURE                                                     |
|     Window Captcha diberi flag FLAG_SECURE. Tool adb screencap, screenshot, |
|     maupun background screen recording hanya menghasilkan LAYAR HITAM POLOS.|
|                                                                             |
| [2] Ultra-Short Token Lifetime (TTL 7 - 10 Detik)                           |
|     Layanan 2Captcha butuh 15-30 detik untuk kirim gambar & tunggu pekerja. |
|     Token puzzle kita kedaluwarsa dalam 7 detik -> Jawaban pasti EXPIRED.   |
|                                                                             |
| [3] Biometric Gesture & Micro-Tremor Detection (Ala GeeTest / Taobao)       |
|     - Jari manusia: memiliki akselerasi, deselerasi, dan micro-tremor getar.|
|     - Bot script: koordinat lurus sempurna (Y=0) atau kecepatan konstan.    |
|     - Sensor Gyroscope HP: getaran fisik perangkat ikut diverifikasi.       |
|                                                                             |
| [4] Canvas Obfuscation & Dynamic Puzzle Rendering                           |
|     Puzzle dirender di canvas lokal secara dinamis, bukan file JPG mentah.  |
+-----------------------------------------------------------------------------+
```

### 4.4. Kebijakan Penalti Bertingkat (3x Gagal = Dibatasi)
- **Gagal Percobaan 1**: Captcha di-refresh dengan koordinat target baru dan jeda waktu 3 detik.
- **Gagal Percobaan 2**: Peringatan keras: *"Terdeteksi aktivitas mencurigakan. Sisa kesempatan verifikasi: 1 kali."*
- **Gagal Percobaan 3**: **AKUN DIBATASI (RESTRICTED MODE)**:
  - Akses ke API Cloud Database dikunci otomatis selama **24 jam**.
  - Log audit mencatat: Nomor HP, Device ID, IP Address, dan jejak waktu anomali.
  - **Efek Terhadap Kasir Asli (Zero Disruption di Meja Kasir)**:
    - Kasir tetap bisa menjual barang lokal yang sudah tersimpan di HP.
    - Muncul tombol resmi: *"Hubungi Bantuan WhatsApp Admin"*. Admin dapat memverifikasi dan membuka kunci dalam 1 klik jika terbukti bukan scraper.
    - Pelaku bot/kompetitor tidak akan berani menghubungi WhatsApp admin karena identitas nomor HP mereka tercatat.

### 4.5. Lapisan Keamanan Tambahan
1. **SSL Pinning**: Menolak inspeksi proxy seperti Burp Suite, Charles Proxy, atau Fiddler.
2. **Request Signing HMAC-SHA256 via C++ Native (.so via NDK)**: Rumus tanda tangan enkripsi disimpan di binary C++, bukan di Kotlin bytecode, sehingga sangat sulit di-reverse engineer menggunakan JADX / APKTool.
3. **Invisible Steganography Watermark**: Thumbnail produk disisipi watermark piksel tak kasat mata berisi `STORE_ID`. Jika kompetitor mencuri gambar, identitas akun pembocor dapat dibuktikan secara matematis.
4. **Honeypot / Canary Barcode**: Kode barcode perangkap non-komersial ditanam di sistem. Bot yang menebak barcode berurutan akan langsung terjebak dan diblokir permanen.

---

## 5. Ringkasan Desain Skema Basis Data (Data Model)

```
[global_products] (Master Pusat)
- barcode: VARCHAR(32) [PK]
- product_name: VARCHAR(255)
- category: VARCHAR(100)
- brand: VARCHAR(100)
- image_path: VARCHAR(255)
- het_price: NUMERIC(12,2)
- is_active: BOOLEAN
- created_at, updated_at

[stores] (Penyewa / Tenant)
- id: VARCHAR(36) [PK]
- phone_number: VARCHAR(20) [UNIQUE]
- store_name: VARCHAR(150)
- owner_name: VARCHAR(100)
- device_hardware_id: VARCHAR(100)
- pos_subscription_expires_at: TIMESTAMP
- catalog_subscription_expires_at: TIMESTAMP
- status: ENUM('ACTIVE', 'RESTRICTED', 'SUSPENDED')

[store_inventory] (Inventori Lokal per Toko)
- id: VARCHAR(36) [PK]
- store_id: VARCHAR(36) [FK -> stores.id]
- barcode: VARCHAR(32) [FK -> global_products.barcode]
- cost_price: NUMERIC(12,2)
- selling_price: NUMERIC(12,2)
- stock_quantity: NUMERIC(12,2)
- min_stock_alert: NUMERIC(12,2)
- updated_at: TIMESTAMP

[security_anomaly_logs] (Pencatatan Bot & Anomali)
- id: VARCHAR(36) [PK]
- store_id: VARCHAR(36)
- phone_number: VARCHAR(20)
- event_type: VARCHAR(50)
- request_count_30s: INT
- captcha_attempts: INT
- action_taken: VARCHAR(50)
- client_ip: VARCHAR(45)
- created_at: TIMESTAMP
```

---

## 6. Kesimpulan & Roadmap Implementasi

Konsep ini memberikan 3 keunggulan strategis yang belum dimiliki kasir lain di pasar UMKM Indonesia:
1. **Model Bisnis Fleksibel & Murah**: Toko bisa mulai dengan kasir Rp 45.000/bln atau bundling katalog Rp 65.000/bln.
2. **Kenyamanan Ekstrem Bagi Pengguna Jujur**: Bebas dari pusing input ribuan barang. Scan barcode, isi harga & stok, langsung siap jualan.
3. **Pertahanan Data Kelas Tinggi**: Aset 9.000+ produk terlindungi aman dari scraping kompetitor melalui pertahanan bot bertingkat ala e-commerce raksasa.
