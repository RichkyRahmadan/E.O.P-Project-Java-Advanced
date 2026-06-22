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

@Slf4j
@RestController
@RequestMapping("/api/support/complaints")
@RequiredArgsConstructor
public class SupportController {

    private final ComplaintService     complaintService;

    private final SupportEventProducer supportEventProducer;

    @PostMapping
    public ResponseEntity<ApiResponse<String>> submitComplaint(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Name") String username,
            @RequestHeader("X-User-Email") String email,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @Valid @RequestBody SubmitComplaintRequest request) {

        log.info("[SupportController] POST /complaints (Event-Driven) — userId={}", userId);

        try {

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

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<ComplaintDocument>>> getMyComplaints(
            @RequestHeader("X-User-Id") String userId) {

        List<ComplaintDocument> complaints = complaintService.getMyComplaints(userId);
        return ResponseEntity.ok(ApiResponse.success(200, "Daftar keluhan berhasil diambil", complaints));
    }

    @GetMapping("/{complaintId}")
    public ResponseEntity<ApiResponse<ComplaintDocument>> getComplaintById(
            @PathVariable String complaintId) {

        ComplaintDocument complaint = complaintService.getComplaintById(complaintId);
        return ResponseEntity.ok(ApiResponse.success(200, "Detail keluhan", complaint));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<ComplaintDocument>>> getByStatus(
            @PathVariable String status) {

        List<ComplaintDocument> complaints = complaintService.getComplaintsByStatus(status.toUpperCase());
        return ResponseEntity.ok(ApiResponse.success(200, "Keluhan dengan status " + status, complaints));
    }

    @PatchMapping("/{complaintId}/status")
    public ResponseEntity<ApiResponse<ComplaintDocument>> updateStatus(
            @PathVariable String complaintId,
            @RequestParam String newStatus) {

        ComplaintDocument updated = complaintService.updateStatus(complaintId, newStatus.toUpperCase());
        return ResponseEntity.ok(ApiResponse.success(200, "Status keluhan diperbarui", updated));
    }
}
