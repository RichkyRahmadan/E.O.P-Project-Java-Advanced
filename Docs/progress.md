# Log Progress & Perubahan Proyek E.O.P (Eyes of Priestess)

Dokumen ini mencatat riwayat perkembangan, perubahan arsitektur, dan perbaikan bug penting yang dilakukan sepanjang pengembangan sistem E.O.P (Stateful Event-Driven E-Wallet).

---

## 📅 Ringkasan Milestone & Riwayat Rilis

### Milestone 1: Fondasi Arsitektur & Monolith to Microservices
* **Deskripsi:** Pemecahan aplikasi menjadi 4 unit independen (Gateway, Identity, Core Finance, Oracle/Support).
* **Perubahan Utama:**
  * Inisialisasi Spring Cloud Gateway WebMVC (`gateway` pada port `8080`) untuk routing terpusat.
  * Pemisahan database per service: `eop_identity_db` (Postgres), `eop_finance_db` (Postgres), dan `eop_support_db` (MongoDB).
  * Pengaturan CORS global di level Gateway.

### Milestone 2: Sistem Keamanan & Autentikasi Stateless (JWT)
* **Deskripsi:** Penerapan autentikasi aman tanpa menyimpan sesi stateful di memori server untuk core microservices.
* **Perubahan Utama:**
  * Implementasi generator dan verifikator token JWT (`JWTUtil`) di `identity` service.
  * Penambahan `GatewayJwtFilter` di `gateway` untuk validasi token otomatis sebelum meneruskan request ke downstream services.
  * Konfigurasi `SecurityConfig` stateless dan `CustomPermissionEvaluator` di masing-masing service untuk otorisasi berbasis anotasi `@PreAuthorize`.

### Milestone 3: Integrasi Message Broker (Event-Driven & Saga Pattern)
* **Deskripsi:** Migrasi dari komunikasi REST API antar-service yang bersifat sinkron (*blocking*) ke asinkron (*non-blocking*) menggunakan RabbitMQ.
* **Perubahan Utama:**
  * Konfigurasi RabbitMQ exchange, queue, dan routing keys (`RabbitMQConfig`).
  * Implementasi Saga pattern untuk transaksi QRIS:
    * Inisiasi QRIS menerbitkan event `qris-payment-initiated`.
    * Debit/kredit saldo berjalan di Postgres dengan Optimistic Locking (`@Version`), dilanjutkan penerbitan event `qris-payment-success` atau `qris-payment-failed`.
    * Sinkronisasi status log transaksi di MongoDB secara eventual consistency.
  * Pembuatan fitur penendangan user secara instan jika admin melakukan suspend via event broker (`user.suspended`).

### Milestone 4: Analisis AI Asinkron & Sistem Mailing (Oracle Service)
* **Deskripsi:** Otomatisasi penanganan keluhan support menggunakan model Google Gemini AI API dan pengiriman notifikasi email.
* **Perubahan Utama:**
  * Integrasi Gemini AI API untuk klasifikasi keluhan, penentuan prioritas, dan saran balasan otomatis.
  * Penerapan delegasi `@Async` + `@Retryable` terpisah (`RetryableGeminiExecutor`) guna menjamin kestabilan pemanggilan API eksternal.
  * Konfigurasi Java Mail Sender untuk mengirimkan email otomatis berformat HTML kepada admin/user jika terdeteksi pengaduan berkategori `HIGH` priority.

### Milestone 5 (Terbaru): Propagasi Identitas via JWT & Gateway Headers
* **Deskripsi:** Refactoring mekanisme submission keluhan untuk mematuhi prinsip decoupling data identitas dari request body frontend.
* **Perubahan Utama:**
  * **Identity Service:** Menambahkan klaim `email` ke dalam payload JWT token saat login/registrasi.
  * **Gateway Service:** Memperbarui `GatewayJwtFilter.java` untuk mengekstrak klaim `username` dan `email` dari JWT, lalu menyuntikkannya ke header internal:
    * `X-User-Name`
    * `X-User-Email`
  * **Oracle Service:**
    * Merefaktor `SubmitComplaintRequest.java` dengan menghapus field `username` dan `email` dari JSON request body, serta menyesuaikan field `message` menjadi `rawMessage`.
    * Memperbarui `SupportController.java` agar membaca identitas pengguna dari request header internal (`X-User-Name`, `X-User-Email`) daripada request body.
    * Membersihkan kode usang `submitComplaint` di kelas service karena pemrosesan tiket kini sepenuhnya event-driven melalui antrean RabbitMQ.

### Milestone 6 (Terbaru): Stateful Redis Session & Eliminasi ObjectMapper
* **Deskripsi:** Transisi dari sessionless JWT murni ke Stateful Session menggunakan Redis di level Gateway dan Identity Service untuk meningkatkan kontrol keamanan session (seperti logout instan dan suspend user seketika), serta mengeliminasi `ObjectMapper` untuk performa lebih efisien.
* **Perubahan Utama:**
  * **Redis Session Storage:** Menyimpan data sesi di Redis saat user login, refresh token, atau registrasi dengan key `session:<accessToken>` (TTL 15 menit) dan mencatat semua session aktif per User ID di Redis Set `user:sessions:<userId>`.
  * **Delimited Serialization:** Menyimpan data sesi dalam format flat string ber-pembatas (`userId:::username:::email:::role:::status:::permissions`) sehingga tidak memerlukan library JSON `ObjectMapper` yang berpotensi memicu error startup bean context.
  * **Gateway Session Verification:** `GatewayJwtFilter` memverifikasi JWT signature, kemudian mengambil raw session string dari Redis, mem-parse-nya via delimited split, memvalidasi status pengguna, dan menyuntikkan data ke downstream headers.
  * **Real-time Session Eviction:**
    * Saat logout, `identity` service menghapus seluruh sesi aktif dari Redis Set user tersebut.
    * Saat admin menangguhkan akun (suspend), sesi dibersihkan langsung dari Redis, lalu event `user.suspended` dikirim via RabbitMQ agar Gateway mendaftarkannya ke in-memory cache lokal untuk penolakan instan.

### Milestone 7 (Terbaru): Refactoring UI Tailwind & Perbaikan Angular Compiler
* **Deskripsi:** Pembaruan menyeluruh pada tampilan visual frontend Angular dengan mengadopsi Tailwind CSS secara konsisten, mengeliminasi emoji, dan memperbaiki kendala kompilasi template.
* **Perubahan Utama:**
  * **Penerapan Tailwind CSS:** Merefaktor seluruh halaman (Login, Register, User Dashboard, Merchant Registration, Admin Dashboard, Support complaints) menjadi bertema *premium dark-mode* dengan ornamen modern (kaca transparan/glassmorphism, gradient dinamis, layout responsif).
  * **Pembersihan Emoji:** Menghapus emoji sebagai representasi visual dan menggantinya dengan ikon SVG inline yang setema dan minimalis.
  * **Perbaikan Error Compiler (NG5002):** Memperbaiki error sintaksis pada `admin-dashboard.component.html` yang disebabkan oleh binding nama kelas yang memiliki karakter slash `/` (misalnya `[class.bg-primary/10]`). Seluruh class binding tersebut diganti menggunakan direktif `[ngClass]`.

---

## 🛠 Status Fitur & Checklist Perubahan

| Fitur / Modul | Deskripsi | Status | Keterangan |
| :--- | :--- | :--- | :--- |
| **Routing Gateway** | Forwarding request, CORS, Path rewriting | **Selesai** | Stabil di port 8080 |
| **Registrasi/Login** | Manajemen akun user, enkripsi password, JWT | **Selesai** | Email dimasukkan ke klaim JWT |
| **Stateful Session (Redis)** | Penyimpanan sesi aktif dan tracking per user id di Redis | **Selesai** | Serialisasi delimited string tanpa ObjectMapper |
| **Transaksi QRIS** | Pembayaran QRIS via Saga pattern | **Selesai** | Mendukung eventual consistency |
| **Support Tiket** | Pengajuan keluhan & riwayat | **Selesai** | Payload DTO diselaraskan dengan frontend |
| **AI Analisis** | Klasifikasi & prioritas keluhan otomatis | **Selesai** | Berjalan asinkron menggunakan Gemini AI |
| **Email Alert** | Notifikasi keluhan prioritas tinggi | **Selesai** | Berjalan asinkron menggunakan thread pool |
| **Fitur Suspend** | Real-time user session invalidation | **Selesai** | Sinkronisasi cache memori via broker & Redis eviction |
| **UI/UX Tailwind** | Refaktor desain UI premium & perbaikan syntax compiler | **Selesai** | Menggunakan visual terpadu dark-theme, SVG, & ngClass |

---

## 📋 Rencana Pengembangan Selanjutnya (TODO)

Berikut adalah daftar fitur dan perbaikan yang akan dikerjakan pada fase berikutnya:
- [ ] **Repair AI-Based Admin and Complaint Check** — Mengoptimalkan mekanisme evaluasi otomatis tiket pengaduan menggunakan Gemini AI serta peninjauan manual di dashboard admin.
- [ ] **Editable Profile Picture** — Implementasi unggah foto profil pengguna yang dapat diedit langsung dari halaman dashboard.
- [ ] **Xendit Payment Gateway to self Top-Up** — Integrasi gateway pembayaran Xendit untuk isi ulang saldo dompet digital secara mandiri menggunakan Virtual Account atau retail outlet.
- [ ] **Admin Voucher Generation** — Fitur bagi administrator untuk meng-generate kode voucher promo atau saldo yang dapat ditukarkan (redeem) oleh pengguna.
- [ ] **Repair Payment Gateway** — Perbaikan alur transaksi pembayaran yang menyebabkan user tidak dapat login setelah melakukan pembayaran.
- [ ] **Repair Registration Bug** - Perbaikan bug button daftar agar tidak dapat ditekan secara spam 
