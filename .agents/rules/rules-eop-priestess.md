---
trigger: always_on
---

# MASTER ARCHITECTURE BLUEPRINT: E.O.P (Eyes Of Priestess)

## SECTION 1: METADATA PROYEK & FILOSOFI SISTEM
* **Nama Sistem:** E.O.P (Eyes Of Priestess)
* **Karakteristik Sistem:** Sistem inti dompet digital (E-Wallet) berbasis arsitektur Microservices dengan skalabilitas ringan (lightweight), keamanan berbasis token (stateless security), dan persistensi data hibrida (polyglot persistence).
* **Pengembang Utama:** Richky Rahmadan (Solo Developer)
* **Program Studi:** Teknik Informatika
* **Konteks Proyek:** Ujian Akhir Semester (UAS) untuk Pelatihan Java Lanjutan

---

## SECTION 2: ARCHITECTURAL BOUNDARIES & RESTRICTIONS (BATASAN MUTLAK)
Untuk menjaga keringanan aplikasi dan menghindari kompleksitas infrastruktur pada lingkungan local development, arsitektur E.O.P DENGAN TEGAS MELARANG penggunaan teknologi berikut:
1. TIDAK BOLEH menggunakan Message Broker (Kafka, RabbitMQ, ActiveMQ, dsb.).
2. TIDAK BOLEH menggunakan In-Memory Database / Cache Server (Redis, Memcached, dsb.).
3. TIDAK BOLEH menggunakan Protokol Sinkronisasi Real-Time Kompleks (WebSockets atau Server-Sent Events / SSE).
4. TIDAK BOLEH ada Direct Database Access antar-service. Setiap service murni mengelola databasenya sendiri (Database-per-Service).

Mitigasi integritas data, kontrol konkurensi, dan fungsionalitas sistem wajib menggunakan solusi native software di level kode Java (Spring Boot 3.x) dan TypeScript (Angular) yang tertera pada dokumen ini.

---

## SECTION 3: STRUKTUR ARSITEKTUR LAYERED & PACKAGE DIRECTORY
Setiap microservice wajib dipecah ke dalam lapisan paket (package) yang terisolasi secara kaku mengikuti konvensi berikut:
* `com.priestess.eop.entity` : Kelas entitas database (JPA/Hibernate untuk PostgreSQL atau POJO @Document untuk MongoDB). Menggunakan PascalCase (Contoh: `WalletEntity`).
* `com.priestess.eop.repository` : Interface yang meng-extend JpaRepository atau MongoRepository (Contoh: `UserRepository`).
* `com.priestess.eop.dto` : Objek transfer data untuk request body (berakhir dengan 'Req' atau 'Request', contoh: `LoginReq`) dan standarisasi response.
* `com.priestess.eop.service` : Murni kumpulan INTERFACE kontrak logika bisnis.
* `com.priestess.eop.service.impl` : Kelas IMPLEMENTASI dari interface service. Nama kelas wajib berakhiran 'ServiceImpl' (Contoh: `AuthServiceImpl`). Semua anotasi `@Service` dan `@Transactional` diletakkan di sini.
* `com.priestess.eop.controller` : Kelas `@RestController` yang murni menangani HTTP request mapping, validasi input (`@Valid`, `@RequestBody`), dan mengembalikan ResponseEntity. Logika bisnis dilarang ditulis di sini.
* `com.priestess.eop.utility` : Tempat kelas utilitas penunjang sistem yang bersifat terisolasi seperti `JWTUtil` dan `JWTFilter`.
* `com.priestess.eop.config` : Tempat seluruh kelas konfigurasi global, `@Configuration`, `SecurityConfig`, dan komponen kustom keamanan seperti `CustomPermissionEvaluator`.

*Gaya Pengodean:* Wajib menggunakan konstruktor berbasis anotasi `@RequiredArgsConstructor` dari Lombok untuk menginjeksi dependency (Repository/Service lain). Hindari penggunaan `@Autowired` langsung pada field variabel.

---

## SECTION 4: TOPOLOGI LAYANAN & DISTRIBUSI PORT
Sistem terbagi menjadi 1 Gateway sebagai pintu masuk tunggal dan 3 Layanan Internal independen:

1. **E.O.P Gateway (Port 8080):** Menggunakan Spring Cloud Gateway. Bertindak sebagai Single Entry Point, menangani CORS terpusat, dan melakukan validasi Access Token (JWT) secara stateless. Melakukan Header Mutation untuk menyuntikkan `X-User-Id`, `X-User-Role`, dan `X-User-Permissions` sebelum request diteruskan.
2. **Identity Service (Port 8081):** Mengelola database PostgreSQL (`eop_identity_db`). Menangani autentikasi, pendaftaran, verifikasi KYC oleh Admin, pembekuan akun, dan manajemen hak akses dinamis (RBAC).
3. **Core Finance Service (Port 8082):** Menggunakan database Polyglot (PostgreSQL `eop_finance_db` & MongoDB `eop_transaction_log`). Menangani sirkulasi uang, pemotongan saldo, transfer P2P, pembuatan invoice QRIS dinamis, eksekusi pembayaran QRIS, serta klaim kode voucher.
4. **Support & Oracle Service (Port 8083):** Menggunakan database MongoDB (`eop_support_db`). Menangani pengaduan pengguna secara asinkron (`@Async`), integrasi Google Gemini AI API, dan pengiriman email via Java Mail Sender (JSM).

---

## SECTION 5: SPESIFIKASI DATA DEFINITION (DDL DATABASE)

### A. IDENTITY SERVICE DATABASE (PostgreSQL - eop_identity_db)
* **roles** -> `id` (UUID, PK), `role_name` (VARCHAR(50), Unique, Not Null)
* **menus** -> `id` (UUID, PK), `menu_name` (VARCHAR(50), Unique, Not Null), `description` (TEXT)
* **role_permissions** -> `id` (UUID, PK), `role_id` (UUID, FK -> roles), `menu_id` (UUID, FK -> menus), `access_type` (VARCHAR(20))
* **users** -> `id` (UUID, PK), `username` (VARCHAR(50), Unique), `email` (VARCHAR(100), Unique), `password` (VARCHAR(255), BCrypt), `role_id` (UUID, FK), `status` (VARCHAR(20) [PENDING, ACTIVE, SUSPENDED]), `refresh_token` (VARCHAR(500)), `created_at` (TIMESTAMP)
* **merchants** -> `id` (UUID, PK), `user_id` (UUID, FK, Unique), `merchant_name` (VARCHAR(100)), `address` (TEXT), `is_verified` (BOOLEAN), `created_at` (TIMESTAMP)

### B. CORE FINANCE SERVICE DATABASE (PostgreSQL & MongoDB)
* **wallets** (PostgreSQL) -> `id` (UUID, PK), `owner_id` (UUID, Unique), `owner_type` (VARCHAR(20) [USER, MERCHANT]), `balance` (DECIMAL(19,2), CHECK balance >= 0.00), `version` (INTEGER, Default 0, **Wajib untuk Optimistic Locking**), `created_at` (TIMESTAMP), `updated_at` (TIMESTAMP)
* **vouchers** (PostgreSQL) -> `id` (BIGSERIAL, PK), `code` (VARCHAR(50), Unique), `amount` (DECIMAL(19,2)), `is_redeemed` (BOOLEAN, Default False), `redeemed_by` (UUID), `redeemed_at` (TIMESTAMP)
* **transactions** (MongoDB Collection) -> `invoice_id` (String, Unique Indexed), `transaction_type` (String), `status` (String [PENDING, SUCCESS, FAILED]), `amount` (Decimal128), `sender` (Object), `recipient` (Object), `raw_qris_data` (String), `note` (String), `created_at` (ISODate)

### C. SUPPORT & ORACLE SERVICE DATABASE (MongoDB)
* **complaints** (MongoDB Collection) -> `complaint_id` (String), `user_id` (String), `username` (String), `email` (String), `invoice_id` (String), `raw_message` (String), `status` (String [OPEN, IN_PROGRESS, RESOLVED]), `ai_analysis` (Object: category, priority, sentiment, score, suggested_reply), `created_at` (ISODate)

---

## SECTION 6: SECURITY MECHANISM & DUAL-TOKEN FLOW (STATELESS SECURITY)
1. **Access Token (JWT):** Umur eksplorasi **15 Menit**. Membawa payload `sub`, `role`, dan klaim string `permissions` (dipisah koma). Bersifat stateless, divalidasi oleh Gateway via kunci kriptografi (`JWTUtil` & `JWTFilter` di package `utility`). Otorisasi tingkat method di Controller menggunakan `@PreAuthorize` dibantu oleh `CustomPermissionEvaluator` di package `config`.
2. **Refresh Token:** Umur eksplorasi **7 Hari**. Berupa string acak aman yang dicatat di tabel `users.refresh_token` PostgreSQL.
3. **Mitigasi Akun Dibekukan (Suspend Account):** Ketika Admin mengubah status user menjadi `SUSPENDED`, user tetap bisa mengakses sistem menggunakan Access Token berjalan hingga maksimal 15 menit. Begitu Access Token habis, Angular HTTP Interceptor akan menangkap error `401 Unauthorized` dan memanggil `POST /api/auth/refresh`. `Identity Service` akan memeriksa database, mendeteksi status `SUSPENDED`, lalu MENOLAK penerbitan token baru sehingga user langsung ter-logout paksa.

---

## SECTION 7: WORKFLOW MITIGASI DUAL-WRITE & CONCURRENCY CONTROL
Pemrosesan transaksi QRIS Dinamis dan mutasi finansial wajib menerapkan pola **State Machine** sinkron di dalam `Core Finance Service` untuk mencegah hilangnya jejak data:

1. **Langkah 1 (Persiapan Tulis):** Simpan dokumen log transaksi awal ke **MongoDB** dengan status `PENDING`. Ini adalah jejak rekam awal agar mutasi tidak lenyap tanpa bukti jika terjadi kegagalan sistem setelahnya.
2. **Langkah 2 (Eksekusi Finansial):** Masuk ke dalam blok kode beranotasi `@Transactional` (PostgreSQL). Lakukan manipulasi saldo (Kredit/Debit) pada tabel `wallets`. Di level entitas, sertakan anotasi `@Version` untuk mengaktifkan **Optimistic Locking** guna menghindari *double-spend* atau tabrakan saldo di milidetik yang sama.
3. **Langkah 3 (Pengesahan Bukti):**
   * Jika Langkah 2 **SUKSES**, perbarui dokumen di MongoDB tadi dari `PENDING` menjadi `SUCCESS`.
   * Jika Langkah 2 **GAGAL** (masuk ke blok `catch`), database PostgreSQL otomatis melakukan *rollback*, lalu perbarui dokumen di MongoDB menjadi `FAILED` dengan mencatat detail error pada kolom `note`, kemudian lemparkan HTTP Exception 400 ke klien.

---

## SECTION 8: IMPLEMENTASI FITUR SPESIFIK & CLIENT INTERACTION
* **Real-Time Polling (Angular):** Aplikasi Front-End Angular tidak menggunakan WebSocket. Pada antarmuka Dashboard Merchant saat memunculkan QRIS, Angular mengaktifkan fungsi `RxJS interval` untuk melakukan **HTTP Polling** ke Core Finance Service setiap **3-5 detik sekali** guna mengecek status `invoice_id`. Begitu status berubah menjadi `SUCCESS`, polling berhenti dan layar berubah secara real-time.
* **Asynchronous Complaint & Gemini AI:** `Support & Oracle Service` menerima keluhan, langsung menyimpannya ke MongoDB dengan status `OPEN`, dan secara instan mengembalikan HTTP 202 Accepted ke klien. Proses analisis teks keluhan via **Google Gemini AI API** dijalankan di latar belakang menggunakan `@Async` dan dilindungi oleh `@Retryable` (max 3 kali, jeda 2 detik) jika terjadi timeout. Jika hasil analisis AI berkategori priority `HIGH`, gunakan **JavaMailSender (JSM)** untuk mengirimkan email laporan berformat HTML terstruktur ke Gmail Admin.
* **Excel Reporting via Apache POI:** Semua fitur import/export laporan data transaksi ke dalam file Excel wajib memanfaatkan pustaka Apache POI di dalam lapisan `ServiceImpl` dan dialirkan ke klien menggunakan stream output yang tepat pada Controller.
* **Global Error Handling:** Semua REST API wajib dikawal oleh `@ControllerAdvice` untuk menangkap Exception dan mengubahnya menjadi format JSON respons yang seragam (status, message, timestamp).