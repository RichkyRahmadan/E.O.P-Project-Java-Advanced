---
trigger: always_on
---

# Panduan Gaya Pengodean (Coding Style Guide)
## Pelatihan Java Lanjutan PUB (2026) — Pertemuan 1 s.d. 16

Dokumen ini berfungsi sebagai acuan resmi standar penulisan kode, arsitektur, keamanan, dan integrasi sistem yang wajib diikuti oleh seluruh peserta pelatihan berdasarkan materi yang telah dipelajari dari Pertemuan 1 hingga 16.

---

## 1. Arsitektur Sistem & Struktur Proyek

### 1.1 Pola Arsitektur Microservices
* **Separasi Kode:** Hindari arsitektur Monolith. Aplikasi harus dipecah menjadi layanan-layanan kecil (*microservices*) yang independen dan terkelompok sesuai domain bisnisnya (misalnya: `Service Barang`, `Service Penjualan`, `Service Auth`).
* **Komunikasi Antar-Service:** Komunikasi *Backend-to-Backend* dilakukan secara *asynchronous* atau melalui perantara **REST API** menggunakan `ResponseEntity` untuk bertukar data dalam format **JSON**.
* **API Gateway:** Semua request dari Front-End (Angular) tidak boleh langsung menembus ke core microservices secara *direct*, melainkan wajib melewati pintu gerbang tunggal (**Spring Cloud Gateway**) untuk perutean (*routing*), *path rewriting*, monitoring, dan pengamanan terpusat.

### 1.2 Penerapan Pola MVC (Model-View-Controller)
Setiap microservice wajib mengorganisir struktur kodenya menggunakan pola ruang lingkup berikut:
* **Model (Entity):** Representasi tabel database berupa objek Java, memanfaatkan anotasi JPA.
* **Repository:** Interface yang meng-extend Spring Data JPA untuk interaksi query ke PostgreSQL.
* **Service & ServiceImpl:** Pemisahan tegas antara kontrak bisnis (Interface) dan implementasi logika inti (*Business Logic Class*). Seluruh kalkulasi, manipulasi data, dan validasi wajib diletakkan di `ServiceImpl`.
* **Controller:** Mengelola *endpoint* API, menerima input dari client (`@RequestParam`, `@RequestBody`, `@PathVariable`), dan mengembalikan `ResponseEntity` dengan HTTP Status Code yang sesuai.

---

## 2. Standar REST API & Penulisan JSON

### 2.1 HTTP Methods & Response Status
Gunakan HTTP Method sesuai dengan standar operasi CRUD:
* `GET`: Mengambil data dari server.
* `POST`: Membuat/menyisipkan data baru (Gunakan `@RequestBody`).
* `PUT`: Memperbarui data yang sudah ada secara keseluruhan.
* `DELETE`: Menghapus data.

Setiap *response* wajib dibungkus di dalam objek `ResponseEntity` yang memuat komponen **Body**, **Headers**, dan **HTTP Status Code** yang presisi (contoh: `200 OK`, `201 Created`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `500 Internal Server Error`).

### 2.2 Penulisan Objek JSON
Aturan sintaksis JSON yang wajib dipatuhi dalam *request* maupun *response*:
* Gunakan kurung kurawal `{ }` untuk mendefinisikan objek dan kurung siku `[ ]` untuk *list/array of objects*.
* Setiap properti (*key*) wajib berupa string yang dibungkus dengan tanda petik ganda (`"key"`), ditulis dalam format *camelCase*.
* Gunakan tipe data yang universal. Manfaatkan class `Object` di Java pada parameter Controller jika struktur nilai properti yang dilempar oleh Client bersifat dinamis atau tidak dapat diprediksi secara pasti, namun pastikan nama *key* tetap konsisten.

---

## 3. Database & Persistensi Data (PostgreSQL)

### 3.1 Desain Tabel & Auto-Increment
* Setiap tabel utama wajib memiliki kolom ID primer (*Primary Key*).
* Gunakan query penyesuaian di PostgreSQL agar kolom ID berjalan secara otomatis menggunakan klausa `GENERATED ALWAYS AS IDENTITY` untuk menjamin kelancaran fitur *auto-increment* saat melakukan operasi *insert* massal (seperti pada fitur import Excel).
* Pada level kode Java, pastikan anotasi pada properti ID di class Entity disesuaikan dengan strategi identitas database tersebut (`@Id`, `@GeneratedValue(strategy = GenerationType.IDENTITY)`).

### 3.2 Fungsi, Prosedur, & Trigger
* **Database-Level Logic:** Operasi kalkulasi berat atau otomatisasi internal database (misalnya kalkulasi otomatis `harga x jumlah`) harus diletakkan pada **Function & Procedure** di PostgreSQL.
* **Trigger Event:** Manfaatkan objek *Trigger* di PostgreSQL untuk mendeteksi manipulasi data (INSERT/UPDATE/DELETE).
* **Spring Boot Listener:** Hubungkan *trigger* database dengan Spring Boot menggunakan *Service Listener* khusus yang berjalan secara *stand-by* (memanfaatkan loop tak terbatas/infinite di background thread atau @PostConstruct) untuk langsung menangkap perubahan data dari DB.

---

## 4. Penanganan Kesalahan (Exception Handling)

* **Runtime Protection:** Semua blok kode yang memiliki potensi kegagalan sistem saat *runtime* (misalnya: interaksi database, pembacaan file IO, koneksi jaringan internet/SMTP, parsing token) wajib dibungkus dengan blok `try-catch` atau `try-catch-finally`.
* **Graceful Degradation:** Mencegah aplikasi berhenti secara abnormal (*freeze* atau *crash*). Blok `catch` harus menangkap tipe Exception secara spesifik, melakukan *logging* yang jelas, dan mengembalikan pesan kesalahan yang aman dan informatif kepada pengguna melalui `ResponseEntity`.
* **Blok Finally:** Gunakan blok `finally` untuk mengeksekusi *cleanup code* yang harus tetap berjalan, baik ketika terjadi error maupun tidak (seperti menutup koneksi stream/file).

---

## 5. Sistem Keamanan & Otentikasi (Spring Security)

Aplikasi mengimplementasikan dua jenis manajemen sesi keamanan tergantung kebutuhan spesifikasi sistem:

### 5.1 Stateful Authentication (Session-Based)
* Digunakan jika server perlu mengingat kondisi login user secara aktif di memori server.
* Waktu kedaluwarsa sesi (*session timeout*) wajib dikonfigurasi secara global di file `application.properties`.
* Gunakan class `SecurityConfig` untuk memetakan jalur *endpoint* mana saja yang memerlukan proteksi otentikasi.

### 5.2 Stateless Authentication (Token-Based / JWT)
* Digunakan untuk arsitektur microservices yang scalable, ringan, dan independen. Server tidak menyimpan data sesi user.
* **Alur Implementasi JWT:**
    1.  Tambahkan dependensi JWT di file `pom.xml`.
    2.  Buat class `JWTUtil` di package `utility` untuk bertindak sebagai pembuat (*generator*) dan validator token.
    3.  Buat class `JWTFilter` di package `utility` untuk menyaring dan memvalidasi token JWT pada setiap request HTTP yang masuk di header `Authorization: Bearer <token>`.
    4.  Modifikasi `SecurityConfig` untuk mengubah mode session creation menjadi *Stateless* (`SessionCreationPolicy.STATELESS`).

### 5.3 Otorisasi (Authorization) tingkat Method
* **Struktur Tabel Pendukung:** Konfigurasi hak akses wajib didukung oleh skema database minimal yang meliputi tabel: `User`, `Role`, `Menu`, dan tabel relasi `RolePermissions`/`MenuRole`.
* **Anotasi Keamanan:** Batasi hak akses eksekusi API tepat di atas method Controller menggunakan anotasi `@PreAuthorize`.
* **Custom Evaluator:** Untuk logika otorisasi yang dinamis dan berbasis hak akses menu dari DB, wajib mengimplementasikan class `CustomPermissionEvaluator` yang meng-override interface `PermissionEvaluator` di dalam package `config`.
* **Enkripsi Password:** Setiap password pengguna yang disimpan ke dalam database wajib dienkripsi terlebih dahulu menggunakan algoritma pengaman (misalnya MD5 atau BCrypt) pada class `AuthServiceImpl`.

---

## 6. Otomatisasi (Scheduler, Event, & Threading)

Bedakan penggunaan mekanisme pemicu (*trigger bungkusan kode*) berdasarkan karakteristik berikut:

### 6.1 Java Thread & @Async
* **Thread Manual:** Gunakan `new Thread().start()` jika aplikasi memerlukan komponen *Listener* independen yang harus terus stand-by di background tanpa menghentikan aliran utama aplikasi.
* **Anotasi @Async:** Gunakan `@Async` untuk mem-by pass operasi yang membutuhkan waktu eksekusi yang lama (seperti pengiriman email masal atau pengolahan file besar) agar dikelola otomatis secara asynchronous oleh sistem.
* *Catatan:* Berhati-hatilah terhadap borosnya resource memori dan potensi inkonsistensi data lintas thread.

### 6.2 Scheduler (Time-Driven)
* Gunakan anotasi `@Scheduled` di atas method yang harus berjalan otomatis berdasarkan waktu internal komputer tanpa perlu adanya hit/request dari user.
* **Tipe Penjadwalan:**
    * `fixedRate`: Berjalan berdasarkan interval waktu tetap yang dihitung dari awal eksekusi task sebelumnya.
    * `fixedDelay`: Berjalan dengan jeda waktu tetap setelah task sebelumnya selesai dieksekusi secara utuh.
    * `cron`: Berjalan pada jadwal spesifik yang sangat akurat (contoh: `"0 0 6 * * *"` untuk berjalan setiap pukul 06:00 pagi).
* *Kondisi Lapangan:* Pastikan class penyedia scheduler memiliki anotasi `@EnableScheduling`. Jika aplikasi *down*, scheduler tidak akan berjalan dan tidak ada mekanisme *auto-catch-up* terhadap jadwal yang terlewat setelah aplikasi menyala kembali.

### 6.3 Event Listener & Message Broker (Event-Driven)
* **Event Listener:** Digunakan untuk mengeksekusi task ketika ada pemicu berupa kejadian internal (event) di dalam aplikasi atau database, waktu eksekusinya tidak menentu.
* **Message Broker (Asynchronous Inter-service):** Untuk pengiriman pesan/event antar microservice yang berbeda secara terdistribusi dan asynchronous, gunakan platform Message Broker eksternal (seperti Kafka, RabbitMQ, atau ActiveMQ).

---

## 7. Sistem Integrasi Pengiriman Email (Mailing System)

* **Dependensi:** Memanfaatkan library `javax.mail` yang dikonfigurasi melalui Maven (`pom.xml`).
* **Konektivitas SMTP:** Layanan wajib terhubung ke internet untuk menghubungi server SMTP Google. Konfigurasi kredensial SMTP (Host, Port, Username, dan *App Passwords* dari Google Akun yang telah aktif 2FA) wajib diamankan dan diletakkan di file `application.properties`.
* **Format Pesan:** Informasi daftar data yang dikirim melalui email wajib diformat menggunakan struktur kode **HTML** di dalam string *body* agar tampilan laporan rapi dan mudah dibaca oleh pengguna.
* **Penerima Jamak:** Jika email dikirim ke lebih dari satu alamat tujuan di bagian `To`, `Cc`, atau `Bcc`, pisahkan antar-alamat menggunakan tanda koma (`,`).
* **Batasan Layanan:** Sadari batasan kuota akun Google standar gratis, yaitu maksimal 500 email per 24 jam. Gunakan *Public Inbox* (seperti Mailinator) untuk keperluan testing performa pengiriman agar aman.

---

## 8. Fitur Pengolahan File (Import & Export Excel)

* **Pustaka Apache POI:** Pemrosesan file Excel menggunakan pustaka **Apache POI** (*Poor Obfuscation Implementation*).
* **Fitur Import (Upload Excel):** Menerima file multipart dari client, membaca lembar kerja (*Workbook & Sheet*), melompati baris header, melakukan perulangan (*looping*) data mulai dari baris ke-2, melakukan *parsing* nilai sel, mengubahnya menjadi objek Entity, lalu menyimpannya ke tabel database.
* **Fitur Export (Download Excel):** Mengambil list data dari database, membuat instance Workbook baru secara dinamis, mengonstruksi baris header, mengisi sel demi sel dengan data entitas, mengonfigurasi tipe konten HTTP (*Content-Type*) sebagai spreadsheet, dan mengalirkannya ke *response output stream*.
* **Template Upload:** Sediakan endpoint khusus untuk mendownload file template kosong yang hanya berisi struktur header kolom yang valid agar meminimalisir kesalahan format saat user melakukan import data kembali.

---