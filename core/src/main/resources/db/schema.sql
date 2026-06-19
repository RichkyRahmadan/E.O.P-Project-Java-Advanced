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

-- =============================================================================
-- FUNCTION: fn_update_wallet_updated_at()
-- Fungsi trigger untuk memperbarui kolom `updated_at` secara otomatis
-- setiap kali ada UPDATE pada tabel `wallets`.
--
-- Sesuai coding-style.md Section 3.2: "Operasi otomatisasi internal database
-- harus diletakkan pada Function & Procedure di PostgreSQL."
-- =============================================================================
CREATE OR REPLACE FUNCTION fn_update_wallet_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

-- =============================================================================
-- TRIGGER: trg_wallets_update_timestamp
-- Memanggil fn_update_wallet_updated_at() setiap kali ada operasi UPDATE
-- pada tabel wallets. Ini menjamin kolom `updated_at` selalu akurat
-- tanpa bergantung pada logika aplikasi (defense-in-depth).
-- =============================================================================
DROP TRIGGER IF EXISTS trg_wallets_update_timestamp ON wallets;
CREATE TRIGGER trg_wallets_update_timestamp
    BEFORE UPDATE ON wallets
    FOR EACH ROW
    EXECUTE FUNCTION fn_update_wallet_updated_at();

-- =============================================================================
-- FUNCTION: fn_get_wallet_balance(p_owner_id UUID)
-- Fungsi utilitas untuk mengambil saldo wallet berdasarkan owner_id.
-- Digunakan untuk verifikasi saldo di level database sebelum mutasi.
--
-- Sesuai coding-style.md Section 3.2: "Operasi kalkulasi berat atau
-- otomatisasi internal database harus diletakkan pada Function & Procedure."
-- =============================================================================
CREATE OR REPLACE FUNCTION fn_get_wallet_balance(p_owner_id UUID)
RETURNS DECIMAL(19,2)
LANGUAGE plpgsql
AS $$
DECLARE
    v_balance DECIMAL(19,2);
BEGIN
    SELECT balance INTO v_balance
    FROM wallets
    WHERE owner_id = p_owner_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Wallet untuk owner_id % tidak ditemukan.', p_owner_id;
    END IF;

    RETURN v_balance;
END;
$$;

-- =============================================================================
-- PROCEDURE: proc_top_up_wallet(p_owner_id UUID, p_amount DECIMAL)
-- Prosedur untuk melakukan top-up saldo dompet secara atomik.
-- Dapat dipanggil langsung dari SQL Client untuk maintenance / rekonsiliasi.
--
-- Contoh pemanggilan:
--   CALL proc_top_up_wallet('uuid-owner-id', 100000.00);
-- =============================================================================
CREATE OR REPLACE PROCEDURE proc_top_up_wallet(
    p_owner_id UUID,
    p_amount   DECIMAL(19,2)
)
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_amount <= 0 THEN
        RAISE EXCEPTION 'Jumlah top-up harus lebih besar dari 0. Nilai diterima: %', p_amount;
    END IF;

    UPDATE wallets
    SET balance = balance + p_amount
    WHERE owner_id = p_owner_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Wallet untuk owner_id % tidak ditemukan.', p_owner_id;
    END IF;

    RAISE NOTICE 'Top-up sebesar % berhasil untuk owner_id %', p_amount, p_owner_id;
END;
$$;
