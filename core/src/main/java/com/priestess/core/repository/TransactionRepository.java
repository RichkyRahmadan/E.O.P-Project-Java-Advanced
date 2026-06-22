package com.priestess.core.repository;

import com.priestess.core.entity.TransactionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * TransactionRepository — Repository MongoDB untuk dokumen {@link TransactionDocument}.
 *
 * <p>Menyediakan operasi CRUD dan query kustom untuk koleksi {@code transactions}
 * di MongoDB. Digunakan dalam pola Dual-Write (SECTION 7) untuk:
 * <ul>
 *   <li>Menyimpan log transaksi {@code PENDING} sebelum mutasi PostgreSQL.</li>
 *   <li>Memperbarui status menjadi {@code SUCCESS} atau {@code FAILED}
 *       setelah hasil transaksi PostgreSQL diketahui.</li>
 *   <li>Polling status invoice oleh Angular Dashboard Merchant.</li>
 * </ul>
 */
@Repository
public interface TransactionRepository extends MongoRepository<TransactionDocument, String> {

    /**
     * Mencari transaksi berdasarkan invoice ID unik.
     *
     * <p>Digunakan oleh endpoint polling Angular ({@code GET /api/finance/transactions/{invoiceId}})
     * untuk mengecek apakah status QRIS sudah berubah dari {@code PENDING} ke {@code SUCCESS}.
     *
     * @param invoiceId ID invoice unik (format: {@code INV-<UUID>} atau {@code QRIS-<UUID>})
     * @return {@link Optional} berisi dokumen transaksi
     */
    Optional<TransactionDocument> findByInvoiceId(String invoiceId);

    /**
     * Mengambil semua riwayat transaksi milik pengguna tertentu (sebagai pengirim).
     *
     * @param senderId UUID pengguna (dari klaim JWT {@code sub})
     * @return daftar dokumen transaksi yang dikirim oleh userId tersebut
     */
    List<TransactionDocument> findBySenderUserId(String senderId);

    /**
     * Mengambil semua riwayat transaksi di mana pengguna menjadi penerima.
     *
     * @param recipientId UUID pengguna sebagai penerima
     * @return daftar dokumen transaksi yang diterima oleh userId tersebut
     */
    List<TransactionDocument> findByRecipientUserId(String recipientId);

    /**
     * Mengecek apakah invoice ID sudah terdaftar di database.
     * Digunakan untuk mencegah duplikasi invoice sebelum menyimpan.
     *
     * @param invoiceId ID invoice yang akan diperiksa
     * @return {@code true} jika invoice sudah ada
     */
    boolean existsByInvoiceId(String invoiceId);

    /**
     * Mencari semua transaksi QRIS_PAYMENT berstatus PENDING yang sudah melewati
     * batas waktu pembayaran ({@code expiresAt} sebelum waktu saat ini).
     *
     * <p>Digunakan oleh {@code QrisExpiryScheduler} untuk mengganti status
     * menjadi {@code DENIED} secara otomatis setiap menit.
     *
     * @param now waktu referensi (biasanya {@code LocalDateTime.now()})
     * @return daftar dokumen yang kedaluwarsa
     */
    @Query("{ 'transaction_type': 'QRIS_PAYMENT', 'status': 'PENDING', 'expires_at': { $lt: ?0 } }")
    List<TransactionDocument> findExpiredPendingQris(LocalDateTime now);
}
