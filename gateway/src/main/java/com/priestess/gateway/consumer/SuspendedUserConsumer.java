package com.priestess.gateway.consumer;

import com.priestess.gateway.config.RabbitMQConfig;
import com.priestess.gateway.filter.SuspendedUserCache;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SuspendedUserConsumer {

    private final SuspendedUserCache suspendedUserCache;
    private final StringRedisTemplate redisTemplate;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_USER_SUSPENDED)
    public void handleUserSuspended(UserSuspendedEvent event) {
        log.warn("[SuspendedUserConsumer] MENERIMA user.suspended — userId={}, username={}, at={}",
                event.getUserId(), event.getUsername(), event.getSuspendedAt());

        try {

            suspendedUserCache.addSuspendedUser(event.getUserId());
            log.info("[SuspendedUserConsumer] userId={} berhasil ditambahkan ke suspended cache. " +
                    "Semua request berikutnya dari user ini akan ditolak 403 Forbidden.",
                    event.getUserId());

            String userSessionsKey = "user:sessions:" + event.getUserId();
            Set<String> activeTokens = redisTemplate.opsForSet().members(userSessionsKey);
            if (activeTokens != null) {
                for (String token : activeTokens) {
                    redisTemplate.delete("session:" + token);
                }
            }
            redisTemplate.delete(userSessionsKey);
            log.info("[SuspendedUserConsumer] Berhasil menghapus semua sesi Redis untuk userId={}", event.getUserId());

        } catch (Exception e) {
            log.error("[SuspendedUserConsumer] Gagal memproses penangguhan akun userId={}: {}",
                    event.getUserId(), e.getMessage(), e);
        }
    }

    @Data
    @NoArgsConstructor
    public static class UserSuspendedEvent {
        private String userId;
        private String username;
        private String suspendedAt;
    }
}
