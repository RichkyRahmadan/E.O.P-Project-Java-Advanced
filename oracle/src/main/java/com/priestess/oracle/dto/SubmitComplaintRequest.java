package com.priestess.oracle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitComplaintRequest {

    private String invoiceId;

    @NotBlank(message = "Pesan keluhan tidak boleh kosong")
    @Size(min = 20, max = 2000, message = "Pesan keluhan harus antara 20 hingga 2000 karakter")
    private String rawMessage;
}
