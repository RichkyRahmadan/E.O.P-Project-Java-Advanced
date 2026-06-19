# Rencana Koreksi E.O.P — Fintech-Grade Architecture

## Analisis Gap vs Fintech Kelas Produksi (GoPay/OVO/Dana)

---

## GAP 1 — Tidak Ada Standard API Response Envelope [KRITIKAL]
Fintech seperti GoPay selalu membungkus response dalam envelope:
{ "code": "SUCCESS", "message": "...", "data": {...}, "timestamp": "..." }
Fix: Buat ApiResponse<T> generic wrapper. Semua Controller return ResponseEntity<ApiResponse<T>>.

## GAP 2 — Transfer Menggunakan Wallet ID bukan Username [UX FATAL]
TransferRequest.recipientWalletId pakai UUID mentah. GoPay pakai nomor HP/username.
Fix: Tambah ownerUsername + walletNumber di WalletEntity. Transfer by walletNumber.

## GAP 3 — Tidak Ada Riwayat Transaksi [UX KRITIKAL]
Tidak ada endpoint GET /api/finance/transactions/my untuk riwayat user.
Fix: Tambah endpoint + query di TransactionRepository + Tab riwayat di frontend.

## GAP 4 — Tidak Ada Fitur Top-Up [FEATURE MISSING]
Saldo hanya bisa bertambah dari voucher.
Fix: Buat TopUpRequest DTO + endpoint POST /api/finance/topup.

## GAP 5 — Frontend/Backend DTO Mismatch [BUG]
finance.service.ts kirim "recipientUsernameOrEmail" tapi backend ekspek "recipientWalletId" — selalu gagal 400.
Fix: Sinkronkan setelah GAP 2 selesai.

## GAP 6 — Wallet Tidak Ada Nomor Mudah Dibaca [UX]
Wallet ID adalah UUID raw. GoPay tampilkan nomor dompet format EOP-XXXX-XXXX-XXXX.
Fix: Generate walletNumber saat wallet dibuat.

## GAP 7 — Wallet Tidak Dibuat Otomatis Saat Registrasi [ARSITEKTUR]
User harus hit /api/finance/wallet dulu baru wallet terbentuk (lazy init).
Fix: Identity Service publish event user.registered ke RabbitMQ. Core Finance consume event dan buat wallet otomatis.

---

## URUTAN IMPLEMENTASI

### Phase 1 — Backend Core Finance Service
1. Buat ApiResponse<T> envelope
2. Tambah walletNumber + ownerUsername di WalletEntity + migration
3. Buat TopUpRequest.java DTO
4. Update TransferRequest — ganti recipientWalletId -> recipientWalletNumber
5. Update WalletRepository — query by walletNumber, ownerUsername
6. Update FinanceService/Impl — tambah topUp() + getMyTransactions()
7. Update FinanceController — endpoint baru + wrap semua response

### Phase 2 — Backend Identity Service
8. AuthServiceImpl.registerUser + registerMerchant publish event user.registered
9. Core Finance tambah consumer UserRegisteredConsumer untuk inisialisasi wallet otomatis

### Phase 3 — Frontend Angular
10. Update finance.service.ts — sync DTO baru, tambah getMyTransactions()
11. Update user-dashboard — riwayat transaksi, top-up, wallet number display
12. Update merchant-dashboard — sync
