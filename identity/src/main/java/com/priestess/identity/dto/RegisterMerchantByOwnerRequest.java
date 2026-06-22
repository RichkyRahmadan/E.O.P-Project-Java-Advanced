package com.priestess.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RegisterMerchantByOwnerRequest — DTO untuk request body
 * {@code POST /api/identity/merchant/register}.
 *
 * <p>Digunakan oleh User yang sudah terverifikasi (ACTIVE) untuk mendaftarkan
 * akun merchant baru. Owner ID diambil dari klaim JWT (X-User-Id header dari Gateway),
 * sehingga tidak perlu menyertakan nomor telepon owner.
 *
 * <p>Field email dihapus intentionally — username dan password cukup sebagai
 * credential merchant. Email merchant = opsional dan tidak diperlukan.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterMerchantByOwnerRequest {

    @NotBlank(message = "Username merchant tidak boleh kosong.")
    @Size(min = 3, max = 50, message = "Username harus antara 3-50 karakter.")
    private String username;

    @NotBlank(message = "Password tidak boleh kosong.")
    @Size(min = 8, message = "Password minimal 8 karakter.")
    private String password;

    @NotBlank(message = "Nama merchant/toko tidak boleh kosong.")
    @Size(max = 100, message = "Nama merchant maksimal 100 karakter.")
    private String merchantName;

    @NotBlank(message = "Alamat toko tidak boleh kosong.")
    private String address;
}
