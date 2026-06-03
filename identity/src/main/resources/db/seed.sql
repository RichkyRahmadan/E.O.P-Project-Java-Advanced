-- =============================================================================
-- E.O.P IDENTITY SERVICE — Seed Data (Data Awal)
-- Database  : eop_identity_db (PostgreSQL)
-- Eksekusi  : Jalankan SETELAH schema.sql berhasil dieksekusi.
--
-- Script ini menggunakan pola INSERT ... ON CONFLICT DO NOTHING agar aman
-- dijalankan berulang kali tanpa menyebabkan error duplikat.
--
-- PENTING — Password Admin:
--   Plain text : Admin@EOP2025!
--   BCrypt hash: Di-generate menggunakan BCryptPasswordEncoder strength 10.
--   JANGAN ubah hash ini secara manual kecuali Anda menggantinya dengan hash baru
--   dari password yang benar.
-- =============================================================================

-- =============================================================================
-- LANGKAH 1 — Insert Roles
-- =============================================================================
INSERT INTO roles (id, role_name) VALUES
    ('a1000000-0000-0000-0000-000000000001', 'ROLE_USER'),
    ('a1000000-0000-0000-0000-000000000002', 'ROLE_MERCHANT'),
    ('a1000000-0000-0000-0000-000000000003', 'ROLE_ADMIN')
ON CONFLICT (role_name) DO NOTHING;

-- =============================================================================
-- LANGKAH 2 — Insert Menus (Nama Permission untuk @PreAuthorize)
--
-- Format penamaan menu_name mengikuti konvensi:
--   [DOMAIN]_[ACTION]
-- Ini adalah nilai yang digunakan di controller pada anotasi:
--   @PreAuthorize("hasPermission(null, 'WALLET_VIEW')")
-- =============================================================================
INSERT INTO menus (id, menu_name, description) VALUES
    -- Wallet & Finance Features
    ('b1000000-0000-0000-0000-000000000001', 'WALLET_VIEW',          'Melihat saldo dan info dompet sendiri'),
    ('b1000000-0000-0000-0000-000000000002', 'TRANSFER',             'Melakukan transfer P2P ke pengguna lain'),
    ('b1000000-0000-0000-0000-000000000003', 'QRIS_GENERATE',        'Merchant: Generate invoice QRIS dinamis'),
    ('b1000000-0000-0000-0000-000000000004', 'QRIS_PAY',             'User: Membayar tagihan QRIS dari merchant'),
    ('b1000000-0000-0000-0000-000000000005', 'VOUCHER_REDEEM',       'Mengklaim kode voucher untuk isi saldo'),
    ('b1000000-0000-0000-0000-000000000006', 'TRANSACTION_HISTORY',  'Melihat riwayat transaksi sendiri'),
    -- Support Features
    ('b1000000-0000-0000-0000-000000000007', 'COMPLAINT_SUBMIT',     'Mengajukan keluhan / tiket support'),
    -- Admin Features
    ('b1000000-0000-0000-0000-000000000008', 'ADMIN_KYC',            'Admin: Verifikasi KYC pengguna'),
    ('b1000000-0000-0000-0000-000000000009', 'ADMIN_SUSPEND',        'Admin: Membekukan akun pengguna'),
    ('b1000000-0000-0000-0000-000000000010', 'ADMIN_MERCHANT_VERIFY','Admin: Verifikasi profil merchant'),
    ('b1000000-0000-0000-0000-000000000011', 'ADMIN_DASHBOARD',      'Admin: Melihat dashboard dan laporan'),
    ('b1000000-0000-0000-0000-000000000012', 'EXPORT_REPORT',        'Export laporan transaksi ke Excel')
ON CONFLICT (menu_name) DO NOTHING;

-- =============================================================================
-- LANGKAH 3 — Insert Role Permissions
-- Maping: Role → Menu → Access Type
-- =============================================================================

-- ---
-- ROLE_USER Permissions
-- User biasa bisa: lihat wallet, transfer, bayar QRIS, redeem voucher,
-- lihat riwayat, dan submit keluhan
-- ---
INSERT INTO role_permissions (id, role_id, menu_id, access_type) VALUES
    ('c1000000-0000-0000-0000-000000000001',
        'a1000000-0000-0000-0000-000000000001',
        'b1000000-0000-0000-0000-000000000001',
        'READ'),    -- USER → WALLET_VIEW
    ('c1000000-0000-0000-0000-000000000002',
        'a1000000-0000-0000-0000-000000000001',
        'b1000000-0000-0000-0000-000000000002',
        'EXECUTE'), -- USER → TRANSFER
    ('c1000000-0000-0000-0000-000000000003',
        'a1000000-0000-0000-0000-000000000001',
        'b1000000-0000-0000-0000-000000000004',
        'EXECUTE'), -- USER → QRIS_PAY
    ('c1000000-0000-0000-0000-000000000004',
        'a1000000-0000-0000-0000-000000000001',
        'b1000000-0000-0000-0000-000000000005',
        'EXECUTE'), -- USER → VOUCHER_REDEEM
    ('c1000000-0000-0000-0000-000000000005',
        'a1000000-0000-0000-0000-000000000001',
        'b1000000-0000-0000-0000-000000000006',
        'READ'),    -- USER → TRANSACTION_HISTORY
    ('c1000000-0000-0000-0000-000000000006',
        'a1000000-0000-0000-0000-000000000001',
        'b1000000-0000-0000-0000-000000000007',
        'EXECUTE')  -- USER → COMPLAINT_SUBMIT
ON CONFLICT DO NOTHING;

-- ---
-- ROLE_MERCHANT Permissions
-- Merchant bisa: semua yang USER bisa + generate QRIS
-- ---
INSERT INTO role_permissions (id, role_id, menu_id, access_type) VALUES
    ('c1000000-0000-0000-0000-000000000011',
        'a1000000-0000-0000-0000-000000000002',
        'b1000000-0000-0000-0000-000000000001',
        'READ'),    -- MERCHANT → WALLET_VIEW
    ('c1000000-0000-0000-0000-000000000012',
        'a1000000-0000-0000-0000-000000000002',
        'b1000000-0000-0000-0000-000000000003',
        'EXECUTE'), -- MERCHANT → QRIS_GENERATE
    ('c1000000-0000-0000-0000-000000000013',
        'a1000000-0000-0000-0000-000000000002',
        'b1000000-0000-0000-0000-000000000004',
        'EXECUTE'), -- MERCHANT → QRIS_PAY
    ('c1000000-0000-0000-0000-000000000014',
        'a1000000-0000-0000-0000-000000000002',
        'b1000000-0000-0000-0000-000000000006',
        'READ'),    -- MERCHANT → TRANSACTION_HISTORY
    ('c1000000-0000-0000-0000-000000000015',
        'a1000000-0000-0000-0000-000000000002',
        'b1000000-0000-0000-0000-000000000007',
        'EXECUTE'), -- MERCHANT → COMPLAINT_SUBMIT
    ('c1000000-0000-0000-0000-000000000016',
        'a1000000-0000-0000-0000-000000000002',
        'b1000000-0000-0000-0000-000000000012',
        'READ')     -- MERCHANT → EXPORT_REPORT
ON CONFLICT DO NOTHING;

-- ---
-- ROLE_ADMIN Permissions
-- Admin memiliki SEMUA akses
-- ---
INSERT INTO role_permissions (id, role_id, menu_id, access_type) VALUES
    ('c1000000-0000-0000-0000-000000000021',
        'a1000000-0000-0000-0000-000000000003',
        'b1000000-0000-0000-0000-000000000001',
        'READ'),    -- ADMIN → WALLET_VIEW
    ('c1000000-0000-0000-0000-000000000022',
        'a1000000-0000-0000-0000-000000000003',
        'b1000000-0000-0000-0000-000000000006',
        'READ'),    -- ADMIN → TRANSACTION_HISTORY
    ('c1000000-0000-0000-0000-000000000023',
        'a1000000-0000-0000-0000-000000000003',
        'b1000000-0000-0000-0000-000000000008',
        'EXECUTE'), -- ADMIN → ADMIN_KYC
    ('c1000000-0000-0000-0000-000000000024',
        'a1000000-0000-0000-0000-000000000003',
        'b1000000-0000-0000-0000-000000000009',
        'EXECUTE'), -- ADMIN → ADMIN_SUSPEND
    ('c1000000-0000-0000-0000-000000000025',
        'a1000000-0000-0000-0000-000000000003',
        'b1000000-0000-0000-0000-000000000010',
        'EXECUTE'), -- ADMIN → ADMIN_MERCHANT_VERIFY
    ('c1000000-0000-0000-0000-000000000026',
        'a1000000-0000-0000-0000-000000000003',
        'b1000000-0000-0000-0000-000000000011',
        'READ'),    -- ADMIN → ADMIN_DASHBOARD
    ('c1000000-0000-0000-0000-000000000027',
        'a1000000-0000-0000-0000-000000000003',
        'b1000000-0000-0000-0000-000000000012',
        'EXECUTE')  -- ADMIN → EXPORT_REPORT
ON CONFLICT DO NOTHING;

-- =============================================================================
-- LANGKAH 4 — Insert Akun Admin Default
--
-- Username : admin
-- Email    : admin@eop.priestess.com
-- Password : Admin@EOP2025!   (BCrypt hash di bawah)
-- Status   : ACTIVE (Admin tidak perlu proses KYC)
-- Role     : ROLE_ADMIN
--
-- PERINGATAN KEAMANAN: Ganti password ini segera di lingkungan PRODUCTION!
-- =============================================================================
INSERT INTO users (id, username, email, password, role_id, status, created_at) VALUES (
    'd1000000-0000-0000-0000-000000000001',
    'admin',
    'admin@eop.priestess.com',
    '$2a$10$N.PQkVGGe7v5Wf5YJn0kQO6n3K0w8UHyQ/3G8s4Q1LYd0E3XsIH5W',
    'a1000000-0000-0000-0000-000000000003',
    'ACTIVE',
    NOW()
) ON CONFLICT (username) DO NOTHING;

-- =============================================================================
-- VERIFIKASI (Opsional — jalankan untuk mengecek seed data berhasil)
-- =============================================================================
-- SELECT r.role_name, m.menu_name, rp.access_type
-- FROM role_permissions rp
-- JOIN roles r  ON rp.role_id = r.id
-- JOIN menus m  ON rp.menu_id = m.id
-- ORDER BY r.role_name, m.menu_name;
