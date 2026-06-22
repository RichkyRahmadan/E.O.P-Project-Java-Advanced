package com.priestess.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RegisterMerchantRequest — DTO untuk request body {@code POST /api/auth/register/merchant}.
 *
 * <p>Digunakan oleh calon Merchant untuk mendaftarkan akun bisnis.
 * Selain data akun User biasa, Merchant juga wajib menyertakan informasi
 * nama toko dan alamat.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterMerchantRequest {

    @NotBlank(message = "Username tidak boleh kosong.")
    @Size(min = 3, max = 50, message = "Username harus antara 3-50 karakter.")
    private String username;

    @NotBlank(message = "Email tidak boleh kosong.")
    @Email(message = "Format email tidak valid.")
    private String email;

    @NotBlank(message = "Password tidak boleh kosong.")
    @Size(min = 8, message = "Password minimal 8 karakter.")
    private String password;

    @NotBlank(message = "Nama merchant tidak boleh kosong.")
    @Size(max = 100, message = "Nama merchant maksimal 100 karakter.")
    private String merchantName;

    @NotBlank(message = "Alamat merchant tidak boleh kosong.")
    private String address;

    @NotBlank(message = "Nomor telepon owner tidak boleh kosong.")
    private String ownerPhoneNumber;
}
