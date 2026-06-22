package com.priestess.oracle.service.impl;

import com.priestess.oracle.entity.ComplaintDocument;
import com.priestess.oracle.service.GeminiAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiAiServiceImpl implements GeminiAiService {

    private final RetryableGeminiExecutor retryableExecutor;

    @Async
    @Override
    public void analyzeComplaint(ComplaintDocument complaint) {
        log.info("[GeminiAI] Thread async dimulai untuk complaintId={}", complaint.getComplaintId());
        try {

            retryableExecutor.executeWithRetry(complaint);
        } catch (Exception e) {

            log.error("[GeminiAI] SEMUA RETRY HABIS untuk complaintId={}. Keluhan tetap OPEN. Error: {}",
                    complaint.getComplaintId(), e.getMessage());
        }
    }
}
