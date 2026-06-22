package com.priestess.identity.service.impl;

import com.priestess.identity.dto.AuthResponse;
import com.priestess.identity.dto.LoginRequest;
import com.priestess.identity.dto.RefreshTokenRequest;
import com.priestess.identity.dto.RegisterMerchantByOwnerRequest;
import com.priestess.identity.dto.RegisterMerchantRequest;
import com.priestess.identity.dto.RegisterUserRequest;
import com.priestess.identity.dto.RegisterResponse;
import com.priestess.identity.dto.UserResolutionResponse;
import com.priestess.identity.entity.MerchantEntity;
import com.priestess.identity.entity.RoleEntity;
import com.priestess.identity.entity.UserEntity;
import com.priestess.identity.repository.MerchantRepository;
import com.priestess.identity.repository.RolePermissionRepository;
import com.priestess.identity.repository.RoleRepository;
import com.priestess.identity.repository.UserRepository;
import com.priestess.identity.service.AuthService;
import com.priestess.identity.utility.JWTUtil;
import com.priestess.identity.producer.IdentityEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MerchantRepository merchantRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final JWTUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final IdentityEventProducer identityEventProducer;

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("[AuthService] Memproses login untuk username: {}", request.getUsername());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        UserEntity user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalStateException("User tidak ditemukan setelah autentikasi."));

        if ("SUSPENDED".equalsIgnoreCase(user.getStatus())) {
            log.warn("[AuthService] Login ditolak — akun SUSPENDED: {}", user.getUsername());
            throw new IllegalStateException("Akun Anda telah dibekukan!");
        }

        if ("PENDING".equalsIgnoreCase(user.getStatus())) {
            log.warn("[AuthService] Login ditolak — akun PENDING: {}", user.getUsername());
            throw new IllegalStateException("Akun Anda belum aktif! Silakan tunggu verifikasi admin.");
        }

        return buildAuthResponseAndSaveToken(user);
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        log.info("[AuthService] Memproses refresh token.");

        UserEntity user = userRepository.findByRefreshToken(request.getRefreshToken())
                .orElseThrow(() -> new IllegalStateException(
                        "Refresh token tidak valid atau sudah kedaluwarsa. Silakan login kembali."));

        if ("SUSPENDED".equalsIgnoreCase(user.getStatus())) {
            log.warn("[AuthService] Refresh ditolak — akun SUSPENDED: {}", user.getUsername());

            user.setRefreshToken(null);
            userRepository.save(user);
            throw new IllegalStateException("Akun Anda telah dibekukan!");
        }

        if ("PENDING".equalsIgnoreCase(user.getStatus())) {
            log.warn("[AuthService] Refresh ditolak — akun PENDING: {}", user.getUsername());

            user.setRefreshToken(null);
            userRepository.save(user);
            throw new IllegalStateException("Akun Anda belum aktif! Silakan tunggu verifikasi admin.");
        }

        log.debug("[AuthService] Refresh token valid untuk userId={}", user.getId());
        return buildAuthResponseAndSaveToken(user);
    }

    @Override
    @Transactional
    public RegisterResponse registerUser(RegisterUserRequest request) {
        log.info("[AuthService] Mendaftarkan user baru: {}", request.getUsername());

        validateUniqueCredentials(request.getUsername(), request.getEmail(), request.getPhone());

        RoleEntity roleUser = roleRepository.findByRoleName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException(
                        "Role ROLE_USER tidak ditemukan. Pastikan data seed sudah dijalankan."));

        UserEntity newUser = UserEntity.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(roleUser)
                .status("PENDING")
                .build();

        userRepository.save(newUser);
        log.info("[AuthService] User baru berhasil didaftarkan: {}", newUser.getUsername());

        return RegisterResponse.builder()
                .message(
                        "Registrasi berhasil. Akun Anda berstatus PENDING. Silakan hubungi admin untuk melakukan verifikasi.")
                .username(newUser.getUsername())
                .status(newUser.getStatus())
                .build();
    }

    @Override
    @Transactional
    public RegisterResponse registerMerchant(RegisterMerchantRequest request) {
        log.info("[AuthService] Mendaftarkan merchant baru: {}", request.getUsername());

        validateUniqueCredentials(request.getUsername(), request.getEmail(), null);

        UserEntity ownerUser = userRepository.findByPhone(request.getOwnerPhoneNumber())
                .orElseThrow(() -> new IllegalArgumentException("Nomor telepon owner tidak ditemukan di sistem."));

        if (!"ACTIVE".equalsIgnoreCase(ownerUser.getStatus())) {
            throw new IllegalArgumentException("Akun owner belum aktif!");
        }

        RoleEntity roleMerchant = roleRepository.findByRoleName("ROLE_MERCHANT")
                .orElseThrow(() -> new IllegalStateException(
                        "Role ROLE_MERCHANT tidak ditemukan. Pastikan data seed sudah dijalankan."));

        UserEntity newUser = UserEntity.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(roleMerchant)
                .status("PENDING")
                .build();

        userRepository.save(newUser);

        MerchantEntity merchant = MerchantEntity.builder()
                .user(newUser)
                .merchantName(request.getMerchantName())
                .address(request.getAddress())
                .owner(ownerUser)
                .isVerified(false)
                .build();

        merchantRepository.save(merchant);
        log.info("[AuthService] Merchant baru berhasil didaftarkan: {} (User: {})",
                merchant.getMerchantName(), newUser.getUsername());

        try {
            identityEventProducer.publishMerchantRegistered(
                    newUser.getId().toString(),
                    merchant.getId().toString(),
                    ownerUser.getId().toString(),
                    ownerUser.getPhone(),
                    merchant.getMerchantName()
            );
        } catch (Exception e) {
            log.error("[AuthService] Gagal mengirim event merchant.registered: {}", e.getMessage(), e);
        }

        return RegisterResponse.builder()
                .message(
                        "Registrasi merchant berhasil. Akun Anda berstatus PENDING. Silakan hubungi admin untuk melakukan verifikasi.")
                .username(newUser.getUsername())
                .status(newUser.getStatus())
                .build();
    }

    @Override
    @Transactional
    public RegisterResponse registerMerchantByOwner(String ownerUserId, RegisterMerchantByOwnerRequest request) {
        log.info("[AuthService] Owner {} mendaftarkan merchant baru: {}", ownerUserId, request.getUsername());

        UserEntity ownerUser = userRepository.findById(java.util.UUID.fromString(ownerUserId))
                .orElseThrow(() -> new IllegalArgumentException("Data owner tidak ditemukan."));

        if (!"ACTIVE".equalsIgnoreCase(ownerUser.getStatus())) {
            throw new IllegalArgumentException("Hanya akun yang sudah terverifikasi (ACTIVE) yang dapat mendaftarkan merchant.");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalStateException("Username '" + request.getUsername() + "' sudah terdaftar.");
        }

        RoleEntity roleMerchant = roleRepository.findByRoleName("ROLE_MERCHANT")
                .orElseThrow(() -> new IllegalStateException(
                        "Role ROLE_MERCHANT tidak ditemukan. Pastikan data seed sudah dijalankan."));

        UserEntity newMerchantUser = UserEntity.builder()
                .username(request.getUsername())
                .email(request.getUsername() + "@merchant.eop")
                .password(passwordEncoder.encode(request.getPassword()))
                .role(roleMerchant)
                .status("PENDING")
                .build();

        userRepository.save(newMerchantUser);

        MerchantEntity merchant = MerchantEntity.builder()
                .user(newMerchantUser)
                .merchantName(request.getMerchantName())
                .address(request.getAddress())
                .owner(ownerUser)
                .isVerified(false)
                .build();

        merchantRepository.save(merchant);
        log.info("[AuthService] Merchant '{}' berhasil didaftarkan oleh owner '{}'.",
                merchant.getMerchantName(), ownerUser.getUsername());

        try {
            identityEventProducer.publishMerchantRegistered(
                    newMerchantUser.getId().toString(),
                    merchant.getId().toString(),
                    ownerUser.getId().toString(),
                    ownerUser.getPhone() != null ? ownerUser.getPhone() : "",
                    merchant.getMerchantName()
            );
        } catch (Exception e) {
            log.error("[AuthService] Gagal mengirim event merchant.registered: {}", e.getMessage(), e);
        }

        return RegisterResponse.builder()
                .message("Pendaftaran merchant '" + merchant.getMerchantName() +
                        "' berhasil! Akun merchant berstatus PENDING. Silakan hubungi admin untuk verifikasi.")
                .username(newMerchantUser.getUsername())
                .status(newMerchantUser.getStatus())
                .build();
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        log.info("[AuthService] Memproses logout.");

        userRepository.findByRefreshToken(refreshToken).ifPresentOrElse(user -> {
            user.setRefreshToken(null);
            userRepository.save(user);

            String userSessionsKey = "user:sessions:" + user.getId().toString();
            try {
                Set<String> activeTokens = redisTemplate.opsForSet().members(userSessionsKey);
                if (activeTokens != null) {
                    for (String token : activeTokens) {
                        redisTemplate.delete("session:" + token);
                    }
                }
                redisTemplate.delete(userSessionsKey);
                log.info("[AuthService] Berhasil menghapus semua sesi Redis untuk userId={}", user.getId());
            } catch (Exception e) {
                log.error("[AuthService] Gagal menghapus sesi Redis saat logout: {}", e.getMessage(), e);
            }

            log.info("[AuthService] Logout berhasil — userId={}", user.getId());
        }, () -> log.warn("[AuthService] Logout dipanggil dengan token yang tidak ditemukan di DB."));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResolutionResponse resolveUser(String usernameOrEmail) {
        log.info("[AuthService] Menerima request resolveUser untuk: {}", usernameOrEmail);
        UserEntity user = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .orElseThrow(() -> new IllegalStateException("Penerima tidak ditemukan dengan username/email: " + usernameOrEmail));

        return UserResolutionResponse.builder()
                .userId(user.getId().toString())
                .username(user.getUsername())
                .email(user.getEmail())
                .build();
    }

    private AuthResponse buildAuthResponseAndSaveToken(UserEntity user) {
        List<String> permissions = rolePermissionRepository
                .findAllByRole(user.getRole())
                .stream()
                .map(rp -> rp.getMenu().getMenuName())
                .collect(Collectors.toList());

        String accessToken = jwtUtil.generateAccessToken(
                user.getId(), user.getUsername(), user.getEmail(), user.getRole().getRoleName(), user.getStatus(),
                permissions);
        String refreshToken = jwtUtil.generateRefreshToken();

        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        String sessionValue = String.format("%s:::%s:::%s:::%s:::%s:::%s",
                user.getId().toString(),
                user.getUsername() != null ? user.getUsername() : "",
                user.getEmail() != null ? user.getEmail() : "",
                user.getRole().getRoleName() != null ? user.getRole().getRoleName() : "",
                user.getStatus() != null ? user.getStatus() : "",
                String.join(",", permissions));

        try {

            redisTemplate.opsForValue().set("session:" + accessToken, sessionValue, 15, TimeUnit.MINUTES);

            String userSessionsKey = "user:sessions:" + user.getId().toString();
            redisTemplate.opsForSet().add(userSessionsKey, accessToken);
            redisTemplate.expire(userSessionsKey, 24, TimeUnit.HOURS);
            log.info("[AuthService] Berhasil menyimpan session di Redis untuk userId={}", user.getId());
        } catch (Exception e) {
            log.error("[AuthService] Gagal menyimpan session ke Redis: {}", e.getMessage(), e);
        }

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(AuthResponse.UserSummary.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .roleName(user.getRole().getRoleName())
                        .status(user.getStatus())
                        .build())
                .build();
    }

    private void validateUniqueCredentials(String username, String email, String phone) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalStateException("Username '" + username + "' sudah terdaftar.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException("Email '" + email + "' sudah terdaftar.");
        }
        if (phone != null && !phone.isBlank() && userRepository.existsByPhone(phone)) {
            throw new IllegalStateException("Nomor telepon '" + phone + "' sudah terdaftar.");
        }
    }
}
