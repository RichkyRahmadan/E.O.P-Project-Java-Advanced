package com.priestess.core.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TransferRequest {

    @NotBlank(message = "ID wallet penerima tidak boleh kosong.")
    private String recipientWalletId;

    @NotNull(message = "Jumlah transfer tidak boleh kosong.")
    @DecimalMin(value = "1000.00", message = "Minimum transfer adalah Rp 1.000.")
    private BigDecimal amount;

    private String note;
}
