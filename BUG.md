# BUG.md - Log Pencatatan dan Penanganan Bug DenKasir

Dokumen ini mencatat seluruh potensi bug, race condition, isu performa, dan kegagalan sistem yang diidentifikasi selama pengembangan aplikasi DenKasir, beserta status dan solusinya sesuai standar `AGENTS.md`.

---

## Ringkasan Status Bug

| Total Bug | OPEN | IN_PROGRESS | RESOLVED | WONT_FIX |
|---|---|---|---|---|
| 15 | 0 | 0 | 15 | 0 |

---

## Log Detail Bug

### [BUG-001]
- **Tanggal**: 2026-09-05
- **Modul/Komponen**: Backend & Mobile Sync Engine
- **Tingkat Keparahan**: `HIGH`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**: Potensi duplikasi ID transaksi atau entitas saat kasir membuat transaksi dalam kondisi offline pada beberapa perangkat kasir secara simultan jika menggunakan ID auto-increment.
- **Dampak / Potensi Resiko**: Data transaksi saling menimpa (*overwrite*) atau gagal sinkronisasi (*conflict error*) saat push ke PostgreSQL.
- **Rekomendasi / Solusi Perbaikan**: Wajib menggunakan **UUID v4** (String 36 karakter) sebagai Primary Key di Room SQLite dan PostgreSQL.

---

### [BUG-002]
- **Tanggal**: 2026-09-05
- **Modul/Komponen**: POS & Inventory Logic (Produk Digital vs Retail)
- **Tingkat Keparahan**: `MEDIUM`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**: Penjualan pulsa/token listrik bisa gagal jika sistem memperlakukan produk digital sama seperti produk fisik yang mengharuskan pengecekan stok unit fisik (qty stok).
- **Dampak / Potensi Resiko**: Kasir tidak dapat menjual pulsa jika stok item menunjukkan 0, padahal saldo deposit pulsa server mencukupi.
- **Rekomendasi / Solusi Perbaikan**: Tambahkan enum `product_type` (`RETAIL` vs `DIGITAL`). Untuk tipe `DIGITAL`, lewati validasi stok kuantitas fisik dan alihkan ke pencatatan margin laba modal vs harga jual.

---

### [BUG-003]
- **Tanggal**: 2026-09-05
- **Modul/Komponen**: Hardware / Bluetooth Thermal Printer
- **Tingkat Keparahan**: `MEDIUM`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**: Koneksi Bluetooth SPP dapat terputus (*socket drop*) secara tiba-tiba saat printer kehabisan baterai/kertas atau di luar jangkauan sinyal, menyebabkan aplikasi kasir *hang* / crash (ANR).
- **Dampak / Potensi Resiko**: Aplikasi kasir macet dan kasir tidak tahu apakah struk berhasil dicetak atau tidak.
- **Rekomendasi / Solusi Perbaikan**: Bungkus operasi Bluetooth dalam Kotlin Coroutine `withTimeout` dan `try-catch` terisolasi, serta sediakan fallback tombol "Kirim Struk via WhatsApp" atau "Cetak Ulang".

---

### [BUG-004]
- **Tanggal**: 2026-09-05
- **Modul/Komponen**: Multi-Satuan & Stok Opname
- **Tingkat Keparahan**: `HIGH`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**: Kesalahan perhitungan selisih stok saat penjualan dilakukan dalam satuan eceran (pcs) sementara pembelian stok masuk dalam satuan kardus/renceng jika tidak memiliki basis satuan dasar (*base unit*).
- **Dampak / Potensi Resiko**: Stok sistem menjadi minus atau tidak akurat saat stok opname.
- **Rekomendasi / Solusi Perbaikan**: Semua perhitungan mutasi stok wajib dinormalisasi ke *Base Unit (satuan terkecil/pcs)* di dalam database, sedangkan satuan Dus/Renceng disimpan sebagai faktor pengali konversi.

---

### [BUG-005]
- **Tanggal**: 2026-09-05
- **Modul/Komponen**: H2H Digital Pulse Engine / OtomaX Gateway
- **Tingkat Keparahan**: `CRITICAL`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**: Potensi transaksi ganda (*double charge*) atau status gantung (*indeterminate status*) saat request tembak pulsa ke server distributor H2H mengalami timeout/koneksi lambat sementara uang kasir sudah diterima.
- **Dampak / Potensi Resiko**: Saldo deposit warung terpotong dua kali atau pelanggan komplain pulsa belum masuk padahal uang sudah dibayar.
- **Rekomendasi / Solusi Perbaikan**: Gunakan `h2h_trx_id` berbasis `UUID` unik sebagai *idempotency key* ke server H2H. Jika terjadi network timeout, set status transaksi `PENDING` (bukan langsung `FAILED`) dan buat worker/polling otomatis untuk mengecek status final transaksi beserta nomor SN ke distributor sebelum refund atau retry.

---

### [BUG-006]
- **Tanggal**: 2026-09-05
- **Modul/Komponen**: BKU & Laba Pulsa Digital
- **Tingkat Keparahan**: `HIGH`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**: Pencatatan omset pulsa Rp 100.000 (modal Rp 98.000, laba Rp 2.000) jika dicatat sebagai kas masuk murni tanpa mengurangi saldo deposit pulsa akan menyebabkan laporan arus kas riil dan laba rugi BKU menggelembung (*distorted cash flow*).
- **Dampak / Potensi Resiko**: Saldo kas riil di laci kasir tidak cocok dengan pembukuan BKU.
- **Rekomendasi / Solusi Perbaikan**: Transaksi pulsa tunai mencatat:
  1. Kas Masuk (Laci) = Rp 100.000
  2. Pengeluaran Modal Deposit Pulsa = Rp 98.000
  3. Laba Bersih = Rp 2.000
  Saldo deposit H2H di-update secara terpisah dari saldo kas fisik toko.

---

### [BUG-007]
- **Tanggal**: 2026-09-05
- **Modul/Komponen**: Android Build & D8 Dexing (Apache POI XLSX)
- **Tingkat Keparahan**: `MEDIUM`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**: Error kompilasi D8 `MethodHandle.invoke` saat memproses dependency POI & Log4j untuk ekspor XLSX jika `minSdk` diset di bawah level 26 (Android 8.0).
- **Dampak / Potensi Resiko**: Build APK gagal saat proses dexing `mergeExtDexDebug`.
- **Rekomendasi / Solusi Perbaikan**: Naikkan `minSdk = 26` pada `app/build.gradle.kts`.

---

### [BUG-008]
- **Tanggal**: 2026-09-05
- **Modul/Komponen**: Hardware / CameraX Barcode Scanner
- **Tingkat Keparahan**: `HIGH`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**: Layar scan barcode blank hitam saat dibuka karena Android `Manifest.permission.CAMERA` adalah *dangerous runtime permission* yang belum diminta melalui `ActivityResultContracts.RequestPermission()`.
- **Dampak / Potensi Resiko**: Kamera tidak menampilkan preview gambar dan scan barcode tidak dapat berjalan.
- **Rekomendasi / Solusi Perbaikan**: Tambahkan pemeriksaan izin runtime `CAMERA` sebelum menginisialisasi `ProcessCameraProvider` dan minta izin secara otomatis dengan dialog prompt sistem Android.

---

### [BUG-009]
- **Tanggal**: 2026-09-05
- **Modul/Komponen**: Android Launcher / Adaptive Icons (API 26+)
- **Tingkat Keparahan**: `MEDIUM`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**: Ikon aplikasi tidak berubah pada Android 8.0+ (MIUI / OneUI / Pixel) jika hanya menyediakan aset PNG di `mipmap-*dpi` tanpa definisi `mipmap-anydpi-v26/ic_launcher.xml` (Adaptive Icon dengan layer background dan foreground).
- **Dampak / Potensi Resiko**: Launcher HP menampilkan ikon default robot hijau Android.
- **Rekomendasi / Solusi Perbaikan**: Sediakan direktori `mipmap-anydpi-v26` berisi `ic_launcher.xml` dan `ic_launcher_round.xml` dengan layer `ic_launcher_background.xml` dan `ic_launcher_foreground.xml`.

---

### [BUG-010]
- **Tanggal**: 2026-09-05
- **Modul/Komponen**: Android AAPT2 Resource Merger & Kotlin Compiler
- **Tingkat Keparahan**: `HIGH`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**: Header file UTF-8 BOM (`0xEF, 0xBB, 0xBF`) pada beberapa file XML layout menyebabkan AAPT2 Resource Parser melempar `NullPointerException (elmName is null)`. Selain itu duplikasi file `BkuDaos.kt` menyebabkan error `Redeclaration: CashFlowDao`.
- **Dampak / Potensi Resiko**: Gradle build dan penggabungan resource layout gagal saat compile debug.
- **Rekomendasi / Solusi Perbaikan**: Format ulang file layout menjadi UTF-8 standar tanpa BOM dan konsolidasikan DAO Room ke `Daos.kt`.

---

### [BUG-011]
- **Tanggal**: 2026-09-05
- **Modul/Komponen**: Digital PPOB / Contact Picker & Sanitasi Input
- **Tingkat Keparahan**: `MEDIUM`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**: Nomor telepon yang disalin dari buku kontak pelanggan sering memuat spasi, tanda minus, atau kode negara `+62` / `62` yang dapat menggagalkan deteksi operator otomatis jika tidak dinormalisasi ke format lokal `08xx`.
- **Dampak / Potensi Resiko**: Operator salah terdeteksi dan transaksi pulsa gagal saat dikirim ke H2H gateway.
- **Rekomendasi / Solusi Perbaikan**: Lakukan sanitasi otomatis: hapus karakter non-digit, konversi prefix `+62` dan `62` menjadi `08`, dan validasi panjang minimum input.

---

### [BUG-012]
- **Tanggal**: 2026-09-05
- **Modul/Komponen**: Account & RBAC / Store Profile Fallback
- **Tingkat Keparahan**: `MEDIUM`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**: Potensi `NullPointerException` atau data profil toko kosong saat pergantian akun antara OWNER dan KASIR jika record tabel `stores` belum ter-seed pada migrasi database.
- **Dampak / Potensi Resiko**: Nama toko di header POS atau struk kasir menampilkan teks kosong atau crash saat dibuka.
- **Rekomendasi / Solusi Perbaikan**: Tambahkan `SessionManager` sebagai fallback in-memory/SharedPreferences dan sediakan seeding otomatis default store & user di `AppDatabase.kt` Room callback.
---

### [BUG-013]
- **Tanggal**: 2026-09-05
- **Modul/Komponen**: BKU & Hutang Piutang / Cash Flow Synchronization
- **Tingkat Keparahan**: `HIGH`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**: Pembayaran piutang pelanggan atau pelunasan hutang supplier jika hanya mengupdate tabel `debts` tanpa mencatat entri mutasi kas masuk/keluar ke tabel `cash_flows` akan menyebabkan saldo riil laci kas dan buku kas umum (BKU) tidak seimbang (*ledger discrepancy*).
- **Dampak / Potensi Resiko**: Uang fisik masuk dari cicilan hutang tidak tercatat di laba/rugi dan laporan mutasi kas toko.
- **Rekomendasi / Solusi Perbaikan**: Setiap pelunasan piutang otomatis memicu insert `CashFlowEntity` tipe `IN` (Penerimaan Piutang), dan pembayaran hutang supplier memicu insert `CashFlowEntity` tipe `OUT` (Pembayaran Hutang Supplier) secara atomik.

---

### [BUG-014]
- **Tanggal**: 2026-09-05
- **Modul/Komponen**: Inventory & Stock Opname / Auto-Draft Purchase Order
- **Tingkat Keparahan**: `HIGH`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**: Ketika kasir / staf menyelesaikan stok opname fisik dan menemukan produk yang stoknya habis atau menipis di bawah batas aman, ketiadaan integrasi otomatis ke modul Transaksi & Daftar Belanja mengharuskan owner mencatat ulang secara manual sehingga rentan kelupaan restock barang (*out-of-stock risk*).
- **Dampak / Potensi Resiko**: Toko kehabisan stok barang laris dan kehilangan potensi omset penjualan.
- **Rekomendasi / Solusi Perbaikan**: Tambahkan fitur konfigurasi aturan batas stok minimum owner dan generator otomatis entitas `PurchaseOrderEntity` berstatus `Draft` pada saat penyelesaian stok opname fisik.

---

### [BUG-015]
- **Tanggal**: 2026-09-05
- **Modul/Komponen**: Inventory & Product Photo Upload / SAF URI Persistence
- **Tingkat Keparahan**: `HIGH`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**: Menyimpan string `Uri` langsung dari Storage Access Framework (SAF) atau Galeri Android (`content://...`) ke database `ProductEntity.imagePath` menyebabkan `SecurityException` atau gambar gagal dimuat saat aplikasi di-restart karena izin akses URI sementara sudah kedaluwarsa.
- **Dampak / Potensi Resiko**: Thumbnail produk menjadi blank/rusak setelah aplikasi ditutup dan dibuka kembali.
- **Rekomendasi / Solusi Perbaikan**: Buat utilitas `ImageUtils.copyUriToInternalStorage()` untuk menyalin file gambar ke penyimpanan privat aplikasi (`filesDir/product_images/`) secara offline-first dan simpan path file lokal yang permanen ke database Room.
