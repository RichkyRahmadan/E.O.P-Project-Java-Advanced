package com.priestess.identity.controller;

import com.priestess.identity.dto.AuthResponse;
import com.priestess.identity.dto.LoginRequest;
import com.priestess.identity.dto.RefreshTokenRequest;
import com.priestess.identity.dto.RegisterMerchantRequest;
import com.priestess.identity.dto.RegisterUserRequest;
import com.priestess.identity.dto.RegisterResponse;
import com.priestess.identity.service.AuthService;
import com.priestess.identity.dto.UserResolutionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("[AuthController] POST /api/auth/login — username: {}", request.getUsername());
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("[AuthController] POST /api/auth/refresh");
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> registerUser(@Valid @RequestBody RegisterUserRequest request) {
        log.info("[AuthController] POST /api/auth/register — username: {}", request.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerUser(request));
    }

    @PostMapping("/register/merchant")
    public ResponseEntity<RegisterResponse> registerMerchant(
            @Valid @RequestBody RegisterMerchantRequest request) {
        log.info("[AuthController] POST /api/auth/register/merchant — username: {}", request.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerMerchant(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("X-Refresh-Token") String refreshToken) {
        log.info("[AuthController] POST /api/auth/logout");
        authService.logout(refreshToken);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/resolve")
    public ResponseEntity<UserResolutionResponse> resolveUser(@RequestParam("query") String query) {
        log.info("[AuthController] GET /api/auth/resolve — query: {}", query);
        return ResponseEntity.ok(authService.resolveUser(query));
    }
}
