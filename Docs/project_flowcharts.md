# Alur Kerja Aplikasi E.O.P (Eyes of Priestess) - Edisi Standar Akademik (Koreksi Percabangan)

Semua titik keputusan (**Decision**) dalam diagram di bawah ini telah dikoreksi agar murni bersifat biner (hanya memiliki 2 panah keluar: **Ya / Tidak** atau **True / False**), tanpa ada percabangan menu bernilai banyak (multi-way switch).

---

## 1. Alur Pengguna Biasa (User / Customer)

```mermaid
flowchart TD
    A([Mulai]) --> B[/Daftar Akun Baru: Input Data Diri/]
    B --> C[/Login: Input Username & Password/]
    C --> D{"Akun Sudah Terverifikasi?"}
    
    D -- Tidak --> E[/Upload Data Identitas / KYC/]
    E --> F[[Proses Verifikasi KYC oleh Admin]]
    F --> C
    
    D -- Ya --> G[Akses Fitur Terbuka]
    G --> H[/Pilih Fitur & Masukkan Data Transaksi/]
    
    H --> H1[/Transfer: Input Penerima & Nominal/]
    H --> H2[/Bayar QRIS: Scan QR & Input PIN/]
    H --> H3[/Voucher: Input Kode Voucher/]
    
    H1 & H2 & H3 --> I{"Transaksi Berhasil?"}
    I -- Ya --> J[/Tampilkan Notifikasi Sukses/]
    I -- Tidak --> K[/Tampilkan Notifikasi Gagal/]
    
    J & K --> L{"Ada Kendala Transaksi?"}
    L -- Ya --> M[/Kirim Pengaduan & Input Keluhan/]
    M --> N[[Proses Resolusi Masalah via AI Gemini / Admin]]
    N --> O([Selesai])
    
    L -- Tidak --> O
```

---

## 2. Alur Pemilik Toko (Merchant)

```mermaid
flowchart TD
    A([Mulai]) --> B[Login User Owner yang Aktif]
    B --> C[/Pendaftaran Merchant: Input Nama Toko & Alamat/]
    C --> D[Sistem Membuat Akun Merchant Terhubung ke Owner]
    
    D --> E[/Login Merchant: Input Username & Password Toko/]
    E --> F[/Input Nominal Tagihan & Buat QRIS/]
    F --> G[/Tampilkan QRIS di Layar Toko/]
    G --> H[Pembeli Membayar Melalui Scan QRIS]
    H --> I[Sistem Memproses Transaksi & Saldo Bertambah]
    
    I --> J[/Input Nominal Tarik Dana Ke Owner/]
    J --> K[Sistem Memindahkan Saldo ke Wallet Owner Terdaftar]
    K --> L[/Tampilkan Resi Penarikan Sukses/]
    L --> M([Selesai])
```

---

## 3. Alur Pengelola Sistem (Admin)
*Catatan: Pilihan menu kelola pada Admin kini menggunakan urutan keputusan biner (Ya/Tidak) agar sesuai aturan akademik.*

```mermaid
flowchart TD
    A([Mulai]) --> B[/Login Admin: Input Kredensial/]
    B --> C{"Pilih Kelola KYC?"}
    
    C -- Ya --> D[/Tampilkan Daftar Pengajuan KYC/]
    D --> E{"Dokumen KYC Valid & Sesuai?"}
    E -- Ya --> F[Proses Aktifkan Status User ke ACTIVE]
    E -- Tidak --> G[Proses Tolak KYC & Kirim Alasan Penolakan]
    
    C -- Tidak --> H{"Pilih Kelola Keamanan?"}
    
    H -- Ya --> I[/Tampilkan Laporan Aktivitas Mencurigakan/]
    I --> J{"Terbukti Melanggar Aturan?"}
    J -- Ya --> K[Proses Bekukan / Suspend Akun]
    J -- Tidak --> L[Abaikan Laporan]
    
    H -- Tidak --> M{"Pilih Kelola Pengaduan?"}
    
    M -- Ya --> N[/Tampilkan Tiket Keluhan Masuk/]
    N --> O{"Perlu Tindakan Manual / Prioritas HIGH?"}
    O -- Ya --> P[Admin Selesaikan Manual & Masukkan Solusi]
    P --> Q[/Kirim Laporan Email Keluhan Selesai ke User/]
    O -- Tidak --> R[[Proses Otomatisasi AI Gemini]]
    
    M -- Tidak --> S([Selesai])
    
    F & G & K & L & Q & R --> S
```
