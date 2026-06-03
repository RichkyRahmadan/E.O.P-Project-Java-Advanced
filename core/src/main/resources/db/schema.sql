-- =============================================================================
-- E.O.P CORE FINANCE SERVICE — DDL Schema
-- Database  : eop_finance_db (PostgreSQL)
-- Eksekusi  : Jalankan script ini SEKALI sebelum menjalankan Core Finance Service
--             untuk pertama kali. Gunakan jika ddl-auto=validate/none.
-- Catatan   : MongoDB (eop_transaction_log) tidak memerlukan DDL —
--             collection dibuat otomatis saat dokumen pertama disimpan.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================================================
-- TABEL: wallets
-- Dompet digital untuk User dan Merchant.
-- owner_id merujuk ke users.id atau merchants.id di Identity Service
-- (TIDAK ada FK lintas database — sesuai prinsip Database-per-Service).
--
-- PENTING:
--   - kolom 'version' adalah kunci Optimistic Locking (@Version di JPA).
--     JANGAN pernah update nilai ini secara manual via SQL.
--     Hibernate mengelola increment ini secara atomik.
--   - CHECK (balance >= 0.00) adalah safety net terakhir di level DB.
-- =============================================================================
CREATE TABLE IF NOT EXISTS wallets (
    id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID           NOT NULL UNIQUE,
    owner_type  VARCHAR(20)    NOT NULL CHECK (owner_type IN ('USER', 'MERCHANT')),
    balance     DECIMAL(19,2)  NOT NULL DEFAULT 0.00
                               CHECK (balance >= 0.00),
    version     INTEGER        NOT NULL DEFAULT 0,
    created_at  TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- =============================================================================
-- TABEL: vouchers
-- Kode voucher yang dapat diklaim pengguna untuk mengisi saldo.
-- Menggunakan BIGSERIAL (auto-increment) sebagai PK, bukan UUID,
-- sesuai blueprint SECTION 5B.
-- =============================================================================
CREATE TABLE IF NOT EXISTS vouchers (
    id          BIGSERIAL      PRIMARY KEY,
    code        VARCHAR(50)    NOT NULL UNIQUE,
    amount      DECIMAL(19,2)  NOT NULL CHECK (amount > 0),
    is_redeemed BOOLEAN        NOT NULL DEFAULT FALSE,
    redeemed_by UUID,
    redeemed_at TIMESTAMP,
    created_at  TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- =============================================================================
-- INDEX
-- =============================================================================
CREATE INDEX IF NOT EXISTS idx_wallets_owner_id   ON wallets(owner_id);
CREATE INDEX IF NOT EXISTS idx_vouchers_code      ON vouchers(code);
CREATE INDEX IF NOT EXISTS idx_vouchers_redeemed  ON vouchers(is_redeemed);
