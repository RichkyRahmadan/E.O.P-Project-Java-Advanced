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

/**
 * ComplaintDocument — Entitas MongoDB untuk collection {@code complaints}.
 *
 * <p>Menyimpan data pengaduan pengguna beserta hasil analisis AI dari Google Gemini.
 * Status bergerak satu arah: {@code OPEN} → {@code IN_PROGRESS} → {@code RESOLVED}.
 *
 * <p>DDL reference (SECTION 5C blueprint):
 * <pre>
 *   complaint_id  String       (Unique, diisi manual dengan UUID)
 *   user_id       String
 *   username      String
 *   email         String
 *   invoice_id    String       (opsional — invoice yang dikeluhkan)
 *   raw_message   String       (teks keluhan mentah dari pengguna)
 *   status        String       OPEN | IN_PROGRESS | RESOLVED
 *   ai_analysis   Object       Hasil analisis Gemini AI
 *   created_at    ISODate
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "complaints")
public class ComplaintDocument {

    @Id
    private String id;

    /** UUID keluhan — digenerate di service sebelum disimpan. */
    @Indexed(unique = true)
    @Field("complaint_id")
    private String complaintId;

    @Field("user_id")
    private String userId;

    @Field("username")
    private String username;

    @Field("email")
    private String email;

    /**
     * ID invoice yang dikeluhkan. Boleh {@code null} jika keluhan bersifat umum
     * dan tidak terkait dengan transaksi tertentu.
     */
    @Field("invoice_id")
    private String invoiceId;

    /** Teks keluhan mentah dari pengguna. Ini yang akan dianalisis oleh Gemini AI. */
    @Field("raw_message")
    private String rawMessage;

    /**
     * Status pengaduan. Nilai awal selalu {@code OPEN}.
     * Berubah menjadi {@code IN_PROGRESS} saat analisis AI sedang berjalan,
     * dan {@code RESOLVED} saat selesai.
     */
    @Field("status")
    @Builder.Default
    private String status = "OPEN";

    /**
     * Hasil analisis dari Google Gemini AI.
     * Diisi secara asinkron setelah respons HTTP 202 dikirim ke klien.
     * Bernilai {@code null} sampai proses analisis selesai.
     */
    @Field("ai_analysis")
    private AiAnalysis aiAnalysis;

    @Field("created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // =========================================================================
    // NESTED CLASS: AiAnalysis
    // =========================================================================

    /**
     * Hasil terstruktur dari analisis Google Gemini AI.
     * Disimpan sebagai sub-dokumen MongoDB (BSON nested object).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AiAnalysis {

        private String category;

        private String priority;

        private String sentiment;

        private Double score;

        /**
         * Saran balasan dari AI. Gemini mengembalikan key "suggestedReply"
         * sesuai prompt — @JsonProperty memastikan deserialisasi benar.
         */
        @JsonProperty("suggestedReply")
        private String suggestedReply;
    }
}
