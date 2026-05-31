package com.priestess.identity.service;

import com.priestess.identity.dto.AuthResponse;
import com.priestess.identity.dto.LoginRequest;
import com.priestess.identity.dto.RefreshTokenRequest;
import com.priestess.identity.dto.RegisterMerchantRequest;
import com.priestess.identity.dto.RegisterUserRequest;

/**
 * AuthService — Kontrak logika bisnis untuk lapisan Autentikasi E.O.P.
 *
 * <p>Sesuai dengan aturan arsitektur SECTION 3 blueprint E.O.P, interface ini
 * hanya berisi DEKLARASI kontrak method tanpa implementasi apapun.
 * Semua implementasi berada di kelas {@code AuthServiceImpl}.
 */
public interface AuthService {

    /**
     * Memproses login: verifikasi credential → cek status → terbitkan Dual Token.
     *
     * @param request DTO berisi {@code username} dan {@code password}
     * @return {@link AuthResponse} berisi accessToken, refreshToken, dan UserSummary
     */
    AuthResponse login(LoginRequest request);

    /**
     * Memperbarui Access Token menggunakan Refresh Token yang tersimpan di DB.
     * Implementasi SECTION 6 — mitigasi akun SUSPENDED: jika status user SUSPENDED,
     * penerbitan token baru ditolak sehingga user ter-logout paksa.
     *
     * @param request DTO berisi string {@code refreshToken}
     * @return {@link AuthResponse} baru dengan Access Token dan Refresh Token yang diperbarui
     * @throws IllegalStateException jika refresh token tidak valid atau akun SUSPENDED
     */
    AuthResponse refresh(RefreshTokenRequest request);

    /**
     * Mendaftarkan pengguna baru dengan role USER dan status PENDING.
     *
     * @param request DTO berisi username, email, password
     * @return {@link AuthResponse} berisi token pasangan (user langsung aktif login)
     * @throws IllegalStateException jika username atau email sudah terdaftar
     */
    AuthResponse registerUser(RegisterUserRequest request);

    /**
     * Mendaftarkan pengguna baru dengan role MERCHANT, status PENDING,
     * dan sekaligus membuat entitas Merchant terkait.
     *
     * @param request DTO berisi data akun + nama merchant + alamat
     * @return {@link AuthResponse} berisi token pasangan
     * @throws IllegalStateException jika username atau email sudah terdaftar
     */
    AuthResponse registerMerchant(RegisterMerchantRequest request);

    /**
     * Menghapus Refresh Token dari database (logout pengguna).
     * Access Token yang sedang berjalan akan tetap valid hingga 15 menit kadaluarsa
     * secara alami (stateless — tidak bisa di-invalidate dari server).
     *
     * @param refreshToken string Refresh Token yang akan dihapus
     */
    void logout(String refreshToken);
}
