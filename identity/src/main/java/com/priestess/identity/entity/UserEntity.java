package com.priestess.identity.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * UserEntity — Representasi tabel {@code users} di database PostgreSQL.
 *
 * <p>Menyimpan data akun pengguna sistem E.O.P. Field {@code password}
 * disimpan dalam format BCrypt hash — TIDAK PERNAH disimpan sebagai plain text.
 * Field {@code refreshToken} diperbarui setiap kali pengguna melakukan login
 * atau melakukan rotasi token, dan dihapus saat logout.
 *
 * <p>Kolom {@code status} menggunakan nilai diskrit:
 * <ul>
 *   <li>{@code PENDING}   — Akun baru, belum diverifikasi KYC oleh Admin.</li>
 *   <li>{@code ACTIVE}    — Akun aktif dan dapat menggunakan semua fitur.</li>
 *   <li>{@code SUSPENDED} — Akun dibekukan oleh Admin; Refresh Token ditolak.</li>
 * </ul>
 *
 * <p>DDL reference:
 * <pre>
 *   id            UUID          PRIMARY KEY
 *   username      VARCHAR(50)   UNIQUE  NOT NULL
 *   email         VARCHAR(100)  UNIQUE  NOT NULL
 *   password      VARCHAR(255)  NOT NULL   (BCrypt hash)
 *   role_id       UUID          FK -> roles(id)
 *   status        VARCHAR(20)   NOT NULL   (PENDING | ACTIVE | SUSPENDED)
 *   refresh_token VARCHAR(500)
 *   created_at    TIMESTAMP
 * </pre>
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    /** Primary key — UUID digenerate otomatis oleh Hibernate. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Nama pengguna yang unik, digunakan untuk login dan tampilan UI. */
    @Column(name = "username", length = 50, unique = true, nullable = false)
    private String username;

    /** Alamat email pengguna yang unik, bisa digunakan sebagai identitas alternatif. */
    @Column(name = "email", length = 100, unique = true, nullable = false)
    private String email;

    /**
     * Password dalam format BCrypt hash.
     * JANGAN pernah menyimpan atau mengembalikan nilai ini ke klien secara langsung.
     */
    @Column(name = "password", length = 255, nullable = false)
    private String password;

    /**
     * Role yang dimiliki pengguna ini.
     * Relasi Many-to-One ke tabel {@code roles}: banyak pengguna
     * dapat memiliki satu role yang sama.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_role"))
    private RoleEntity role;

    /**
     * Status akun pengguna.
     * Nilai yang valid: {@code PENDING}, {@code ACTIVE}, {@code SUSPENDED}.
     * Divalidasi pada lapisan Service, bukan di sini.
     */
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    /**
     * Refresh Token acak aman yang disimpan di server (bukan stateless).
     * Digunakan untuk menerbitkan Access Token baru. Akan diperiksa keberadaan
     * dan validitasnya oleh Identity Service saat endpoint {@code /api/auth/refresh}
     * dipanggil. Bernilai {@code null} saat pengguna logout.
     */
    @Column(name = "refresh_token", length = 500)
    private String refreshToken;

    /**
     * Waktu pembuatan akun.
     * Di-set sekali pada saat INSERT dan tidak pernah diperbarui.
     */
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** Set {@code createdAt} secara otomatis sebelum pertama kali di-persist. */
    @PrePersist
    protected void onPrePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
