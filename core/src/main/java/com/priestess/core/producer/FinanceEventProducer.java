package com.priestess.core.producer;

import com.priestess.core.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class FinanceEventProducer {

    private final RabbitTemplate rabbitTemplate;

    public void publishQrisInitiated(String invoiceId, String buyerOwnerId, BigDecimal amount) {
        QrisPaymentEvent event = QrisPaymentEvent.builder()
                .invoiceId(invoiceId)
                .buyerOwnerId(buyerOwnerId)
                .amount(amount)
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_FINANCE,
                RabbitMQConfig.QUEUE_QRIS_INITIATED,
                event
        );

        log.info("[FinanceEventProducer] PUBLISHED qris.payment.initiated — invoiceId={}, buyer={}",
                invoiceId, buyerOwnerId);
    }

    public void publishQrisSuccess(String invoiceId, String buyerOwnerId, String buyerWalletId) {
        QrisResultEvent event = QrisResultEvent.builder()
                .invoiceId(invoiceId)
                .buyerOwnerId(buyerOwnerId)
                .buyerWalletId(buyerWalletId)
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_FINANCE,
                RabbitMQConfig.QUEUE_QRIS_SUCCESS,
                event
        );

        log.info("[FinanceEventProducer] PUBLISHED qris.payment.success — invoiceId={}", invoiceId);
    }

    public void publishQrisFailed(String invoiceId, String errorReason) {
        QrisResultEvent event = QrisResultEvent.builder()
                .invoiceId(invoiceId)
                .errorReason(errorReason)
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_FINANCE,
                RabbitMQConfig.QUEUE_QRIS_FAILED,
                event
        );

        log.info("[FinanceEventProducer] PUBLISHED qris.payment.failed — invoiceId={}, reason={}",
                invoiceId, errorReason);
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class QrisPaymentEvent {
        private String     invoiceId;
        private String     buyerOwnerId;
        private BigDecimal amount;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class QrisResultEvent {
        private String invoiceId;
        private String buyerOwnerId;
        private String buyerWalletId;
        private String errorReason;
    }
}
