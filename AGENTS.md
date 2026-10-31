# AGENTS.md - Aturan & Panduan AI Coding DenKasir

Dokumen ini berisi standar kerja, konvensi kode, dan alur penanganan bug untuk AI agent dalam pengembangan aplikasi **DenKasir**.

---

## 1. Prinsip Utama Pengembangan
- **Offline-First**: Aplikasi mobile (Android Kotlin) harus tetap berfungsi 100% tanpa jaringan internet.
- **Data Integrity**: Gunakan UUID (String) untuk primary key, transaksi database atomik (Room `@Transaction` / SQLAlchemy transaction), dan soft-delete (`deleted_at`).
- **Separation of Concerns**: Arsitektur MVVM + Clean Architecture di mobile (UI -> ViewModel -> Repository -> Local/Remote Source) dan Clean Layer di backend FastAPI (Routers -> Services -> CRUD/Models -> DB).
- **Zero Data Loss**: Sinkronisasi harus idempotent dengan resolusi konflik `Last-Write-Wins` atau `Client-Server Timestamp Matching`.

---

## 2. Aturan Wajib Penanganan & Pencatatan Bug (BUG.md)
Setiap kali AI mendeteksi, menemukan, atau memprediksi potensi bug saat perancangan maupun pengkodean:
1. **Dilarang mengabaikan bug/potensi bug**, baik bug logika, race condition, memory leak, sync conflict, parsing, hardware timeout (bluetooth/kamera), atau sanitasi input.
2. **Wajib mencatat ke file `BUG.md`** secara langsung dengan format standar:
   - **ID**: Format `BUG-XXX` (contoh `BUG-001`)
   - **Tanggal**: Waktu identifikasi
   - **Modul/Komponen**: Backend / Mobile / Sync / Hardware / Database
   - **Tingkat Keparahan (Severity)**: `CRITICAL` | `HIGH` | `MEDIUM` | `LOW`
   - **Status**: `OPEN` | `IN_PROGRESS` | `RESOLVED` | `WONT_FIX`
   - **Deskripsi Masalah**: Penjelasan ringkas dan jelas
   - **Dampak / Potensi Resiko**: Efek terhadap sistem jika tidak diperbaiki
   - **Rekomendasi / Solusi Perbaikan**: Tindakan yang harus atau sudah diambil
3. Jika bug langsung diperbaiki pada patch yang sama, update status bug di `BUG.md` menjadi `RESOLVED` beserta ringkasan solusinya.

---

## 3. Standar Kode & Teknologi

### Android (Kotlin)
- **Language**: Kotlin Modern (Coroutines, Flow).
- **Architecture**: MVVM / MVI + Jetpack Compose / ViewBinding.
- **Database Local**: Room Database + SQLite.
- **Networking**: Retrofit2 + OkHttp3 + KotlinX Serialization / Moshi.
- **Background Task**: AndroidX WorkManager (untuk auto-sync periodik & retry policy).
- **Hardware**:
  - Barcode: Google ML Kit Barcode Scanning via CameraX.
  - Printer: Bluetooth SPP ESC/POS (58mm & 80mm).

### Backend (FastAPI + PostgreSQL)
- **Framework**: FastAPI (Python 3.10+).
- **ORM**: SQLAlchemy 2.0 / SQLModel + Alembic migrations.
- **Database**: PostgreSQL dengan index pada kolom `store_id`, `sync_status`, dan `updated_at`.
- **Auth**: OAuth2 + JWT (Role-Based Access Control: `OWNER`, `KASIR`).
- **File Handling**: OpenPyXL / Pandas untuk ekspor & impor XLSX.

---

## 4. Alur Kerja Implementasi
1. Cek `PLAN.md` untuk spesifikasi modul yang sedang dikerjakan.
2. Buat skema & migrasi terlebih dahulu sebelum business logic.
3. Selalu validasi input di sisi klien (Kotlin) dan sisi server (Pydantic).
4. Verifikasi dan catat potensi bug/edge case di `BUG.md`.
5. **Standar Instalasi Bersih (Clean State)**: Setiap instalasi atau deployment APK baru ke perangkat pengujian wajib menjamin kondisi basis data awal benar-benar bersih (0 produk, 0 transaksi, 0 mutasi kas). Jika menginstal ulang via ADB, pastikan menghapus data lama (`pm clear com.denkasir`) agar aplikasi tidak mewarisi database usang dari instalasi sebelumnya.
