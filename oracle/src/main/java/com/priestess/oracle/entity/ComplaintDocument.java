package com.priestess.oracle.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "complaints")
public class ComplaintDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("complaint_id")
    private String complaintId;

    @Field("user_id")
    private String userId;

    @Field("username")
    private String username;

    @Field("email")
    private String email;

    @Field("invoice_id")
    private String invoiceId;

    @Field("raw_message")
    private String rawMessage;

    @Field("status")
    @Builder.Default
    private String status = "OPEN";

    @Field("ai_analysis")
    private AiAnalysis aiAnalysis;

    @Field("created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AiAnalysis {

        private String category;

        private String priority;

        private String sentiment;

        private Double score;

        @JsonProperty("suggestedReply")
        private String suggestedReply;
    }
}
