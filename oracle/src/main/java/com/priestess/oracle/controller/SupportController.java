package com.priestess.oracle.controller;

import com.priestess.oracle.dto.ApiResponse;
import com.priestess.oracle.dto.SubmitComplaintRequest;
import com.priestess.oracle.entity.ComplaintDocument;
import com.priestess.oracle.producer.SupportEventProducer;
import com.priestess.oracle.service.ComplaintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SupportController — REST Controller untuk manajemen pengaduan pengguna.
 *
 * <p>Semua endpoint mengambil identitas pengguna dari header {@code X-User-Id}
 * yang disuntikkan oleh E.O.P Gateway. Controller ini tidak pernah mem-parse JWT.
 *
 * <h2>Pola Event-Driven untuk Submit Keluhan (SECTION 8)</h2>
 * <p>Endpoint {@code POST /api/support/complaints} tidak lagi memanggil
 * {@code ComplaintService} secara langsung. Sebaliknya, ia mempublikasikan
 * event ke queue {@code complaint.processing} via {@link SupportEventProducer}.
 * {@link com.priestess.oracle.consumer.ComplaintConsumer} yang mendengarkan
 * queue akan menyimpan keluhan ke MongoDB dan memicu analisis AI secara asinkron.
 *
 * <p>Ini memastikan HTTP layer (Controller) sepenuhnya terpisah dari
 * processing layer (Consumer), sesuai prinsip Event-Driven Architecture.
 *
 * <h2>Endpoints</h2>
 * <pre>
 *   POST   /api/support/complaints           — Submit keluhan baru (publish ke broker)
 *   GET    /api/support/complaints/my        — Lihat keluhan saya (user)
 *   GET    /api/support/complaints/{id}      — Detail satu keluhan
 *   GET    /api/support/complaints/status/{s}— Semua keluhan by status (Admin)
 *   PATCH  /api/support/complaints/{id}/status — Update status (Admin)
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/support/complaints")
@RequiredArgsConstructor
public class SupportController {

    private final ComplaintService     complaintService;
    /**
     * Producer untuk mempublikasikan event keluhan ke RabbitMQ.
     * ComplaintConsumer akan memproses event ini secara asinkron.
     */
    private final SupportEventProducer supportEventProducer;

    // =========================================================================
    // POST /api/support/complaints — Submit keluhan baru (Event-Driven)
    // =========================================================================

    /**
     * Menerima keluhan dari user dan mempublikasikannya ke RabbitMQ.
     *
     * <p>Sesuai SECTION 8: "Support & Oracle Service bertindak sebagai Consumer
     * asinkron murni yang dipicu oleh masuknya event pengaduan dari broker,
     * bukan dari hit API HTTP langsung."
     *
     * <p>Controller ini hanya mem-publish event dan langsung return 202 Accepted.
     * Proses penyimpanan MongoDB + analisis AI ditangani oleh
     * {@code ComplaintConsumer} secara asinkron di background.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<String>> submitComplaint(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Name") String username,
            @RequestHeader("X-User-Email") String email,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @Valid @RequestBody SubmitComplaintRequest request) {

        log.info("[SupportController] POST /complaints (Event-Driven) — userId={}", userId);

        try {
            // Publish event ke RabbitMQ — ComplaintConsumer yang memproses
            supportEventProducer.publishComplaintSubmitted(
                    userId,
                    username,
                    email,
                    request.getInvoiceId(),
                    request.getRawMessage()
            );

            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.success(
                            202,
                            "Keluhan berhasil diterima dan sedang diproses. " +
                            "Kami akan menganalisis keluhan Anda menggunakan AI dan mengirimkan " +
                            "konfirmasi melalui email dalam beberapa saat.",
                            "PENDING"
                    ));

        } catch (Exception e) {
            log.error("[SupportController] Gagal mempublikasikan event keluhan: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "Gagal menerima keluhan. Silakan coba lagi."));
        }
    }

    // =========================================================================
    // GET /api/support/complaints/my — Lihat keluhan sendiri
    // =========================================================================

    /**
     * Ambil semua keluhan milik pengguna yang sedang login.
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<ComplaintDocument>>> getMyComplaints(
            @RequestHeader("X-User-Id") String userId) {

        List<ComplaintDocument> complaints = complaintService.getMyComplaints(userId);
        return ResponseEntity.ok(ApiResponse.success(200, "Daftar keluhan berhasil diambil", complaints));
    }

    // =========================================================================
    // GET /api/support/complaints/{complaintId} — Detail satu keluhan
    // =========================================================================

    /**
     * Ambil detail satu keluhan berdasarkan complaint ID.
     */
    @GetMapping("/{complaintId}")
    public ResponseEntity<ApiResponse<ComplaintDocument>> getComplaintById(
            @PathVariable String complaintId) {

        ComplaintDocument complaint = complaintService.getComplaintById(complaintId);
        return ResponseEntity.ok(ApiResponse.success(200, "Detail keluhan", complaint));
    }

    // =========================================================================
    // GET /api/support/complaints/status/{status} — Filter by status (Admin)
    // =========================================================================

    /**
     * Ambil semua keluhan berdasarkan status (untuk dashboard Admin).
     * Status yang valid: OPEN, IN_PROGRESS, RESOLVED
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<ComplaintDocument>>> getByStatus(
            @PathVariable String status) {

        List<ComplaintDocument> complaints = complaintService.getComplaintsByStatus(status.toUpperCase());
        return ResponseEntity.ok(ApiResponse.success(200, "Keluhan dengan status " + status, complaints));
    }

    // =========================================================================
    // PATCH /api/support/complaints/{complaintId}/status — Update status (Admin)
    // =========================================================================

    /**
     * Perbarui status keluhan (Admin only — diproteksi di level SecurityConfig/Gateway).
     */
    @PatchMapping("/{complaintId}/status")
    public ResponseEntity<ApiResponse<ComplaintDocument>> updateStatus(
            @PathVariable String complaintId,
            @RequestParam String newStatus) {

        ComplaintDocument updated = complaintService.updateStatus(complaintId, newStatus.toUpperCase());
        return ResponseEntity.ok(ApiResponse.success(200, "Status keluhan diperbarui", updated));
    }
}
