-- =============================================================================
-- E.O.P IDENTITY SERVICE — DDL Schema
-- Database  : eop_identity_db (PostgreSQL)
-- Eksekusi  : Jalankan script ini SEKALI sebelum menjalankan Identity Service
--             untuk pertama kali. Gunakan jika ddl-auto=validate/none.
-- Urutan    : roles → menus → users → merchants → role_permissions
--             (urutkan berdasarkan dependency FK)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- EKSTENSI
-- Aktifkan uuid-ossp agar PostgreSQL bisa generate UUID via gen_random_uuid()
-- -----------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================================================
-- TABEL: roles
-- Menyimpan daftar role yang tersedia di sistem (ROLE_USER, ROLE_MERCHANT, ROLE_ADMIN)
-- =============================================================================
CREATE TABLE IF NOT EXISTS roles (
    id        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    role_name VARCHAR(50) NOT NULL UNIQUE
);

-- =============================================================================
-- TABEL: menus
-- Menyimpan daftar fitur/menu yang dapat dikontrol aksesnya via RBAC
-- =============================================================================
CREATE TABLE IF NOT EXISTS menus (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    menu_name   VARCHAR(50) NOT NULL UNIQUE,
    description TEXT
);

-- =============================================================================
-- TABEL: users
-- Menyimpan akun pengguna. password disimpan dalam format BCrypt hash.
-- Status: PENDING (default) → ACTIVE (setelah KYC) → SUSPENDED (dibekukan Admin)
-- =============================================================================
CREATE TABLE IF NOT EXISTS users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(50)  NOT NULL UNIQUE,
    email         VARCHAR(100) NOT NULL UNIQUE,
    phone         VARCHAR(20)  UNIQUE,
    password      VARCHAR(255) NOT NULL,
    role_id       UUID         NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                               CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED')),
    refresh_token VARCHAR(500),
    created_at    TIMESTAMP    DEFAULT NOW(),

    CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES roles(id)
);

-- =============================================================================
-- TABEL: merchants
-- Data profil merchant. Terhubung 1-ke-1 dengan users.
-- is_verified: false (default) → true setelah Admin memverifikasi
-- =============================================================================
CREATE TABLE IF NOT EXISTS merchants (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL UNIQUE,
    merchant_name VARCHAR(100) NOT NULL,
    address       TEXT,
    is_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    owner_id      UUID,
    created_at    TIMESTAMP    DEFAULT NOW(),

    CONSTRAINT fk_merchant_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_merchant_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);

-- =============================================================================
-- TABEL: role_permissions
-- Tabel junction many-to-many antara roles dan menus,
-- dengan kolom tambahan access_type (READ, WRITE, EXECUTE)
-- =============================================================================
CREATE TABLE IF NOT EXISTS role_permissions (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id     UUID        NOT NULL,
    menu_id     UUID        NOT NULL,
    access_type VARCHAR(20),

    CONSTRAINT fk_roleperm_role FOREIGN KEY (role_id) REFERENCES roles(id),
    CONSTRAINT fk_roleperm_menu FOREIGN KEY (menu_id) REFERENCES menus(id)
);

-- =============================================================================
-- INDEX
-- Percepat query yang sering dipanggil oleh AuthService dan JWTFilter
-- =============================================================================
CREATE INDEX IF NOT EXISTS idx_users_username       ON users(username);
-- =============================================================================
-- INDEX
-- Percepat query yang sering dipanggil oleh AuthService dan JWTFilter
-- =============================================================================
CREATE INDEX IF NOT EXISTS idx_users_email          ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_phone          ON users(phone);
CREATE INDEX IF NOT EXISTS idx_users_refresh_token  ON users(refresh_token);
CREATE INDEX IF NOT EXISTS idx_users_role_id        ON users(role_id);
CREATE INDEX IF NOT EXISTS idx_merchants_user_id    ON merchants(user_id);
CREATE INDEX IF NOT EXISTS idx_merchants_owner_id   ON merchants(owner_id);
CREATE INDEX IF NOT EXISTS idx_roleperm_role_id     ON role_permissions(role_id);
