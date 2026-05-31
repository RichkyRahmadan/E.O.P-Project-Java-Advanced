package com.priestess.identity.service.impl;

import com.priestess.identity.dto.AuthResponse;
import com.priestess.identity.dto.LoginRequest;
import com.priestess.identity.dto.RefreshTokenRequest;
import com.priestess.identity.dto.RegisterMerchantRequest;
import com.priestess.identity.dto.RegisterUserRequest;
import com.priestess.identity.entity.MerchantEntity;
import com.priestess.identity.entity.RoleEntity;
import com.priestess.identity.entity.UserEntity;
import com.priestess.identity.repository.MerchantRepository;
import com.priestess.identity.repository.RolePermissionRepository;
import com.priestess.identity.repository.RoleRepository;
import com.priestess.identity.repository.UserRepository;
import com.priestess.identity.service.AuthService;
import com.priestess.identity.utility.JWTUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AuthServiceImpl — Implementasi lengkap kontrak {@link AuthService}.
 *
 * <p>Mengorkestrasi seluruh alur autentikasi: login, refresh token, registrasi
 * user/merchant, dan logout berdasarkan Dual-Token Flow SECTION 6 blueprint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository        userRepository;
    private final RoleRepository        roleRepository;
    private final MerchantRepository    merchantRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final JWTUtil               jwtUtil;
    private final PasswordEncoder       passwordEncoder;

    // =========================================================================
    // LOGIN
    // =========================================================================

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("[AuthService] Memproses login untuk username: {}", request.getUsername());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        UserEntity user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalStateException("User tidak ditemukan setelah autentikasi."));

        if ("SUSPENDED".equalsIgnoreCase(user.getStatus())) {
            log.warn("[AuthService] Login ditolak — akun SUSPENDED: {}", user.getUsername());
            throw new IllegalStateException("Akun Anda telah dibekukan!");
        }

        return buildAuthResponseAndSaveToken(user);
    }

    // =========================================================================
    // REFRESH TOKEN (SECTION 6 — Mitigasi Suspend)
    // =========================================================================

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        log.info("[AuthService] Memproses refresh token.");

        // Cari user berdasarkan refresh token yang disimpan di DB
        UserEntity user = userRepository.findByRefreshToken(request.getRefreshToken())
                .orElseThrow(() -> new IllegalStateException(
                        "Refresh token tidak valid atau sudah kedaluwarsa. Silakan login kembali."));

        // SECTION 6: Cek status saat ini — jika SUSPENDED, tolak penerbitan token baru.
        // Ini memaksa logout pada siklus refresh berikutnya meskipun access token masih hidup.
        if ("SUSPENDED".equalsIgnoreCase(user.getStatus())) {
            log.warn("[AuthService] Refresh ditolak — akun SUSPENDED: {}", user.getUsername());
            // Hapus refresh token agar tidak bisa dicoba lagi
            user.setRefreshToken(null);
            userRepository.save(user);
            throw new IllegalStateException("Akun Anda telah dibekukan!");
        }

        log.debug("[AuthService] Refresh token valid untuk userId={}", user.getId());
        return buildAuthResponseAndSaveToken(user);
    }

    // =========================================================================
    // REGISTER USER
    // =========================================================================

    @Override
    @Transactional
    public AuthResponse registerUser(RegisterUserRequest request) {
        log.info("[AuthService] Mendaftarkan user baru: {}", request.getUsername());

        validateUniqueCredentials(request.getUsername(), request.getEmail());

        RoleEntity roleUser = roleRepository.findByRoleName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException(
                        "Role ROLE_USER tidak ditemukan. Pastikan data seed sudah dijalankan."));

        UserEntity newUser = UserEntity.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(roleUser)
                .status("PENDING")
                .build();

        userRepository.save(newUser);
        log.info("[AuthService] User baru berhasil didaftarkan: {}", newUser.getUsername());

        return buildAuthResponseAndSaveToken(newUser);
    }

    // =========================================================================
    // REGISTER MERCHANT
    // =========================================================================

    @Override
    @Transactional
    public AuthResponse registerMerchant(RegisterMerchantRequest request) {
        log.info("[AuthService] Mendaftarkan merchant baru: {}", request.getUsername());

        validateUniqueCredentials(request.getUsername(), request.getEmail());

        RoleEntity roleMerchant = roleRepository.findByRoleName("ROLE_MERCHANT")
                .orElseThrow(() -> new IllegalStateException(
                        "Role ROLE_MERCHANT tidak ditemukan. Pastikan data seed sudah dijalankan."));

        // Simpan akun user terlebih dahulu
        UserEntity newUser = UserEntity.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(roleMerchant)
                .status("PENDING")
                .build();

        userRepository.save(newUser);

        // Buat entitas Merchant yang terhubung ke user ini
        MerchantEntity merchant = MerchantEntity.builder()
                .user(newUser)
                .merchantName(request.getMerchantName())
                .address(request.getAddress())
                .isVerified(false)
                .build();

        merchantRepository.save(merchant);
        log.info("[AuthService] Merchant baru berhasil didaftarkan: {} (User: {})",
                merchant.getMerchantName(), newUser.getUsername());

        return buildAuthResponseAndSaveToken(newUser);
    }

    // =========================================================================
    // LOGOUT
    // =========================================================================

    @Override
    @Transactional
    public void logout(String refreshToken) {
        log.info("[AuthService] Memproses logout.");

        userRepository.findByRefreshToken(refreshToken).ifPresentOrElse(user -> {
            user.setRefreshToken(null);
            userRepository.save(user);
            log.info("[AuthService] Logout berhasil — userId={}", user.getId());
        }, () -> log.warn("[AuthService] Logout dipanggil dengan token yang tidak ditemukan di DB."));
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Membangun {@link AuthResponse} dan menyimpan Refresh Token baru ke DB.
     * Dipanggil bersama oleh login(), refresh(), registerUser(), registerMerchant().
     */
    private AuthResponse buildAuthResponseAndSaveToken(UserEntity user) {
        List<String> permissions = rolePermissionRepository
                .findAllByRole(user.getRole())
                .stream()
                .map(rp -> rp.getMenu().getMenuName())
                .collect(Collectors.toList());

        String accessToken  = jwtUtil.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().getRoleName(), permissions);
        String refreshToken = jwtUtil.generateRefreshToken();

        user.setRefreshToken(refreshToken);
        userRepository.save(user);

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

    /**
     * Memvalidasi bahwa username dan email belum terdaftar.
     * Melempar {@link IllegalStateException} jika sudah ada.
     */
    private void validateUniqueCredentials(String username, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalStateException("Username '" + username + "' sudah terdaftar.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException("Email '" + email + "' sudah terdaftar.");
        }
    }
}
