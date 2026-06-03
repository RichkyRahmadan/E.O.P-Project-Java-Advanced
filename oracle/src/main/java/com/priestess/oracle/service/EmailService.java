package com.priestess.oracle.service;

import com.priestess.oracle.entity.ComplaintDocument;

/**
 * EmailService — Kontrak interface untuk pengiriman email notifikasi via JavaMailSender.
 */
public interface EmailService {

    /**
     * Mengirim email notifikasi kepada Admin ketika keluhan memiliki prioritas {@code HIGH}.
     *
     * <p>Email dikirim dalam format HTML terstruktur berisi:
     * <ul>
     *   <li>Identitas pelapor (username, email)</li>
     *   <li>Kategori dan sentimen keluhan</li>
     *   <li>Teks keluhan mentah</li>
     *   <li>Saran balasan dari AI</li>
     * </ul>
     *
     * @param complaint dokumen keluhan yang sudah selesai dianalisis AI
     */
    void sendHighPriorityAlert(ComplaintDocument complaint);
}
