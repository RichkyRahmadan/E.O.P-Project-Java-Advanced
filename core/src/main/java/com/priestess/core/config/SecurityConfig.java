package com.priestess.core.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SecurityConfig — Konfigurasi Spring Security untuk Core Finance Service.
 *
 * <h2>Peran dalam Arsitektur E.O.P</h2>
 * <p>Core Finance Service (Port 8082) berada di belakang E.O.P Gateway (Port 8080).
 * Seluruh validasi JWT dan autentikasi <b>sudah dilakukan oleh Gateway</b> sebelum
 * request diteruskan ke sini. Gateway menyuntikkan header:
 * <ul>
 *   <li>{@code X-User-Id}          — UUID pengguna dari klaim {@code sub} JWT</li>
 *   <li>{@code X-User-Role}        — Role pengguna dari klaim {@code role} JWT</li>
 *   <li>{@code X-User-Permissions} — Klaim permission dari JWT (dipisah koma)</li>
 * </ul>
 *
 * <h2>Strategi Keamanan di Sini</h2>
 * <ol>
 *   <li><b>Nonaktifkan CSRF</b> — API stateless tidak memerlukan CSRF token.</li>
 *   <li><b>Nonaktifkan CORS bawaan</b> — Gateway sudah menangani CORS terpusat.</li>
 *   <li><b>Sesi Stateless</b> — Tidak ada HttpSession yang dibuat di service ini.</li>
 *   <li><b>Require semua request terotentikasi</b> — Karena Gateway sudah memfilter,
 *       setiap request yang masuk ke sini pasti sudah membawa header {@code X-User-Id}.
 *       SecurityConfig mengkonfigurasi agar semua endpoint memerlukan authentication,
 *       dan kelas {@link GatewayHeaderAuthFilter} mengisi SecurityContext dari header tersebut.</li>
 *   <li><b>Entry Point 401</b> — Jika ada request yang entah bagaimana berhasil
 *       melewati Gateway tanpa header (misal direct access), kembalikan 401 bukan
 *       redirect ke halaman login HTML bawaan Spring.</li>
 * </ol>
 *
 * <h2>Penting: Tidak Ada JWT Parsing di Sini</h2>
 * <p>Service ini tidak pernah mem-parse atau memvalidasi JWT secara langsung.
 * Kepercayaan diberikan sepenuhnya kepada Gateway sebagai Single Entry Point.
 * Ini adalah pola yang disebut <i>"trust the gateway"</i> dalam arsitektur microservices.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final GatewayHeaderAuthFilter gatewayHeaderAuthFilter;

    public SecurityConfig(GatewayHeaderAuthFilter gatewayHeaderAuthFilter) {
        this.gatewayHeaderAuthFilter = gatewayHeaderAuthFilter;
    }

    /**
     * Konfigurasi utama SecurityFilterChain.
     *
     * <p>{@link GatewayHeaderAuthFilter} akan dijalankan terlebih dahulu
     * (didaftarkan via {@code addFilterBefore} di kelas filter itu sendiri
     * menggunakan {@code @Component}), mengisi SecurityContext dengan objek
     * autentikasi berdasarkan header {@code X-User-Id}.
     *
     * @param http objek HttpSecurity dari Spring Security
     * @return SecurityFilterChain yang sudah dikonfigurasi
     * @throws Exception jika terjadi kesalahan konfigurasi
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Nonaktifkan CSRF — REST API stateless tidak memerlukan CSRF protection
            .csrf(AbstractHttpConfigurer::disable)

            // Nonaktifkan CORS bawaan Spring Security — Gateway sudah tangani CORS
            .cors(AbstractHttpConfigurer::disable)

            // Sesi stateless — tidak ada HttpSession yang dibuat di sini
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Konfigurasi otorisasi: semua endpoint butuh authentication
            .authorizeHttpRequests(auth -> auth
                // Endpoint untuk polling status transaksi boleh tanpa auth
                // (diperlukan oleh Angular polling yang mungkin tidak membawa header)
                .requestMatchers("/api/finance/transactions/**").permitAll()
                // Semua endpoint lain wajib terautentikasi
                .anyRequest().authenticated()
            )

            // Custom entry point: kembalikan 401 JSON (bukan redirect ke login HTML)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                        "{\"status\":401,\"message\":\"Akses ditolak: " +
                        "Request tidak memiliki identitas yang valid dari Gateway.\",\"timestamp\":\"" +
                        java.time.LocalDateTime.now() + "\"}"
                    );
                })
            )

            // Daftarkan filter kustom SEBELUM UsernamePasswordAuthenticationFilter
            // agar SecurityContext sudah terisi saat Spring Security mengecek otorisasi
            .addFilterBefore(gatewayHeaderAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
