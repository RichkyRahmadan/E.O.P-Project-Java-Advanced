package com.priestess.oracle.producer;

import com.priestess.oracle.config.RabbitMQConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupportEventProducer {

    private final RabbitTemplate rabbitTemplate;

    public void publishComplaintSubmitted(String userId, String username, String email,
                                          String invoiceId, String rawMessage) {
        ComplaintSubmittedEvent event = ComplaintSubmittedEvent.builder()
                .userId(userId)
                .username(username)
                .email(email)
                .invoiceId(invoiceId)
                .rawMessage(rawMessage)
                .submittedAt(java.time.LocalDateTime.now().toString())
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_SUPPORT,
                RabbitMQConfig.QUEUE_COMPLAINT_PROCESSING,
                event
        );

        log.info("[SupportEventProducer] PUBLISHED complaint.processing — userId={}, invoiceId={}",
                userId, invoiceId);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplaintSubmittedEvent {
        private String userId;
        private String username;
        private String email;
        private String invoiceId;
        private String rawMessage;
        private String submittedAt;
    }
}
