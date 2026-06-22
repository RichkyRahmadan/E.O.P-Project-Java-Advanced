package com.priestess.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class VoucherRedeemRequest {

    @NotBlank(message = "Kode voucher tidak boleh kosong.")
    private String code;
}
