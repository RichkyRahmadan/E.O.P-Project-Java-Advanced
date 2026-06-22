package com.priestess.oracle.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.priestess.oracle.entity.ComplaintDocument;
import com.priestess.oracle.repository.ComplaintRepository;
import com.priestess.oracle.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetryableGeminiExecutor {

    private final ComplaintRepository complaintRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Retryable(
        retryFor = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000)
    )
    public void executeWithRetry(ComplaintDocument complaint) {
        log.info("[GeminiExecutor] Memulai analisis — complaintId={}", complaint.getComplaintId());

        complaint.setStatus("IN_PROGRESS");
        complaintRepository.save(complaint);

        try {
            String prompt = buildPrompt(complaint.getRawMessage());
            String rawResponse = callGeminiApi(prompt);
            ComplaintDocument.AiAnalysis analysis = parseGeminiResponse(rawResponse);

            complaint.setAiAnalysis(analysis);
            complaint.setStatus("RESOLVED");
            complaintRepository.save(complaint);

            log.info("[GeminiExecutor] Selesai — complaintId={}, priority={}",
                    complaint.getComplaintId(), analysis.getPriority());

            if ("HIGH".equalsIgnoreCase(analysis.getPriority())) {
                log.warn("[GeminiExecutor] Priority HIGH terdeteksi — kirim email admin");
                emailService.sendHighPriorityAlert(complaint);
            }

        } catch (Exception e) {
            log.warn("[GeminiExecutor] Percobaan gagal untuk complaintId={}: {}",
                    complaint.getComplaintId(), e.getMessage());

            complaint.setStatus("OPEN");
            complaintRepository.save(complaint);

            throw new RuntimeException("Analisis Gemini gagal (fitur under development): " + e.getMessage(), e);
        }
    }

    private String buildPrompt(String rawMessage) {
        return """
                Kamu adalah sistem analis keluhan pelanggan untuk aplikasi E-Wallet bernama E.O.P (Eyes Of Priestess).
                Analisis teks keluhan berikut dan berikan respons dalam format JSON yang valid.

                Teks Keluhan:
                "%s"

                Berikan respons HANYA dalam format JSON berikut, tanpa teks tambahan, tanpa markdown:
                {
                  "category": "TRANSACTION_ERROR | ACCOUNT_ISSUE | FRAUD_REPORT | GENERAL_INQUIRY | PAYMENT_FAILURE | BALANCE_ISSUE | OTHER",
                  "priority": "LOW | MEDIUM | HIGH",
                  "sentiment": "POSITIVE | NEUTRAL | NEGATIVE",
                  "score": 0.0,
                  "suggestedReply": "Saran balasan untuk tim support dalam bahasa Indonesia, maks 200 karakter"
                }

                Panduan priority:
                - HIGH: Keluhan terkait penipuan, kehilangan uang besar, akun diblokir, atau darurat
                - MEDIUM: Keluhan transaksi gagal, saldo tidak sesuai
                - LOW: Pertanyaan umum, keluhan minor
                """.formatted(rawMessage);
    }

    private String callGeminiApi(String prompt) {

        RestClient restClient = RestClient.create();

        Map<String, Object> requestBody = Map.of(
            "contents", new Object[]{
                Map.of("parts", new Object[]{
                    Map.of("text", prompt)
                })
            }
        );

        return restClient.post()
                .uri(geminiApiUrl + "?key=" + geminiApiKey)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(String.class);
    }

    private ComplaintDocument.AiAnalysis parseGeminiResponse(String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);
        JsonNode candidates = root.path("candidates");

        if (candidates.isMissingNode() || !candidates.isArray() || candidates.isEmpty()) {
            String blockReason = root.path("promptFeedback").path("blockReason").asText("UNKNOWN");
            log.warn("[GeminiExecutor] Respons candidates kosong. Alasan blokir: {}. Menggunakan fallback analysis.", blockReason);
            return buildFallbackAnalysis("Analisis tidak tersedia (konten diblokir oleh filter keamanan AI: " + blockReason + ")");
        }

        JsonNode firstCandidate = candidates.get(0);

        String finishReason = firstCandidate.path("finishReason").asText("UNKNOWN");
        if (!"STOP".equalsIgnoreCase(finishReason)) {
            log.warn("[GeminiExecutor] finishReason bukan STOP: {}. Menggunakan fallback analysis.", finishReason);
            return buildFallbackAnalysis("Analisis tidak tersedia (model berhenti dengan alasan: " + finishReason + ")");
        }

        JsonNode parts = firstCandidate.path("content").path("parts");
        if (parts.isMissingNode() || parts.isEmpty()) {
            log.warn("[GeminiExecutor] Array parts kosong dalam respons Gemini.");
            return buildFallbackAnalysis("Analisis tidak tersedia (respons model kosong).");
        }

        String text = parts.get(0).path("text").asText();

        text = text.replace("```json", "").replace("```", "").trim();

        if (text.isBlank()) {
            return buildFallbackAnalysis("Analisis tidak tersedia (teks output Gemini kosong).");
        }

        return objectMapper.readValue(text, ComplaintDocument.AiAnalysis.class);
    }

    private ComplaintDocument.AiAnalysis buildFallbackAnalysis(String reason) {
        ComplaintDocument.AiAnalysis fallback = new ComplaintDocument.AiAnalysis();
        fallback.setCategory("GENERAL_INQUIRY");
        fallback.setPriority("MEDIUM");
        fallback.setSentiment("NEUTRAL");
        fallback.setScore(0.5);
        fallback.setSuggestedReply("Tim support akan segera meninjau keluhan Anda secara manual. [" + reason + "]");
        return fallback;
    }
}
