package com.priestess.identity.producer;

import com.priestess.identity.config.RabbitMQConfig;
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
public class IdentityEventProducer {

    private final RabbitTemplate rabbitTemplate;

    public void publishUserSuspended(String userId, String username) {
        UserSuspendedEvent event = UserSuspendedEvent.builder()
                .userId(userId)
                .username(username)
                .suspendedAt(java.time.LocalDateTime.now().toString())
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_IDENTITY,
                RabbitMQConfig.QUEUE_USER_SUSPENDED,
                event
        );

        log.info("[IdentityEventProducer] PUBLISHED user.suspended — userId={}, username={}",
                userId, username);
    }

    public void publishMerchantRegistered(String merchantUserId, String merchantId, String ownerUserId, String ownerPhoneNumber, String merchantName) {
        MerchantRegisteredEvent event = MerchantRegisteredEvent.builder()
                .merchantUserId(merchantUserId)
                .merchantId(merchantId)
                .ownerUserId(ownerUserId)
                .ownerPhoneNumber(ownerPhoneNumber)
                .merchantName(merchantName)
                .registeredAt(java.time.LocalDateTime.now().toString())
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_IDENTITY,
                RabbitMQConfig.QUEUE_MERCHANT_REGISTERED,
                event
        );

        log.info("[IdentityEventProducer] PUBLISHED merchant.registered — merchantUserId={}, merchantId={}, ownerUserId={}, ownerPhoneNumber={}",
                merchantUserId, merchantId, ownerUserId, ownerPhoneNumber);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSuspendedEvent {
        private String userId;
        private String username;
        private String suspendedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MerchantRegisteredEvent {
        private String merchantUserId;
        private String merchantId;
        private String ownerUserId;
        private String ownerPhoneNumber;
        private String merchantName;
        private String registeredAt;
    }
}
