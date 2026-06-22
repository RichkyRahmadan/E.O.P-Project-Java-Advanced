package com.priestess.core.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * TransactionDocument — Dokumen MongoDB untuk koleksi {@code transactions}.
 *
 * <p>Digunakan sebagai <b>log jejak rekam transaksi</b> sesuai pola Dual-Write
 * pada SECTION 7 blueprint E.O.P. Setiap transaksi finansial (Transfer P2P,
 * pembayaran QRIS, top-up, klaim voucher) wajib mencatat dokumen ini terlebih
 * dahulu ke MongoDB dengan status {@code PENDING} SEBELUM memanipulasi saldo
 * di PostgreSQL.
 *
 * <h2>Pola State Machine Transaksi</h2>
 * <ul>
 *   <li>{@code PENDING} — Jejak rekam awal dibuat, PostgreSQL belum diubah.</li>
 *   <li>{@code SUCCESS} — Mutasi saldo PostgreSQL berhasil.</li>
 *   <li>{@code FAILED}  — PostgreSQL rollback, field {@code note} berisi detail error.</li>
 *   <li>{@code DENIED}  — QRIS tidak dibayar dalam batas waktu (5 menit kedaluwarsa).</li>
 * </ul>
 *
 * <p>Desain ini memastikan tidak ada transaksi yang "hilang tanpa jejak"
 * meskipun terjadi kegagalan di tengah proses.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "transactions")
public class TransactionDocument {

    /** ID dokumen MongoDB (auto-generated ObjectId). */
    @Id
    private String id;

    /**
     * ID invoice unik untuk setiap transaksi.
     * Diindeks secara unique untuk pencarian cepat dan mencegah duplikat.
     * Format: {@code INV-<UUID>} atau {@code QRIS-<UUID>}.
     */
    @Indexed(unique = true)
    @Field("invoice_id")
    private String invoiceId;

    /**
     * Tipe transaksi. Contoh nilai:
     * {@code TRANSFER}, {@code QRIS_PAYMENT}, {@code TOP_UP}, {@code VOUCHER_REDEEM}.
     */
    @Field("transaction_type")
    private String transactionType;

    /**
     * Status transaksi: {@code PENDING}, {@code SUCCESS}, atau {@code FAILED}.
     */
    @Field("status")
    private String status;

    /** Nominal transaksi dalam rupiah. */
    @Field("amount")
    private BigDecimal amount;

    /**
     * Informasi pengirim (sender) dalam format objek tertanam.
     * Berisi {@code userId} dan {@code walletId}.
     */
    @Field("sender")
    private PartyInfo sender;

    /**
     * Informasi penerima (recipient) dalam format objek tertanam.
     * Berisi {@code userId} dan {@code walletId}.
     */
    @Field("recipient")
    private PartyInfo recipient;

    /**
     * Raw data QRIS (Base64 atau string JSON QR) untuk transaksi QRIS.
     * Bernilai {@code null} untuk tipe transaksi non-QRIS.
     */
    @Field("raw_qris_data")
    private String rawQrisData;

    /**
     * Catatan transaksi dari pengguna, atau pesan error jika status {@code FAILED}.
     */
    @Field("note")
    private String note;

    @Field("created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Batas waktu pembayaran QRIS.
     *
     * <p>Diisi saat {@code generateQris()} dengan nilai {@code createdAt + 5 menit}.
     * Null untuk transaksi non-QRIS (Transfer, Voucher, dll).
     * Scheduler {@code QrisExpiryScheduler} akan mengubah status menjadi
     * {@code DENIED} jika {@code expiresAt < now()} dan status masih {@code PENDING}.
     */
    @Field("expires_at")
    private LocalDateTime expiresAt;

    // =========================================================================
    // NESTED EMBEDDED DOCUMENT
    // =========================================================================

    /**
     * PartyInfo — Informasi identitas pihak yang terlibat dalam transaksi.
     * Disimpan sebagai sub-dokumen tertanam (embedded) di MongoDB.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PartyInfo {

        /** UUID pengguna dari Identity Service (tanpa FK lintas DB). */
        @Field("user_id")
        private String userId;

        /** UUID wallet dari tabel wallets PostgreSQL. */
        @Field("wallet_id")
        private String walletId;

        /** Nama/label pihak — opsional, untuk display di riwayat transaksi. */
        @Field("display_name")
        private String displayName;
    }
}
