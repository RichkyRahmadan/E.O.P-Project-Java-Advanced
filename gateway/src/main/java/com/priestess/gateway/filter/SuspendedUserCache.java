package com.priestess.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SuspendedUserCache {

    private final Set<String> suspendedUserIds = ConcurrentHashMap.newKeySet();

    public void addSuspendedUser(String userId) {
        suspendedUserIds.add(userId);
        log.warn("[SuspendedUserCache] User {} ditambahkan ke suspended cache. " +
                "Total suspended: {}", userId, suspendedUserIds.size());
    }

    public boolean isSuspended(String userId) {
        return suspendedUserIds.contains(userId);
    }

    public void removeSuspendedUser(String userId) {
        boolean removed = suspendedUserIds.remove(userId);
        if (removed) {
            log.info("[SuspendedUserCache] User {} dihapus dari suspended cache.", userId);
        }
    }

    public int size() {
        return suspendedUserIds.size();
    }
}
