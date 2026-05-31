package com.priestess.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO untuk request klaim kode voucher — {@code POST /api/finance/voucher/redeem}. */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class VoucherRedeemRequest {

    @NotBlank(message = "Kode voucher tidak boleh kosong.")
    private String code;
}
