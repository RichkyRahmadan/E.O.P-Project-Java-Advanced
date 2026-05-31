package com.priestess.identity.repository;

import com.priestess.identity.entity.MenuEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * MenuRepository — Kontrak akses data untuk entitas {@link MenuEntity}.
 *
 * <p>Meng-extend {@link JpaRepository} untuk mendapatkan operasi CRUD bawaan
 * serta kemampuan pagination dan sorting tanpa implementasi tambahan.
 *
 * <p>Method query tambahan:
 * <ul>
 *   <li>{@link #findByMenuName(String)} — Digunakan saat proses seed data awal
 *       (data initialization) untuk memastikan menu belum ada sebelum di-insert.</li>
 * </ul>
 */
public interface MenuRepository extends JpaRepository<MenuEntity, UUID> {

    /**
     * Mencari menu berdasarkan nama yang tepat (case-sensitive).
     *
     * @param menuName nama menu yang dicari, contoh: {@code "SCAN_QRIS"}
     * @return {@link Optional} berisi {@link MenuEntity} jika ditemukan,
     *         atau {@link Optional#empty()} jika tidak ada.
     */
    Optional<MenuEntity> findByMenuName(String menuName);
}
