package com.priestess.oracle.service;

import com.priestess.oracle.entity.ComplaintDocument;

/**
 * GeminiAiService — Kontrak interface untuk integrasi Google Gemini AI.
 *
 * <p>Implementasi di {@link com.priestess.oracle.service.impl.GeminiAiServiceImpl}
 * menggunakan {@code @Async} agar pemanggilan non-blocking, dan {@code @Retryable}
 * untuk toleransi terhadap timeout API Gemini (max 3 kali, jeda 2 detik).
 */
public interface GeminiAiService {

    /**
     * Menganalisis teks keluhan menggunakan Google Gemini AI API secara asinkron.
     *
     * <p>Proses:
     * <ol>
     *   <li>Ubah status keluhan di MongoDB menjadi {@code IN_PROGRESS}.</li>
     *   <li>Kirim prompt ke Gemini API dan parse hasilnya ke {@link ComplaintDocument.AiAnalysis}.</li>
     *   <li>Perbarui dokumen MongoDB dengan hasil analisis dan status {@code RESOLVED}.</li>
     *   <li>Jika priority {@code HIGH}, kirim email notifikasi ke Admin via {@link EmailService}.</li>
     * </ol>
     *
     * @param complaint dokumen keluhan yang akan dianalisis
     */
    void analyzeComplaint(ComplaintDocument complaint);
}
