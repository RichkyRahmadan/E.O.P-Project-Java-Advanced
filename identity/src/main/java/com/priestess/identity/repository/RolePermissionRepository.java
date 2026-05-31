package com.priestess.identity.repository;

import com.priestess.identity.entity.RoleEntity;
import com.priestess.identity.entity.RolePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * RolePermissionRepository — Kontrak akses data untuk entitas {@link RolePermissionEntity}.
 *
 * <p>Meng-extend {@link JpaRepository} untuk mendapatkan operasi CRUD bawaan
 * serta kemampuan pagination dan sorting tanpa implementasi tambahan.
 *
 * <p>Method query tambahan:
 * <ul>
 *   <li>{@link #findAllByRole(RoleEntity)} — Digunakan oleh {@code JWTUtil} saat
 *       membangun klaim {@code permissions} pada Access Token. Semua menu / fitur
 *       yang dapat diakses oleh role tertentu akan diambil dan di-encode ke dalam JWT
 *       sebagai string yang dipisahkan koma.</li>
 * </ul>
 */
public interface RolePermissionRepository extends JpaRepository<RolePermissionEntity, UUID> {

    /**
     * Mengambil seluruh entri permission yang dimiliki oleh role tertentu.
     *
     * <p>Digunakan pada saat proses pembuatan Access Token JWT untuk membangun
     * klaim {@code permissions} yang berisi daftar nama menu yang dapat diakses,
     * sesuai dengan mekanisme stateless security di blueprint SECTION 6.
     *
     * @param role objek {@link RoleEntity} yang menjadi kriteria pencarian
     * @return {@link List} dari {@link RolePermissionEntity}; kosong jika role
     *         tidak memiliki permission apapun.
     */
    List<RolePermissionEntity> findAllByRole(RoleEntity role);
}
