package com.priestess.identity.config;

import com.priestess.identity.utility.JWTFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * SecurityConfig — Pusat konfigurasi keamanan global Identity Service.
 *
 * <p>Mendefinisikan seluruh aturan keamanan HTTP dan method-level untuk
 * layanan Identity Service E.O.P. Prinsip desain utama:
 * <ul>
 *   <li><b>Stateless</b> — Tidak ada HTTP Session yang dibuat atau digunakan.
 *       Setiap request harus membawa JWT yang valid.</li>
 *   <li><b>CSRF Dimatikan</b> — Aman dilakukan karena sistem menggunakan token
 *       JWT stateless, bukan session cookie.</li>
 *   <li><b>CORS Terpusat</b> — Izin origin dikonfigurasikan di sini untuk
 *       konsistensi. Pada lingkungan produksi, origin Angular frontend wajib
 *       didaftarkan secara eksplisit.</li>
 *   <li><b>Whitelist Endpoint</b> — Hanya {@code /api/auth/**} yang terbuka
 *       untuk umum tanpa autentikasi. Semua endpoint lainnya dilindungi.</li>
 * </ul>
 *
 * <h2>Alur Request yang Dilindungi</h2>
 * <pre>
 *   HTTP Request
 *       │
 *       ▼
 *   [JWTFilter]  ← Validasi & injeksi Authentication ke SecurityContext
 *       │
 *       ▼
 *   [UsernamePasswordAuthenticationFilter]  ← Di-bypass karena stateless
 *       │
 *       ▼
 *   [SecurityFilterChain rules]  ← Periksa apakah endpoint butuh autentikasi
 *       │
 *       ▼
 *   [Controller]
 *       │
 *       ▼
 *   [@PreAuthorize]  ← Evaluasi oleh CustomPermissionEvaluator
 * </pre>
 *
 * @see JWTFilter
 * @see CustomPermissionEvaluator
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    // =========================================================================
    // DEPENDENCY
    // =========================================================================

    private final JWTFilter jwtFilter;
    private final CustomPermissionEvaluator customPermissionEvaluator;
    private final UserDetailsService userDetailsService;

    // =========================================================================
    // SECURITY FILTER CHAIN
    // =========================================================================

    /**
     * Mendefinisikan rantai filter keamanan HTTP utama untuk Identity Service.
     *
     * <p>Konfigurasi yang diterapkan:
     * <ul>
     *   <li>CSRF: <b>Dinonaktifkan</b> — sistem menggunakan JWT stateless.</li>
     *   <li>CORS: <b>Diaktifkan</b> dengan sumber konfigurasi dari {@link #corsConfigurationSource()}.</li>
     *   <li>Session: <b>STATELESS</b> — tidak ada pembuatan atau penggunaan session.</li>
     *   <li>Endpoint {@code /api/auth/**}: <b>Publik</b> — tidak memerlukan autentikasi.</li>
     *   <li>Endpoint lainnya: <b>Dilindungi</b> — memerlukan autentikasi via JWT.</li>
     *   <li>{@code JWTFilter} disisipkan <b>SEBELUM</b> {@code UsernamePasswordAuthenticationFilter}.</li>
     * </ul>
     *
     * @param http objek builder {@link HttpSecurity} yang disediakan Spring
     * @return objek {@link SecurityFilterChain} yang sudah terkonfigurasi
     * @throws Exception jika terjadi error saat konfigurasi (dari API Spring Security)
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("[SecurityConfig] Menginisialisasi SecurityFilterChain Identity Service...");

        http
            // --- Nonaktifkan CSRF (aman karena stateless JWT) ---
            .csrf(AbstractHttpConfigurer::disable)

            // --- Aktifkan CORS dengan konfigurasi kustom ---
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // --- Aturan otorisasi HTTP ---
            .authorizeHttpRequests(auth -> auth
                // Endpoint publik: registrasi, login, refresh token
                .requestMatchers("/api/auth/**").permitAll()
                // Semua endpoint lainnya memerlukan autentikasi yang valid
                .anyRequest().authenticated()
            )

            // --- Konfigurasi manajemen session: STATELESS ---
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // --- Sisipkan JWTFilter sebelum filter autentikasi bawaan Spring ---
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        log.info("[SecurityConfig] SecurityFilterChain berhasil dikonfigurasi.");
        return http.build();
    }

    // =========================================================================
    // CORS CONFIGURATION
    // =========================================================================

    /**
     * Mendefinisikan aturan CORS (Cross-Origin Resource Sharing) untuk Identity Service.
     *
     * <p>Konfigurasi ini memungkinkan Angular frontend (yang berjalan di origin berbeda)
     * untuk berkomunikasi dengan Identity Service. Pada lingkungan produksi, ganti
     * wildcard {@code "*"} dengan domain Angular yang spesifik, contoh:
     * {@code "https://eop.priestess.com"}.
     *
     * <p>Header {@code Authorization} diizinkan agar klien dapat mengirim Bearer Token.
     *
     * @return sumber konfigurasi CORS yang diregistrasikan untuk semua path ({@code /**})
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Izinkan semua origin (development mode).
        // GANTI dengan origin spesifik di produksi!
        configuration.setAllowedOriginPatterns(List.of("*"));

        // Metode HTTP yang diizinkan
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Header yang boleh disertakan dalam request dari klien
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With"
        ));

        // Izinkan klien membaca header response (termasuk Authorization jika dibutuhkan)
        configuration.setExposedHeaders(List.of("Authorization"));

        // Izinkan pengiriman credentials (diperlukan jika menggunakan cookie httpOnly)
        configuration.setAllowCredentials(true);

        // Cache preflight response selama 1 jam (3600 detik)
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // =========================================================================
    // BEAN: PASSWORD ENCODER
    // =========================================================================

    /**
     * Mendaftarkan {@link BCryptPasswordEncoder} sebagai implementasi
     * {@link PasswordEncoder} yang digunakan di seluruh aplikasi.
     *
     * <p>BCrypt dipilih karena sifatnya yang adaptif: faktor cost-nya dapat
     * ditingkatkan seiring peningkatan kemampuan hardware, menjaga keamanan
     * password tetap kuat di masa depan.
     *
     * <p>Strength default {@code 10} memberikan keseimbangan yang baik antara
     * keamanan dan performa pada hardware modern.
     *
     * @return instance {@link BCryptPasswordEncoder} dengan strength 10
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    // =========================================================================
    // BEAN: AUTHENTICATION PROVIDER
    // =========================================================================

    /**
     * Mendaftarkan {@link DaoAuthenticationProvider} yang mengintegrasikan
     * {@link UserDetailsService} dan {@link PasswordEncoder}.
     *
     * <p>Provider ini digunakan oleh {@code AuthenticationManager} untuk
     * memvalidasi kombinasi username dan password pada saat proses login.
     *
     * @return instance {@link DaoAuthenticationProvider} yang sudah dikonfigurasi
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    // =========================================================================
    // BEAN: AUTHENTICATION MANAGER
    // =========================================================================

    /**
     * Mengekspos {@link AuthenticationManager} sebagai Spring Bean.
     *
     * <p>{@code AuthenticationManager} dibutuhkan secara eksplisit oleh
     * {@code AuthServiceImpl} untuk memproses autentikasi programatik
     * (memanggil {@code authenticate(UsernamePasswordAuthenticationToken)})
     * saat pengguna melakukan login, sebelum token diterbitkan.
     *
     * @param config objek {@link AuthenticationConfiguration} yang disediakan Spring Boot
     * @return instance {@link AuthenticationManager} yang aktif
     * @throws Exception jika terjadi error saat mengambil AuthenticationManager
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    // =========================================================================
    // BEAN: METHOD SECURITY EXPRESSION HANDLER
    // =========================================================================

    /**
     * Mendaftarkan {@link DefaultMethodSecurityExpressionHandler} yang
     * menyertakan {@link CustomPermissionEvaluator} kustom.
     *
     * <p>Bean ini adalah jembatan antara anotasi {@code @PreAuthorize("hasPermission(...)")}
     * di Controller dengan logika evaluasi yang ada di {@link CustomPermissionEvaluator}.
     * Tanpa bean ini, metode {@code hasPermission()} tidak akan diproses oleh evaluator
     * kustom kita dan akan selalu mengembalikan {@code false}.
     *
     * @return handler ekspresi method security yang sudah dikonfigurasi dengan
     *         evaluator kustom E.O.P
     */
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        DefaultMethodSecurityExpressionHandler expressionHandler =
                new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setPermissionEvaluator(customPermissionEvaluator);
        log.info("[SecurityConfig] CustomPermissionEvaluator berhasil didaftarkan ke MethodSecurityExpressionHandler.");
        return expressionHandler;
    }
}
