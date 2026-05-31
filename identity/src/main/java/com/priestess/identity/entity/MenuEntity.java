package com.priestess.identity.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * MenuEntity — Representasi tabel {@code menus} di database PostgreSQL.
 *
 * <p>Menyimpan master daftar fitur/menu yang tersedia di dalam sistem E.O.P.
 * Contoh nilai {@code menuName}: {@code SCAN_QRIS},
 * {@code GENERATE_QRIS_DYNAMIC}, {@code VERIFY_KYC}.
 *
 * <p>DDL reference:
 * <pre>
 *   id          UUID         PRIMARY KEY
 *   menu_name   VARCHAR(50)  UNIQUE NOT NULL
 *   description TEXT
 * </pre>
 */
@Entity
@Table(name = "menus")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuEntity {

    /** Primary key — UUID digenerate otomatis oleh Hibernate. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Nama menu / fitur yang unik di seluruh sistem.
     * Contoh: {@code SCAN_QRIS}, {@code GENERATE_QRIS_DYNAMIC}.
     */
    @Column(name = "menu_name", length = 50, unique = true, nullable = false)
    private String menuName;

    /** Deskripsi singkat tentang fungsi menu ini, bersifat opsional. */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
