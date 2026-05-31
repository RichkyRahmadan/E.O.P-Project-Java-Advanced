package com.priestess.identity.repository;

import com.priestess.identity.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * RoleRepository — Kontrak akses data untuk entitas {@link RoleEntity}.
 *
 * <p>Meng-extend {@link JpaRepository} untuk mendapatkan operasi CRUD bawaan
 * (save, findById, findAll, delete, dsb.) serta kemampuan pagination dan sorting
 * tanpa implementasi tambahan.
 *
 * <p>Method query tambahan:
 * <ul>
 *   <li>{@link #findByRoleName(String)} — Digunakan saat proses registrasi untuk
 *       mencari role default (misal: {@code ROLE_USER}) yang akan di-assign ke
 *       pengguna baru.</li>
 * </ul>
 */
public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {

    /**
     * Mencari role berdasarkan nama yang tepat (case-sensitive).
     *
     * @param roleName nama role yang dicari, contoh: {@code "ROLE_USER"}
     * @return {@link Optional} berisi {@link RoleEntity} jika ditemukan,
     *         atau {@link Optional#empty()} jika tidak ada.
     */
    Optional<RoleEntity> findByRoleName(String roleName);
}
