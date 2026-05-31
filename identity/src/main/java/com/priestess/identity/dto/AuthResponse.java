package com.priestess.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * AuthResponse — DTO standar untuk response endpoint autentikasi E.O.P.
 *
 * <p>Dikembalikan oleh endpoint {@code POST /api/auth/login} dan
 * {@code POST /api/auth/refresh} kepada klien Angular. Berisi dua buah token
 * sesuai Dual-Token Flow pada SECTION 6 blueprint, serta ringkasan identitas
 * pengguna yang sudah terautentikasi.
 *
 * <p>Klien Angular wajib menyimpan {@code accessToken} di memori aplikasi
 * (bukan localStorage) dan {@code refreshToken} di tempat penyimpanan yang aman.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    /**
     * Access Token JWT berumur 15 menit.
     * Harus disertakan di setiap request terproteksi sebagai:
     * {@code Authorization: Bearer <accessToken>}
     */
    private String accessToken;

    /**
     * Refresh Token acak kriptografis berumur 7 hari.
     * Digunakan untuk memperbarui Access Token via {@code POST /api/auth/refresh}
     * tanpa perlu login ulang, selama akun tidak berstatus {@code SUSPENDED}.
     */
    private String refreshToken;

    /**
     * Ringkasan identitas pengguna yang sudah terautentikasi.
     * Digunakan Angular untuk menampilkan informasi UI dan menentukan
     * menu navigasi yang sesuai dengan role.
     */
    private UserSummary user;

    // =========================================================================
    // NESTED DTO — UserSummary
    // =========================================================================

    /**
     * UserSummary — Representasi ringkas identitas pengguna.
     *
     * <p>Sengaja hanya memuat field yang relevan untuk keperluan tampilan UI
     * dan kontrol akses di sisi klien. Tidak menyertakan {@code password},
     * {@code email}, atau {@code refreshToken} demi keamanan.
     */
    @Getter
    @Builder
    public static class UserSummary {

        /** UUID unik pengguna, konsisten dengan klaim {@code sub} di dalam JWT. */
        private UUID id;

        /** Nama pengguna yang ditampilkan di UI. */
        private String username;

        /**
         * Nama role pengguna (contoh: {@code ROLE_USER}, {@code ROLE_MERCHANT}, {@code ROLE_ADMIN}).
         * Digunakan frontend untuk menentukan menu navigasi yang ditampilkan.
         */
        private String roleName;

        /**
         * Status akun saat ini ({@code ACTIVE} atau {@code PENDING}).
         * Membantu frontend menampilkan notifikasi jika KYC belum selesai.
         */
        private String status;
    }
}
