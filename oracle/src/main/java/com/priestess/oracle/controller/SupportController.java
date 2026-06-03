package com.priestess.oracle.controller;

import com.priestess.oracle.dto.ApiResponse;
import com.priestess.oracle.dto.SubmitComplaintRequest;
import com.priestess.oracle.entity.ComplaintDocument;
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
 * <h2>Endpoints</h2>
 * <pre>
 *   POST   /api/support/complaints           — Submit keluhan baru (semua user)
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

    private final ComplaintService complaintService;

    /**
     * Submit keluhan baru. Langsung mengembalikan 202 Accepted,
     * analisis AI berjalan di background.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ComplaintDocument>> submitComplaint(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody SubmitComplaintRequest request) {

        log.info("[SupportController] POST /complaints — userId={}", userId);
        ComplaintDocument saved = complaintService.submitComplaint(userId, request);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(
                        202,
                        "Keluhan berhasil diterima. Analisis sedang diproses di latar belakang.",
                        saved
                ));
    }

    /**
     * Ambil semua keluhan milik pengguna yang sedang login.
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<ComplaintDocument>>> getMyComplaints(
            @RequestHeader("X-User-Id") String userId) {

        List<ComplaintDocument> complaints = complaintService.getMyComplaints(userId);
        return ResponseEntity.ok(ApiResponse.success(200, "Daftar keluhan berhasil diambil", complaints));
    }

    /**
     * Ambil detail satu keluhan berdasarkan ID.
     */
    @GetMapping("/{complaintId}")
    public ResponseEntity<ApiResponse<ComplaintDocument>> getComplaintById(
            @PathVariable String complaintId) {

        ComplaintDocument complaint = complaintService.getComplaintById(complaintId);
        return ResponseEntity.ok(ApiResponse.success(200, "Detail keluhan", complaint));
    }

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
