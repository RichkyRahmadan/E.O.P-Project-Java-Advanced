package com.priestess.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingMerchantResponse {
    private UUID id;
    private String merchantName;
    private String address;
    private String username;
    private String email;
    private LocalDateTime createdAt;
}
