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

/**
 * ComplaintConsumer — Consumer asinkron yang memproses pengaduan pengguna via RabbitMQ.
 *
 * <p>Sesuai rules-eop-priestess.md SECTION 3 dan SECTION 8:
 * <blockquote>
 * "Support & Oracle Service bertindak sebagai Consumer asinkron murni yang dipicu
 * oleh masuknya event pengaduan dari broker, bukan dari hit API HTTP langsung."
 * </blockquote>
 *
 * <h2>Alur Pemrosesan Keluhan (Event-Driven)</h2>
 * <ol>
 *   <li>Consumer mendengarkan queue {@code complaint.processing}.</li>
 *   <li>Saat event diterima, membuat {@link ComplaintDocument} baru di MongoDB
 *       dengan status {@code OPEN}.</li>
 *   <li>Memicu analisis AI via {@link GeminiAiService#analyzeComplaint(ComplaintDocument)}.
 *       Method ini berjalan {@code @Async} sehingga tidak memblokir Consumer thread.</li>
 *   <li>Jika Consumer gagal setelah retry habis, pesan otomatis dikirim ke
 *       {@code complaint-dlq} sesuai konfigurasi {@code x-dead-letter-exchange}.</li>
 * </ol>
 *
 * <h2>Dead Letter Queue (DLQ)</h2>
 * <p>Sesuai SECTION 8: Jika exception terjadi dan pesan di-reject, RabbitMQ
 * secara otomatis merutekan pesan ke {@code complaint-dlq} (sudah dikonfigurasi
 * di {@code RabbitMQConfig.complaintProcessingQueue()} dengan {@code x-dead-letter-*}).
 * Admin dapat memeriksa DLQ untuk investigasi manual.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplaintConsumer {

    private final ComplaintRepository complaintRepository;
    private final GeminiAiService     geminiAiService;

    /**
     * Mendengarkan event {@code complaint.processing} dari RabbitMQ.
     *
     * <p>Dipanggil secara asinkron oleh Spring AMQP saat {@code SupportController}
     * mempublikasikan keluhan baru. Method ini melakukan penyimpanan ke MongoDB
     * dan memicu analisis AI secara asinkron.
     *
     * <p>Jika exception tidak tertangkap, Spring AMQP akan mengirim ulang pesan
     * (retry). Jika semua retry habis, pesan diroute ke {@code complaint-dlq}
     * sesuai konfigurasi Dead Letter Exchange.
     *
     * @param event payload event berisi detail keluhan pengguna
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_COMPLAINT_PROCESSING)
    public void handleComplaintSubmitted(SupportEventProducer.ComplaintSubmittedEvent event) {
        log.info("[ComplaintConsumer] Menerima event complaint.processing — userId={}, invoiceId={}",
                event.getUserId(), event.getInvoiceId());

        try {
            // ================================================================
            // SIMPAN KELUHAN KE MONGODB — Status awal OPEN
            // ================================================================
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

            // ================================================================
            // PICU ANALISIS AI — Berjalan @Async (non-blocking)
            // GeminiAiService.analyzeComplaint() akan return segera,
            // analisis AI berjalan di thread pool oracle-async-*
            // ================================================================
            geminiAiService.analyzeComplaint(saved);
            log.info("[ComplaintConsumer] Analisis AI dipicu secara asinkron untuk complaintId={}",
                    saved.getComplaintId());

        } catch (Exception e) {
            // Jika exception tidak tertangkap, pesan akan di-nack ke broker
            // dan akhirnya berakhir di complaint-dlq setelah retry habis
            log.error("[ComplaintConsumer] GAGAL memproses keluhan dari userId={}: {}",
                    event.getUserId(), e.getMessage(), e);
            // Re-throw agar AMQP tahu pesan gagal dan perlu di-route ke DLQ
            throw new RuntimeException("Gagal memproses keluhan: " + e.getMessage(), e);
        }
    }
}
