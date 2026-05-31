package com.priestess.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LoginRequest — DTO untuk request body endpoint {@code POST /api/auth/login}.
 *
 * <p>Divalidasi secara otomatis oleh Bean Validation (Jakarta EE) melalui
 * anotasi {@code @Valid} di parameter Controller. Jika validasi gagal,
 * {@code @ControllerAdvice} akan menangkap {@code MethodArgumentNotValidException}
 * dan mengembalikan HTTP 400 Bad Request dengan detail error.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {

    /**
     * Username akun yang akan diautentikasi.
     * Tidak boleh {@code null}, kosong, atau hanya berisi spasi.
     */
    @NotBlank(message = "Username tidak boleh kosong.")
    private String username;

    /**
     * Password akun dalam bentuk plain-text.
     * Akan diverifikasi oleh BCrypt via {@code DaoAuthenticationProvider}.
     * Tidak boleh {@code null}, kosong, atau hanya berisi spasi.
     */
    @NotBlank(message = "Password tidak boleh kosong.")
    private String password;
}
