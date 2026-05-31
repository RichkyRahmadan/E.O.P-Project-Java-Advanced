package com.priestess.identity.service.impl;

import com.priestess.identity.entity.UserEntity;
import com.priestess.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * UserDetailsServiceImpl — Implementasi {@link UserDetailsService} untuk Spring Security.
 *
 * <p>Kelas ini adalah jembatan antara Spring Security Authentication Framework
 * dengan database pengguna E.O.P. Spring Security memanggil method
 * {@link #loadUserByUsername(String)} secara otomatis di dalam proses
 * {@code AuthenticationManager.authenticate()} untuk:
 * <ol>
 *   <li>Memuat data pengguna dari database berdasarkan username.</li>
 *   <li>Mengembalikan objek {@link UserDetails} yang berisi hashed password,
 *       authorities, dan flag status akun.</li>
 *   <li>{@code DaoAuthenticationProvider} kemudian memverifikasi password
 *       yang diterima dari klien terhadap hash yang tersimpan via BCrypt.</li>
 * </ol>
 *
 * <p>Bean ini diinjeksi oleh {@code SecurityConfig} ke dalam
 * {@code DaoAuthenticationProvider} sebagai sumber data autentikasi.
 *
 * @see com.priestess.identity.config.SecurityConfig
 * @see com.priestess.identity.service.impl.AuthServiceImpl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Memuat data pengguna berdasarkan username untuk keperluan autentikasi.
     *
     * <p>Dipanggil secara internal oleh Spring Security — BUKAN oleh kode aplikasi
     * secara langsung. Method ini mengembalikan objek {@link UserDetails} yang
     * hanya berisi data minimal yang dibutuhkan oleh framework untuk proses
     * verifikasi credential.
     *
     * <p>Status {@code PENDING} dan {@code SUSPENDED} tidak langsung memblokir
     * di sini — pengecekan status bisnis dilakukan di {@code AuthServiceImpl}
     * setelah autentikasi berhasil, agar pesan error dapat dikontrol secara tepat.
     *
     * @param username nama pengguna yang akan dimuat
     * @return objek {@link UserDetails} yang siap digunakan oleh Spring Security
     * @throws UsernameNotFoundException jika username tidak ditemukan di database;
     *         Spring Security akan mengubah ini menjadi {@code BadCredentialsException}
     *         agar tidak terjadi user enumeration attack
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("[UserDetailsService] Memuat data pengguna untuk username: {}", username);

        UserEntity userEntity = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("[UserDetailsService] Username tidak ditemukan: {}", username);
                    return new UsernameNotFoundException(
                            "Pengguna dengan username '" + username + "' tidak ditemukan."
                    );
                });

        // Bangun GrantedAuthority dari nama role pengguna
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority(userEntity.getRole().getRoleName())
        );

        log.debug("[UserDetailsService] Data pengguna berhasil dimuat: username={}, role={}",
                username, userEntity.getRole().getRoleName());

        // Kembalikan UserDetails bawaan Spring Security.
        // Password yang dikembalikan adalah BCrypt hash dari database —
        // DaoAuthenticationProvider akan memverifikasi password plain-text
        // dari request terhadap hash ini secara otomatis.
        return User.builder()
                .username(userEntity.getUsername())
                .password(userEntity.getPassword())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }
}
