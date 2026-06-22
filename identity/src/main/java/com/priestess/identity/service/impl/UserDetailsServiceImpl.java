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

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

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

        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority(userEntity.getRole().getRoleName())
        );

        log.debug("[UserDetailsService] Data pengguna berhasil dimuat: username={}, role={}",
                username, userEntity.getRole().getRoleName());

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
