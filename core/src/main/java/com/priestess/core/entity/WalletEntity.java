package com.priestess.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * WalletEntity — Entitas JPA untuk tabel {@code wallets} di PostgreSQL.
 *
 * <p>Merepresentasikan dompet digital milik seorang User atau Merchant.
 * Satu entitas WalletEntity dapat dimiliki oleh User ({@code OWNER_TYPE=USER})
 * atau Merchant ({@code OWNER_TYPE=MERCHANT}).
 *
 * <h2>Optimistic Locking</h2>
 * <p>Field {@code version} dianotasi {@code @Version} untuk mengaktifkan mekanisme
 * <b>Optimistic Locking</b> bawaan JPA/Hibernate. Setiap kali record diperbarui,
 * Hibernate secara otomatis memeriksa apakah nilai {@code version} di database
 * masih sama dengan yang dibaca. Jika ada transaksi lain yang sudah mengubah record
 * ini terlebih dahulu, Hibernate akan melempar {@code OptimisticLockException},
 * mencegah <b>double-spend</b> dan <b>race condition saldo</b> secara efektif
 * tanpa memerlukan database lock (SELECT FOR UPDATE) yang lebih mahal.
 *
 * <p>Sesuai blueprint SECTION 7 (Workflow Dual-Write & Concurrency Control).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@Entity
@Table(name = "wallets")
public class WalletEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * UUID pemilik dompet — merujuk ke {@code users.id} atau {@code merchants.id}
     * di Identity Service. TIDAK menggunakan FK lintas database sesuai prinsip
     * Database-per-Service blueprint SECTION 2.
     */
    @Column(name = "owner_id", unique = true, nullable = false)
    private UUID ownerId;

    /**
     * Tipe pemilik dompet. Nilai valid: {@code USER} atau {@code MERCHANT}.
     */
    @Column(name = "owner_type", nullable = false, length = 20)
    private String ownerType;

    /**
     * Saldo dompet. Precision 19 digit, scale 2 desimal.
     * Constraint {@code CHECK balance >= 0.00} diatur di level DDL database.
     * Di level aplikasi, service wajib memvalidasi saldo sebelum debit.
     */
    @Column(name = "balance", nullable = false, precision = 19, scale = 2,
            columnDefinition = "DECIMAL(19,2) DEFAULT 0.00")
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    /**
     * Versi record untuk Optimistic Locking.
     * Dikelola otomatis oleh Hibernate — jangan pernah di-set manual.
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
