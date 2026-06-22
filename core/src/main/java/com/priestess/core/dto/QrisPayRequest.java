package com.priestess.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class QrisPayRequest {

    @NotBlank(message = "Invoice ID tidak boleh kosong.")
    private String invoiceId;
}
