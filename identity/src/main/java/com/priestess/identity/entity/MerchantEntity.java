package com.priestess.identity.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * MerchantEntity — Representasi tabel {@code merchants} di database PostgreSQL.
 *
 * <p>Menyimpan profil usaha/toko yang dimiliki oleh pengguna ber-role
 * {@code ROLE_MERCHANT}. Satu akun pengguna hanya dapat memiliki satu profil
 * merchant (relasi One-to-One via {@code user_id UNIQUE}).
 *
 * <p>Field {@code isVerified} menentukan apakah merchant sudah melewati proses
 * verifikasi KYC oleh Admin. Merchant yang belum terverifikasi tidak dapat
 * menggunakan fitur Generate QRIS.
 *
 * <p>DDL reference:
 * <pre>
 *   id            UUID          PRIMARY KEY
 *   user_id       UUID          FK -> users(id)  UNIQUE  NOT NULL
 *   merchant_name VARCHAR(100)  NOT NULL
 *   address       TEXT
 *   is_verified   BOOLEAN       NOT NULL  DEFAULT false
 *   created_at    TIMESTAMP
 * </pre>
 */
@Entity
@Table(name = "merchants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantEntity {

    /** Primary key — UUID digenerate otomatis oleh Hibernate. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Pengguna pemilik merchant ini.
     * Relasi One-to-One ke tabel {@code users}: satu user hanya boleh
     * memiliki satu profil merchant (dijamin oleh UNIQUE constraint pada
     * kolom {@code user_id}).
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_merchant_user"))
    private UserEntity user;

    /** Nama toko / usaha merchant. */
    @Column(name = "merchant_name", length = 100, nullable = false)
    private String merchantName;

    /** Alamat fisik toko merchant, bersifat opsional. */
    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    /**
     * User pemilik merchant ini (sebagai owner).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", foreignKey = @ForeignKey(name = "fk_merchant_owner"))
    private UserEntity owner;

    /**
     * Status verifikasi KYC merchant.
     * {@code false} = belum terverifikasi (default setelah pendaftaran).
     * {@code true}  = sudah diverifikasi oleh Admin; merchant dapat menerima pembayaran QRIS.
     */
    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    /**
     * Waktu pembuatan profil merchant.
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
