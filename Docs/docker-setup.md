# 🐳 Docker Setup Guide — E.O.P (Eyes Of Priestess)

## Struktur File yang Dibuat

```
UAS/
├── docker-compose.yml          ← Orkestrasi seluruh stack
├── .env.example                ← Template variabel lingkungan
├── gateway/
│   ├── Dockerfile
│   ├── .dockerignore
│   └── src/main/resources/
│       └── application-docker.yml       ← Override host untuk Docker
├── identity/
│   ├── Dockerfile
│   ├── .dockerignore
│   └── src/main/resources/
│       └── application-docker.properties
├── core/
│   ├── Dockerfile
│   ├── .dockerignore
│   └── src/main/resources/
│       └── application-docker.properties
├── oracle/
│   ├── Dockerfile
│   ├── .dockerignore
│   └── src/main/resources/
│       └── application-docker.properties
└── frontend/
    ├── Dockerfile
    ├── .dockerignore
    └── nginx.conf
```

---

## Cara Menjalankan

### 1. Buat file `.env` dari template
```bash
cd d:\Java\lanjutan\UAS
copy .env.example .env
```

Lalu buka `.env` dan isi nilai yang sesuai — terutama:
- `GEMINI_API_KEY` → API Key Gemini AI Anda
- `MAIL_USERNAME` / `MAIL_PASSWORD` → Kredensial Gmail SMTP

### 2. Build dan jalankan semua container
```bash
docker compose up --build -d
```

### 3. Cek status container
```bash
docker compose ps
```

### 4. Lihat log service tertentu
```bash
docker compose logs -f gateway
docker compose logs -f identity
docker compose logs -f core
docker compose logs -f oracle
```

---

## URL Akses

| Service            | URL                          |
|--------------------|------------------------------|
| Frontend Angular   | http://localhost              |
| Gateway (API)      | http://localhost:8080         |
| Identity Service   | http://localhost:8081         |
| Core Finance       | http://localhost:8082         |
| Oracle Support     | http://localhost:8083         |
| RabbitMQ UI        | http://localhost:15672        |
| PostgreSQL Identity| localhost:5433                |
| PostgreSQL Finance | localhost:5434                |
| MongoDB            | localhost:27017               |

---

## Perintah Berguna

```bash
# Hentikan semua container (data tetap tersimpan)
docker compose stop

# Hentikan dan hapus container (data volume tetap)
docker compose down

# Hentikan dan hapus SEMUA termasuk data (berbahaya!)
docker compose down -v

# Rebuild hanya satu service
docker compose up --build -d identity

# Masuk ke shell PostgreSQL
docker exec -it eop-postgres-identity psql -U postgres -d eop_identity_db

# Masuk ke MongoDB shell
docker exec -it eop-mongodb mongosh eop_transaction_log

# Cek queue RabbitMQ
docker exec -it eop-rabbitmq rabbitmqctl list_queues
```

---

## Catatan Penting

### Spring Docker Profile
Setiap service memiliki file `application-docker.properties` yang meng-override host `localhost` ke nama container Docker. Profile ini otomatis aktif via `SPRING_PROFILES_ACTIVE=docker`.

### Inisialisasi Database (WAJIB sebelum pertama kali start)
Karena `spring.jpa.hibernate.ddl-auto=validate`, Spring tidak membuat tabel secara otomatis.

**Langkah yang disarankan:**
1. Jalankan dulu infrastruktur: `docker compose up -d postgres-identity postgres-finance mongodb rabbitmq`
2. Tunggu hingga healthy, lalu jalankan SQL schema (dari file `query` di root project)
3. Kemudian jalankan semua service: `docker compose up -d`

### Dua Instance PostgreSQL
- `postgres-identity` (port `5433`) → `eop_identity_db`
- `postgres-finance` (port `5434`) → `eop_finance_db`

Ini sesuai prinsip **Database-per-Service** microservices E.O.P.
