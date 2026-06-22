package com.priestess.oracle.consumer;

import com.priestess.oracle.config.RabbitMQConfig;
import com.priestess.oracle.entity.ComplaintDocument;
import com.priestess.oracle.producer.SupportEventProducer;
import com.priestess.oracle.repository.ComplaintRepository;
import com.priestess.oracle.service.GeminiAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ComplaintConsumer {

    private final ComplaintRepository complaintRepository;
    private final GeminiAiService     geminiAiService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_COMPLAINT_PROCESSING)
    public void handleComplaintSubmitted(SupportEventProducer.ComplaintSubmittedEvent event) {
        log.info("[ComplaintConsumer] Menerima event complaint.processing — userId={}, invoiceId={}",
                event.getUserId(), event.getInvoiceId());

        try {

            String complaintId = "CPL-" + UUID.randomUUID();

            ComplaintDocument complaint = ComplaintDocument.builder()
                    .complaintId(complaintId)
                    .userId(event.getUserId())
                    .username(event.getUsername())
                    .email(event.getEmail())
                    .invoiceId(event.getInvoiceId())
                    .rawMessage(event.getRawMessage())
                    .status("OPEN")
                    .build();

            ComplaintDocument saved = complaintRepository.save(complaint);
            log.info("[ComplaintConsumer] Keluhan disimpan ke MongoDB — complaintId={}",
                    saved.getComplaintId());

            geminiAiService.analyzeComplaint(saved);
            log.info("[ComplaintConsumer] Analisis AI dipicu secara asinkron untuk complaintId={}",
                    saved.getComplaintId());

        } catch (Exception e) {

            log.error("[ComplaintConsumer] GAGAL memproses keluhan dari userId={}: {}",
                    event.getUserId(), e.getMessage(), e);

            throw new RuntimeException("Gagal memproses keluhan: " + e.getMessage(), e);
        }
    }
}
