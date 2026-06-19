# Panduan Setup & Instalasi Proyek E.O.P

Dokumen ini menjelaskan langkah-langkah persiapan, instalasi, dan cara menjalankan seluruh komponen sistem E.O.P (Gateway, Identity, Core Finance, Oracle, dan Frontend Angular) secara lokal.

---

## 1. Prasyarat Sistem & Dependensi

Pastikan perangkat lokal Anda telah terinstal software berikut:
* **Java Development Kit (JDK):** Versi 17 atau 21 (direkomendasikan versi 21).
* **Apache Maven:** Versi 3.8+ untuk membuild dependensi Java.
* **Node.js & npm:** Versi 18+ untuk menjalankan Angular CLI.
* **Docker Desktop:** Sangat direkomendasikan untuk menjalankan seluruh database, broker pesan, dan Redis cache secara kontainer.

---

## 2. Menjalankan Infrastruktur (Docker Compose)

Proyek ini telah menyediakan file `docker-compose.yml` lengkap di root direktori yang mengonfigurasi seluruh infrastruktur backend:
* **PostgreSQL (Identity DB):** Port `5433`
* **PostgreSQL (Finance DB):** Port `5434`
* **MongoDB (Support DB & Transaction Log):** Port `27017`
* **RabbitMQ (Message Broker):** Port `5672` (AMQP) & `15672` (Web Management UI)
* **Redis (Session Store & Cache):** Port `6379`

### Langkah-langkah:

1. **Konfigurasi Environment:**
   Salin file `.env.example` menjadi `.env` di root folder dan sesuaikan variabel lingkupnya (seperti `GEMINI_API_KEY` dan email SMTP).

2. **Jalankan Infrastruktur:**
   Jika ingin menjalankan **hanya** kontainer pendukung (database, broker, Redis) secara lokal dan menjalankan microservice Java dari IDE/Maven:
   ```bash
   docker compose up -d postgres-identity postgres-finance mongodb rabbitmq redis
   ```
   Jika ingin menjalankan **seluruh stack** (termasuk microservice backend Java dan frontend Angular di Nginx) dalam kontainer:
   ```bash
   docker compose up --build -d
   ```

### Inisialisasi Database PostgreSQL
Setelah PostgreSQL menyala, inisialisasi skema dan data awal menggunakan script berikut:
- Skema & Seed untuk Identity: `identity/src/main/resources/db/schema.sql` dan `seed.sql`.
- Skema & Seed untuk Finance: `core/src/main/resources/db/schema.sql` dan `seed.sql`.

---

## 3. Menjalankan Backend Microservices (Lokal / Non-Docker)

Jika Anda ingin menjalankan layanan Java secara lokal dari Command Line/IDE:

### A. E.O.P Gateway (Port 8080)
Pastikan Redis (Port 6379) aktif untuk stateful session storage.
```bash
cd gateway
mvn spring-boot:run
```

### B. Identity Service (Port 8081)
Pastikan PostgreSQL (Port 5433) dan Redis (Port 6379) aktif.
```bash
cd identity
mvn spring-boot:run
```

### C. Core Finance Service (Port 8082)
Pastikan PostgreSQL (Port 5434) dan MongoDB (Port 27017) aktif.
```bash
cd core
mvn spring-boot:run
```

### D. Support & Oracle Service (Port 8083)
Pastikan MongoDB (Port 27017) aktif dan API Key Google Gemini telah dikonfigurasi di `.env` atau `oracle/src/main/resources/application.properties`:
```properties
gemini.api.key=YOUR_GEMINI_API_KEY
```
Kemudian jalankan:
```bash
cd oracle
mvn spring-boot:run
```

---

## 4. Menjalankan Frontend Angular (Port 4200)

1. Buka folder `frontend`:
   ```bash
   cd frontend
   ```
2. Instal seluruh dependensi npm (hanya pada setup awal):
   ```bash
   npm install
   ```
3. Jalankan aplikasi web lokal:
   ```bash
   npm start
   ```
4. Buka peramban (browser) Anda pada alamat: `http://localhost:4200`
5. Untuk masuk sebagai Administrator, gunakan kredensial berikut:
   * **Username:** `admin`
   * **Password:** `Admin@EOP2025!`
