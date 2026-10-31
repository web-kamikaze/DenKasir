# PLAN.md - Rencana Induk & Spesifikasi Sistem DenKasir

Aplikasi POS & Pembukuan Kasir Warung Kelontong + Produk Digital (Pulsa/PPOB) dengan arsitektur **Offline-First Android Kotlin** dan sinkronisasi **Cloud FastAPI + PostgreSQL**.

---

## 1. Ikhtisar Fitur Utama

### A. Manajemen Toko & Role Pengguna (Multi-Toko)
- **Multi Toko**: 1 akun owner bisa memiliki beberapa cabang toko warung.
- **Role Permissions**:
  - `OWNER`: Full akses ke seluruh cabang, BKU master, HPP/laba, manajemen produk, konfigurasi API Pulsa H2H, manajemen karyawan/kasir, ekspor/impor Excel, dan pengaturan server.
  - `KASIR`: Akses terbatas ke POS transaksi, scan barcode, penjualan pulsa, cetak struk bluetooth, riwayat transaksi shift aktif, dan buka/tutup kasir.

### B. Manajemen Produk (Retail & Digital Pulsa)
- **Produk Fisik (Retail Kelontong)**:
  - Barcode scanner (CameraX + ML Kit), Nama produk, Kategori, Harga Modal (HPP), Harga Jual.
  - **Multi-Satuan & Grosir**: Konversi satuan (cth: 1 Dus = 40 Pcs, 1 Renceng = 10 Pcs), harga eceran vs grosir.
  - Minimal stok alert (notifikasi saat stok menipis).
- **Produk Digital (Pulsa / PPOB / Paket Data / Token PLN via H2H OtomaX / Distributor API)**:
  - Input nomor tujuan pelanggan (auto-detect operator: Telkomsel, Indosat, XL, Tri, Smartfren, PLN).
  - Integrasi API H2H / OtomaX / Provider Pulsa:
    - Cek saldo deposit server pulsa.
    - Cek harga produk real-time dari provider.
    - Eksekusi transaksi otomatis via API saat kasir klik "Proses Bayar".
    - Penyimpanan Serial Number (SN) / Token PLN untuk dicetak di struk kasir atau dikirim via WhatsApp.
    - Status transaksi otomatis: `PENDING` -> `SUCCESS` / `FAILED` dengan webhook callback atau auto-polling status.

### C. Modul Kasir (POS) & Hardware
- **Layar POS Cepat**: Input manual, pencarian kilat, scan barcode kamera real-time, dan tab khusus "Pulsa & PPOB".
- **Dukungan Thermal Bluetooth**: Cetak struk via ESC/POS Bluetooth (58mm dan 80mm), mencetak nomor SN/Token pulsa.
- **Alternatif Digital**: Bagikan struk pembelian (termasuk SN pulsa) via WhatsApp atau ekspor PDF.
- **Metode Pembayaran**: Tunai, QRIS, Transfer Bank, dan Kasbon (Hutang Pelanggan).

### D. Pembukuan Kas Umum (BKU) & Hutang/Kasbon
- **Jurnal Kas Otomatis**: Setiap transaksi POS retail & laba pulsa masuk otomatis ke BKU penerimaan.
- **Arus Kas Masuk/Keluar**: Catat beban operasional (listrik, sewa, gaji, belanja barang, top up deposit pulsa H2H, setor modal, prive owner).
- **Buku Kasbon Pelanggan**: Catatan piutang pelanggan (termasuk kasbon pulsa), batas plafon hutang, histori cicilan, dan status pelunasan.
- **Manajemen Shift Kasir**: Modal awal laci kasir (opening cash) vs uang aktual fisik saat penutupan shift (closing cash).

### E. Stok Opname
- Form penyesuaian stok berkala (stok sistem vs stok fisik riil).
- Catatan alasan selisih (rusak, hilang, kadaluwarsa, bonus supplier).
- Rekonsiliasi otomatis ke nilai aset persediaan di BKU.

### F. Backup, Restore & Sinkronisasi
- **Ekspor / Impor XLSX**: Format template Excel terstandar untuk produk, stok, dan laporan keuangan.
- **Local Database Backup**: Backup file database lokal `.db` / `.zip` terenkripsi ke penyimpanan internal/SD card.
- **Cloud Sync Engine**:
  - Offline-first: Transaksi retail tetap berjalan tanpa internet, disimpan di Room SQLite.
  - Online Engine untuk Transaksi Digital: API Pulsa H2H.
  - Auto-sync via Android WorkManager saat koneksi internet tersedia ke backend FastAPI.

---

## 2. Status Pengerjaan (Milestones)

- [x] **Fase 1: Layar Kasir POS (Sesuai Screenshot 1)**
  - Header gradient ungu "Kasir" + subtitle "DENKASIR UMKM" + icon QR & Profil.
  - Search bar rounded "Scan barcode atau cari produk" + tombol barcode scanner.
  - 4 Tab Kategori: `Scan`, `Pcs`, `Kg`, `Pulsa`.
  - Item List Cart Card dengan stepper `−`, `QTY`, `+`, subtotal, dan tombol `X`.
  - Bottom summary card: `Total Item`, `Total Bayar` (hijau), tombol `BATAL` & `BAYAR →`.
  - 5 Navigasi Bawah: `Kasir`, `Produk`, `Transaksi`, `Laporan`, `Akun`.

- [x] **Fase 2: Layar Laporan Keuangan & BKU (Sesuai Screenshot 2)**
  - Sub-tabs: `Ringkasan`, `Kas`, `Hutang`, `Piutang`.
  - Card Ringkasan Keuangan: Periode, Kas Masuk, Kas Keluar, Laba Bersih.
  - Grafik Arus Kas interaktif 7 hari real-time (garis hijau & merah).
  - Tombol aksi: Unduh Laporan PDF & Koneksi Mesin Cetak Struk.

- [x] **Fase 3: Layar Stokopname (Sesuai Screenshot 3)**
  - Metrik: Total Produk (128), Cocok (102), Selisih (26), Progres (80%).
  - Tombol Pindai Barcode & Input Manual.
  - Filter chips `Semua`, `Selisih`, `Cocok` dengan badge status `✓ Cocok` & `⊖ Selisih`.
  - Tombol `✓ Selesaikan Stokopname`.

- [x] **Fase 4: Layar Scan Barcode Kamera Real-time (Sesuai Screenshot 4)**
  - Header: Back button, title "Scan Barcode", tombol Flashlight.
  - Instruction pill badge: "Arahkan barcode ke dalam area kotak".
  - Viewfinder dengan 4 sudut hijau (corner brackets) dan animasi laser scan bergerak.
  - Card deteksi produk bawah: Nama, varian/barcode, harga, stok, dan tombol `+` cepat masuk keranjang kasir.

- [x] **Fase 5: Ikon Aplikasi Launcher (Auto Generated)**
  - Ikon aplikasi modern mesin kasir DenKasir di semua resolusi `mipmap` (`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`).
  - Berhasil di-install & aktif di perangkat Android via ADB.

- [x] **Fase 6: Backend Cloud FastAPI + PostgreSQL + Sync Adapter**
  - Setup API Auth JWT & Multi-toko.
  - Setup API Products, Transactions, BKU & Debts.
  - Setup API Sync Push/Pull Idempotent.
  - Setup H2H OtomaX Provider Adapter.
