package com.priestess.identity.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * RolePermissionEntity — Representasi tabel {@code role_permissions} di database PostgreSQL.
 *
 * <p>Bertindak sebagai tabel penengah (junction table) dari relasi many-to-many
 * antara {@link RoleEntity} dan {@link MenuEntity}, sekaligus menyimpan
 * tipe akses ({@code access_type}) yang dimiliki suatu role terhadap suatu menu.
 *
 * <p>Dengan pola ini, sistem RBAC (Role-Based Access Control) E.O.P dapat
 * dikonfigurasi secara dinamis tanpa perlu mengubah kode program.
 *
 * <p>DDL reference:
 * <pre>
 *   id          UUID        PRIMARY KEY
 *   role_id     UUID        FK -> roles(id)   NOT NULL
 *   menu_id     UUID        FK -> menus(id)   NOT NULL
 *   access_type VARCHAR(20)
 * </pre>
 */
@Entity
@Table(name = "role_permissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolePermissionEntity {

    /** Primary key — UUID digenerate otomatis oleh Hibernate. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Role yang memiliki hak akses ini.
     * Relasi Many-to-One ke tabel {@code roles}: satu role dapat memiliki
     * banyak entri permission.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_roleperm_role"))
    private RoleEntity role;

    /**
     * Menu / fitur yang diakses oleh role ini.
     * Relasi Many-to-One ke tabel {@code menus}: satu menu dapat diakses
     * oleh banyak role dengan tipe akses yang berbeda.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_roleperm_menu"))
    private MenuEntity menu;

    /**
     * Tipe akses yang diberikan kepada role terhadap menu ini.
     * Contoh nilai: {@code READ}, {@code WRITE}, {@code EXECUTE}.
     */
    @Column(name = "access_type", length = 20)
    private String accessType;
}
