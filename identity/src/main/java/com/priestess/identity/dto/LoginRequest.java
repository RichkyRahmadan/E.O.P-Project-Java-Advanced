package com.priestess.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {

    @NotBlank(message = "Username tidak boleh kosong.")
    private String username;

    @NotBlank(message = "Password tidak boleh kosong.")
    private String password;
}
