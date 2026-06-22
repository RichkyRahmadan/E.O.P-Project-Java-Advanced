package com.priestess.identity.service;

import com.priestess.identity.dto.AuthResponse;
import com.priestess.identity.dto.LoginRequest;
import com.priestess.identity.dto.RefreshTokenRequest;
import com.priestess.identity.dto.RegisterMerchantByOwnerRequest;
import com.priestess.identity.dto.RegisterMerchantRequest;
import com.priestess.identity.dto.RegisterUserRequest;
import com.priestess.identity.dto.RegisterResponse;
import com.priestess.identity.dto.UserResolutionResponse;

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
     * @return {@link RegisterResponse} berisi status registrasi
     * @throws IllegalStateException jika username atau email sudah terdaftar
     */
    RegisterResponse registerUser(RegisterUserRequest request);

    /**
     * Mendaftarkan pengguna baru dengan role MERCHANT, status PENDING,
     * dan sekaligus membuat entitas Merchant terkait.
     *
     * @param request DTO berisi data akun + nama merchant + alamat
     * @return {@link RegisterResponse} berisi status registrasi
     * @throws IllegalStateException jika username atau email sudah terdaftar
     */
    RegisterResponse registerMerchant(RegisterMerchantRequest request);

    /**
     * Mendaftarkan merchant baru dimana owner adalah user yang sedang login.
     * Owner ID diambil dari konteks JWT (header X-User-Id dari Gateway),
     * sehingga tidak perlu menyertakan nomor telepon owner di body request.
     *
     * <p>Hanya user dengan status ACTIVE yang dapat mendaftarkan merchant.
     *
     * @param ownerUserId UUID string dari owner (diambil dari JWT claim)
     * @param request     DTO berisi username, password, merchantName, address
     * @return {@link RegisterResponse} berisi status registrasi
     * @throws IllegalArgumentException jika owner tidak ditemukan atau belum aktif
     * @throws IllegalStateException    jika username sudah terdaftar
     */
    RegisterResponse registerMerchantByOwner(String ownerUserId, RegisterMerchantByOwnerRequest request);

    /**
     * Menghapus Refresh Token dari database (logout pengguna).
     * Access Token yang sedang berjalan akan tetap valid hingga 15 menit kadaluarsa
     * secara alami (stateless — tidak bisa di-invalidate dari server).
     *
     * @param refreshToken string Refresh Token yang akan dihapus
     */
    void logout(String refreshToken);

    /**
     * Resolves username/email to userId, username and email.
     *
     * @param usernameOrEmail string containing username or email to search
     * @return {@link UserResolutionResponse} with resolved details
     */
    UserResolutionResponse resolveUser(String usernameOrEmail);
}
