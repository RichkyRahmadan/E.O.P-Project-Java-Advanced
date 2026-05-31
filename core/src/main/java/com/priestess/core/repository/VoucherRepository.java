package com.priestess.core.repository;

import com.priestess.core.entity.VoucherEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * VoucherRepository — Repository JPA untuk entitas {@link VoucherEntity}.
 *
 * <p>Menyediakan operasi database untuk tabel {@code vouchers} di PostgreSQL.
 * Method query kustom di sini dirancang untuk mendukung alur klaim voucher
 * yang aman: cek ketersediaan → update status dalam satu transaksi atomik.
 */
@Repository
public interface VoucherRepository extends JpaRepository<VoucherEntity, Long> {

    /**
     * Mencari voucher yang belum diklaim berdasarkan kode.
     *
     * <p>Query ini mengembalikan {@code Optional.empty()} jika kode tidak ada
     * ATAU jika voucher sudah pernah diklaim ({@code is_redeemed = true}).
     * Ini menyederhanakan logika di Service — cukup satu {@code orElseThrow}.
     *
     * @param code kode voucher yang diinput pengguna
     * @return {@link Optional} berisi voucher yang valid dan belum diklaim
     */
    @Query("SELECT v FROM VoucherEntity v WHERE v.code = :code AND v.isRedeemed = false")
    Optional<VoucherEntity> findAvailableByCode(@Param("code") String code);

    /**
     * Memeriksa apakah sebuah kode voucher valid (ada di database).
     * Tidak memeriksa status redemption.
     *
     * @param code kode voucher
     * @return {@code true} jika kode ditemukan
     */
    boolean existsByCode(String code);
}
