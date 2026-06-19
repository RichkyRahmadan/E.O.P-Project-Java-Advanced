package com.priestess.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CorsConfig — Konfigurasi CORS terpusat untuk E.O.P Gateway.
 *
 * <p>Sesuai blueprint SECTION 4, Gateway bertindak sebagai Single Entry Point
 * dan menangani CORS secara terpusat. Semua microservice internal (Identity,
 * Core, Oracle) TIDAK perlu mengonfigurasi CORS sendiri.
 *
 * <p>Menggunakan {@link CorsFilter} (Servlet filter) agar preflight OPTIONS
 * request diproses SEBELUM Spring Security dan routing filter lainnya,
 * mencegah error 403/401 pada preflight dari Angular.
 */
@Configuration
public class CorsConfig {

    /**
     * Mendaftarkan CorsFilter sebagai Servlet filter dengan prioritas tertinggi.
     * Preflight OPTIONS request dari Angular (localhost:4200) akan diizinkan
     * sebelum mencapai GatewayJwtFilter maupun SecurityConfig.
     *
     * @return CorsFilter yang dikonfigurasi untuk seluruh endpoint Gateway
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Izinkan request dari Angular dev server
        config.addAllowedOrigin("http://localhost");
        config.addAllowedOrigin("http://localhost:80");
        config.addAllowedOrigin("http://localhost:4200");

        // Izinkan semua HTTP method yang digunakan API (termasuk OPTIONS untuk preflight)
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("PATCH");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("OPTIONS");

        // Izinkan semua header request (Content-Type, Authorization, X-User-Id, dsb.)
        config.addAllowedHeader("*");

        // Izinkan browser meneruskan cookie/Authorization header
        config.setAllowCredentials(true);

        // Cache preflight response selama 1 jam (kurangi jumlah preflight request)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Terapkan ke semua endpoint Gateway
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
