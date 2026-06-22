package com.priestess.identity.controller;

import com.priestess.identity.dto.RegisterMerchantByOwnerRequest;
import com.priestess.identity.dto.RegisterResponse;
import com.priestess.identity.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/identity")
@RequiredArgsConstructor
public class IdentityController {

    private final AuthService authService;

    @PostMapping("/merchant/register")
    public ResponseEntity<RegisterResponse> registerMerchant(
            @RequestHeader("X-User-Id") String ownerId,
            @RequestHeader(value = "X-User-Status", required = false) String status,
            @Valid @RequestBody RegisterMerchantByOwnerRequest request) {
        log.info("[IdentityController] POST /api/identity/merchant/register — ownerId={}, status={}", ownerId, status);
        RegisterResponse response = authService.registerMerchantByOwner(ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
