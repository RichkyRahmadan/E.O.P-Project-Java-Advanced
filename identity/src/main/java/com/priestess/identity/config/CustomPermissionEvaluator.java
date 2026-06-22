package com.priestess.identity.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Slf4j
@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    @Override
    public boolean hasPermission(Authentication authentication,
                                 Object targetDomainObject,
                                 Object permission) {
        if (authentication == null || permission == null) {
            log.warn("[PermissionEvaluator] Evaluasi gagal: authentication atau permission bernilai null.");
            return false;
        }

        String requiredPermission = permission.toString();
        String principalName = authentication.getName();

        boolean granted = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals(requiredPermission));

        if (granted) {
            log.debug("[PermissionEvaluator] IZIN DIBERIKAN — principal={}, permission={}",
                    principalName, requiredPermission);
        } else {
            log.warn("[PermissionEvaluator] AKSES DITOLAK — principal={} tidak memiliki permission={}",
                    principalName, requiredPermission);
        }

        return granted;
    }

    @Override
    public boolean hasPermission(Authentication authentication,
                                 Serializable targetId,
                                 String targetType,
                                 Object permission) {
        log.debug("[PermissionEvaluator] hasPermission (targetId overload) dipanggil dengan targetType={}, permission={}",
                targetType, permission);
        return hasPermission(authentication, null, permission);
    }
}
