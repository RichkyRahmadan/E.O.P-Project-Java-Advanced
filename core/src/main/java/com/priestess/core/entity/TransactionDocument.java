package com.priestess.core.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "transactions")
public class TransactionDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("invoice_id")
    private String invoiceId;

    @Field("transaction_type")
    private String transactionType;

    @Field("status")
    private String status;

    @Field("amount")
    private BigDecimal amount;

    @Field("sender")
    private PartyInfo sender;

    @Field("recipient")
    private PartyInfo recipient;

    @Field("raw_qris_data")
    private String rawQrisData;

    @Field("note")
    private String note;

    @Field("created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Field("expires_at")
    private LocalDateTime expiresAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PartyInfo {

        @Field("user_id")
        private String userId;

        @Field("wallet_id")
        private String walletId;

        @Field("display_name")
        private String displayName;
    }
}
