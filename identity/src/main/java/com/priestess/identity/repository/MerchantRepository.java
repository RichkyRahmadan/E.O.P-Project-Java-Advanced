package com.priestess.identity.repository;

import com.priestess.identity.entity.MerchantEntity;
import com.priestess.identity.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * MerchantRepository — Kontrak akses data untuk entitas {@link MerchantEntity}.
 *
 * <p>Meng-extend {@link JpaRepository} untuk mendapatkan operasi CRUD bawaan
 * serta kemampuan pagination dan sorting tanpa implementasi tambahan.
 *
 * <p>Method query tambahan:
 * <ul>
 *   <li>{@link #findByUser(UserEntity)} — Digunakan untuk mengambil profil merchant
 *       berdasarkan entitas pengguna pemiliknya.</li>
 *   <li>{@link #findByUser_Id(UUID)} — Digunakan untuk mengambil profil merchant
 *       berdasarkan UUID pengguna; berguna saat hanya UUID yang tersedia
 *       (misal: dari klaim {@code X-User-Id} di HTTP header internal).</li>
 *   <li>{@link #existsByUser(UserEntity)} — Digunakan untuk validasi cepat,
 *       memastikan seorang pengguna belum memiliki profil merchant sebelum
 *       dibuat yang baru (constraint One-to-One).</li>
 * </ul>
 */
public interface MerchantRepository extends JpaRepository<MerchantEntity, UUID> {

    /**
     * Mencari profil merchant berdasarkan objek pengguna pemiliknya.
     *
     * @param user objek {@link UserEntity} pemilik merchant
     * @return {@link Optional} berisi {@link MerchantEntity} jika ditemukan,
     *         atau {@link Optional#empty()} jika pengguna belum memiliki profil merchant.
     */
    Optional<MerchantEntity> findByUser(UserEntity user);

    /**
     * Mencari profil merchant berdasarkan UUID pengguna pemiliknya.
     *
     * <p>Method ini menggunakan Spring Data JPA's derived query dari relasi
     * {@code user.id}, sehingga Hibernate akan men-generate JOIN secara otomatis.
     *
     * @param userId UUID dari pengguna pemilik merchant
     * @return {@link Optional} berisi {@link MerchantEntity} jika ditemukan,
     *         atau {@link Optional#empty()} jika tidak ada.
     */
    Optional<MerchantEntity> findByUser_Id(UUID userId);

    /**
     * Memeriksa apakah seorang pengguna sudah memiliki profil merchant.
     *
     * <p>Digunakan untuk mencegah pembuatan profil merchant ganda
     * bagi satu pengguna yang sama (menegakkan batasan One-to-One).
     *
     * @param user objek {@link UserEntity} yang diperiksa
     * @return {@code true} jika pengguna sudah memiliki profil merchant,
     *         {@code false} jika belum.
     */
    boolean existsByUser(UserEntity user);

    /**
     * Mencari semua merchant berdasarkan status verifikasinya (misal: false untuk belum diverifikasi).
     */
    List<MerchantEntity> findAllByIsVerified(Boolean isVerified);
}
