package com.priestess.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** DTO response untuk data dompet pengguna. */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletResponse {
    private UUID        id;
    private UUID        ownerId;
    private String      ownerType;
    private BigDecimal  balance;
    private LocalDateTime updatedAt;
}
