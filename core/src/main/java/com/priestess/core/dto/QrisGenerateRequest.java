package com.priestess.core.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class QrisGenerateRequest {

    @NotNull(message = "Jumlah pembayaran tidak boleh kosong.")
    @DecimalMin(value = "100.00", message = "Minimum jumlah QRIS adalah Rp 100.")
    private BigDecimal amount;

    private String note;
}
