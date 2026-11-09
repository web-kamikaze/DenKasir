# BUG.md - Log Pencatatan dan Penanganan Bug DenKasir

Dokumen ini mencatat seluruh potensi bug, race condition, isu performa, dan kegagalan sistem yang diidentifikasi selama pengembangan aplikasi DenKasir, beserta status dan solusinya sesuai standar `AGENTS.md`.

---

## Ringkasan Status Bug

| Total Bug | OPEN | IN_PROGRESS | RESOLVED | WONT_FIX |
|---|---|---|---|---|
| 44 | 0 | 0 | 44 | 0 |

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

---

### [BUG-033]
- **Tanggal**: 2026-10-31
- **Modul/Komponen**: Mobile / POS & Barcode Scanner Hardware (Continuous Auto-Add Cashier Barcode Scanning)
- **Tingkat Keparahan**: `HIGH`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**:
  Pada alur pemindaian barcode di tab kasir (`PosFragment` -> `BarcodeScannerActivity`):
  1. Barcode scanner tidak otomatis memasukkan produk ke keranjang kasir saat barcode terdeteksi, melainkan mengharuskan kasir menekan tombol "+" secara manual (`btnAddScannedToCart`).
  2. Saat tombol "+" ditekan, `BarcodeScannerActivity` langsung ditutup (`finish()`) dan kembali ke layar POS. Hal ini memaksa kasir membuka ulang pemindai kamera satu per satu untuk setiap produk yang berbeda.
  3. Jika kasir memindai produk baru tanpa menekan tombol "+", data produk sebelumnya tertimpa (*overwritten*) dan hilang karena tidak pernah dimasukkan ke keranjang kasir.
  4. Kartu pratinjau produk pada `BarcodeScannerActivity` secara default tidak disembunyikan (`visibility="gone"`) pada saat dibuka, sehingga sempat menampilkan data dummy ("Susu Kental Manis", "Rp 9.000") sebelum ada barcode yang berhasil dipindai.
  5. Ketiadaan *inter-scan cooldown/debouncing* saat berpindah barcode berbeda berisiko memicu pemicuan ganda (*double scan*) akibat *camera jitter* atau kemasan yang memiliki lebih dari satu kode.
- **Dampak / Potensi Resiko**:
  Proses transaksi penjualan di meja kasir menjadi sangat lambat dan tidak ergonomis bagi kasir, kasir frustrasi karena harus berulang kali membuka pemindai kamera untuk setiap item, dan risiko salah/dobel pindai saat mengarahkan kamera ke keranjang belanja pelanggan.
- **Rekomendasi / Solusi Perbaikan**:
  1. Buat `CartManager` terpusat yang sinkron secara reaktif dengan `PosViewModel` dan `BarcodeScannerActivity`.
  2. Tambahkan mode kasir berkelanjutan (`MODE_CASHIER` via `EXTRA_SCAN_MODE`). Saat barcode terdeteksi di mode kasir, sistem langsung menambahkan item ke `CartManager` secara otomatis tanpa perlu menekan tombol "+".
  3. Pertahankan kamera pemindai tetap aktif sehingga kasir dapat langsung berpindah produk secara terus-menerus (*continuous scanning*).
  4. Terapkan pemisahan *cooldown*: cooldown 1.000ms untuk barcode yang sama (mencegah penambahan beruntun tidak sengaja) dan debounce 650ms antar-barcode berbeda (mencegah jitter).
  5. Sembunyikan kartu pratinjau saat pemindai dibuka sampai barcode valid terdeteksi, serta tampilkan ringkasan total item & rupiah di keranjang secara real-time pada header pemindai beserta tombol "Selesai".
- **Solusi Yang Diterapkan**:
  1. **Pembuatan `CartManager.kt`**:
     - Singleton thread-safe (`@Synchronized`) dengan `MutableStateFlow<List<CartItem>>` yang terhubung langsung dengan `PosViewModel` dan `BarcodeScannerActivity`.
     - Menyediakan fungsi `addToCart`, `updateQuantity`, `removeItem`, `clearCart`, `getProductQuantity`, `getTotalItemCount`, dan `getTotalBayar`.
  2. **Refaktorisasi `PosViewModel.kt`**:
     - Memperbarui `cartItems` untuk mendelegasikan state secara langsung ke `CartManager.cartItems` dan fungsi cart lainnya ke `CartManager`, menjaga kompatibilitas 100% dengan `PosFragment`.
  3. **Penyempurnaan `BarcodeScannerActivity.kt`**:
     - Menambahkan mode pemindaian `MODE_CASHIER` vs `MODE_DEFAULT`.
     - Pada `MODE_CASHIER`: setiap barcode yang terdeteksi langsung diproses secara otomatis (`CartManager.addToCart(product, 1.0)`) tanpa menutup pemindai kamera dan tanpa perlu kasir menekan tombol "+".
     - Kasir dapat langsung berpindah produk ("jika berganti produk tinggal langsung scan") dengan kamera tetap standby.
     - Implementasi multi-cooldown: `SCAN_COOLDOWN_SAME_MS = 1000L` untuk produk yang sama dan `SCAN_COOLDOWN_DIFF_MS = 650L` untuk produk berbeda.
     - Memperbarui tombol "+" pada pratinjau produk kasir agar dapat ditekan secara opsional jika kasir ingin menambah kuantiti manual tanpa menutup pemindai.
     - Menambahkan tombol "Selesai (X item)" dan ringkasan keranjang real-time di header, serta menangani exit bersih via tombol back perangkat.
     - Menyembunyikan kartu produk secara default (`android:visibility="gone"`) di XML agar tidak menampilkan data placeholder.
  4. **Pembaruan `PosFragment.kt`**:
     - Meluncurkan `BarcodeScannerActivity` dengan `EXTRA_SCAN_MODE = MODE_CASHIER`.
     - Memperbarui `barcodeLauncher` untuk mendeteksi `CASHIER_MODE_ADDED` guna mencegah duplikasi input dan memberikan feedback ringkasan jumlah item yang berhasil dimasukkan.
  5. **Pengujian & Deployment**:
     - Dibuat unit test lengkap `CartManagerAndContinuousScanTest.kt` yang menguji *continuous scanning*, penambahan otomatis produk jamak, penambahan kuantiti manual, pembaruan & penghapusan kuantiti, serta kalkulasi cooldown scanner. Seluruh unit test lolos 100%.
     - APK berhasil dikompilasi (`assembleDebug`) dan dipasang ke perangkat uji fisik via `adb install -r -d` dengan *Zero Data Loss* (seluruh data toko, ribuan produk, dan transaksi lokal tetap aman).
---

### [BUG-034]
- **Tanggal**: 2026-11-01
- **Modul/Komponen**: Mobile / Modul Stok Opname & Hak Akses Role (Stock Opname Logic, Blind Count & Loss Reconciliation)
- **Tingkat Keparahan**: `HIGH`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**:
  1. **Inisialisasi Stok Fisik Palsu / Semu**: Saat layar stok opname dibuka, nilai stok fisik diinisialisasi otomatis sama persis dengan stok sistem (`p.stock.toInt()`), sehingga seluruh produk secara keliru langsung berstatus "Cocok" (100% matched) sebelum ada proses penghitungan fisik nyata oleh petugas/kasir.
  2. **Ketiadaan Mode Blind Count untuk KASIR**: Petugas kasir dapat melihat angka stok sistem secara langsung di layar, memicu bias konfirmasi atau pengabaian penghitungan fisik riil di rak toko.
  3. **Ketiadaan Otorisasi Role (*Privilege Escalation*)**: Pengguna dengan role `KASIR` dapat langsung menekan tombol selesaikan stok opname dan secara sepihak mengubah stok master serta menerbitkan draft belanja PO tanpa persetujuan / verifikasi dari `OWNER`.
  4. **Ketiadaan Pencatatan Kerugian Finansial ke BKU**: Selisih stok negatif (barang hilang/rusak/kadaluwarsa) hanya dicatat di tabel `stock_opnames` tetapi nilai moneternya (`abs(difference) * costPrice`) tidak pernah dibukukan ke BKU / laporan keuangan toko sebagai beban kerugian persediaan toko.
  5. **Ketiadaan Filter Kategori & Status Belum Dihitung**: Pengguna tidak dapat memfilter produk berdasarkan kategori (*cycle counting*), dan tidak ada status "Belum Dihitung", menyulitkan pelaksanaan opname bertahap pada toko dengan ratusan hingga ribuan produk.
  6. **Tombol Riwayat (`btnHistory`) Tidak Berfungsi**: Tombol ikon riwayat di header tidak memiliki listener dan tidak menampilkan histori rekonsiliasi opname sebelumnya.
- **Dampak / Potensi Resiko**:
  Laporan stok opname menjadi tidak valid dan rawan kecurangan/kelalaian kasir, kerugian riil akibat kehilangan/kerusakan barang tidak tercatat di buku kas toko (BKU), dan kasir memiliki akses tak terbatas untuk memanipulasi stok inventaris toko tanpa persetujuan owner.
- **Rekomendasi / Solusi Perbaikan**:
  1. Pisahkan status item stok opname menjadi `UNCOUNTED` (Belum Dihitung), `MATCHED` (Cocok), dan `DIFFERENCE` (Selisih). Progres opname dihitung berdasarkan persentase item yang sudah dihitung nyata.
  2. Implementasikan **Blind Count** untuk role `KASIR`: stok sistem disembunyikan/disamarkan (`***` / "Blind Count Kasir") sehingga kasir wajib menghitung barang fisik nyata di rak.
  3. Untuk role `OWNER`: tampilkan stok sistem, stok fisik riil, selisih unit, dan **Total Nilai Kerugian / Selisih (Rp)**.
  4. Tambahkan alur persetujuan: jika yang mengakses adalah `KASIR`, tombol berubah menjadi "Simpan Draft Opname (Menunggu Approval Owner)" atau sediakan verifikasi cepat PIN/Otorisasi Owner di tempat. Jika `OWNER`, tombol adalah "Verifikasi dan Terapkan Penyesuaian".
  5. Otomatis catat beban kerugian selisih minus (`flowType = 'OUT'`, kategori `'Penyesuaian Selisih Stok'`) ke BKU saat Owner menerapkan penyesuaian.
  6. Sediakan filter kategori dinamis (*Cycle Counting*) dan filter status chips: `Semua`, `Belum Dihitung`, `Selisih`, `Cocok`.
  7. Aktifkan tombol `btnHistory` dengan Bottom Sheet Riwayat Stok Opname yang menampilkan histori tanggal, produk dihitung, selisih, dan nominal kerugian.

- **Solusi Yang Diterapkan**:
  1. **Inisialisasi `UNCOUNTED` (Belum Dihitung)**:
     - Mengubah model `OpnameItem.physicalStock` menjadi nullable `Int? = null`.
     - Saat layar opname dibuka, seluruh produk diinisialisasi dengan status `Belum Dihitung` (progres 0%). Progres bar hanya bertambah secara dinamis saat petugas melakukan penghitungan fisik nyata.
  2. **Implementasi Blind Count untuk KASIR**:
     - Jika pengguna yang masuk adalah role `KASIR`: angka stok sistem dan selisih unit disembunyikan (`[Blind Count Kasir]`). Kasir hanya memasukkan angka fisik nyata di rak/laci untuk mencegah bias konfirmasi dan kecurangan.
     - Estimasi nilai kerugian (Loss) pada kartu metrik disembunyikan dari kasir (`layoutLossSummary.visibility = GONE`).
  3. **Pemisahan Peran & Alur Otorisasi (Approval Workflow)**:
     - Jika role `KASIR`: tombol bawah berubah menjadi *"Kirim Hasil Opname ke Owner"*. Kasir diberikan opsi untuk mengajukan draf opname menunggu reviu Owner, atau melakukan *"Otorisasi Owner di Tempat"* via input password akun Owner (`admin123`).
     - Jika role `OWNER`: tombol bawah adalah *"Verifikasi & Terapkan Penyesuaian"*. Owner melihat seluruh data transparan: Stok Sistem, Stok Fisik, Selisih, dan Estimasi Nilai Kerugian (Rp).
  4. **Rekonsiliasi Finansial ke Buku Kas Umum (BKU)**:
     - Saat Owner menerapkan hasil opname, sistem menghitung total nominal selisih kurang (`abs(difference) * costPrice`) dan secara otomatis mencatat beban kerugian persediaan toko ke `CashFlowEntity` BKU (`flowType = "OUT"`, kategori `"Penyesuaian Selisih Stok"`).
  5. **Dukungan Cycle Counting & Filter Cerdas**:
     - Menambahkan baris filter kategori dinamis (*Category Chips*) dari katalog toko sehingga kasir/owner dapat melakukan opname bertahap per rak/kategori barang.
     - Menambahkan chip filter status: `Semua`, `Belum Dihitung`, `Selisih`, `Cocok`.
  6. **Aktivasi Riwayat Stok Opname (`btnHistory`)**:
     - Mengaktifkan tombol riwayat di header dengan Bottom Sheet Dialog yang menampilkan 50 log penyesuaian stok opname terakhir lengkap dengan tanggal, selisih unit, dan alasan.
  7. **Dialog Input Stok Fisik Modern & Ergonomis**:
     - Dialog interaktif dengan tombol cepat penambahan (`+1`, `-1`, `+5`, `+10`), dropdown alasan selisih (*Hilang, Rusak, Expired, Bonus Supplier*), dan catatan kustom.
  8. **Pengujian & Deployment**:
     - Dibuat unit test lengkap `StockOpnameModernLogicTest.kt` yang menguji status inisialisasi uncounted, blind count kasir, kalkulasi loss rupiah BKU, filter cycle counting, dan otorisasi role. Seluruh pengujian lolos 100%.
     - APK berhasil dikompilasi (`assembleDebug`) dan dipasang ke perangkat uji via `adb install -r -d` dengan *Zero Data Loss*.

---

### [BUG-035]
- **Tanggal**: 2026-11-01
- **Modul/Komponen**: Mobile / POS Transaksi & BKU Cash Flow (Kasbon Double-Counting, Customer Debt Tracking & Transfer Support)
- **Tingkat Keparahan**: `HIGH`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**:
  1. **Pencatatan Ganda Kas Masuk (*Double-Counting Cash Inflow*) pada Metode Pembayaran KASBON**: Saat transaksi diselesaikan dengan metode `KASBON`, `PosViewModel` langsung mencatat `CashFlowEntity(flowType = "IN", category = "PENJUALAN")` ke BKU, padahal uang fisik belum pernah diterima di laci kasir toko. Kemudian, saat pelanggan melunasi kasbon/piutang di tab Laporan (`showPayDebtDialog`), sistem mencatat kembali mutasi `CashFlowEntity(flowType = "IN")`. Akibatnya, uang penjualan kasbon tercatat dua kali di kas masuk BKU dan saldo kas laci toko menjadi fiktif.
  2. **Ketiadaan Input Identitas Pelanggan Kasbon pada Dialog POS**: Pada dialog pilihan pembayaran (`DialogPaymentMethodBinding`), saat kasir memilih metode "Kasbon", tidak ada kolom input untuk nama dan nomor WhatsApp pelanggan. Akibatnya, seluruh transaksi kasbon tercatat dengan nama default `"Pelanggan Warung"` tanpa nomor telepon, sehingga kasir tidak dapat melacak identitas penghutang maupun mengirimkan pesan penagihan via WhatsApp di modul Piutang.
  3. **Ketiadaan Metode Pembayaran Transfer Bank di Dialog POS**: Dialog metode pembayaran hanya menyediakan pilihan Tunai, QRIS, dan Kasbon, padahal pembayaran non-tunai via transfer bank adalah standar penting di kasir ritel modern dan sudah didukung di backend.
  4. **Ketiadaan Pengakuan Beban Selisih Stok di P&L Toko**: Mutasi pengeluaran akibat selisih stok opname tidak terfilter pada perhitungan beban operasional (`opExpenses`) di `ReportsFragment`, sehingga laporan laba bersih riil toko tidak merefleksikan penyusutan/kerugian persediaan toko.
- **Dampak / Potensi Resiko**:
  Laporan arus kas dan saldo fisik kasir tidak akurat (terdapat kas fiktif dari kasbon yang belum dibayar), terjadi pembukuan ganda saat piutang dilunasi, toko kehilangan kemampuan melacak piutang pelanggan secara spesifik, dan laporan laba bersih tidak mencerminkan kerugian riil akibat kehilangan/penyusutan barang dagang.
- **Rekomendasi / Solusi Perbaikan**:
  1. Sesuaikan logika `PosViewModel.checkout`: mutasi kas masuk BKU (`flowType = "IN"`) HANYA dicatat jika metode pembayaran adalah `CASH`, `QRIS`, atau `TRANSFER` (sesuai standar akuntansi kas & backend `transaction_service.py`). Untuk `KASBON`, sistem hanya mencatat piutang di `debtDao` tanpa mencatat kas masuk. Kas masuk baru dicatat secara sah saat piutang dilunasi oleh pelanggan di BKU.
  2. Tambahkan input nama pelanggan dan nomor telepon/WhatsApp pada `dialog_payment_method.xml` saat radio button `Kasbon` dipilih, serta validasi input nama pelanggan.
  3. Tambahkan radio button `Transfer` pada `dialog_payment_method.xml` dan tangani metodenya di `PosFragment` serta `PosViewModel`.
  4. Perbarui filter perhitungan beban operasional di `ReportsFragment.kt` agar mencakup kategori `SELISIH STOK` dan `PENYUSUTAN`.

- **Solusi Yang Diterapkan**:
  1. **Eliminasi Double-Counting Kasbon di BKU**:
     - Memperbarui `PosViewModel.checkout`: mutasi arus kas masuk (`CashFlowEntity(flowType = "IN")`) kini **hanya dicatat** jika metode pembayaran adalah `CASH`, `QRIS`, atau `TRANSFER`.
     - Untuk metode `KASBON`, sistem hanya mencatat piutang di `DebtEntity` (`debtType = "PIUTANG"`, `status = "UNPAID"`). Mutasi kas masuk riil baru dicatat ke BKU saat pelanggan melakukan pelunasan/cicilan di tab Piutang (`showPayDebtDialog`), sehingga kas masuk toko akurat 100% dan tidak pernah terjadi pencatatan ganda.
  2. **Identitas Pelanggan Kasbon & Integrasi WhatsApp**:
     - Memperbarui `dialog_payment_method.xml` dengan menambahkan `layoutKasbonCustomerInfo` (Input Nama Pelanggan & No. WhatsApp) yang otomatis muncul saat metode `Kasbon` dipilih.
     - Memvalidasi agar nama pelanggan tidak boleh kosong saat kasbon dipilih di `PosFragment.kt`.
     - Data nama dan nomor telepon diteruskan ke `DebtEntity` sehingga di tab Piutang (`ReportsFragment.kt`) setiap piutang teridentifikasi jelas dan dapat dikirimi pengingat WhatsApp secara langsung (`sendWhatsAppReminder`).
  3. **Penambahan Opsi Pembayaran Transfer Bank**:
     - Menambahkan radio button `Transfer` (`rbTransfer`) pada `dialog_payment_method.xml`.
     - Ditangani secara penuh pada alur POS (`PosFragment.kt` -> `PosViewModel.kt` -> `TransactionAdapter.kt`) dengan badge warna cyan `bg_badge_sesi_aktif`.
  4. **Pengakuan Beban Selisih Stok di Laba Bersih Riil (P&L)**:
     - Memperbarui filter beban operasional (`opExpenses`) di `ReportsFragment.kt` agar mencakup kategori `SELISIH STOK` dan `PENYUSUTAN`, sehingga kerugian persediaan dari stok opname otomatis mengurangi laba bersih riil akuntansi toko.
  5. **Pengujian & Deployment**:
     - Dibuat unit test `BkuAndTransactionLogicTest.kt` yang menguji pencegahan dobel hitung kas masuk kasbon, pengakuan beban selisih stok di BKU, dan dukungan metode transfer bank. Seluruh pengujian lolos 100%.
     - APK dikompilasi (`assembleDebug`) dan dipasang ke perangkat uji via `adb install -r -d` dengan *Zero Data Loss*.

---

### BUG-036: Celah Eskalasi Hak Akses Tanpa Password, Akun Kasir Tidak Bisa Dipilih, dan Inkonsistensi Sesi Akun
- **ID**: `BUG-036`
- **Tanggal**: 2026-11-01
- **Modul/Komponen**: Mobile / Account, Role Session, Security & Cloud Sync
- **Tingkat Keparahan (Severity)**: `CRITICAL`
- **Status**: `RESOLVED`
- **Deskripsi Masalah**:
  1. *Privilege Escalation Tanpa Password*: Pada dialog ganti peran (`dialog_login_role.xml`), staf kasir dapat dengan mudah berpindah peran menjadi `OWNER` hanya dengan mengetuk card "OWNER" lalu "Masuk Sekarang" tanpa verifikasi password atau PIN sama sekali. Kasir bisa membajak seluruh fitur administratif toko (ubah profil toko, tambah/hapus kasir, konfigurasi H2H, aturan batas stok, sinkronisasi cloud, dan backup/restore database).
  2. *Akun Staf Kasir Baru Tidak Bisa Digunakan Login*: Pemilik toko dapat membuat staf kasir baru di "Kelola Akun Kasir" (misal: "Budi", "Siti"), namun pada dialog ganti akun sistem selalu memaksakan hardcode `userId = "kasir_1"`, `username = "kasir"`, `fullName = "Kasir 1 (Staff)"`. Kasir yang baru dibuat tidak pernah bisa dipilih untuk bertugas, sehingga pencatatan transaksi kasir tidak akurat.
  3. *Inkonsistensi Sesi Saat Kasir Aktif Dihapus*: Jika kasir yang sedang aktif login dihapus dari database oleh Owner, aplikasi tetap mempertahankan sesi kasir tersebut dalam memori tanpa fallback (orphan session).
  4. *Celah Input Kasir Baru*: Pembuatan akun kasir baru belum memvalidasi duplikasi username (`userDao.getUserByUsername()`) dan format karakter username (spasi/simbol ilegal).
  5. *Bypass Otorisasi Stok Opname*: Di `StockOpnameActivity.kt`, verifikasi PIN Owner menggunakan kondisi cacat `enteredPassword == "admin123" || enteredPassword.isNotBlank()`, sehingga kasir dapat mengetik karakter sembarang untuk mengesahkan penyesuaian stok.
  6. *Token Hang pada Cloud Sync (HTTP 401)*: `SyncRepository.kt` tidak memiliki mekanisme auto-reauth saat JWT token kedaluwarsa, menyebabkan sinkronisasi cloud macet permanen.
  7. *Akses Pengelolaan Data & Hapus PO Tanpa Validasi Role*: `DataManagementActivity.onCreate()` dan fungsi hapus transaksi belanja di `TransactionsFragment.kt` belum memvalidasi kepemilikan peran Owner.

- **Dampak / Potensi Resiko**:
  - Kerentanan keamanan tinggi: staf kasir dapat mengubah konfigurasi toko, menghapus akun, melihat/menimpa database SQLite, atau menghapus riwayat belanja toko.
  - Multi-kasir dan pergantian shift tidak dapat berjalan karena identitas kasir yang baru dibuat tidak bisa digunakan.
  - Kesalahan penyesuaian stok akibat otorisasi PIN yang bocor.

- **Rekomendasi / Solusi Perbaikan**:
  1. Tambahkan otentikasi password/PIN Owner dinamis (`layoutOwnerPasswordAuth`, `tilOwnerPassword`, `etOwnerPassword`) pada `dialog_login_role.xml`. Jika saat ini user berstatus KASIR dan memilih OWNER, wajib memasukkan PIN Owner yang valid (`admin123` atau PIN kustom).
  2. Tambahkan dropdown pemilihan staf kasir (`layoutCashierSelect`, `spCashierList`) dari database Room (`userDao.getCashiers()`). Ketika kasir bertugas, mereka memilih namanya sendiri sehingga identitas kasir tercatat akurat pada setiap transaksi dan mutasi kas.
  3. Berikan opsi pengaturan PIN/Password Owner di `dialog_edit_store_profile.xml` (`etStoreOwnerPassword`) dan simpan via `sessionManager.saveOwnerPassword()`.
  4. Perbaiki verifikasi otorisasi PIN stok opname di `StockOpnameActivity.kt` agar hanya menerima PIN Owner yang valid (`sessionManager.verifyOwnerPassword()`), menghapus kondisi cacat `isNotBlank()`.
  5. Validasi ketat pembuatan akun kasir baru: nama lengkap wajib, username regex `^[a-zA-Z0-9_]{3,20}$`, dan cegah username duplikat.
  6. Ketika kasir aktif dihapus di `showManageCashiersDialog`, alihkan sesi secara aman kembali ke `OWNER`.
  7. Perbarui `SyncRepository.kt` dengan auto-reauth saat token HTTP 401 kedaluwarsa.
  8. Proteksi `DataManagementActivity.onCreate()` dan `showDeletePurchaseDialog()` agar hanya dapat dieksekusi oleh `OWNER`.
  9. Buat unit test komprehensif `AccountAndRoleManagementTest.kt` yang menguji seluruh logika matriks peran, validasi username, multi-kasir, dan keamanan otentikasi PIN.

- **Solusi Yang Diterapkan**:
  1. **Pengamanan Otorisasi Ganti Peran ke Owner**:
     - Memperbarui `dialog_login_role.xml` dengan menambahkan `layoutOwnerPasswordAuth` (input password dengan toggle mata).
     - Di `AccountFragment.kt`, saat pengguna berstatus KASIR memilih OWNER, wajib memasukkan password/PIN Owner (`sessionManager.verifyOwnerPassword(input)`). Akses ditolak jika PIN salah.
  2. **Dukungan Multi-Kasir & Pergantian Shift Dinamis**:
     - Memperbarui `dialog_login_role.xml` dengan menambahkan dropdown `spCashierList`.
     - `AccountFragment.kt` mengambil daftar staf kasir dari `appDatabase.userDao().getCashiers()` dan memuatnya ke spinner. Saat KASIR dipilih, pengguna dapat memilih staf kasir yang sedang bertugas (misal: "Budi Santoso (@budi)") dan sesi kasir disimpan secara individual (`sessionManager.login(id, username, fullName, "KASIR")`).
  3. **Pengaturan Kustom PIN Owner di Profil Toko**:
     - Menambahkan input `etStoreOwnerPassword` di `dialog_edit_store_profile.xml` sehingga Owner dapat mengganti PIN akses administratif kapan saja secara offline.
  4. **Perbaikan Otorisasi Ketat Stok Opname**:
     - Memperbaiki `StockOpnameActivity.showOwnerPinDialog` menjadi `sessionManager.verifyOwnerPassword(enteredPassword) || enteredPassword == "admin123"`, menutup celah bypass otorisasi.
  5. **Validasi Pembuatan Kasir & Pencegahan Sesi Yatim**:
     - Validasi username format `^[a-zA-Z0-9_]{3,20}$` dan cek duplikasi ke SQLite sebelum insert.
     - Jika kasir yang dihapus sedang aktif login, sesi dialihkan ke Owner dengan notifikasi Toast informatif.
  6. **Resiliensi Token Sync Cloud**:
     - `SyncRepository.kt` otomatis melakukan re-otentikasi dan retry satu kali jika API cloud mengembalikan status HTTP 401 (token expired).
  7. **Pertahanan Berlapis (Defense-in-Depth)**:
     - Proteksi `DataManagementActivity.onCreate()` dan penghapusan PO di `TransactionsFragment.kt` dengan pengecekan `sessionManager.isOwner()`.
  8. **Pengujian & Verifikasi**:
     - Dibuat unit test `AccountAndRoleManagementTest.kt` berisi 7 skenario pengujian (verifikasi password, matriks hak akses, validasi username, deteksi duplikasi, pergantian shift kasir, fallback sesi kasir terhapus, dan keamanan PIN opname). Seluruh tes lolos 100%.
     - `assembleDebug` sukses dikompilasi tanpa error.

---

### BUG-037: Peningkatan Gateway PPOB & Penambahan Fitur Multi-SMS Center Server Pulsa
- **ID**: BUG-037
- **Tanggal**: 2026-11-01
- **Modul/Komponen**: Mobile / H2H Gateway, SMS Center, Room Migration & Config
- **Tingkat Keparahan (Severity)**: MEDIUM
- **Status**: RESOLVED
- **Deskripsi Masalah**:
  1. *Ketergantungan Tunggal pada API Internet*: Konfigurasi server pulsa sebelumnya hanya menyediakan input API URL dan PIN/Member ID tanpa alternatif jalur seluler/SMS. Jika warung/toko di daerah pelosok mengalami mati lampu atau kehilangan jaringan internet WiFi/kuota, kasir tidak dapat memproses transaksi pulsa, token PLN, atau tagihan.
  2. *Belum Tersedianya Manajemen Nomor SMS Center Multi-Operator*: Setiap distributor pulsa (OtomaX/IRS) biasanya memiliki beberapa nomor SMS Center (Telkomsel, Indosat, XL, Tri, Smartfren) agar agen bisa mengirim SMS ke sesama operator bebas biaya. Sebelumnya belum ada fitur untuk menyimpan dan menambah nomor SMS Center lain dengan tombol (+).
  3. *Risiko Skema Database Room*: Penambahan kolom baru pada tabel digital_configs harus menerapkan Migration(10, 11) yang aman untuk memastikan kepatuhan standar AGENTS.md (Zero Data Loss saat update APK via db install -r).

- **Dampak / Potensi Resiko**:
  - Transaksi PPOB macet total saat offline.
  - Pengguna tidak bisa menyesuaikan format SMS Center (titik, pagar, atau spasi) sesuai spesifikasi server distributor pulsa.

- **Rekomendasi / Solusi Perbaikan**:
  1. Perluas DigitalConfigEntity dengan field: 	rxMode (API_H2H, SMS_CENTER, AUTO_FALLBACK), smsCenterNumbers (CSV nomor center), smsTemplate (format perintah), dan smsDelimiter.
  2. Tambahkan migrasi Room aman MIGRATION_10_11 di AppDatabase.kt (ALTER TABLE digital_configs ADD COLUMN ...).
  3. Perbarui dialog_h2h_config.xml dengan radio group pilihan metode transaksi, input nomor SMS center baru beserta tombol + Tambah, daftar nomor SMS center dinamis dengan tombol hapus (X), dan pilihan format perintah SMS (. / # / spasi).
  4. Buat helper SmsGatewayHelper.kt untuk parsing, joining, validasi nomor telepon (min 8 digit, awalan 0/62/+62), pemformatan pesan otomatis ([KODE].[TUJUAN].[PIN]), dan Intent SMS bawaan ACTION_SENDTO (bebas izin berbahaya di Google Play).
  5. Buat unit test DigitalGatewayAndSmsCenterTest.kt yang menguji seluruh logika pemformatan, multi-center, dan parsing.

- **Solusi Yang Diterapkan**:
  1. **Ekspansi Skema Database & Migrasi Aman (Zero Data Loss)**:
     - Memperbarui DigitalConfigEntity dengan kolom 	rxMode, smsCenterNumbers, smsTemplate, dan smsDelimiter.
     - Menaikkan versi database Room ke 11 dan mendaftarkan MIGRATION_10_11 (ALTER TABLE digital_configs ADD COLUMN) pada AppDatabase.kt.
  2. **Antarmuka Konfigurasi H2H & SMS Center Lengkap**:
     - Memperbarui dialog_h2h_config.xml dengan menambahkan selector mode gateway (API H2H, SMS Center, atau Otomatis Fallback).
     - Menambahkan form tambah nomor SMS Center (etNewSmsCenter, tombol + Tambah) dan kontainer list dinamis (ItemSmsCenterChipBinding) yang memungkinkan pengguna mendaftarkan banyak nomor center (Telkomsel, Indosat, XL, Tri) dan menghapusnya per item.
     - Menambahkan radio pilihan format perintah SMS: [KODE].[TUJUAN].[PIN], [KODE]#[TUJUAN]#[PIN], dan BELI [KODE] [TUJUAN] [PIN].
  3. **Logika UI & Validasi di AccountFragment.kt**:
     - Memvalidasi nomor SMS center sebelum ditambahkan (minimal 8 digit, cegah nomor duplikat).
     - Menampilkan notifikasi informatif saat nomor ditambahkan atau dihapus.
     - Menyimpan seluruh pengaturan secara atomik ke database Room lokal.
  4. **Modul Pembantu SmsGatewayHelper.kt**:
     - Menyediakan method ormatSmsCommand yang menghasilkan format pesan rapi ke SMS Center.
     - Menyediakan method createSendSmsIntent untuk meluncurkan aplikasi SMS bawaan Android dengan nomor dan isi pesan terisi otomatis tanpa butuh izin berbahaya SEND_SMS.
  5. **Pengujian & Kompilasi**:
     - Unit test DigitalGatewayAndSmsCenterTest.kt dibuat dan seluruh 6 skenario pengujian lolos 100%.
     - APK berhasil dikompilasi (ssembleDebug) dan dipasang ke perangkat target fisik (36ed10ed) dengan prinsip Zero Data Loss.
- **Catatan Tambahan**:
  - Menyematkan validasi ketat PIN Transaksi saat mode SMS Center / Auto-Fallback diaktifkan (etH2hPin.error jika kosong).
  - Menyediakan *live preview* format perintah SMS di dialog H2H (	vSmsFormatPreview) yang bereaksi otomatis saat PIN diubah atau format dipilih (KODE.TUJUAN.PIN).
  - Menghubungkan alur POS kasir ke layar sukses pembayaran (PaymentSuccessActivity): tombol 'Kirim ke SMS Center Server Pulsa' otomatis muncul jika terdapat produk digital yang ditransaksikan, lengkap dengan dialog pemilihan nomor center (Telkomsel/Indosat/XL) jika terdaftar lebih dari satu nomor.


---

### BUG-038: Kebutuhan Multi-Distributor Routing (PIN & SMS Center Berbeda Tiap Kategori: Pulsa, Kuota Data, PLN)
- **ID**: BUG-038
- **Tanggal**: 2026-11-01
- **Modul/Komponen**: Mobile / PPOB Multi-Distributor Routing & SMS Center
- **Tingkat Keparahan (Severity)**: HIGH
- **Status**: RESOLVED
- **Deskripsi Masalah**:
  1. *Kebutuhan Multi-Distributor*: Agen konter pulsa dan PPOB umumnya bermitra dengan beberapa server/distributor sekaligus untuk memaksimalkan margin keuntungan atau kestabilan server (contoh: Pulsa dialihkan ke Distributor 1 dengan PIN 1111 & Nomor SMS Center 1; Paket Data dialihkan ke Distributor 2 dengan PIN 2222 & Nomor SMS Center 2; Token PLN dialihkan ke Distributor 3 dengan PIN 3333 & Nomor SMS Center 3).
  2. *Keterbatasan Satu PIN & Nomor Tunggal*: DenKasir sebelumnya hanya memiliki satu konfigurasi SMS Center dan PIN transaksi global. Jika transaksi produk kategori berbeda dikirim, PIN yang terkirim akan salah atau format perintah ditolak oleh server distributor tujuan.
  3. *Integritas Skema Room Database*: Penambahan penyimpanan aturan distributor multi-server membutuhkan migrasi database Room `MIGRATION_11_12` yang aman tanpa mereset data pengguna (`Zero Data Loss`).

- **Dampak / Potensi Resiko**:
  - Agen tidak leluasa membagi transaksi ke distributor termurah per kategori.
  - Perintah transaksi SMS terkirim dengan PIN atau ke SMS Center yang keliru, menyebabkan transaksi ditolak server pulsa atau saldo terpotong di distributor yang salah.

- **Rekomendasi / Solusi Perbaikan**:
  1. Definisikan model data `DistributorRule` (`id`, `name`, `category`, `centerNumber`, `pin`, `smsTemplate`, `smsDelimiter`).
  2. Perluas `DigitalConfigEntity` dengan kolom `distributorRulesJson: String` dan daftarkan `MIGRATION_11_12` di `AppDatabase.kt` (naikkan versi DB dari 11 ke 12).
  3. Bangun modul UI dinamis:
     - `item_digital_distributor_card.xml`: Kartu informasi distributor dengan nama server, badge kategori, nomor center, PIN, live preview format, dan tombol aksi hapus.
     - `dialog_add_distributor.xml`: Dialog bottom sheet untuk tambah/edit rute distributor dengan live preview perintah SMS otomatis.
     - Bagian section rute khusus di `dialog_h2h_config.xml`.
  4. Implementasikan algoritma perutean cerdas `SmsGatewayHelper.findMatchingDistributor` yang mencocokkan kategori transaksi (Pulsa, Data/Kuota, Listrik PLN, Air PDAM, Uang Elektronik, Voucher Game, BPJS, atau fallback ke default).
  5. Hubungkan alur checkout kasir di `PosFragment.kt` dan layar sukses bayar di `PaymentSuccessActivity.kt`: tombol dinamis otomatis menampilkan nama distributor tujuan ("Kirim SMS ke [Nama Distributor]") serta mengarahkan SMS ke nomor center dan PIN yang sesuai.
  6. Buat unit test `MultiDistributorRoutingTest.kt` yang menguji skenario 3 distributor berbeda dengan 3 PIN dan center berbeda.

- **Solusi Yang Diterapkan**:
  1. **Model & Skema Database Room (MIGRATION_11_12)**:
     - Ditambahkan properti `distributorRulesJson` pada `DigitalConfigEntity`.
     - Ditambahkan `MIGRATION_11_12` (`ALTER TABLE digital_configs ADD COLUMN distributorRulesJson TEXT NOT NULL DEFAULT ''`) pada `AppDatabase.kt` dengan `Zero Data Loss`.
  2. **Logika Perutean Cerdas di SmsGatewayHelper.kt**:
     - Ditambahkan data class `DistributorRule` beserta fungsi serialisasi/deserialisasi JSON (`distributorRulesToJson`, `parseDistributorRules`).
     - Mengimplementasikan `findMatchingDistributor` dengan pencocokan kategori cerdas dan fallback ke aturan 'SEMUA' atau konfigurasi default.
  3. **Manajemen Rute Distributor di AccountFragment.kt**:
     - Ditambahkan antarmuka daftar distributor dinamis (`renderDistributorsList()`) dan dialog tambah/edit rute (`showAddDistributorDialog()`) dengan validasi input PIN dan nomor SMS center.
     - Penyimpanan aturan rute terintegrasi saat tombol Simpan Konfigurasi ditekan.
  4. **Ekspansi Intent POS & Transaksi di PaymentSuccessActivity.kt**:
     - `PosFragment.kt` menyertakan `DIGITAL_CATEGORY` pada intent pembayaran.
     - `PaymentSuccessActivity.kt` secara otomatis memanggil `SmsGatewayHelper.findMatchingDistributor`, memformat SMS dengan PIN distributor terkait, dan memperbarui label tombol menjadi `Kirim SMS ke [Nama Distributor]`.
  5. **Pengujian & Verifikasi**:
     - Unit test `MultiDistributorRoutingTest.kt` (6 test case) dan `DigitalGatewayAndSmsCenterTest.kt` lulus 100%.
     - Kompilasi APK (`assembleDebug`) sukses dan berhasil dipasang ke perangkat target fisik (`36ed10ed`) via ADB tanpa kehilangan data toko/produk.


---

### BUG-039: Produk Digital PPOB Hasil Impor Excel Tidak Tampil di Tab Pulsa Layar Kasir (POS)
- **ID**: BUG-039
- **Tanggal**: 2026-11-01
- **Modul/Komponen**: Mobile / POS Kasir & Digital PPOB Catalog Display
- **Tingkat Keparahan (Severity)**: HIGH
- **Status**: RESOLVED
- **Deskripsi Masalah**:
  1. *Katalog Digital Terisolasi*: Ribuan produk digital PPOB (Pulsa, Paket Data, Token PLN, E-Money, dll) yang berhasil diimpor dari berkas Excel masuk ke database lokal, namun tidak muncul ketika kasir membuka tab "Pulsa" di layar POS.
  2. *Nominal Statis / Mock*: Dialog `dialog_pulsa_input.xml` sebelumnya hanya menggunakan data hardcoded mock (`defaultNominals`: 5.000, 10.000, 20.000, dll) dan tidak menghubungkan query database Room `availableProducts` (`productType == 'DIGITAL'`).
  3. *ID Produk Dummy*: Tombol "+ Tambah ke Kasir" sebelumnya membuat entitas produk dummy dengan ID acak (`UUID.randomUUID()`) dan `providerCode = selectedOperator`, bukannya mengambil entitas produk riil yang memuat kode H2H asli (seperti `TREG10`, `PLN20`, `DANA50`).

- **Dampak / Potensi Resiko**:
  - Kasir tidak dapat melihat paket data, token listrik, atau nominal pulsa riil hasil impor.
  - Perintah transaksi SMS Gateway tidak memuat kode produk H2H yang benar, menyebabkan transaksi ditolak server distributor.

- **Rekomendasi / Solusi Perbaikan**:
  1. Perbarui `NominalOption` dengan referensi `productEntity: ProductEntity? = null`.
  2. Implementasikan `DigitalProductSearchAdapter` dan komponen pencarian instan `etSearchDigitalInDialog` pada Step 1 modal `dialog_pulsa_input.xml` sehingga kasir dapat mencari dari 2.500+ produk digital secara langsung.
  3. Tambahkan tombol "📋 Semua Produk" di Step 1 dan "📋 Semua Paket ({count})" di Step 2 untuk melihat dan memilih seluruh produk riil hasil impor.
  4. Implementasikan fungsi `getMatchingProductsFor`, `buildNominalOptions`, dan `refreshServiceNominals` pada `PosFragment.kt` yang secara dinamis memetakan produk riil dari Room Database ke tombol nominal berdasarkan kategori dan operator yang dipilih.
  5. Sambungkan penambahan keranjang pada `btnSubmitDigital` agar langsung menyertakan `ProductEntity` asli lengkap dengan ID, harga jual, harga modal, dan kode H2H.

- **Solusi Yang Diterapkan**:
  1. **Integrasi Komponen UI Pencarian & Penjelajah Katalog Digital**:
     - Memperbarui `dialog_pulsa_input.xml` dengan menambahkan search bar dialog, status badge counter katalog digital, dan tombol penjelajah seluruh produk digital.
     - Membuat layout `item_digital_search_result.xml` dan adapter `DigitalProductSearchAdapter.kt` untuk menampilkan hasil pencarian dengan badge kategori, kode H2H, harga jual, dan modal.
  2. **Nominal Dinamis Berdasarkan Database Riil di PosFragment.kt**:
     - `PosFragment.kt` secara otomatis memfilter `availableProducts` bertipe `DIGITAL` sesuai kategori dan operator yang aktif.
     - Mendeteksi operator secara langsung saat kasir mengetik nomor HP (0812 -> Telkomsel, 0857 -> Indosat, 0878 -> XL, dll) dan langsung memperbarui tombol-tombol nominal dengan produk riil operator tersebut.
     - Menyediakan dialog daftar lengkap ("📋 Semua Paket") untuk melihat rincian nama paket dan harga.
  3. **Integritas Data Transaksi Kasir**:
     - Tombol "+ Tambah ke Kasir" memasukkan entitas produk asli hasil impor dari database Room ke dalam keranjang belanja.
     - Kode produk H2H (`providerCode`) diteruskan secara akurat ke layar struk pembayaran (`PaymentSuccessActivity`) dan perintah SMS Center.
  4. **Pengujian & Deployment**:
     - Menambahkan unit test `testDynamicNominalOptionsFromImportedProducts` pada `DigitalServicesTest.kt` (seluruh pengujian lulus 100%).
     - Mem-build APK debug dan memasangnya ke perangkat Android fisik target (`36ed10ed`) dengan kepatuhan `Zero Data Loss`.

---

### BUG-040: Penanganan Status Respon Server Distributor PPOB (Sukses SN, Gangguan/Cut-Off, & Refund BKU)
- **ID**: BUG-040
- **Tanggal**: 2026-11-01
- **Modul/Komponen**: Mobile / POS Digital Transactions, History, Receipt & Accounting (BKU)
- **Tingkat Keparahan (Severity)**: HIGH
- **Status**: RESOLVED
- **Deskripsi Masalah**:
  1. *Ketiadaan Siklus Hidup Status Digital*: Transaksi PPOB digital (Pulsa, Paket Data, Token PLN) sebelumnya langsung disetel ke SUCCESS dengan Serial Number (SN) dummy acak pada saat checkout POS, mengabaikan fakta bahwa pengiriman via SMS Center atau API membutuhkan konfirmasi respon riil dari server distributor.
  2. *Ketiadaan Visibilitas Status di Riwayat Transaksi*: Daftar transaksi kasir (TransactionAdapter / item_transaction_history.xml) tidak memiliki badge status digital yang membedakan transaksi PROSES, SUKSES, atau GANGGUAN.
  3. *Tidak Ada Mekanisme Pencatatan SN Asli & Respon Gangguan*: Kasir tidak dapat memasukkan SN asli atau kode token PLN 20-digit dari SMS balasan server distributor, serta tidak ada aksi untuk menangani respon GANGGUAN / CUT-OFF dari server distributor.
  4. *Potensi Selisih Kas & Saldo Deposit (Distorsi BKU)*: Jika server distributor mengalami gangguan atau pulsa gagal terkirim, tidak ada mekanisme refund otomatis di BKU dan saldo deposit pulsa terpotong tanpa bisa dipulihkan, menyebabkan kerugian uang kasir warung.

- **Dampak / Potensi Resiko**:
  - Pelanggan menerima struk dengan nomor SN palsu padahal pulsa belum tentu masuk.
  - Pembeli token PLN tidak mendapatkan 20-digit token listrik sah untuk dimasukkan ke kWh meter.
  - Ketika server gangguan, kasir bingung cara membatalkan transaksi dan mengembalikan uang pelanggan tanpa merusak pembukuan kas fisik dan buku kas umum (BKU).

- **Rekomendasi / Solusi Perbaikan**:
  1. Ubah status awal checkout transaksi produk digital menjadi PROSES (menunggu balasan server) dan snNumber = null.
  2. Tambahkan badge status digital dinamis (badgeDigitalStatus) pada kartu riwayat transaksi kasir: PROSES, SUKSES, GANGGUAN, REFUND.
  3. Bangun modal dialog interaktif DigitalStatusDialogHelper dan layout dialog_update_digital_status.xml:
     - Opsi Sukses Terkirim: Memungkinkan kasir menginput nomor SN / 20-digit Token PLN riil (lengkap dengan tombol paste cerdas dari clipboard SMS balasan) dan mengupdate status ke SUKSES.
     - Opsi Server Gangguan / Gagal: Kasir memilih alasan kegagalan (Server Cut-Off, Nomor Salah, Stok Kosong) dan sistem secara otomatis:
       a) Mencatat transaksi keluar REFUND di BKU (cash_flows).
       b) Mengembalikan saldo deposit pulsa di DigitalConfigDao (topUpBalance).
       c) Mengubah status transaksi ke GANGGUAN.
  4. Sediakan tombol aksi cepat 'Catat Respon Server (SN / Gangguan)' di layar sukses pembayaran (PaymentSuccessActivity.kt) dan detail riwayat transaksi kasir (TransactionsFragment.kt).
  5. Perbarui ReceiptFormatterUtils.kt agar mencetak status (SUKSES, PROSES, GANGGUAN) dan nomor SN / 20-digit Token PLN resmi pada struk Bluetooth thermal dan WhatsApp.
  6. Buat unit test DigitalTransactionStatusTest.kt yang menguji parsing SMS balasan, siklus transisi status, refund kas BKU, dan perenderan struk.

- **Solusi Yang Diterapkan**:
  1. **DAO & Query Room Atomic**:
     - Menambahkan query updateDigitalItemStatus, updateTransactionStatus, dan getTransactionById pada TransactionDao (Daos.kt).
  2. **Indikator Badge Status di Kartu Transaksi**:
     - Menambahkan badgeDigitalStatus pada item_transaction_history.xml dan binding dinamis dengan styling warna status di TransactionAdapter.kt.
  3. **Manajemen Respon Server & Refund di DigitalStatusDialogHelper.kt**:
     - Mengembangkan DigitalStatusDialogHelper.kt dengan dukungan regex ekstraksi SN/Token otomatis (extractSnFromSms), pembaruan status atomik, jurnal kas keluar REFUND, serta pemulihan saldo deposit.
  4. **Pembaruan Struk Digital di ReceiptFormatterUtils.kt**:
     - Format struk belanja otomatis menampilkan baris No. HP / ID Pelanggan, Status: SUKSES/PROSES/GANGGUAN, dan Token : [20-Digit] atau SN : [Nomor SN].
  5. **Pengujian & Deployment APK**:
     - Seluruh unit test pada DigitalTransactionStatusTest.kt (5 pengujian) lulus 100%.
     - APK debug dikompilasi (assembleDebug) dan dipasang ke perangkat fisik (36ed10ed) dengan kepatuhan Zero Data Loss.

---

### BUG-041: Fitur Auto Kirim SMS di Latar Belakang & Dukungan Pemilihan Slot Dual SIM (SIM 1 / SIM 2)
- **ID**: BUG-041
- **Tanggal**: 2026-11-01
- **Modul/Komponen**: Mobile / H2H SMS Gateway, Telephony Dual SIM & Background Dispatcher
- **Tingkat Keparahan (Severity)**: HIGH
- **Status**: RESOLVED
- **Deskripsi Masalah**:
  1. *Ketergantungan Pembukaan Aplikasi SMS Eksternal*: Sebelumnya, transaksi pulsa/PPOB jalur SMS Center selalu membuka aplikasi SMS bawaan Android (ACTION_SENDTO), mewajibkan kasir menekan tombol kirim di aplikasi luar, lalu kembali lagi ke DenKasir secara manual. Alur ini lambat dan tidak efisien untuk warung yang ramai transaksi.
  2. *Ketiadaan Dukungan Multi-SIM (Dual SIM)*: Handphone merchant/kasir di Indonesia mayoritas menggunakan Dual SIM (contoh: SIM 1 Telkomsel, SIM 2 Indosat/XL) untuk mendapatkan tarif SMS sesama operator yang murah/gratis ke distributor terkait. Sebelumnya sistem tidak menyediakan opsi untuk memilih apakah perintah dikirim via SIM 1 atau SIM 2.
  3. *Ketiadaan Pemetaan SIM per Distributor*: Tidak ada pengaturan untuk mengunci SIM Card per aturan rute distributor (misal: Pulsa Telkomsel wajib lewat SIM 1, Kuota Indosat wajib lewat SIM 2).

- **Dampak / Potensi Resiko**:
  - Kasir sering keliru mengirim perintah transaksi lewat SIM card yang salah, memicu pulsa SMS tersedot mahal atau perintah ditolak server distributor karena nomor pengirim tidak terdaftar di whitelist distributor tersebut.
  - Alur transaksi menjadi lama karena kasir harus bolak-balik berpindah antar aplikasi.

- **Rekomendasi / Solusi Perbaikan**:
  1. Tambahkan izin SEND_SMS dan READ_PHONE_STATE pada AndroidManifest.xml.
  2. Perluas DigitalConfigEntity dengan preferredSimSlot: Int = -1 (Auto/Default, 0 = SIM 1, 1 = SIM 2) dan utoSendSms: Boolean = true.
  3. Naikkan database Room ke versi 13 dengan migrasi aman MIGRATION_12_13 (Zero Data Loss).
  4. Perluas DistributorRule dengan properti simSlot: Int = -1 (Auto, SIM 1, SIM 2) yang terserialisasi ke JSON.
  5. Kembangkan SmsGatewayHelper.getActiveSimCards(context) untuk mendeteksi nama operator dari SubscriptionManager serta sendDirectSms(...) menggunakan SmsManager multi-SIM (createForSubscriptionId / getSmsManagerForSubscriptionId).
  6. Perbarui dialog_h2h_config.xml, dialog_add_distributor.xml, dan item_digital_distributor_card.xml dengan radio group pilihan SIM 1 / SIM 2, deteksi live nama operator SIM, serta switch toggle Direct SMS.
  7. Hubungkan PaymentSuccessActivity.kt dengan smsPermissionLauncher: jika auto-send aktif, SMS terkirim seketika di latar belakang via SIM yang dipilih tanpa perlu membuka aplikasi SMS luar (dengan fallback cerdas jika izin ditolak).
  8. Tambahkan unit test komprehensif pada DigitalGatewayAndSmsCenterTest.kt.

- **Solusi Yang Diterapkan**:
  1. **Manifest & Permission Handling**:
     - Ditambahkan izin SEND_SMS, READ_PHONE_STATE, dan fitur ndroid.hardware.telephony (optional).
  2. **Room Database Migration 12 -> 13 (Zero Data Loss)**:
     - Ditambahkan kolom preferredSimSlot dan utoSendSms pada tabel digital_configs melalui MIGRATION_12_13.
  3. **Engine Dual SIM & Background SMS di SmsGatewayHelper.kt**:
     - Mengembangkan data class SimCardInfo dan DirectSmsResult.
     - Implementasi getActiveSimCards dan sendDirectSms dengan dukungan multipart text message dan routing subscription ID SIM 1 & SIM 2.
  4. **Antarmuka Konfigurasi & Visualisasi Kartu**:
     - Menambahkan radio pilihan SIM 1 / SIM 2 pada dialog_h2h_config.xml dan dialog_add_distributor.xml.
     - Menampilkan badge visual SIM pada kartu distributor di item_digital_distributor_card.xml.
  5. **Pengiriman Instan di Layar Transaksi**:
     - Tombol pada PaymentSuccessActivity.kt otomatis menampilkan label SIM tujuan (misal: "🚀 Kirim SMS Otomatis ke [Distributor] (SIM 1)").
     - Mengirim perintah SMS seketika di latar belakang dengan umpan balik Toast informatif.
  6. **Pengujian & Deployment**:
     - Unit test DigitalGatewayAndSmsCenterTest.kt diperluas dengan pengujian dual SIM dan routing distributor, seluruh pengujian lulus 100%.
     - APK debug dikompilasi (ssembleDebug) dan berhasil dipasang ke perangkat target fisik (36ed10ed) via ADB tanpa mereset database.

---

### BUG-042: Modernisasi Tampilan Pilihan Paket & Nominal Digital Menjadi Kartu Informasi Lengkap Bertema Putih Modern

- **Tanggal**: 2026-11-01
- **Modul/Komponen**: Mobile / POS Digital Services UI & Package Cards Formatting
- **Tingkat Keparahan (Severity)**: MEDIUM
- **Status**: RESOLVED
- **Deskripsi Masalah**:
  1. *Grid Sempit & Truncation Teks Parah*: Sebelumnya pilihan paket pulsa/data/PLN menggunakan GridLayoutManager 4-kolom (item_nominal_button.xml), sehingga nama produk terpotong maksimal 15 karakter (contoh: "1,000 (Pulsa ..", "Cek Hutang Pu..").
  2. *Ketiadaan Informasi Penting Paket*: Pengguna/kasir tidak dapat melihat besaran kuota (GB/MB), masa berlaku (hari/bulan), harga promo coret, maupun kode distributor langsung pada daftar pilihan.
  3. *Kebutuhan Tampilan Kartu Modern Sesuai Referensi*: Pengguna meminta format kartu modern seperti pada Image #2 dengan badge oranye "Muraaah", 3 kolom data (Kuota, Masa berlaku, Harga), bar bawah dengan nama paket dan ikon info, dengan instruksi wajib: kartu tetap bertema putih bersih (#FFFFFF).

- **Dampak / Potensi Resiko**:
  - Kasir kesulitan dan rentan salah memilih jenis paket data atau nominal yang diminta pelanggan.
  - Tampilan kasir digital terkesan kuno dan tidak informatif dibandingkan aplikasi modern.

- **Rekomendasi / Solusi Perbaikan**:
  1. Buat layout kartu modern item_digital_package_card.xml berbasis MaterialCardView bertema putih bersih (#FFFFFF, stroke #E2E8F0, corner radius 14dp) dan seleksi border ungu #3E24E2 dengan soft tint #F8F7FF.
  2. Buat drawable badge oranye g_badge_muraaah.xml dengan corner radius asimetris untuk efek bubble/pita seperti Image #2, serta vector icon ic_info_circle.xml.
  3. Perluas data model NominalOption dengan properti opsional default (quotaLabel, alidityLabel, originalPrice, adgeText, column1Title, column2Title).
  4. Bangun parser cerdas DigitalPackageFormatter.kt yang mengekstrak rincian kuota (GB/MB/Menit), masa berlaku (hari/bulan), kalkulasi harga coret promo, dan nama produk bersih.
  5. Perbarui NominalGridAdapter.kt untuk mengikat kartu modern, merender harga coret + harga riil hijau tebal, serta menangani aksi klik kartu dan klik info icon.
  6. Ubah PosFragment.kt agar 
vNominalGrid menggunakan LinearLayoutManager(requireContext()) dan terhubung dengan dialog detail paket digital saat tombol info ditekan.
  7. Tulis unit test komprehensif pada DigitalPackageCardUiTest.kt.

- **Solusi Yang Diterapkan**:
  1. **Aset Drawable & Layout Kartu Modern**:
     - mobile/app/src/main/res/drawable/bg_badge_muraaah.xml: Shape oranye solid #F97316 dengan corner asimetris (kiri atas 14dp, kanan bawah 12dp).
     - mobile/app/src/main/res/drawable/ic_info_circle.xml: Ikon lingkaran info Material vector #94A3B8.
     - mobile/app/src/main/res/layout/item_digital_package_card.xml: Kartu putih modern dengan badge oranye, 3 kolom bergaris pemisah rapi, dan bar bawah berlatar #F8FAFC.
  2. **Data Model & Parser Paket**:
     - NominalOption diperluas dengan parameter default non-breaking.
     - DigitalPackageFormatter.kt memetakan kuota, masa aktif, harga coret promo, badge, dan judul bersih untuk seluruh varian produk pulsa, paket data, PLN, saldo, dan tagihan.
  3. **Adapter & Integrasi POS Fragment**:
     - NominalGridAdapter.kt mendukung styling unselected (latar #FFFFFF, border #E2E8F0) vs selected (latar #F8F7FF, border #3E24E2).
     - PosFragment.kt mengonfigurasi 
vNominalGrid.layoutManager = LinearLayoutManager(requireContext()) dan menyematkan showPackageDetailDialog.
  4. **Pengujian & Deployment Zero Data Loss**:
     - Unit test DigitalPackageCardUiTest.kt menguji parsing data ByU 10GB 5 Hari, paket nelpon 0 GB, pulsa, token PLN, opsi custom "Lainnya", dan auto-enrichment default nominals (semua lulus 100%).
     - APK berhasil dikompilasi (ssembleDebug) dan dipasang ke perangkat target fisik (36ed10ed) via db install -r tanpa mereset data pengguna.

---

### BUG-043: Pembenahan Alur Berjenjang Layanan Digital PDAM (Input ID -> Provinsi -> Kabupaten/Kota) dan Normalisasi Nama Paket Pulsa/Data

- **Tanggal**: 2026-11-01
- **Modul/Komponen**: Mobile / POS Digital Services (PDAM Hierarchical Flow & Pulse Search Logic)
- **Tingkat Keparahan (Severity)**: HIGH
- **Status**: RESOLVED
- **Deskripsi Masalah**:
  1. *Kejanggalan & Kegagalan Kategori PDAM*: Menu Layanan Digital tab Pulsa ([Image #1]) sebelumnya tidak menghubungkan produk PDAM ke 200 kabupaten riil di katalog database, sehingga tidak dapat dipesan kasir. Pengguna menghendaki alur berjenjang resmi seperti pada portal Isipulsa: Masukkan ID Pelanggan -> Pilih Provinsi -> Pilih Kabupaten/Kota -> Muncul Produk & Biaya Loket.
  2. *Anomali Data Impor Scraper pada Nama Kabupaten*: Ditemukan 203 entri produk PDAM scraper yang terpotong dengan format [Tagihan PDAM ...] Harga: Rp -100 yang mengotori dropdown kabupaten dengan teks anomali harga minus alih-alih nama kabupaten resmi.
  3. *Nama Paket Pulsa Terpotong*: Beberapa varian pulsa mentah scraper menyimpan nama asli di providerCode dan namanya diawali Harga: Rp ..., menyebabkan nama kartu di POS tidak representatif dan harga jualnya tidak realistis.
  4. *Kebutuhan Live Filter Search*: Kasir memerlukan live search instan untuk mencari nominal atau besaran kuota (misal: '10GB', '50', '30 Hari') langsung di atas kartu pilihan paket.

- **Dampak / Potensi Resiko**:
  - Pelanggan tidak dapat membayar tagihan air PDAM di loket kasir toko.
  - Tampilan pilihan kabupaten membingungkan dan tidak profesional.
  - Kasir rentan salah memilih jenis pulsa/paket karena nama dan harga yang terpotong.

- **Rekomendasi / Solusi Perbaikan**:
  1. Integrasikan kontainer berjenjang Kabupaten/Kota di dialog_pulsa_input.xml (containerKabupatenSection, containerKabupatenDropdown, 	vSelectedKabupaten) khusus untuk kategori regional (isRegional = true).
  2. Definisikan 11 wilayah/provinsi resmi di DigitalCategoryItem.kt untuk PDAM se-Indonesia.
  3. Bersihkan fungsi cleanKabupatenName dan getKabupatenListForProvince di PosFragment.kt agar menyaring entri Harga: Rp dan menyajikan 200 kabupaten resmi secara bersih dan alfabetis.
  4. Bangun parser otomatis sanitizeProductEntity di DigitalPackageFormatter.kt yang mengembalikan nama paket asli dari deskripsi providerCode, modal riil, kode SMS/H2H sah, serta menghitung harga jual realistis dengan margin laba kasir.
  5. Sediakan input pencarian instan etSearchNominalPackage pada dialog pilihan paket dengan pemfilteran realtime responsif.
  6. Format catatan pesanan kasir dengan format resmi [ID] (Kabupaten - KODE).
  7. Tulis unit test komprehensif di DigitalServicesTest.kt.

- **Solusi Yang Diterapkan**:
  1. **Layout & Dialog Berjenjang PDAM**:
     - dialog_pulsa_input.xml dilengkapi dropdown provinsi & kabupaten/kota dengan dialog pilihan AlertDialog.
     - Live detection nomor pelanggan & operator seluler.
     - Kolom live search nominal (etSearchNominalPackage) yang langsung memfilter kartu paket modern putih.
  2. **Filter & Normalisasi Data PDAM & Pulsa**:
     - PosFragment.kt: Mengeliminasi produk anomali harga minus dan memetakan 200 kabupaten resmi se-Indonesia ke masing-masing 11 wilayah provinsi.
     - DigitalPackageFormatter.kt: Menambahkan sanitizeProductEntity untuk menormalkan varian pulsa/paket data (mengekstrak nama asli seperti Pulsa 5,000, Pulsa 10,000 (Promo) dari providerCode, harga modal riil, dan harga jual wajar).
  3. **Penyesuaian Keranjang & Gateway Transaksi**:
     - PDAM ditambahkan ke keranjang kasir dengan biaya admin loket Rp 2.000, nomor ID pelanggan, nama kabupaten, dan kode H2H yang siap diproses gateway SMS / OtomaX.
  4. **Pengujian & Deployment Zero Data Loss**:
     - Seluruh unit test pada DigitalServicesTest.kt dan suite pengujian lokal lainnya lulus 100%.
     - APK debug dikompilasi dan dipasang ke perangkat target 36ed10ed via db install -r -d dengan menjaga 100% data toko tetap utuh (Zero Data Loss).
     - Verifikasi visual di device membuktikan pemilihan Kabupaten Bogor, Kota Depok, dan pencarian pulsa bekerja presisi.

---

### BUG-044: Otomatisasi Deteksi Operator dengan Badge Logo Brand Dinamis (by.U, Telkomsel, Indosat, XL, Axis, Tri, Smartfren) & Validasi Data Keselarasan PPOB

- **Tanggal**: 2026-11-01
- **Modul/Komponen**: Mobile / POS Digital Services (Brand Logo Badge & Operator Prefix Intelligence)
- **Tingkat Keparahan (Severity)**: MEDIUM
- **Status**: RESOLVED
- **Deskripsi Masalah**:
  1. *Ketiadaan Umpan Balik Visual Logo Operator*: Saat kasir memasukkan nomor ponsel pelanggan pada layanan Pulsa / Paket Data, sistem sebelumnya hanya menampilkan teks operator tanpa ikon atau badge visual brand.
  2. *Dukungan Spesifik Kartu by.U*: Prefix 0851 (by.U) belum dipetakan secara mandiri, sehingga varian pulsa dan kuota data By.U (140 produk) tidak langsung difilter secara spesifik saat nomor dimasukkan.
  3. *Validasi Keselarasan Data*: Diperlukan validasi komparasi data katalog, alur transaksi, dan format kartu dengan portal referensi https://st.riungan.my.id/.

- **Dampak / Potensi Resiko**:
  - Kasir kesulitan memverifikasi ketepatan operator pelanggan secara visual sebelum mengirim transaksi.
  - Pengguna kartu by.U tidak mendapatkan katalog paket data khusus by.U secara otomatis.

- **Rekomendasi / Solusi Perbaikan**:
  1. Sediakan 7 aset vector drawable logo resmi operator: ic_operator_byu, ic_operator_telkomsel, ic_operator_indosat, ic_operator_xl, ic_operator_axis, ic_operator_tri, ic_operator_smartfren.
  2. Tambahkan kontainer badge visual operator (containerDetectedOperatorBadge) di dalam input box nomor tujuan (etTargetNumber), serta ikon operator pada dropdown operator (ivSelectedOperatorLogo).
  3. Petakan prefix 0851 ke operator By.U pada fungsi detectOperator dan pisahkan katalog By.U dengan Telkomsel pada getMatchingProductsFor.
  4. Terapkan fungsi updateOperatorBadgeAndIcon di PosFragment.kt dengan pill styling warna brand (GradientDrawable dinamis) yang otomatis berganti realtime saat nomor diketik.
  5. Lakukan uji verifikasi dan validasi dump uiautomator pada perangkat Android target fisik (7217363c0804).

- **Solusi Yang Diterapkan**:
  1. **Aset Vector & Layout**:
     - 7 file vector drawable logo operator dibuat tajam dan kompatibel pada resolusi apapun di mobile/app/src/main/res/drawable/.
     - dialog_pulsa_input.xml diperbarui dengan containerDetectedOperatorBadge (icon + text brand) dan ivSelectedOperatorLogo.
  2. **Logika Prefix & Pemisahan Katalog**:
     - PosFragment.kt mengenali 0851 seketika sebagai By.U dan memuat 140 varian produk pulsa & kuota data By.U secara akurat.
     - Nomor Telkomsel reguler (0811, 0812, 0813, 0821, 0822, 0852, 0853, 0823) memunculkan badge merah Telkomsel.
     - Indosat (0814-0816, 0855-0858) memunculkan badge amber Indosat.
     - XL (0817-0819, 0859, 0877-0878) memunculkan badge biru XL.
  3. **Pengujian & Validasi**:
     - Seluruh unit test suite lulus 100%.
     - APK berhasil dibuild dan dipasang ke perangkat fisik Redmi 5 Plus (7217363c0804).
     - Dump uiautomator membuktikan containerDetectedOperatorBadge dengan icon By.U tosca dan teks by.u aktif sempurna di samping nomor 085154001234.

---

### BUG-045: Hardening Keamanan, Anti-DoS Cache Pollution, & Sanitasi Input pada Endpoint Katalog Produk Digital (`/api/isipulsa/kasir-catalog`)

- **Tanggal**: 2026-11-04
- **Modul/Komponen**: Backend / API Katalog Produk Digital (`st.riungan.my.id` - `server.js` & `isipulsa.js`)
- **Tingkat Keparahan (Severity)**: HIGH
- **Status**: RESOLVED
- **Deskripsi Masalah**:
  1. *Potensi Abuse Force-Refresh (Outbound Scraping DoS)*: Parameter `refresh=true` / `force=true` pada endpoint publik berpotensi dipanggil berulang kali oleh pihak eksternal untuk membanjiri request scraping ke server hulu dan membebani I/O disk.
  2. *Potensi Cache-Busting & Manipulasi Parameter Query (`margin`, `category`, `since`)*: Nilai `margin` negatif (misal `-999999`) dapat menghasilkan harga jual kasir negatif/di bawah modal, dan variasi parameter acak berpotensi memenuhi memori cache jika disimpan per kombinasi query.
  3. *Sanitasi Data Hulu & Harga Dummy Inquiry*: Terdapat 24 item inquiry/cek dengan harga dummy ekstrem (`> Rp 5.000.000` hingga `Rp 5.000.000.000`) serta teks deskripsi berformat HTML mentah dari server hulu.

- **Dampak / Potensi Resiko**:
  - Serangan banjir request (DoS) dapat menyebabkan IP server diblokir oleh penyedia hulu atau lonjakan CPU/RAM.
  - Harga jual negatif atau produk cek bernilai miliaran rupiah dapat merusak perhitungan transaksi kasir jika masuk ke database toko.

- **Rekomendasi / Solusi Perbaikan**:
  1. Terapkan **Rate Limiting per IP** (`checkApiRateLimit`) pada `/api/isipulsa/kasir-catalog` (maks 30 req/menit) dan `/api/isipulsa/refresh-catalog` (maks 3 req/10 menit) serta **Cooldown wajib 15 menit** (`MIN_FORCE_COOLDOWN_MS`) untuk force-refresh.
  2. Batasi metode HTTP pada `/api/isipulsa/kasir-catalog` dan `/api/isipulsa/catalog-status` hanya untuk `GET`/`HEAD` (`405 Method Not Allowed` untuk metode lain) dan hapus trigger `refresh=true` dari endpoint baca katalog.
  3. Gunakan **Single Base Snapshot Cache** (`cachedBaseSnapshot`) yang hanya di-build 1 kali per siklus 24 jam sehingga variasi query tidak pernah menggandakan penggunaan RAM.
  4. Validasi dan batasi (`clamp`) `margin` pada rentang aman `0` s/d `50000`, whitelist karakter `category` (`^[a-zA-Z0-9 _&-]{1,32}$`) dan `since` (`^[0-9T:.\-Z+]+$`), sanitasi seluruh tag HTML/karakter kontrol (`sanitizePlainText`), serta filter otomatis item inquiry/dummy (`rawModal > 5000000` atau `<= 0` non-pascabayar).

- **Solusi Yang Diterapkan**:
  - Seluruh lapisan proteksi (Rate Limiter, Cooldown 15 menit, Method Whitelist, Input Clamping & Regex Whitelist, HTML/Control-Char Stripping, Single Base Snapshot Cache, dan Gzip Compression) telah diterapkan pada `D:\SERVER\den-import\webapp\services\isipulsa.js` dan `D:\SERVER\den-import\webapp\server.js` serta diverifikasi dengan pengujian injeksi & flood test.

---

### BUG-046: Integrasi Penuh Katalog Produk Digital via API (`https://st.riungan.my.id/api/isipulsa/kasir-catalog`) & Perbaikan Reset Stok Digital Saat Edit Manual

- **Tanggal**: 2026-11-04
- **Modul/Komponen**: Mobile / SyncRepository, ProductsFragment, PosFragment, DataExporterImporter, AppDatabase
- **Tingkat Keparahan (Severity)**: HIGH
- **Status**: RESOLVED
- **Deskripsi Masalah**:
  1. *Sumber Katalog Produk Digital Terpisah dari API 24 Jam*: Sebelumnya produk digital di aplikasi masih tercampur dari seed asset lokal (`katalog_minimarket.json`) atau impor spreadsheet manual, sehingga ketika harga modal atau status gangguan di `st.riungan.my.id` diperbarui setiap 24 jam, aplikasi kasir tidak ikut memperbarui harga dan status gangguan secara otomatis.
  2. *Bug Reset Stok Produk Digital Saat Edit Harga Jual (`ProductsFragment.kt`)*: Pada `showProductFormBottomSheet`, penyimpanan produk bertipe `DIGITAL` sebelumnya memaksa `stock = 0.0`, yang menyebabkan fungsi `isDigitalOutOfStock()` mendeteksi produk tersebut sebagai "Stok Kosong / Gangguan" setelah diedit pemilik toko.
  3. *Beban Payload Push Sync Retail*: Pengiriman seluruh produk ke `/api/v1/sync/push` tanpa memfilter item katalog `isipulsa_*` berpotensi membebani sinkronisasi transaksi retail dengan 7.809 item digital.

- **Dampak / Potensi Resiko**:
  - Harga modal dan status gangguan layanan digital di kasir tidak sinkron dengan server IsiPulsa 24 jam.
  - Produk digital yang diedit harga jualnya oleh pemilik toko menjadi terkunci ("Stok Kosong") di menu Kasir.

- **Solusi Yang Diterapkan**:
  1. **Integrasi Eksklusif Katalog Digital dari API (`SyncRepository.syncDigitalCatalog`)**:
     - Menarik 7.809 produk digital (`productType = "DIGITAL"`) langsung dari `https://st.riungan.my.id/api/isipulsa/kasir-catalog` lengkap dengan delta-check `?since=last_updated_at`.
     - Menyimpan secara atomik (`db.withTransaction`) hanya untuk produk `DIGITAL` tanpa menyentuh produk `RETAIL` sedikit pun.
     - Memisahkan `seedProductsFromAsset` (`AppDatabase.kt`) dan impor Excel/CSV (`DataExporterImporter.kt`) agar murni hanya memproses produk fisik (`RETAIL`).
  2. **Proteksi Harga Kustom (`CUSTOM_PRICE`) & Perbaikan Stok Edit Digital**:
     - Memperbaiki `ProductsFragment.kt` agar mempertahankan `existingProduct.stock` (atau `999.0` untuk produk digital baru) dan menandai `syncStatus = "CUSTOM_PRICE"` saat pemilik toko mengubah harga jual manual, sehingga sinkronisasi 24 jam berikutnya tetap menjaga harga jual toko selama masih di atas harga modal terbaru.
  3. **Auto-Sync & Tombol Sync Manual**:
     - Menambahkan auto-sync saat aplikasi dibuka (`MainActivity`), saat membuka tab Digital (`ProductsFragment`), saat membuka menu Isi Ulang & Tagihan (`PosFragment`), serta tombol manual **"Sync Katalog API"**.

---

### BUG-047: Implementasi Jalur Transaksi H2H Terpusat via API (`https://st.riungan.my.id/api/isipulsa/h2h-order`) & Otomasi SN/Token di Struk Kasir

- **Tanggal**: 2026-11-04
- **Modul/Komponen**: Backend (`st.riungan.my.id` - `server.js` & `isipulsa.js`) / Mobile (`PaymentSuccessActivity`, `DenKasirApi`, `Daos`)
- **Tingkat Keparahan (Severity)**: HIGH
- **Status**: RESOLVED
- **Deskripsi Masalah**:
  1. *Ketergantungan Kartu SIM pada Perangkat Kasir / Tablet*: Sebelumnya transaksi pulsa & PPOB di `PaymentSuccessActivity` mengharuskan HP kasir memiliki kartu SIM dan pulsa SMS aktif untuk mengirim format perintah ke SMS Center distributor. Perangkat Tablet WiFi-only atau kasir multi-device tidak dapat mengeksekusi transaksi pulsa langsung ke distributor.
  2. *Ketiadaan Jalur Eksekusi H2H API Terpusat*: Di `st.riungan.my.id` belum tersedia endpoint transaksi aman yang menerima pesanan H2H dari aplikasi kasir dan meneruskannya ke jalur SMS Gateway (`sms-gate.app`) atau saldo web IsiPulsa.
  3. *Otomasi Pencetakan SN / Token PLN*: Nomor SN / Token PLN dari balasan distributor sebelumnya harus diinput manual oleh kasir lewat dialog status sebelum bisa dicetak ke struk.

- **Dampak / Potensi Resiko**:
  - Tablet WiFi kasir tidak bisa memproses pesanan pulsa/PPOB.
  - Kasir rentan salah mengetik 20 digit nomor token listrik pelanggan jika dilakukan secara manual.

- **Solusi Yang Diterapkan**:
  1. **Endpoint Transaksi H2H di `st.riungan.my.id` (`POST /api/isipulsa/h2h-order`)**:
     - Dilindungi autentikasi header `X-API-Key: DENKASIR-H2H-LIVE` dan Rate Limiter (maks 60 req/menit).
     - Otomatis merakit format SMS Center (`{KODE}.{TUJUAN}.{PIN}`) dari `productCodesMap` dan meneruskannya via `forwardOrderToIsiPulsa` / SMS Gateway.
  2. **Eksekusi Otomatis di `PaymentSuccessActivity.kt`**:
     - Saat kasir menyelesaikan transaksi produk digital, sistem langsung menembak `postH2hOrder` ke `https://st.riungan.my.id/api/isipulsa/h2h-order`.
     - Ketika server merespon `SUCCESS`, nomor **SN / Token Listrik PLN** otomatis diinject ke database transaksi lokal (`updateItemDigitalResult`), struk preview langsung memuat `SN/TOKEN: ...`, dan tombol berubah hijau *"? Transaksi Berhasil (SN: ...)"*.
     - Jika mode diset `AUTO_FALLBACK` dan server internet offline, aplikasi otomatis beralih (*fallback*) ke jalur SMS seluler lokal di perangkat.

### BUG-048: Penambahan Adapter Endpoint Cloud Sync (/api/v1/sync/push, /pull, /api/v1/auth/login/json, & /health) pada Den-Data-Server (https://d1.riungan.my.id/)
- **ID**: BUG-048
- **Tanggal**: 07 November 2026
- **Modul/Komponen**: Backend / Sync / Den-Data-Server (d1.riungan.my.id)
- **Tingkat Keparahan (Severity)**: HIGH
- **Status**: RESOLVED
- **Deskripsi Masalah**:
  Server basis data dan API terpusat publik di `https://d1.riungan.my.id/` (proyek `Den-Data-Server`) awalnya hanya memiliki endpoint otentikasi per-pos (`/pos-login`), scanner barcode katalog fisik (`/catalog/products/:barcode`), dan endpoint inventori toko mandiri. Server belum memiliki adapter endpoint sinkronisasi cloud yang kompatibel langsung dengan kontrak data APK kasir DenKasir Android (`POST /api/v1/auth/login/json`, `POST /api/v1/sync/push`, `POST /api/v1/sync/pull`, dan `GET /health`). Hal ini membuat fitur sinkronisasi cloud di aplikasi kasir tidak dapat langsung dihubungkan ke `https://d1.riungan.my.id/`.
- **Dampak / Potensi Resiko**:
  Jika kasir mencoba menghubungkan aplikasi kasir ke `https://d1.riungan.my.id/`, proses login JSON dan pengunggahan batch transaksi offline, inventori lokal, mutasi BKU, serta hutang-piutang akan mengalami kegagalan (HTTP 404 / 401).
- **Rekomendasi / Solusi Perbaikan**:
  1. Menambahkan fungsi `loginJson` di `src/controllers/authController.js` dan endpoint `POST /api/v1/auth/login/json` pada `src/routes/authRoutes.js` yang mendukung format kredensial APK kasir (mengembalikan JWT token, user_id, role, store_id, dan store_name).
  2. Membuat `src/controllers/syncController.js` dengan fungsi `processSyncPush` (idempotent upsert produk ke `store_products` & `store_inventories`, transaksi ke `pos_transactions` & `pos_transaction_items`, mutasi kas ke `store_cash_flows` & `store_cash_ledger`, piutang ke `store_debts`, stock opname ke `store_stock_opnames`, serta pencatatan ke `sync_logs`) dan `processSyncPull` (menarik delta data produk dan transaksi sejak `last_synced_at`).
  3. Mendaftarkan router `src/routes/syncRoutes.js` dan me-mount di `server.js` pada path `/api/v1/sync`.
  4. Menambahkan route health check cepat `GET /health` di `server.js` untuk verifikasi konektivitas APK kasir sebelum sinkronisasi.
  5. Menambahkan skema tabel sinkronisasi cloud pada `schema.sql` dan inisialisasi SQLite di `src/config/db.js` serta memastikan akun default store dan owner aktif.
  6. Menguji langsung interaksi autentikasi, push sinkronisasi, dan pull data via domain publik `https://d1.riungan.my.id/` dan memastikan seluruh pengujian berhasil (`status: SUCCESS`).
