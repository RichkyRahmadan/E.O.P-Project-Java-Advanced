package com.priestess.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TransactionResponse {
    private String      invoiceId;
    private String      transactionType;
    private String      status;
    private BigDecimal  amount;
    private String      note;
    private PartyInfo   sender;
    private PartyInfo   recipient;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private String      rawQrisData;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PartyInfo {
        private String userId;
        private String walletId;
        private String displayName;
    }
}
