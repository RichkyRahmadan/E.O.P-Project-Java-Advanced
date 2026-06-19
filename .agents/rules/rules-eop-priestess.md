---
trigger: always_on
---

# MASTER ARCHITECTURE BLUEPRINT: E.O.P (Eyes Of Priestess) - STATEFUL EVENT-DRIVEN EDITION

## SECTION 1: METADATA PROYEK & FILOSOFI SISTEM
* **Nama Sistem:** E.O.P (Eyes Of Priestess)
* **Karakteristik Sistem:** Sistem inti dompet digital (E-Wallet) berbasis arsitektur Stateful Microservices dengan pendekatan Event-Driven Architecture (EDA) menggunakan Message Broker untuk menjamin asynchronicity, high throughput, dan eventual consistency.
* **Pengembang Utama:** Richky Rahmadan (Solo Developer)
* **Institusi:** Universitas Nasional Pasim
* **Program Studi:** Teknik Informatika
* **Konteks Proyek:** Ujian Akhir Semester (UAS)

---

## SECTION 2: ARCHITECTURAL BOUNDARIES & INFRASTRUCTURE
Sistem E.O.P mengadopsi arsitektur pesan terdistribusi dengan aturan infrastruktur sebagai berikut:
1. **WAJIB Menggunakan Message Broker:** RabbitMQ digunakan sebagai media komunikasi asinkron utama antar-service (Event-Driven Broker).
2. **TIDAK BOLEH ada Direct Database Access** antar-service. Setiap service murni mengelola databasenya sendiri (Database-per-Service).
3. **Komunikasi Sinkron vs Asinkron:** Komunikasi sinkron (HTTP REST) hanya digunakan dari Klien Angular ke Gateway, dan dari Gateway ke Service Entry Point. Komunikasi internal antar-service wajib diarahkan melalui mekanisme *Publish/Subscribe Topic/Queue* via RabbitMQ.
4. **Decoupling Data Identitas (Identity Propagation):**
   * Client (Angular) **TIDAK BOLEH** mengirimkan informasi pribadi sensitif seperti `username` dan `email` di dalam request body.
   * `E.O.P Gateway` wajib memvalidasi JWT secara stateless, mengekstrak data dari klaim token (`userId`, `username`, `email`, `role`, `permissions`), lalu menyuntikkannya sebagai HTTP Header downstream (`X-User-Id`, `X-User-Name`, `X-User-Email`, `X-User-Role`, `X-User-Permissions`).
   * Downstream services menerima header tersebut secara langsung untuk proses otorisasi dan identifikasi.

---

## SECTION 3: STRUKTUR ARSITEKTUR LAYERED & PACKAGE DIRECTORY
Setiap microservice wajib dipecah ke dalam lapisan paket (package) yang terisolasi secara kaku mengikuti konvensi berikut:
* `com.priestess.eop.entity` : Kelas entitas database (JPA/Hibernate untuk PostgreSQL atau POJO @Document untuk MongoDB).
* `com.priestess.eop.repository` : Interface yang meng-extend JpaRepository atau MongoRepository.
* `com.priestess.eop.dto` : Objek transfer data untuk request body (berakhir dengan 'Req' atau 'Request') dan standarisasi response.
* `com.priestess.eop.producer` : Kelas beranotasi `@Component` yang bertugas mengirimkan pesan/event ke Message Broker (RabbitMQ).
* `com.priestess.eop.consumer` : Kelas beranotasi `@Component` / `@RabbitListener` yang bertugas mendengarkan event dari broker dan memicu logika bisnis.
* `com.priestess.eop.service` : Murni kumpulan INTERFACE kontrak logika bisnis.
* `com.priestess.eop.service.impl` : Kelas IMPLEMENTASI dari interface service (Berakhiran 'ServiceImpl'). Semua anotasi `@Service` dan `@Transactional` diletakkan di sini.
* `com.priestess.eop.controller` : Kelas `@RestController` yang murni menangani HTTP request mapping, validasi input, dan mengembalikan ResponseEntity. Logika bisnis dilarang ditulis di sini.
* `com.priestess.eop.config` : Tempat seluruh kelas konfigurasi global, `@Configuration`, `SecurityConfig`, dan konfigurasi Broker (RabbitMQ Queue/Exchange Bean).

*Gaya Pengodean:* Wajib menggunakan konstruktor berbasis anotasi `@RequiredArgsConstructor` dari Lombok untuk menginjeksi dependency. Hindari penggunaan `@Autowired` langsung pada field variabel.

---

## SECTION 4: TOPOLOGI LAYANAN & ESTIMASI ALIRAN EVENT (PORT DISTRIBUTION)
Sistem terbagi menjadi 1 Gateway sebagai pintu masuk tunggal, 3 Layanan Internal independen, dan 1 Pusat Kluster Message Broker:

1. **E.O.P Gateway (Port 8080):** Berbasis Spring Cloud Gateway. Bertindak sebagai Single Entry Point, penanganan CORS terpusat, dan validasi token masuk.
2. **Identity Service (Port 8081):** Mengelola database PostgreSQL (`eop_identity_db`). Menangani autentikasi, manajemen pengguna, dan penerbitan Event terkait user (Contoh: `user.suspended`).
3. **Core Finance Service (Port 8082):** Menggunakan database Polyglot (PostgreSQL `eop_finance_db` & MongoDB `eop_transaction_log`). Menghandle validasi saldo berjalan, pembentukan invoice QRIS, dan bertindak sebagai *Producer* utama untuk event finansial.
4. **Support & Oracle Service (Port 8083):** Menggunakan database MongoDB (`eop_support_db`). Menangani pengaduan pengguna dengan bertindak sebagai *Consumer* yang mendengarkan antrean keluhan, memprosesnya via Google Gemini AI API, dan memicu Java Mail Sender (JSM).
5. **Message Broker Cluster (Port 5672 untuk RabbitMQ / Port 15672 untuk Management UI):** Jantung pengelolaan State Antrean Transaksi Terdistribusi.

---

## SECTION 5: WORKFLOW TRANSAKSI STATEFUL & EVENT-DRIVEN (ANTI DUAL-WRITE VIA SAGA)
Dengan hadirnya Message Broker, manipulasi saldo dan pencatatan log transaksi tidak lagi dijalankan dalam satu transaksi blok atomik lokal, melainkan diatur melalui mekanisme **Saga Pattern (Choreography/Orchestration)**:

1. **Fase Inisiasi (Core Finance Service):**
   * Merchant menembak API generate QRIS -> Core Finance membuat data transaksi di MongoDB dengan status `PENDING` dan menerbitkan pesan ke Broker pada Topic `qris-payment-initiated`.
2. **Fase Pemrosesan Saldo (Stateful Consumer):**
   * Consumer di Core Finance mendengarkan Topic `qris-payment-initiated`. Begitu pesan dibaca, Consumer membuka blok `@Transactional` di PostgreSQL untuk menguji kelayakan dompet via **Optimistic Locking** (`@Version`).
   * Jika Saldo Cukup: Kredit & Debit berhasil di-commit di Postgres, lalu Consumer mem-publish event `qris-payment-success` ke Broker.
   * Jika Saldo Kurang/Error: Postgres di-rollback otomatis, lalu Consumer mem-publish event `qris-payment-failed` ke Broker.
3. **Fase Sinkronisasi Log (Final State):**
   * Consumer lain yang bertugas menjaga konsistensi MongoDB mendengarkan Topic `qris-payment-success` atau `qris-payment-failed`.
   * Dokumen transaksi di MongoDB diperbarui statusnya dari `PENDING` menjadi `SUCCESS` atau `FAILED` berdasarkan event yang diterima dari Broker (*Eventual Consistency*).

---

## SECTION 6: SECURITY MECHANISM & STATEFUL SESSION HANDLING
1. **Access Token & Refresh Token:** Proses verifikasi token tetap berjalan di level Gateway secara berkala.
2. **Mekanisme Suspend User via Broker:** Ketika Admin membekukan akun di `Identity Service`, service tersebut akan langsung melempar pesan `user.suspended` ke Message Broker. `E.O.P Gateway` atau internal cache service yang bertindak sebagai Consumer akan menangkap pesan tersebut secara real-time dan langsung menandai sesi user tersebut tidak valid saat itu juga di memori, memberikan efek penendangan user secara instan tanpa menunggu token kedaluwarsa 15 menit.

---

## SECTION 7: CLIENT INTERACTION STRATEGY (HTTP POLLING & FUTURE REAL-TIME PUSH)
1. **HTTP Polling saat ini:** Aplikasi Front-End Angular menggunakan metode **HTTP Polling** ke Core Finance Service (`GET /api/finance/transactions/status/{invoice_id}`) setiap 3-5 detik sekali untuk memantau kapan status di MongoDB bergeser dari `PENDING` menjadi `SUCCESS` setelah seluruh rantai event di Message Broker selesai berputar.
2. **Real-time WebSockets (Rancangan Masa Depan):** Mengganti sistem HTTP Polling pada client dengan push notification real-time berbasis **WebSockets / Server-Sent Events (SSE)** melalui API Gateway untuk status transaksi QRIS dan complaint secara instan guna mengurangi overhead jaringan.

---

## SECTION 8: GLOBAL ERROR HANDLING & NOTIFICATION CONSUMER
1. **Global Error Handler:** Setiap service tetap menggunakan `@ControllerAdvice` untuk menangkap exception HTTP lokal.
2. **Dead Letter Queue (DLQ):** Jika terjadi kegagalan pembacaan pesan berulang kali pada Consumer (misal: Google Gemini AI API timeout di Support Service setelah `@Retryable` habis), pesan tersebut akan dilemparkan ke antrean khusus bernama `complaint-dlq` untuk dianalisis oleh Admin secara manual.
3. **Notification System:** `Support & Oracle Service` bertindak sebagai Consumer asinkron murni yang dipicu oleh masuknya event pengaduan dari broker, bukan dari hit API HTTP langsung.
4. **Mailing System Alert:** Jika prioritas tiket keluhan berstatus `HIGH` setelah dianalisis AI, sistem mengirimkan email pemberitahuan HTML secara asinkron lewat background thread pool.

---

## SECTION 9: RANCANGAN PENGEMBANGAN KEDEPAN (FUTURE ROADMAP)
1. **Dashboard Admin & Manual Review UI:** Pembuatan antarmuka dashboard admin pada frontend Angular untuk meninjau secara manual tiket pengaduan berkategori `OPEN` yang gagal dianalisis oleh AI (fallback/retry yang gagal dan masuk ke DLQ).
2. **Service Notifikasi Mandiri (Notification Service - Port 8084):** Memisahkan logika pengiriman email dan notifikasi dari `Support & Oracle Service` ke microservice baru yang didedikasikan khusus untuk mendengarkan event seperti `complaint.resolved` atau `transaction.completed`.
3. **Resilience & Rate Limiting:** Implementasi **Resilience4j** di level API Gateway untuk sirkuit pemutus (*Circuit Breaker*) dan pembatasan laju request (*Rate Limiting*) ke downstream services guna mengamankan sistem dari serangan DDoS atau kegagalan beruntun.