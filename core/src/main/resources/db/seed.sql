-- =============================================================================
-- E.O.P CORE FINANCE SERVICE — Seed Data (Data Awal)
-- Database  : eop_finance_db (PostgreSQL)
-- Eksekusi  : Jalankan SETELAH schema.sql berhasil dieksekusi.
--
-- Catatan   : Data dompet (wallets) akan dibuat OTOMATIS oleh Core Finance
--             Service saat user/merchant pertama kali terdaftar via Identity.
--             Script ini hanya menyediakan beberapa voucher untuk keperluan testing.
-- =============================================================================

-- =============================================================================
-- VOUCHER TESTING
-- Gunakan kode-kode ini untuk menguji fitur redeem voucher.
-- Semua voucher ini bernilai nominal berbeda untuk pengujian yang beragam.
-- =============================================================================
INSERT INTO vouchers (code, amount, is_redeemed) VALUES
    ('EOP-WELCOME-10K',  10000.00,  FALSE),
    ('EOP-BONUS-25K',    25000.00,  FALSE),
    ('EOP-PROMO-50K',    50000.00,  FALSE),
    ('EOP-SPECIAL-100K', 100000.00, FALSE),
    ('EOP-TEST-5K',      5000.00,   FALSE)
ON CONFLICT (code) DO NOTHING;
