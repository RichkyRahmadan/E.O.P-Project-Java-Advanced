package com.priestess.oracle.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.priestess.oracle.entity.ComplaintDocument;
import com.priestess.oracle.repository.ComplaintRepository;
import com.priestess.oracle.service.EmailService;
import com.priestess.oracle.service.GeminiAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * GeminiAiServiceImpl — Implementasi analisis keluhan via Google Gemini AI API.
 *
 * <h2>Alur Asinkron + Retry (SECTION 8 blueprint)</h2>
 * <ol>
 *   <li>{@code @Async}: Dijalankan di thread pool terpisah ({@code oracle-async-*}),
 *       tidak memblokir thread utama request HTTP.</li>
 *   <li>{@code @Retryable}: Jika Gemini API timeout/error, method ini dicoba ulang
 *       hingga 3 kali dengan jeda 2 detik setiap percobaan.</li>
 *   <li>Setelah berhasil: update dokumen MongoDB, jika priority HIGH kirim email Admin.</li>
 *   <li>Jika gagal setelah 3 kali: catat error di log, keluhan tetap berstatus {@code OPEN}.</li>
 * </ol>
 *
 * <h2>Prompt Engineering</h2>
 * <p>Prompt yang dikirim ke Gemini dirancang untuk menghasilkan output JSON
 * dengan struktur yang dapat di-parse langsung ke {@link ComplaintDocument.AiAnalysis}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiAiServiceImpl implements GeminiAiService {

    private final ComplaintRepository complaintRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    /**
     * Menganalisis keluhan secara asinkron dan menyimpan hasilnya ke MongoDB.
     *
     * @param complaint dokumen keluhan yang sudah disimpan di MongoDB
     */
    @Async
    @Retryable(
        retryFor = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000) // 2 detik jeda antar percobaan
    )
    @Override
    public void analyzeComplaint(ComplaintDocument complaint) {
        log.info("[GeminiAI] Memulai analisis untuk complaintId={}", complaint.getComplaintId());

        // Tandai sebagai IN_PROGRESS
        complaint.setStatus("IN_PROGRESS");
        complaintRepository.save(complaint);

        try {
            // Bangun prompt untuk Gemini
            String prompt = buildPrompt(complaint.getRawMessage());

            // Panggil Gemini API
            String rawResponse = callGeminiApi(prompt);

            // Parse respons JSON dari Gemini
            ComplaintDocument.AiAnalysis analysis = parseGeminiResponse(rawResponse);

            // Perbarui dokumen dengan hasil analisis
            complaint.setAiAnalysis(analysis);
            complaint.setStatus("RESOLVED");
            complaintRepository.save(complaint);

            log.info("[GeminiAI] Analisis selesai: complaintId={}, priority={}",
                    complaint.getComplaintId(), analysis.getPriority());

            // Kirim email jika prioritas HIGH
            if ("HIGH".equalsIgnoreCase(analysis.getPriority())) {
                log.warn("[GeminiAI] Keluhan prioritas HIGH terdeteksi — mengirim email ke Admin");
                emailService.sendHighPriorityAlert(complaint);
            }

        } catch (Exception e) {
            log.error("[GeminiAI] Gagal menganalisis complaintId={}: {}",
                    complaint.getComplaintId(), e.getMessage(), e);
            // Set kembali ke OPEN agar bisa dianalisis ulang nanti
            complaint.setStatus("OPEN");
            complaintRepository.save(complaint);
            // Re-throw agar @Retryable menangkap dan mencoba ulang
            throw new RuntimeException("Gagal menganalisis keluhan: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Membangun prompt yang dikirim ke Gemini API.
     * Prompt dirancang untuk menghasilkan output JSON yang dapat langsung di-parse.
     */
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

    /**
     * Memanggil Gemini API menggunakan Spring {@link RestClient}.
     *
     * @param prompt teks prompt yang akan dikirim
     * @return respons mentah dari Gemini (string JSON)
     */
    private String callGeminiApi(String prompt) {
        RestClient restClient = RestClient.create();

        Map<String, Object> requestBody = Map.of(
            "contents", new Object[]{
                Map.of("parts", new Object[]{
                    Map.of("text", prompt)
                })
            }
        );

        String fullUrl = geminiApiUrl + "?key=" + geminiApiKey;

        return restClient.post()
                .uri(fullUrl)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(String.class);
    }

    /**
     * Mem-parse respons Gemini API dan mengekstrak teks JSON dari struktur nested-nya.
     *
     * <p>Struktur respons Gemini:
     * <pre>
     * {
     *   "candidates": [{
     *     "content": {
     *       "parts": [{ "text": "{ \"category\": ... }" }]
     *     }
     *   }]
     * }
     * </pre>
     *
     * @param rawResponse string JSON mentah dari Gemini API
     * @return objek {@link ComplaintDocument.AiAnalysis} yang sudah ter-parse
     * @throws Exception jika respons tidak dapat di-parse
     */
    private ComplaintDocument.AiAnalysis parseGeminiResponse(String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);
        String analysisText = root
                .path("candidates").get(0)
                .path("content")
                .path("parts").get(0)
                .path("text")
                .asText();

        // Bersihkan kemungkinan markdown wrapper dari Gemini (```json ... ```)
        analysisText = analysisText
                .replace("```json", "")
                .replace("```", "")
                .trim();

        return objectMapper.readValue(analysisText, ComplaintDocument.AiAnalysis.class);
    }
}
