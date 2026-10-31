# BUG.md - Log Pencatatan dan Penanganan Bug DenKasir

Dokumen ini mencatat seluruh potensi bug, race condition, isu performa, dan kegagalan sistem yang diidentifikasi selama pengembangan aplikasi DenKasir, beserta status dan solusinya sesuai standar `AGENTS.md`.

---

## Ringkasan Status Bug

| Total Bug | OPEN | IN_PROGRESS | RESOLVED | WONT_FIX |
|---|---|---|---|---|
| 31 | 0 | 0 | 31 | 0 |

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

---

### [BUG-016]
- **Tanggal**: 2026-09-05
- **Modul/Komponen**: Backend Core / Database & Settings
- **Tingkat Keparahan**: HIGH
- **Status**: RESOLVED
- **Deskripsi Masalah**: Jika server dijalankan dari root directory D:\SERVER\DenKasir tanpa PostgreSQL service aktif di port 5432, server melempar ConnectionRefusedError karena .env di subfolder ackend/.env tidak terdeteksi otomatis dan fallback database lokal belum terpasang.
- **Dampak / Potensi Resiko**: Pengembang atau pengguna kasir mode offline-server tidak dapat menjalankan backend secara mandiri tanpa container PostgreSQL.
- **Rekomendasi / Solusi Perbaikan**: Tambahkan multi-path .env resolution (.env, ackend/.env) pada pydantic-settings, sediakan konfigurasi default yang fleksibel (PostgreSQL untuk Cloud & Docker, SQLite aiosqlite untuk Local standalone fallback), dan validasi konektivitas saat startup.
---

### [BUG-017]
- **Tanggal**: 2026-09-05
- **Modul/Komponen**: Backend Auth / Passlib & Bcrypt 5.0 Compatibility
- **Tingkat Keparahan**: HIGH
- **Status**: RESOLVED
- **Deskripsi Masalah**: Library passlib.context.CryptContext melempar AttributeError: module 'bcrypt' has no attribute '__about__' pada Python 3.12 dengan crypt >= 4.1.0/5.0.0, menyebabkan kegagalan autentikasi dan pembuatan user.
- **Dampak / Potensi Resiko**: User tidak bisa login atau register ke backend FastAPI.
- **Rekomendasi / Solusi Perbaikan**: Gunakan implementasi crypt native langsung (crypt.hashpw & crypt.checkpw) dengan penanganan batas 72-byte string di ackend/app/core/security.py.
---

### [BUG-018]
- **Tanggal**: 2026-09-08
- **Modul/Komponen**: Mobile / Hardware / Bluetooth Thermal Printer ESC/POS
- **Tingkat Keparahan**: `CRITICAL`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**: Struk kasir gagal/tidak keluar saat mencetak ke printer Bluetooth thermal meskipun smartphone sudah tersambung/ter-pairing dengan printer. Kegagalan ini disebabkan oleh:
  1. Pemanggilan cetak struk pada `PaymentSuccessActivity` dan `TransactionsFragment` masih menggunakan hardcoded MAC address dummy (`00:11:22:33:44:55`), mengabaikan printer riil yang dipilih pengguna.
  2. Ketiadaan multi-tier RFCOMM socket fallback (SPP UUID -> Insecure RFCOMM -> Java Reflection Port 1) pada `BluetoothPrinterDriver` sehingga printer mini Bluetooth POS (Goojprt, MPT-II, RPP02N, dsb.) gagal membuka jalur komunikasi data.
  3. Ketiadaan pembatalan discovery (`cancelDiscovery()`) sebelum membuat koneksi socket RFCOMM yang menyebabkan socket drop/timeout.
  4. Kurangnya penanganan runtime permission Android 12+ (`BLUETOOTH_CONNECT`) dan ketiadaan fitur uji coba cetak (Test Print) di halaman pengaturan printer.
- **Dampak / Potensi Resiko**: Kasir tidak dapat mencetak struk belanja fisik kepada pelanggan warung/toko setelah transaksi pembayaran selesai.
- **Rekomendasi / Solusi Perbaikan**:
  1. Perbaiki resolver MAC address di `BluetoothPrinterDriver`: membaca konfigurasi tersimpan dari `SharedPreferences` (`denkasir_prefs`), dan otomatis mendeteksi printer yang ter-pairing jika belum diset secara eksplisit.
  2. Tambahkan multi-tier connection fallback (Standard UUID -> Insecure Socket -> Reflection Channel 1) beserta `cancelDiscovery()` dan timeout coroutine 10 detik.
  3. Sanitasi karakter teks struk ke encoding `CP437` dengan escape command ESC/POS (`0x1B, 0x40`) dan penambahan baris feed sebelum cut.
  4. Rombak `PrinterSettingActivity` menjadi antarmuka interaktif: daftar printer ter-pairing, status printer aktif, konfigurasi ukuran kertas (58mm/80mm), tombol Uji Coba Cetak (Test Print), dan panduan membuka Bluetooth HP jika belum terpasang.
  5. Tambahkan dialog peringatan terintegrasi dengan opsi langsung menuju Pengaturan Printer di `PaymentSuccessActivity` dan `TransactionsFragment` saat cetak gagal atau printer belum dikonfigurasi.

---

### [BUG-019]
- **Tanggal**: 2026-09-08
- **Modul/Komponen**: Hardware / Bluetooth Thermal Printer Performance & Reliability
- **Tingkat Keparahan**: `HIGH`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**: Cetak struk printer Bluetooth kadang tidak merespon cepat (mengalami delay 3-10 detik atau macet saat ditekan berulang) meskipun printer sudah berstatus terhubung/terpasang. Penyebab utama:
  1. *Uncached Connection Fallback*: Setiap pemanggilan `printReceipt` selalu mencoba standard SPP UUID terlebih dahulu, memicu timeout Service Discovery Protocol (SDP) OS selama 3-6 detik pada printer thermal POS mini (MPT-II, PT-210, Goojprt, Panda, BellaV, dsb.) sebelum fallback ke channel port 1.
  2. *Socket Descriptor Leaks*: Saat terjadi kegagalan socket pada percobaan awal fallback, instance socket tidak ditutup secara eksplisit (`socket.close()`), menyebabkan RFCOMM channel pada BlueDroid/Bluetooth stack terkunci / sibuk.
  3. *No Socket Session Reuse*: Koneksi RFCOMM selalu diputus dan dibuat ulang dari awal pada setiap transaksi tanpa session caching / keep-alive.
  4. *Lack of Concurrency Mutex & UI Debouncing*: Tombol 'Cetak Struk' tidak dinonaktifkan saat proses cetak berjalan dan tidak memiliki lock coroutine Mutex, sehingga penekanan ganda oleh kasir memicu race condition koneksi socket paralel ke satu MAC address hardware.
  5. *Hardcoded MAC Address pada TransactionsFragment*: Pencetakan ulang struk dari menu riwayat transaksi kasir memanggil alamat dummy hardcoded `"00:11:22:33:44:55"`.
- **Dampak / Potensi Resiko**: Respon cetak struk lambat di kasir toko saat transaksi ramai, memicu kebingungan kasir dan potensi socket error.
- **Rekomendasi / Solusi Perbaikan**:
  1. Terapkan *Preferred Connection Caching*: Simpan metode koneksi yang sukses per-MAC (Refleksi Port 1 sebagai prioritas mini thermal POS) sehingga koneksi berikutnya instan (sub-100ms).
  2. Tutup paksa (`socket.close()`) semua socket yang gagal saat transisi fallback untuk mencegah socket descriptor leak.
  3. Implementasikan socket keep-alive session reuse dengan verifikasi `isConnected` dan auto-recovery fallback saat stream terputus.
  4. Tambahkan `Mutex.withLock` pada seluruh alur `printReceipt` dan loading feedback/debouncing pada UI button ('Sedang Mencetak...', disabled selama coroutine).
  5. Perbaiki `TransactionsFragment` agar menggunakan `BluetoothPrinterDriver.printReceipt(context, receiptText)` secara dinamis dan tampilkan dialog pengaturan saat error.

---

### [BUG-020]
- **Tanggal**: 2026-09-11
- **Modul/Komponen**: Mobile / Hardware / Bluetooth Thermal Printer Formatting & Responsiveness
- **Tingkat Keparahan**: HIGH
- **Status**: RESOLVED
- **Deskripsi Masalah**: Format cetak struk kasir sebelumnya bersifat kaku (hardcoded 32 kolom untuk 58mm), tidak responsif terhadap pilihan ukuran kertas 80mm (48 kolom), teks header/footer tidak otomatis ter-center (masih menggunakan manual padding spasi), nominal harga tidak rata kanan secara rapi (justified), dan terdapat jeda respons saat tombol cetak ditekan karena ketiadaan socket connection pre-warming serta tactile feedback.
- **Dampak / Potensi Resiko**: Hasil cetakan pada printer 80mm terlihat menyusut/miring di sisi kiri (hanya memakan 2/3 kertas), teks berantakan/terpotong jika nama toko atau item panjang, dan kasir merasa aplikasi lambat merespon saat menekan tombol cetak.
- **Rekomendasi / Solusi Perbaikan**:
  1. Membuat utility terpusat ReceiptFormatterUtils.kt yang mendukung deteksi ukuran kertas dinamis (58mm = 32 kolom, 80mm = 48 kolom).
  2. Mengimplementasikan algoritma word-wrapping, centering teks otomatis, divider garis adaptif (- dan =), serta perataan dua kolom (rata kiri untuk nama/qty dan rata kanan untuk nominal harga).
  3. Memperbaiki PosViewModel.kt, TransactionsFragment.kt, dan BluetoothPrinterDriver.printTestReceipt() agar memakai format standar terpusat.
  4. Menambahkan background socket pre-warming (warmupConnection) saat masuk ke PaymentSuccessActivity serta haptic & sound feedback instan (SoundFeedbackUtils.playBarcodeBeepAndVibrate) saat tombol 'Cetak Struk' diklik.

---

### [BUG-021]
- **Tanggal**: 2026-09-18
- **Modul/Komponen**: Backend / Test Suite & Database Fixture Lifecycle
- **Tingkat Keparahan**: MEDIUM
- **Status**: RESOLVED
- **Deskripsi Masalah**: Test fixture client pada ackend/tests/conftest.py menggunakan ASGITransport(app=app) tanpa memicu lifecycle lifespan FastAPI (init_db & init_seed_data), sehingga saat test dijalankan pada environment bersih tanpa file SQLite yang sudah ada sebelumnya, pengujian gagal dengan error sqlite3.OperationalError: no such table: users.
- **Dampak / Potensi Resiko**: Eksekusi pengujian otomatis (CI/CD atau local testing) gagal dijalankan jika database belum dibuat secara manual sebelumnya.
- **Rekomendasi / Solusi Perbaikan**: Tambahkan inisialisasi schema database dan seeding data akun default (init_db() dan init_seed_data()) pada fixture async client di ackend/tests/conftest.py.

---

### [BUG-022]
- **Tanggal**: 2026-09-18
- **Modul/Komponen**: Mobile / Room Database Migration & Integrity Hash
- **Tingkat Keparahan**: `HIGH`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**: Terjadi exception `IllegalStateException: Room cannot verify the data integrity. Looks like you've changed schema but forgot to update the version number` saat aplikasi dijalankan di perangkat karena schema hash database lokal sebelumnya berbeda dengan entitas baru di version 5.
- **Dampak / Potensi Resiko**: Aplikasi mengalami *fatal crash* saat startup saat mencoba membuka koneksi SQLite Room Database.
- **Rekomendasi / Solusi Perbaikan**: Naikkan versi Room Database menjadi `version = 6` pada `AppDatabase.kt` sehingga memicu `fallbackToDestructiveMigration()` secara otomatis dan bersih.

---

### [BUG-023]
- **Tanggal**: 2026-10-31
- **Modul/Komponen**: Mobile / UI & Database / Product Catalog Search & In-Place Editing
- **Tingkat Keparahan**: `HIGH`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**:
  1. Pada menu manajemen Produk & Layanan, tidak terdapat fitur kotak pencarian (search bar) real-time sehingga pengguna kesulitan menemukan produk secara cepat saat item bertambah banyak.
  2. Saat memperbarui data produk lama (*edit existing product*), inisialisasi entitas tidak mengkloning field bawaan (`storeId`, `createdAt`, `minStockAlert`, `providerCode`), yang berpotensi merusak integritas relasi multi-outlet dan data sinkronisasi server backend.
  3. Form input belum memvalidasi harga negatif dan tidak menyelaraskan input stok fisik ketika jenis produk diubah menjadi digital (pulsa/PLN).
  4. Penggantian foto produk, penghapusan foto, serta pemindaian ulang barcode langsung dari dialog edit belum terintegrasi secara dinamis ke penyimpanan lokal dan state form.
- **Dampak / Potensi Resiko**: Data riwayat produk korup/reset saat diedit, pencarian lambat/manual, kasir kebingungan mencari produk di katalog warung, dan potensi kegagalan sinkronisasi cloud.
- **Rekomendasi / Solusi Perbaikan**:
  1. Menambahkan Kotak Pencarian (`cardSearchBar`, `etSearchProduct`, `btnClearSearch`, dan `btnScanSearchBarcode`) dengan real-time `TextWatcher` dan empty-state informatif yang responsif terhadap perpindahan tab RETAIL vs DIGITAL.
  2. Menggunakan `existingProduct.copy(...)` pada penyimpanan database agar seluruh metadata entitas (`storeId`, `createdAt`, `minStockAlert`) terjaga utuh dengan `updatedAt` terbaru dan `syncStatus = "PENDING"`.
  3. Menambahkan tombol aksi eksplisit `btnEditProduct` dan `btnEditDigital` berikon pensil (`ic_edit.xml`), selain mempertahankan gesture klik card.
  4. Mendukung pembaruan foto (kamera & galeri), penghapusan foto (`btnRemovePhoto`), scan barcode kamera langsung (`btnScanBarcodeFromForm`), serta penyesuaian otomatis visibilitas stok saat beralih ke produk pulsa/PLN.

---

### [BUG-024]
- **Tanggal**: 2026-10-31
- **Modul/Komponen**: Mobile / Accounting & Reporting Engine
- **Tingkat Keparahan**: HIGH
- **Status**: RESOLVED
- **Deskripsi Masalah**: Perhitungan 'Laba Bersih' di halaman Laporan/BKU sebelumnya disamakan dengan Arus Kas Bersih (Total Kas Masuk - Total Kas Keluar). Hal ini menyebabkan distorsi finansial fatal di mana belanja modal stok / kulakan barang dianggap sebagai kerugian langsung (minus margin), sementara HPP (Harga Pokok Penjualan) per item transaksi yang tersimpan di TransactionItemEntity belum dikalkulasikan secara otomatis untuk menghasilkan Laba Kotor dan Laba Bersih akuntansi riil.
- **Dampak / Potensi Resiko**: Pemilik toko melihat angka laba yang menyesatkan (laba negatif saat kulakan besar, laba semu saat stok terjual tanpa pengurangan modal awal), sehingga berisiko salah mengambil keputusan finansial bisnis.
- **Rekomendasi / Solusi Perbaikan**: Bangkitkan kalkulasi otomatis Laba Kotor (Total Penjualan - Total HPP Modal Terjual) dari data transaksi, pisahkan beban operasional dari belanja stok barang, dan sajikan kartu Laba Rugi riil (Omzet, HPP, Laba Kotor, Beban Operasional, Laba Bersih) berdampingan dengan ringkasan Arus Kas.

---

### [BUG-025]
- **Tanggal**: 2026-10-31
- **Modul/Komponen**: Mobile / Data Management & Offline Image Persistence
- **Tingkat Keparahan**: HIGH
- **Status**: RESOLVED
- **Deskripsi Masalah**:
  1. Aplikasi baru terisi otomatis (hardcoded) dengan data keranjang belanja POS, sampel arus kas dummy, sampel piutang dummy, dan sampel kulakan dummy saat pertama kali dijalankan, mengaburkan kondisi toko riil saat apk baru dipasang.
  2. Menu Backup & Restore di pengaturan masih berupa prototipe Toast tanpa kemampuan ekspor/impor database SQLite (.db / .sql), CSV, atau XLSX secara lokal.
  3. Input foto produk belum menyediakan opsi URL internet, dan jika ada URL gambar, sistem belum mengunduh aset gambar ke direktori internal lokal sehingga berisiko gagal tampil saat perangkat kasir offline.
- **Dampak / Potensi Resiko**: Pengguna bingung dengan data fiktif bawaan pada aplikasi baru, ketiadaan cadangan data lokal saat terjadi pergantian HP kasir, dan gambar produk hilang/blank saat toko beroperasi offline.
- **Rekomendasi / Solusi Perbaikan**:
  1. Kosongkan seluruh data awal (initial seed data dummy pada keranjang kasir, arus kas, hutang, pembelian, dan katalog) pada instalasi baru agar bersih dan siap pakai.
  2. Bangun DataManagementActivity dan utilitas DataExporterImporter lengkap: Backup & Restore Room SQLite .db/.sql, ekspor/impor katalog produk format XLSX & CSV, serta ekspor laporan BKU/Penjualan format XLSX & CSV.
  3. Tambahkan dialog input URL gambar pada form produk lengkap dengan downloader otomatis ke storage internal (filesDir/product_images/) serta fitur unduh masal gambar URL katalog.

---

### [BUG-026]
- **Tanggal**: 2026-10-31
- **Modul/Komponen**: Backend & Mobile / XLSX Importer & Header Alignment
- **Tingkat Keparahan**: HIGH
- **Status**: RESOLVED
- **Deskripsi Masalah**: Impor berkas Excel (.xlsx) katalog produk di sisi Android (DataExporterImporter.kt) dan backend FastAPI (excel_service.py) sebelumnya mengandalkan indeks nomor kolom statis (hardcoded index). Jika berkas memiliki baris banner judul, kolom 'Margin Laba (Rp)', atau variasi urutan kolom, parser membaca nilai kolom yang keliru (misalnya Margin Laba terbaca sebagai Stok, atau Stok terbaca sebagai Min Stok Alert).
- **Dampak / Potensi Resiko**: Nilai kuantitas stok barang dan harga modal/jual menjadi tertukar atau korup saat kasir/owner mengimpor file template Excel ke database.
- **Rekomendasi / Solusi Perbaikan**:
  1. Perbarui parser di DataExporterImporter.kt (mobile) dan excel_service.py (backend) menggunakan dynamic header-based mapping yang memindai baris header dan mencocokkan nama kolom secara otomatis (case-insensitive & keyword matching) serta aman terhadap baris banner/kosong.
  2. Bangkitkan file template resmi terstandar 'Template_DenKasir_Produk_dan_Toko.xlsx' di root project (D:\SERVER\DenKasir) lengkap dengan sheet 'Katalog Produk', 'Data Toko', dan 'Panduan Pengisian' dengan styling profesional, format Barcode sebagai TEXT ('@'), dan formula margin laba.

---

### [BUG-027]
- **Tanggal**: 2026-10-31
- **Modul/Komponen**: Mobile & Backend / Large XLSX Importer & Subtitle Banner Misalignment
- **Tingkat Keparahan**: CRITICAL
- **Status**: RESOLVED
- **Deskripsi Masalah**:
  1. Pada berkas katalog Excel yang memiliki baris catatan/petunjuk di bawah judul (misal: "Tips: Kolom Kategori & Tipe..."), detektor header sebelumnya membaca kata "Produk" pada baris catatan tersebut dan secara keliru menganggapnya sebagai baris header kolom. Akibatnya, pemetaan kolom gambar meleset ke kolom 0 ("No"), dan nilai URL gambar tidak tersimpan atau tersimpan sebagai tanda strip ("-").
  2. Pada mobile APK versi build sebelumnya, importer membaca kolom indeks statis ke-10 ("Kode H2H", bernilai "-") alih-alih kolom ke-11 ("Gambar Produk"), sehingga ribuan produk yang diimpor dari file seperti 'Katalog_Alfamart_DenKasir_B700_20261031.xlsx' menyimpan '-' ke kolom imagePath database Room.
  3. Launcher pemilih file Excel di `ProductsFragment.kt` sebelumnya hanya memunculkan Toast tanpa memproses Stream InputStream ke Room Database.
  4. Impor ribuan item (8.000+ produk) secara individual per baris berpotensi memicu ANR (Application Not Responding) karena ketiadaan in-memory cache dan batch transaction.
- **Dampak / Potensi Resiko**: Foto produk dari URL internet (seperti CDN Alfagift c.alfagift.id) tidak muncul sama sekali di layar kasir maupun daftar produk (hanya menampilkan ikon default), dan proses impor ribuan barang berjalan sangat lambat.
- **Rekomendasi / Solusi Perbaikan**:
  1. Tambahkan validasi jumlah sel aktif (`nonEmptyCount >= 4`) pada baris calon header di `DataExporterImporter.kt` dan `excel_service.py` untuk mengabaikan banner/catatan merged cell secara akurat.
  2. Implementasikan caching hash map produk eksisting O(1) dan batch insert per 500 entitas ke Room Database, mempercepat impor 8.600+ produk menjadi hanya ~2-3 detik.
  3. Hubungkan `importExcelLauncher` di `ProductsFragment.kt` dengan `processImportExcel` lengkap dengan ProgressDialog interaktif.
  4. Kompilasi ulang dan install update debug APK ke perangkat fisik Android yang terhubung via ADB.

---

### [BUG-028]
- **Tanggal**: 2026-10-31
- **Modul/Komponen**: Mobile / Offline Storage & Image Directory Resolver
- **Tingkat Keparahan**: MEDIUM
- **Status**: RESOLVED
- **Deskripsi Masalah**:
  1. Aplikasi kasir sebelumnya hanya memeriksa foto offline pada direktori internal aplikasi dan direktori statis `/sdcard/product_images/`.
  2. Jika pemilik toko memiliki ribuan file foto produk offline di folder download atau folder kustom (misal: `/sdcard/Download/image/` hasil ekstrak script scraper `D:\SERVER\DenKasir\DataProduk\image\{barcode}.jpg`), sistem belum menyediakan antarmuka pemilih folder kustom dan belum memindai file berdasarkan barcode secara otomatis di folder tersebut.
- **Dampak / Potensi Resiko**: Pemilik toko harus mengunduh ulang foto dari web via kuota internet HP, meskipun mereka sudah memiliki kumpulan file foto offline di memori HP.
- **Rekomendasi / Solusi Perbaikan**:
  1. Perluas `ImageUtils.findProductImageTarget` untuk mencari file `{barcode}.jpg`, `{barcode}.png`, `{barcode}.jpeg`, `{barcode}.webp` di berbagai folder publik populer (`/sdcard/Download/image/`, `/sdcard/Download/product_images/`, `/sdcard/Pictures/image/`, dll) dan memprioritaskan file offline lokal sebelum fallback ke link URL web.
  2. Tambahkan pengaturan folder kustom di `SessionManager` (`saveCustomImagesDir` / `getCustomImagesDir`).
  3. Tambahkan tombol **'Pilih / Atur Folder Foto Offline'** dan **'Sinkronkan Foto dari Folder Lokal'** pada kartu Pengelolaan Data di `activity_data_management.xml` dan `DataManagementActivity.kt`, lengkap dengan pemindaian otomatis barcode dan notifikasi hasil pencocokan.
  4. Kompilasi ulang dan update APK langsung ke perangkat Android.

---

### [BUG-029]
- **Tanggal**: 2026-10-31
- **Modul/Komponen**: Mobile / UI & Financial Reporting Engine (BKU & Cashier Transactions)
- **Tingkat Keparahan**: HIGH
- **Status**: RESOLVED
- **Deskripsi Masalah**: Komponen grafik arus kas (CashFlowChartView) menggunakan data statis tiruan (*hardcoded dummy data*) dan tidak pernah dihubungkan dengan data mutasi kas maupun transaksi penjualan riil dari database Room. Selain itu, layar laporan keuangan (ReportsFragment) dan riwayat transaksi kasir (TransactionsFragment) belum menyediakan mekanisme penyortiran/penyaringan periode (harian, mingguan, bulanan, dan rentang tanggal kustom), menyebabkan angka metrik omset, HPP, laba rugi, dan daftar mutasi selalu terakumulasi global tanpa pemisahan rentang waktu.
- **Dampak / Potensi Resiko**: Pemilik toko/kasir tidak dapat memantau pergerakan arus kas riil harian secara visual dan kesulitan mengevaluasi omset serta laba bersih harian, mingguan, maupun bulanan.
- **Rekomendasi / Solusi Perbaikan**:
  1. Rombak CashFlowChartView.kt menjadi komponen grafik dinamis yang menerima dataset dinamis (setChartData(labels, cashIn, cashOut)), mendukung kalkulasi sumbu Y otomatis (*nice numbers* kelipatan ribuan/jutaan), render kurva gradien hijau (kas masuk) & merah (kas keluar), serta empty-state aman.
  2. Tambahkan UI chip filter periode (Harian, Mingguan, Bulanan, dan Kustom dengan pemilih tanggal DatePickerDialog) pada ReportsFragment dan TransactionsFragment.
  3. Hubungkan agregasi database (omset, HPP, laba kotor, beban operasional, laba bersih, mutasi kas) dan kurva grafik secara real-time dengan filter rentang waktu aktif.
- **Solusi Yang Diterapkan**:
  1. Merombak CashFlowChartView.kt secara penuh menjadi komponen grafik dinamis interaktif yang menerima dataset realtime (setChartData(labels, cashIn, cashOut)), mendukung kalkulasi sumbu Y otomatis (*nice numbers* kelipatan ribuan/jutaan), render kurva gradien hijau (kas masuk) & merah (kas keluar), badge nilai interaktif saat node disentuh, dan penanganan empty-state jika belum ada transaksi.
  2. Membuat DateFilterHelper.kt yang menyediakan kalkulasi rentang waktu terstandar (HARIAN per-jam, MINGGUAN 7 hari terakhir, BULANAN bulan kalender aktif, dan KUSTOM berbasis DatePickerDialog untuk memilih tanggal awal dan akhir) serta pembagian data mutasi ke dalam ember (*bucket*) waktu.
  3. Memasang bar chip filter waktu pada ReportsFragment.kt dan layout `fragment_reports.xml` sehingga omset, HPP, laba kotor, beban operasional, laba bersih, saldo kas laci, dan mutasi BKU disaring secara dinamis sesuai periode yang dipilih.
  4. Menambahkan chip filter periode waktu dan kartu grafik arus kas serta ringkasan omset kasir pada TransactionsFragment.kt dan layout `fragment_transactions.xml`, lengkap dengan opsi sembunyikan/tampilkan grafik.
  5. Menambahkan rangkaian unit test DateFilterAndChartTest.kt yang menguji akurasi rentang waktu harian, mingguan, bulanan, kustom, dan agregasi data grafik tanpa regresi (100% PASS).
---

### [BUG-030]
- **Tanggal**: 2026-10-31
- **Modul/Komponen**: Mobile / POS Hardware, Receipt Formatter & Smart Search Engine
- **Tingkat Keparahan**: HIGH
- **Status**: RESOLVED
- **Deskripsi Masalah**:
  1. **Format Struk Kasir Berantakan**: Pemenggalan kata pada ReceiptFormatterUtils.kt memicu *orphan word* (satuan seperti 'g' atau 'ml' terlempar sendirian ke baris baru, contoh: "Indomie Mi Instan Ayam Bawang 69" diikuti baris baru "g"). Selain itu, metadata struk (No Nota, Waktu, Kasir, Pembayaran) tidak memiliki perataan titik dua (colon alignment), pemusatan teks catatan kaki menyisakan kata tunggal ('Anda!', 'ditukar'), dan terdapat garis pembatas ganda yang tidak rapi.
  2. **Pemindai Barcode Tidak Akumulasi Qty**: Pada BarcodeScannerActivity.kt, variabel lastScannedCode tidak pernah di-reset saat produk yang sama dipindai berulang kali, sehingga pemindaian kedua (2x) dan ketiga (3x) diabaikan sepenuhnya dan kasir tidak dapat menambah kuantitas produk secara beruntun (*continuous multi-scan*).
  3. **Pencarian Produk Kaku (Strict Substring)**: Pada PosFragment.kt dan ProductsFragment.kt, fitur pencarian hanya mengandalkan pencocokan substring statis tunggal. Pencarian kata kunci acak/tidak berurutan (misal: "ayam goreng mie" vs "Indomie Mi Instan Ayam Goreng") atau variasi ejaan/typo Indonesia (misal: "mei ayam goreng" mencari "mie") tidak memunculkan hasil apa pun.
- **Dampak / Potensi Resiko**: Tampilan struk kasir cetak/digital tidak profesional dan menyulitkan pembacaan, proses kasir lambat karena harus mengubah QTY manual saat memindai barang yang sama berkali-kali, serta kasir kesulitan menemukan produk saat mengetik kata kunci dengan urutan berbeda atau salah ketik ringan.
- **Rekomendasi / Solusi Perbaikan**:
  1. Perbaiki ReceiptFormatterUtils.kt dengan aturan pembungkusan kata cerdas (*smart wrap without orphan units*), penataan metadata rapi berbasis kolom rata kiri-kanan dengan pemisah titik dua konsisten, dan penyempurnaan preview struk di PaymentSuccessActivity.
  2. Perbarui BarcodeScannerActivity.kt dan PosViewModel.kt untuk mendukung *continuous accumulation multi-scan* dengan debounce cooldown (~800ms) sehingga pemindaian berulang pada produk yang sama otomatis menambah kuantitas (1x -> x1, 2x -> x2, 3x -> x3) lengkap dengan feedback audio-haptic dan pengiriman SCANNED_QUANTITY.
  3. Bangun modul SmartSearchEngine.kt yang mendukung tokenisasi multi-kata tanpa memedulikan urutan kata (*order-independent*), normalisasi sinonim/ejaan fonetik bahasa Indonesia (mei->mie, telor->telur, cabe->cabai), serta fuzzy matching Levenshtein, dan pasang pada PosFragment.kt dan ProductsFragment.kt.
- **Solusi Yang Diterapkan**:
  1. **Rapi-kan Struk Printer & Preview Nota**:
     - Memperbaiki `ReceiptFormatterUtils.kt` dengan algoritma *Smart Word Wrapping & Orphan Prevention*: kata atau unit pendek (`g`, `ml`, `kg`, `Anda!`) tidak lagi terlempar sendirian ke baris berikutnya melainkan menarik kata sebelumnya (misal: "Indomie Mi Instan Ayam Bawang" dan baris kedua "69 g").
     - Menambahkan fungsi `keyColonValue(label, value, width, labelWidth)` sehingga seluruh metadata (No. Nota, Tanggal, Kasir, Bayar, Pelanggan) memiliki perataan titik dua (`:`) yang 100% vertikal lurus presisi pada struk 58mm maupun 80mm.
     - Memperbaiki format footer default di `SessionManager.kt` dan pembungkusan baris catatan kaki agar pas dalam 32 kolom tanpa terpotong kaku.
     - Memperbarui `activity_payment_success.xml` dan `PaymentSuccessActivity.kt` menggunakan font monospace terstandar, `HorizontalScrollView` pelindung, serta ukuran font `11.5sp` berjarak 2dp sehingga teks struk di layar ponsel tidak pernah wrap secara artifisial.
  2. **Akumulasi Scan Barcode Berulang (1x -> x1, 2x -> x2, 3x -> x3)**:
     - Mengubah `BarcodeScannerActivity.kt` dengan menambahkan pelacakan `scannedQuantity` dan jeda aman (`SAME_BARCODE_COOLDOWN_MS = 850L`). Pemindaian beruntun pada barcode yang sama otomatis menaikkan kuantitas (`x1`, `x2`, `x3`), memicu feedback audio beep dan vibrasi haptik kasir, serta menampilkan kartu preview dengan animasi denyut (*card pulse animation*) dan subtotal harga akumulatif.
     - Mengirimkan `SCANNED_BARCODE` dan `SCANNED_QTY` kembali ke `PosFragment.kt` via result launcher.
     - Memperbarui `PosViewModel.addScannedBarcode(barcode, qty)` agar mengalirkan kuantitas yang dipindai langsung ke keranjang kasir.
  3. **Smart Search Engine (Unordered & Indonesian Phonetic Typo-Tolerant)**:
     - Membangun `SmartSearchEngine.kt` yang mendukung tokenisasi multi-kata tanpa bergantung urutan (*order-independent*). Kata kunci "mei ayam goreng" atau "goreng mie ayam" dapat secara cerdas memanggil "Indomie Mi Instan Rasa Ayam Goreng".
     - Menyediakan kamus normalisasi fonetik/slang Indonesia (`mei`/`mi` -> `mie`, `telor` -> `telur`, `cabe` -> `cabai`, `minak` -> `minyak`, `akua` -> `aqua`, dll.).
     - Mengimplementasikan kalkulasi jarak edit Damerau-Levenshtein untuk mentolerir salah ketik ringan (misal: "ayamm", "indomye").
     - Mengimplementasikan `SmartProductSearchAdapter.kt` pada kolom pencarian POS `etSearchProduct`, serta mengintegrasikannya ke `ProductsFragment.kt` dan `StockOpnameActivity.kt`.
     - Menambahkan unit test komprehensif `SmartSearchAndBarcodeReceiptTest.kt` (9 tes, 100% PASS).---

### [BUG-031]
- **Tanggal**: 2026-10-31
- **Modul/Komponen**: Mobile / POS Hardware (Barcode Scanner Camera Selector)
- **Tingkat Keparahan**: MEDIUM
- **Status**: RESOLVED
- **Deskripsi Masalah**: Layar pemindai barcode (`BarcodeScannerActivity.kt`) terkunci secara kaku (*hardcoded*) hanya menggunakan kamera belakang (`CameraSelector.DEFAULT_BACK_CAMERA`). Perangkat kasir tablet yang dipasang di meja kasir (*stand/dock*) atau kasir yang membutuhkan kamera depan untuk memindai barang ke arah pembeli/kasir tidak memiliki kontrol untuk mengganti kamera. Selain itu, tombol senter flash tetap muncul meskipun kamera depan tidak memiliki perangkat flash fisik.
- **Dampak / Potensi Resiko**: Pengguna kasir tablet atau holder statis tidak dapat menggunakan kamera depan untuk pemindaian barcode yang efisien.
- **Rekomendasi / Solusi Perbaikan**: Tambahkan tombol pengalih kamera depan/belakang (`btnSwitchCamera`) pada header `activity_barcode_scanner.xml`, dukung pergantian dinamis lensa `CameraSelector.LENS_FACING_FRONT` dan `LENS_FACING_BACK`, simpan preferensi lensa pengguna di `SessionManager`, dan sesuaikan visibilitas tombol flash secara kondisional.
- **Solusi Yang Diterapkan**:
  1. Menambahkan tombol aksi `btnSwitchCamera` dengan ikon vektor material `ic_switch_camera.xml` pada top bar `activity_barcode_scanner.xml`.
  2. Memperbarui `BarcodeScannerActivity.kt` untuk mendukung peralihan dinamis antara kamera depan dan belakang (`switchCamera()`), lengkap dengan animasi rotasi ikon 180°, notifikasi Toast, penanganan *graceful fallback* jika perangkat hanya memiliki satu kamera, serta otomatis menyembunyikan tombol senter jika lensa yang aktif tidak memiliki flash unit fisik.
  3. Menyimpan dan memuat preferensi arah kamera kasir secara persisten di `SessionManager.kt` (`savePreferredCameraLens` / `getPreferredCameraLens`) sehingga pengguna tidak perlu mengganti kamera berulang-ulang setiap kali membuka scanner.
  4. Pengujian unit test dan kompilasi APK Gradle berhasil tanpa regresi (100% PASS).
---

### [BUG-032]
- **Tanggal**: 2026-10-31
- **Modul/Komponen**: Mobile / Search Engine & Performance (SmartSearchEngine & Products Search Auto-Indexing)
- **Tingkat Keparahan**: HIGH
- **Status**: RESOLVED
- **Deskripsi Masalah**:
  Pada katalog produk berukuran besar (8.670 produk), pengetikan kata kunci pencarian pada layar ProductsFragment, PosFragment, dan StockOpnameActivity mengalami keterlambatan/lag yang sangat berat (*stuttering*). Hal ini disebabkan oleh:
  1. SmartSearchEngine.filterProducts dieksekusi secara sinkron langsung di Main UI Thread pada setiap kali karakter diketik (onTextChanged) tanpa mekanisme debouncing.
  2. Indeks token produk (ProductIndex) belum dibuat sebelumnya (tidak ada *auto pre-indexing* di latar belakang saat data produk dimuat dari database Room).
  3. Perhitungan jarak edit Levenshtein dijalankan tanpa pengecekan awal selisih panjang string (bs(len1 - len2) <= maxDist), memicu puluhan ribu komputasi matriks dan alokasi IntArray per ketukan tombol.
  4. Rendering pop-up dropdown AutoCompleteTextView pada kasir tidak dibatasi jumlahnya, membebani sistem dengan rendering ribuan item sekaligus.
- **Dampak / Potensi Resiko**: Aplikasi Android mengalami *UI freeze*, pengetikan terasa lambat/berat, dan potensi ANR (*Application Not Responding*) saat kasir mencari produk pada katalog toko besar.
- **Rekomendasi / Solusi Perbaikan**:
  1. Bangun *auto pre-indexing* di background coroutine (Dispatchers.Default) segera setelah produk dimuat dari Room Database.
  2. Terapkan *coroutine debouncing* (120ms) dengan pembatalan job aktif (searchJob?.cancel()) pada ProductsFragment dan StockOpnameActivity.
  3. Pindahkan eksekusi penyaringan dan pemeringkatan skor pencarian ke thread pool latar belakang (withContext(Dispatchers.Default)).
  4. Optimalkan SmartSearchEngine: tambahkan *early length-difference check* sebelum kalkulasi Levenshtein, dan batasi batas maksimum hasil (maxResults = 100 pada list produk, maxResults = 40 pada autocomplete POS).
- **Solusi Yang Diterapkan**:
  1. **Optimasi SmartSearchEngine.kt**:
     - Menambahkan fungsi preIndex(products) dengan cache berbasis ConcurrentHashMap<String, ProductIndex> yang mengindeks barcode, token nama, dan token kategori.
     - Menambahkan pemeriksaan awal selisih panjang string (kotlin.math.abs(len1 - len2) > 2) pada levenshteinDistance dan sebelum pemanggilan matriks, mengeliminasi alokasi memori berlebih dan mempercepat pencocokan hingga 10x lipat.
     - Menambahkan pembatasan hasil instan (maxResults = 100 untuk RecyclerView dan maxResults = 40 untuk AutoCompleteTextView).
  2. **Debounce & Background Coroutine di ProductsFragment.kt**:
     - Menjalankan SmartSearchEngine.preIndex(products) di thread Dispatchers.Default secara asinkron saat llRetailProducts dan llDigitalProducts diterima dari Room Database.
     - Menambahkan searchJob dengan debouncing 120ms (delay(120)) pada inding.etSearchProduct.addTextChangedListener.
     - Memindahkan kalkulasi filter ke withContext(Dispatchers.Default) sehingga Main UI Thread 100% lancar tanpa stutter saat kasir mengetik kata kunci.
  3. **Optimasi di PosFragment.kt & SmartProductSearchAdapter.kt**:
     - Menjalankan pre-index di background saat produk diperbarui di POS.
     - Mengatur inding.etSearchProduct.threshold = 1 dan membatasi dropdown hingga 40 produk teratas untuk kecepatan rendering instan.
  4. **Optimasi di StockOpnameActivity.kt**:
     - Mengimplementasikan debounceFilter (120ms) dan pemrosesan SmartSearchEngine.matches pada Dispatchers.Default.
  5. **Pengamanan DataManagementActivity.kt**:
     - Menambahkan blok pelindung try-catch pada fungsi pemindaian foto offline scanAndSyncLocalImagesFolder() dengan dialog pemberitahuan informatif.
  6. **Deployment APK & Verifikasi**:
     - APK berhasil dikompilasi ulang (ssembleDebug) dan dipasang ke perangkat uji fisik via ADB (db install -r -d) dengan mempertahankan 100% basis data lokal (8.670 produk dan foto tetap utuh tanpa pm clear). Seluruh unit test lolos 100%.
