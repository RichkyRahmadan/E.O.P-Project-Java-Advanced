package com.priestess.identity.repository;

import com.priestess.identity.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * UserRepository — Kontrak akses data untuk entitas {@link UserEntity}.
 *
 * <p>Meng-extend {@link JpaRepository} untuk mendapatkan operasi CRUD bawaan
 * serta kemampuan pagination dan sorting tanpa implementasi tambahan.
 *
 * <p>Method query tambahan:
 * <ul>
 *   <li>{@link #findByUsername(String)} — Digunakan saat proses autentikasi
 *       dan oleh {@code UserDetailsService} untuk memuat principal.</li>
 *   <li>{@link #findByEmail(String)} — Digunakan untuk validasi duplikasi email
 *       saat pendaftaran pengguna baru.</li>
 *   <li>{@link #findByRefreshToken(String)} — Digunakan pada endpoint
 *       {@code /api/auth/refresh} untuk memvalidasi Refresh Token yang dikirim klien,
 *       sekaligus memeriksa status akun (SUSPENDED / ACTIVE).</li>
 *   <li>{@link #existsByUsername(String)} — Digunakan untuk pengecekan cepat
 *       ketersediaan username tanpa memuat seluruh entitas.</li>
 *   <li>{@link #existsByEmail(String)} — Digunakan untuk pengecekan cepat
 *       ketersediaan email tanpa memuat seluruh entitas.</li>
 * </ul>
 */
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    /**
     * Mencari pengguna berdasarkan username (case-sensitive).
     *
     * <p>Digunakan oleh implementasi {@code UserDetailsService} untuk proses
     * autentikasi Spring Security dan oleh {@code AuthServiceImpl} saat login.
     *
     * @param username nama pengguna yang dicari
     * @return {@link Optional} berisi {@link UserEntity} jika ditemukan,
     *         atau {@link Optional#empty()} jika tidak ada.
     */
    Optional<UserEntity> findByUsername(String username);

    /**
     * Mencari pengguna berdasarkan alamat email (case-sensitive).
     *
     * <p>Digunakan untuk memastikan tidak ada duplikasi email saat
     * proses pendaftaran akun baru.
     *
     * @param email alamat email yang dicari
     * @return {@link Optional} berisi {@link UserEntity} jika ditemukan,
     *         atau {@link Optional#empty()} jika tidak ada.
     */
    Optional<UserEntity> findByEmail(String email);

    /**
     * Mencari pengguna berdasarkan nomor telepon.
     */
    Optional<UserEntity> findByPhone(String phone);

    /**
     * Memeriksa apakah nomor telepon sudah terdaftar.
     */
    boolean existsByPhone(String phone);

    /**
     * Mencari pengguna berdasarkan nilai Refresh Token yang tersimpan.
     *
     * <p>Digunakan pada endpoint {@code POST /api/auth/refresh}. Setelah
     * menemukan pengguna, Service WAJIB memeriksa field {@code status}:
     * jika {@code SUSPENDED}, permintaan penerbitan token baru harus ditolak
     * (sesuai mekanisme mitigasi SECTION 6 blueprint).
     *
     * @param refreshToken string refresh token acak milik pengguna
     * @return {@link Optional} berisi {@link UserEntity} jika ditemukan,
     *         atau {@link Optional#empty()} jika token tidak valid / sudah di-revoke.
     */
    Optional<UserEntity> findByRefreshToken(String refreshToken);

    /**
     * Memeriksa apakah username sudah terdaftar di sistem.
     *
     * @param username nama pengguna yang diperiksa
     * @return {@code true} jika username sudah ada, {@code false} jika belum.
     */
    boolean existsByUsername(String username);

    /**
     * Memeriksa apakah email sudah terdaftar di sistem.
     *
     * @param email alamat email yang diperiksa
     * @return {@code true} jika email sudah ada, {@code false} jika belum.
     */
    boolean existsByEmail(String email);

    /**
     * Mencari semua pengguna dengan role tertentu dan status tertentu (misal: ROLE_USER dan PENDING).
     */
    List<UserEntity> findAllByRole_RoleNameAndStatus(String roleName, String status);
}
