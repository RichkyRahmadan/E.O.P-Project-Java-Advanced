package com.priestess.core.repository;

import com.priestess.core.entity.WalletEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

/**
 * WalletRepository — Repository JPA untuk entitas {@link WalletEntity}.
 *
 * <p>Menyediakan operasi database untuk tabel {@code wallets} di PostgreSQL.
 * Query kustom di sini dioptimalkan untuk mendukung pola Dual-Write dan
 * Optimistic Locking yang didefinisikan di SECTION 7 blueprint.
 */
@Repository
public interface WalletRepository extends JpaRepository<WalletEntity, UUID> {

    /**
     * Mencari wallet berdasarkan UUID pemilik.
     *
     * <p>Digunakan setelah autentikasi berhasil untuk mengambil wallet
     * pengguna yang sedang login (via header {@code X-User-Id} dari Gateway).
     *
     * @param ownerId UUID pemilik wallet (User atau Merchant)
     * @return {@link Optional} berisi wallet, atau empty jika tidak ditemukan
     */
    Optional<WalletEntity> findByOwnerId(UUID ownerId);

    /**
     * Mencari wallet berdasarkan UUID pemilik dan tipe pemilik.
     *
     * <p>Digunakan untuk membedakan antara wallet User dan wallet Merchant
     * ketika {@code ownerId} mungkin milik keduanya (dalam skenario Merchant
     * yang juga memiliki akun User).
     *
     * @param ownerId   UUID pemilik
     * @param ownerType tipe pemilik: {@code "USER"} atau {@code "MERCHANT"}
     * @return {@link Optional} berisi wallet yang cocok
     */
    Optional<WalletEntity> findByOwnerIdAndOwnerType(UUID ownerId, String ownerType);

    /**
     * Mengambil wallet dengan PESSIMISTIC WRITE lock untuk operasi debit/kredit
     * yang memerlukan keamanan tinggi (alternatif Optimistic Locking).
     *
     * <p>Digunakan dalam skenario di mana {@code @Version} Optimistic Locking
     * dianggap terlalu agresif (banyak retry). Dalam E.O.P, Optimistic Locking
     * adalah pilihan utama; method ini disediakan sebagai fallback.
     *
     * @param ownerId UUID pemilik wallet
     * @return {@link Optional} wallet yang telah di-lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WalletEntity w WHERE w.ownerId = :ownerId")
    Optional<WalletEntity> findByOwnerIdWithLock(@Param("ownerId") UUID ownerId);
}
