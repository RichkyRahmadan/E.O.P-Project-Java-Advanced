package com.priestess.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * VoucherEntity — Entitas JPA untuk tabel {@code vouchers} di PostgreSQL.
 *
 * <p>Merepresentasikan kode voucher yang dapat diklaim oleh pengguna
 * untuk menambah saldo dompet. Setiap voucher bersifat <b>single-use</b>:
 * sekali diklaim (is_redeemed = true), tidak bisa diklaim ulang.
 *
 * <p>Kontrol integritas klaim menggunakan Optimistic Locking di level
 * {@code WalletEntity} — VoucherEntity sendiri tidak memerlukan {@code @Version}
 * karena skenario klaim bersifat sekuensial (cek is_redeemed → update is_redeemed).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "vouchers")
public class VoucherEntity {

    /**
     * Primary key menggunakan BIGSERIAL (auto-increment) sesuai DDL SECTION 5B.
     * Berbeda dengan entitas lain yang menggunakan UUID.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /** Kode voucher yang diinput oleh pengguna. Unik dan case-sensitive. */
    @Column(name = "code", unique = true, nullable = false, length = 50)
    private String code;

    /** Nominal saldo yang akan ditambahkan saat voucher berhasil diklaim. */
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Flag apakah voucher ini sudah diklaim. Default {@code false}. */
    @Column(name = "is_redeemed", nullable = false)
    @Builder.Default
    private Boolean isRedeemed = false;

    /**
     * UUID pengguna yang mengklaim voucher ini.
     * Bernilai {@code null} selama voucher belum diklaim.
     * Merujuk ke {@code users.id} di Identity Service (tanpa FK lintas DB).
     */
    @Column(name = "redeemed_by")
    private UUID redeemedBy;

    /** Waktu klaim voucher. {@code null} jika belum diklaim. */
    @Column(name = "redeemed_at")
    private LocalDateTime redeemedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
