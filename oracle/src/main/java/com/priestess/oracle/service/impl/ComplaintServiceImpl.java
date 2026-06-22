package com.priestess.oracle.service.impl;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintServiceImpl implements ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final GeminiAiService geminiAiService;

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
