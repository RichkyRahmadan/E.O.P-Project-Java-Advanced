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

/**
 * RetryableGeminiExecutor — Bean terpisah yang mengelola logika retry Gemini API.
 *
 * <h2>Alasan Dipisah dari GeminiAiServiceImpl</h2>
 * <p>Spring AOP proxy tidak bisa membungkus method yang sama dengan DUA proxy
 * sekaligus (@Async dari GeminiAiServiceImpl + @Retryable dari class ini).
 * Solusinya: delegasikan eksekusi ke bean lain — proxy @Retryable bekerja
 * pada bean ini, sementara proxy @Async bekerja pada GeminiAiServiceImpl.
 *
 * <h2>Alur Retry</h2>
 * <pre>
 *   Percobaan 1 → Gagal → Tunggu 2 detik
 *   Percobaan 2 → Gagal → Tunggu 2 detik
 *   Percobaan 3 → Gagal → Exception terakhir di-rethrow ke GeminiAiServiceImpl
 * </pre>
 */
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

    /**
     * Mengeksekusi analisis Gemini dengan retry otomatis.
     * Dipanggil dari {@code GeminiAiServiceImpl.analyzeComplaint()} yang @Async.
     *
     * @param complaint dokumen keluhan yang akan dianalisis
     */
    @Retryable(
        retryFor = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000) // 2 detik jeda antar percobaan
    )
    public void executeWithRetry(ComplaintDocument complaint) {
        log.info("[GeminiExecutor] Memulai analisis — complaintId={}", complaint.getComplaintId());

        // Tandai IN_PROGRESS di MongoDB
        complaint.setStatus("IN_PROGRESS");
        complaintRepository.save(complaint);

        try {
            String prompt = buildPrompt(complaint.getRawMessage());
            String rawResponse = callGeminiApi(prompt);
            ComplaintDocument.AiAnalysis analysis = parseGeminiResponse(rawResponse);

            // Simpan hasil analisis ke MongoDB
            complaint.setAiAnalysis(analysis);
            complaint.setStatus("RESOLVED");
            complaintRepository.save(complaint);

            log.info("[GeminiExecutor] Selesai — complaintId={}, priority={}",
                    complaint.getComplaintId(), analysis.getPriority());

            // Kirim email admin jika prioritas HIGH (blueprint SECTION 8)
            if ("HIGH".equalsIgnoreCase(analysis.getPriority())) {
                log.warn("[GeminiExecutor] Priority HIGH terdeteksi — kirim email admin");
                emailService.sendHighPriorityAlert(complaint);
            }

        } catch (Exception e) {
            log.warn("[GeminiExecutor] Percobaan gagal untuk complaintId={}: {}",
                    complaint.getComplaintId(), e.getMessage());
            // Kembalikan ke OPEN agar status tidak stuck di IN_PROGRESS
            complaint.setStatus("OPEN");
            complaintRepository.save(complaint);
            // Re-throw WAJIB agar @Retryable mendeteksi kegagalan dan retry
            throw new RuntimeException("Analisis Gemini gagal: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

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
        // RestClient.create() ringan — tidak perlu pooling untuk use case ini
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

        // --- GUARD: Candidates kosong (diblokir safety filter atau output kosong) ---
        if (candidates.isMissingNode() || !candidates.isArray() || candidates.isEmpty()) {
            String blockReason = root.path("promptFeedback").path("blockReason").asText("UNKNOWN");
            log.warn("[GeminiExecutor] Respons candidates kosong. Alasan blokir: {}. Menggunakan fallback analysis.", blockReason);
            return buildFallbackAnalysis("Analisis tidak tersedia (konten diblokir oleh filter keamanan AI: " + blockReason + ")");
        }

        JsonNode firstCandidate = candidates.get(0);

        // --- GUARD: Cek finishReason — jika bukan STOP, kemungkinan output terpotong/kosong ---
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

        // Bersihkan markdown wrapper ```json ... ``` jika ada dari Gemini
        text = text.replace("```json", "").replace("```", "").trim();

        if (text.isBlank()) {
            return buildFallbackAnalysis("Analisis tidak tersedia (teks output Gemini kosong).");
        }

        return objectMapper.readValue(text, ComplaintDocument.AiAnalysis.class);
    }

    /**
     * Membuat objek AiAnalysis dengan nilai default yang aman sebagai fallback
     * ketika Gemini API memblokir atau mengembalikan respons kosong.
     *
     * @param reason keterangan alasan fallback untuk ditampilkan di field suggestedReply
     */
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
