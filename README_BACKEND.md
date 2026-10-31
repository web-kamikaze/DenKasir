# DenKasir - FastAPI & PostgreSQL Cloud/Local Backend Hub

Server backend terintegrasi untuk aplikasi kasir Android **DenKasir** (POS Warung Kelontong & Produk Digital PPOB/Pulsa). Backend ini dirancang dengan arsitektur **Clean Layer** dan mendukung **Dual Runtime (Lokal Standalone & Cloud Database)**.

---

## 1. Fitur Utama Backend

- **Autentikasi & RBAC**: OAuth2 Password Bearer dengan JWT token dan pemisahan hak akses peran `OWNER` dan `KASIR`.
- **Manajemen Multi-Toko & Kasir**: CRUD profil toko dan pembuatan akun kasir staf.
- **Katalog Produk (Retail & Digital Pulsa)**: Manajemen barcode, nama, kategori, harga modal (HPP), harga jual, dan notifikasi stok minimum.
- **POS Kasir Transaksi Atomik**: Pencatatan checkout, pengurangan stok retail otomatis, penerbitan invoice, dan integrasi kasbon otomatis.
- **Buku Kas Umum (BKU)**: Jurnal arus kas masuk/keluar otomatis dari transaksi POS, cicilan kasbon, dan beban operasional toko.
- **Buku Kasbon & Hutang Supplier**: Pencatatan piutang pelanggan, pembayaran cicilan, dan sinkronisasi saldo kas masuk.
- **Stok Opname & Auto PO**: Rekonsiliasi stok fisik dan penerbitan draft purchase order.
- **Gateway Server Pulsa H2H / OtomaX**: Cek saldo deposit server, tembak transaksi pulsa/PLN, parsing nomor SN token, dan callback webhook.
- **Bidirectional Offline-First Sync Adapter**: Endpoint `/api/v1/sync/push` dan `/api/v1/sync/pull` dengan resolusi konflik *Last-Write-Wins*.
- **Ekspor & Impor XLSX**: Unduh laporan transaksi POS, BKU kas, dan katalog produk dalam format Excel terstandarisasi.

---

## 2. Kredensial Pengguna Default (Auto-Seeded)

Saat server pertama kali dijalankan, sistem otomatis melakukan seeding akun default:

| Role | Username | Password | Hak Akses |
|---|---|---|---|
| **OWNER** | `owner` | `admin123` | Akses penuh seluruh fitur, laporan laba rugi, BKU, manajemen kasir, ekspor/impor Excel, dan server H2H |
| **KASIR** | `kasir` | `kasir123` | Akses POS transaksi penjualan, scan barcode, penjualan pulsa, dan riwayat transaksi |

---

## 3. Cara Menjalankan Server

### A. Mode Lokal Cepat (Standalone Python)
Jalankan file batch:
```cmd
start_backend.bat
```
Atau via terminal:
```bash
python backend/run_server.py
```
*Server akan aktif di `http://localhost:8000` dengan fallback otomatis ke SQLite lokal `denkasir_local.db`.*

### B. Mode Cloud & Docker (FastAPI + PostgreSQL 16)
Jalankan PostgreSQL dan FastAPI dalam 1 perintah container:
```bash
docker-compose up -d --build
```
*Layanan PostgreSQL 16 dan FastAPI API Server akan otomatis berjalan dengan healthcheck dan persistent volume.*

---

## 4. Dokumentasi Interaktif & OpenAPI
- **Swagger UI**: [http://localhost:8000/docs](http://localhost:8000/docs)
- **ReDoc UI**: [http://localhost:8000/redoc](http://localhost:8000/redoc)

---

## 5. Ringkasan Endpoint REST API (`/api/v1`)

### 🔐 Autentikasi & Akun
- `POST /api/v1/auth/login` - Login form data (OAuth2 Bearer)
- `POST /api/v1/auth/login/json` - Login format JSON (Mobile Retrofit)
- `GET /api/v1/auth/me` - Profil user aktif
- `POST /api/v1/auth/register` - Daftar toko & owner baru

### 🏬 Profil Toko & Pengguna Kasir
- `GET /api/v1/stores/current` - Detail toko aktif
- `PUT /api/v1/stores/current` - Update nama/alamat/nomor toko
- `GET /api/v1/users` - Daftar staf kasir toko (Owner Only)
- `POST /api/v1/users` - Buat akun staf kasir baru (Owner Only)
- `DELETE /api/v1/users/{id}` - Hapus akun kasir

### 📦 Manajemen Produk
- `GET /api/v1/products` - Daftar produk (Filter: kategori, tipe, search)
- `GET /api/v1/products/barcode/{barcode}` - Cari produk via barcode scanner
- `POST /api/v1/products` - Tambah produk baru
- `PUT /api/v1/products/{id}` - Update produk
- `DELETE /api/v1/products/{id}` - Hapus produk
- `GET /api/v1/products/export/excel` - Download katalog produk XLSX
- `POST /api/v1/products/import/excel` - Upload & import produk dari XLSX

### 🛒 Transaksi POS Penjualan
- `GET /api/v1/transactions` - Riwayat transaksi
- `POST /api/v1/transactions` - Buat transaksi penjualan POS (Otomatis potong stok & catat ke BKU)
- `GET /api/v1/transactions/export/excel` - Export laporan penjualan XLSX

### 💰 Pembukuan Kas Umum (BKU)
- `GET /api/v1/cash-flows` - Daftar mutasi kas masuk & keluar
- `POST /api/v1/cash-flows` - Catat kas masuk/keluar manual
- `GET /api/v1/cash-flows/summary` - Ringkasan kas masuk, keluar & laba bersih
- `GET /api/v1/cash-flows/accounts` - Daftar pos akun BKU
- `GET /api/v1/cash-flows/export/excel` - Download buku kas umum XLSX

### 📑 Buku Kasbon (Piutang) & Hutang Supplier
- `GET /api/v1/debts` - Daftar piutang kasbon pelanggan & hutang supplier
- `POST /api/v1/debts` - Catat kasbon/hutang baru
- `POST /api/v1/debts/{id}/pay` - Catat cicilan pembayaran (Otomatis masuk BKU)

### 📊 Laporan & Grafik Real-time
- `GET /api/v1/reports/dashboard` - Statistik metrik dashboard real-time
- `GET /api/v1/reports/chart?days=7` - Data grafik arus kas 7 hari terakhir

### ⚡ Pulsa & PPOB H2H Gateway
- `GET /api/v1/pulsa/config` - Konfigurasi server distributor pulsa
- `POST /api/v1/pulsa/config` - Simpan API key / member ID server pulsa
- `GET /api/v1/pulsa/balance` - Cek sisa saldo deposit server pulsa
- `POST /api/v1/pulsa/order` - Eksekusi transaksi pulsa (Dapatkan SN/Token PLN)
- `POST /api/v1/pulsa/callback` - Webhook notifikasi status dari distributor

### 🔄 Sinkronisasi Cloud (Offline-First)
- `POST /api/v1/sync/push` - Push batch seluruh data lokal offline ke cloud (Idempotent)
- `POST /api/v1/sync/pull` - Tarik delta pembaruan data dari cloud berdasarkan timestamp

---

## 6. Pengujian Unit & Integrasi
Jalankan seluruh rangkaian automated tests:
```bash
python -m pytest backend/tests -v
```
*Seluruh 11 test suites lulus 100% (Auth, Products, POS Transactions, BKU, Kasbon Debts, H2H Pulsa, Sync Push & Pull).*
