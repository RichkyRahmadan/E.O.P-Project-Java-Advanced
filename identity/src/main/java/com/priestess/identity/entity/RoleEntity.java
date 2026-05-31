package com.priestess.identity.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * RoleEntity — Representasi tabel {@code roles} di database PostgreSQL.
 *
 * <p>Menyimpan master daftar peran (role) yang dapat dimiliki oleh seorang
 * pengguna. Contoh nilai {@code roleName}: {@code ROLE_USER},
 * {@code ROLE_MERCHANT}, {@code ROLE_ADMIN}.
 *
 * <p>DDL reference:
 * <pre>
 *   id        UUID         PRIMARY KEY
 *   role_name VARCHAR(50)  UNIQUE NOT NULL
 * </pre>
 */
@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleEntity {

    /** Primary key — UUID digenerate otomatis oleh Hibernate. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Nama role yang unik di seluruh sistem.
     * Contoh: {@code ROLE_USER}, {@code ROLE_MERCHANT}, {@code ROLE_ADMIN}.
     */
    @Column(name = "role_name", length = 50, unique = true, nullable = false)
    private String roleName;
}
