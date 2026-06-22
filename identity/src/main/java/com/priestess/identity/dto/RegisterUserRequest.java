package com.priestess.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterUserRequest {

    @NotBlank(message = "Username tidak boleh kosong.")
    @Size(min = 3, max = 50, message = "Username harus antara 3-50 karakter.")
    private String username;

    @NotBlank(message = "Email tidak boleh kosong.")
    @Email(message = "Format email tidak valid.")
    private String email;

    @NotBlank(message = "Nomor telepon tidak boleh kosong.")
    private String phone;

    @NotBlank(message = "Password tidak boleh kosong.")
    @Size(min = 8, message = "Password minimal 8 karakter.")
    private String password;
}
