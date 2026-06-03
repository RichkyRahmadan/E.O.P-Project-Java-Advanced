package com.priestess.oracle.service.impl;

import com.priestess.oracle.dto.SubmitComplaintRequest;
import com.priestess.oracle.entity.ComplaintDocument;
import com.priestess.oracle.repository.ComplaintRepository;
import com.priestess.oracle.service.ComplaintService;
import com.priestess.oracle.service.GeminiAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * ComplaintServiceImpl — Implementasi logika bisnis manajemen pengaduan.
 *
 * <h2>Pola Asinkron (SECTION 8 blueprint)</h2>
 * <p>Metode {@link #submitComplaint} menggunakan pola "fire and forget":
 * <ol>
 *   <li>Simpan keluhan ke MongoDB dengan status {@code OPEN} — selesai dalam milidetik.</li>
 *   <li>Panggil {@link GeminiAiService#analyzeComplaint} yang sudah dianotasi {@code @Async}
 *       di implementasinya. Method ini langsung return tanpa menunggu analisis selesai.</li>
 *   <li>Service ini mengembalikan dokumen keluhan ke Controller → Controller kirim 202 ke klien.</li>
 * </ol>
 * Hasilnya: klien menerima respons hampir instan, analisis AI berjalan di background thread.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintServiceImpl implements ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final GeminiAiService geminiAiService;

    @Override
    public ComplaintDocument submitComplaint(String userId, SubmitComplaintRequest request) {
        log.info("[ComplaintService] Menerima keluhan baru dari userId={}", userId);

        // Buat dan simpan dokumen awal dengan status OPEN
        ComplaintDocument complaint = ComplaintDocument.builder()
                .complaintId(UUID.randomUUID().toString())
                .userId(userId)
                .username(request.getUsername())
                .email(request.getEmail())
                .invoiceId(request.getInvoiceId())
                .rawMessage(request.getMessage())
                .status("OPEN")
                .build();

        ComplaintDocument saved = complaintRepository.save(complaint);
        log.info("[ComplaintService] Keluhan disimpan: complaintId={}", saved.getComplaintId());

        // Panggil analisis AI secara asinkron (non-blocking)
        // GeminiAiServiceImpl.analyzeComplaint() dianotasi @Async
        geminiAiService.analyzeComplaint(saved);

        return saved;
    }

    @Override
    public ComplaintDocument getComplaintById(String complaintId) {
        return complaintRepository.findByComplaintId(complaintId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Keluhan dengan ID " + complaintId + " tidak ditemukan"
                ));
    }

    @Override
    public List<ComplaintDocument> getMyComplaints(String userId) {
        return complaintRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public List<ComplaintDocument> getComplaintsByStatus(String status) {
        return complaintRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    @Override
    public ComplaintDocument updateStatus(String complaintId, String newStatus) {
        ComplaintDocument complaint = getComplaintById(complaintId);
        complaint.setStatus(newStatus);
        ComplaintDocument updated = complaintRepository.save(complaint);
        log.info("[ComplaintService] Status keluhan {} diperbarui menjadi {}", complaintId, newStatus);
        return updated;
    }
}
