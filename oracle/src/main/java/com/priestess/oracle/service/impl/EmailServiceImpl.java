package com.priestess.oracle.service.impl;

import com.priestess.oracle.entity.ComplaintDocument;
import com.priestess.oracle.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${eop.admin.email}")
    private String adminEmail;

    @Override
    public void sendHighPriorityAlert(ComplaintDocument complaint) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(adminEmail);
            helper.setSubject("⚠️ [E.O.P ALERT] Keluhan Prioritas HIGH — " + complaint.getComplaintId());
            helper.setText(buildHtmlBody(complaint), true);

            mailSender.send(message);
            log.info("[EmailService] Email notifikasi HIGH priority terkirim ke {} untuk complaintId={}",
                    adminEmail, complaint.getComplaintId());

        } catch (MessagingException e) {

            log.error("[EmailService] Gagal mengirim email untuk complaintId={}: {}",
                    complaint.getComplaintId(), e.getMessage(), e);
        }
    }

    private String buildHtmlBody(ComplaintDocument complaint) {
        ComplaintDocument.AiAnalysis ai = complaint.getAiAnalysis();
        String suggestedReply = (ai != null && ai.getSuggestedReply() != null)
                ? ai.getSuggestedReply() : "Tidak tersedia";
        String category    = (ai != null) ? ai.getCategory()  : "N/A";
        String sentiment   = (ai != null) ? ai.getSentiment() : "N/A";
        String score       = (ai != null && ai.getScore() != null) ? String.format("%.2f", ai.getScore()) : "N/A";

        return """
                <!DOCTYPE html>
                <html lang="id">
                <head>
                  <meta charset="UTF-8">
                  <style>
                    body { font-family: 'Segoe UI', Arial, sans-serif; background: #f5f5f5; margin: 0; padding: 20px; }
                    .container { max-width: 600px; margin: auto; background: #fff; border-radius: 12px;
                                 box-shadow: 0 4px 20px rgba(0,0,0,0.1); overflow: hidden; }
                    .header { background: linear-gradient(135deg, #c0392b, #e74c3c); color: white;
                              padding: 28px 32px; }
                    .header h1 { margin: 0; font-size: 22px; }
                    .header p { margin: 6px 0 0; opacity: 0.85; font-size: 14px; }
                    .body { padding: 28px 32px; }
                    .badge { display: inline-block; background: #e74c3c; color: white; border-radius: 6px;
                             padding: 3px 10px; font-size: 12px; font-weight: bold; letter-spacing: 0.5px; }
                    .info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin: 20px 0; }
                    .info-card { background: #f9f9f9; border-radius: 8px; padding: 14px 16px;
                                 border-left: 4px solid #e74c3c; }
                    .info-card label { font-size: 11px; color: #888; text-transform: uppercase;
                                       letter-spacing: 0.5px; display: block; margin-bottom: 4px; }
                    .info-card span { font-size: 14px; font-weight: 600; color: #333; }
                    .message-box { background: #fff3f3; border-radius: 8px; padding: 16px;
                                   border: 1px solid #ffd5d5; margin: 16px 0; }
                    .message-box h3 { margin: 0 0 8px; font-size: 13px; color: #c0392b; }
                    .message-box p { margin: 0; font-size: 14px; color: #555; line-height: 1.6; }
                    .reply-box { background: #f0f8ff; border-radius: 8px; padding: 16px;
                                 border: 1px solid #b3d9ff; margin: 16px 0; }
                    .reply-box h3 { margin: 0 0 8px; font-size: 13px; color: #2980b9; }
                    .reply-box p { margin: 0; font-size: 14px; color: #333; line-height: 1.6;
                                   font-style: italic; }
                    .footer { background: #f9f9f9; padding: 16px 32px; text-align: center;
                              color: #aaa; font-size: 12px; border-top: 1px solid #eee; }
                  </style>
                </head>
                <body>
                  <div class="container">
                    <div class="header">
                      <h1>⚠️ Alert Keluhan Prioritas Tinggi</h1>
                      <p>E.O.P Support & Oracle Service — Notifikasi Otomatis</p>
                    </div>
                    <div class="body">
                      <p>Sistem telah mendeteksi keluhan dengan prioritas <span class="badge">HIGH</span>
                         yang memerlukan perhatian segera.</p>

                      <div class="info-grid">
                        <div class="info-card">
                          <label>Complaint ID</label>
                          <span>%s</span>
                        </div>
                        <div class="info-card">
                          <label>Status</label>
                          <span>%s</span>
                        </div>
                        <div class="info-card">
                          <label>Pelapor</label>
                          <span>%s (%s)</span>
                        </div>
                        <div class="info-card">
                          <label>Invoice Terkait</label>
                          <span>%s</span>
                        </div>
                        <div class="info-card">
                          <label>Kategori</label>
                          <span>%s</span>
                        </div>
                        <div class="info-card">
                          <label>Sentimen / Skor</label>
                          <span>%s / %s</span>
                        </div>
                      </div>

                      <div class="message-box">
                        <h3>📝 Pesan Keluhan Asli</h3>
                        <p>%s</p>
                      </div>

                      <div class="reply-box">
                        <h3>🤖 Saran Balasan dari Gemini AI (Under Development)</h3>
                        <p>%s</p>
                      </div>
                    </div>
                    <div class="footer">
                      Email ini dikirim secara otomatis oleh E.O.P Oracle Service.<br>
                      Waktu: %s
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                complaint.getComplaintId(),
                complaint.getStatus(),
                complaint.getUsername(), complaint.getEmail(),
                complaint.getInvoiceId() != null ? complaint.getInvoiceId() : "Tidak ada",
                category,
                sentiment, score,
                complaint.getRawMessage(),
                suggestedReply,
                java.time.LocalDateTime.now()
        );
    }
}
