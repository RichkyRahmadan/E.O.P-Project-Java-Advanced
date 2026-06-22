package com.priestess.core.consumer;

import com.priestess.core.config.RabbitMQConfig;
import com.priestess.core.entity.MerchantOwnerMappingEntity;
import com.priestess.core.repository.MerchantOwnerMappingRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantRegisteredConsumer {

    private final MerchantOwnerMappingRepository mappingRepository;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_MERCHANT_REGISTERED)
    public void handleMerchantRegistered(MerchantRegisteredEvent event) {
        log.info("[MerchantRegisteredConsumer] Menerima event merchant.registered — merchantUserId={}, ownerUserId={}",
                event.getMerchantUserId(), event.getOwnerUserId());

        try {
            UUID merchantUserId = UUID.fromString(event.getMerchantUserId());
            UUID merchantId = UUID.fromString(event.getMerchantId());
            UUID ownerUserId = UUID.fromString(event.getOwnerUserId());

            MerchantOwnerMappingEntity mapping = mappingRepository.findByMerchantUserId(merchantUserId)
                    .orElse(new MerchantOwnerMappingEntity());

            mapping.setMerchantUserId(merchantUserId);
            mapping.setMerchantId(merchantId);
            mapping.setOwnerUserId(ownerUserId);
            mapping.setOwnerPhoneNumber(event.getOwnerPhoneNumber());
            mapping.setMerchantName(event.getMerchantName());

            mappingRepository.save(mapping);
            log.info("[MerchantRegisteredConsumer] Berhasil menyimpan pemetaan merchant-owner untuk merchant user id: {}", merchantUserId);
        } catch (Exception e) {
            log.error("[MerchantRegisteredConsumer] Gagal memproses event merchant.registered: {}", e.getMessage(), e);
        }
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
