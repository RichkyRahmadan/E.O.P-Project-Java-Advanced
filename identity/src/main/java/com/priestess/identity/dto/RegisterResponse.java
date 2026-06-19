package com.priestess.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RegisterResponse — DTO untuk response setelah registrasi berhasil.
 * Mengembalikan informasi status registrasi dan pesan instruksi bagi pengguna.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterResponse {
    private String message;
    private String username;
    private String status;
}
