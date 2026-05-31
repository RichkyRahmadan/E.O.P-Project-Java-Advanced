package com.priestess.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RefreshTokenRequest — DTO untuk request body {@code POST /api/auth/refresh}.
 *
 * <p>Klien Angular mengirimkan Refresh Token yang tersimpan saat login
 * untuk mendapatkan Access Token baru tanpa perlu login ulang.
 * Identity Service akan memvalidasi token ini di database (bukan JWT),
 * sekaligus memeriksa status akun terbaru (cegah akun SUSPENDED memperbarui token).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token tidak boleh kosong.")
    private String refreshToken;
}
