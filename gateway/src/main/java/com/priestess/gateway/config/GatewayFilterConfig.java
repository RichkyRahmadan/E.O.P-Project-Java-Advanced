package com.priestess.gateway.config;

import com.priestess.gateway.filter.GatewayJwtFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * GatewayFilterConfig — Konfigurasi routing dan filter Gateway via Java DSL.
 *
 * <p>Menggunakan Spring Cloud Gateway Server MVC (WebMVC/Servlet, bukan WebFlux).
 * Dalam versi 2025.1.x, {@link HandlerFunctions#http()} tidak lagi menerima
 * argumen URI/String secara langsung. Destinasi target wajib diset melalui
 * {@link BeforeFilterFunctions#uri(String)} sebagai filter {@code before()} pada
 * builder route, sehingga target URI disuntikkan ke atribut request sebelum
 * handler {@code http()} mengeksekusinya.
 *
 * <h2>Pola Baru (Spring Cloud Gateway ≥ 4.x / 2025.1.x)</h2>
 * <pre>
 *   GatewayRouterFunctions.route("nama-route")
 *       .route(predicate, HandlerFunctions.http())
 *       .before(BeforeFilterFunctions.uri("http://host:port"))
 *       .filter(customFilter)           // opsional
 *       .build();
 * </pre>
 *
 * @see GatewayJwtFilter
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class GatewayFilterConfig {

    private final GatewayJwtFilter gatewayJwtFilter;

    @org.springframework.beans.factory.annotation.Value("${eop.services.identity}")
    private String identityUri;

    @org.springframework.beans.factory.annotation.Value("${eop.services.core}")
    private String coreUri;

    @org.springframework.beans.factory.annotation.Value("${eop.services.oracle}")
    private String oracleUri;

    // =========================================================================
    // ROUTE 1: Identity Service — PUBLIK (tanpa JWT filter)
    // =========================================================================

    /**
     * Route untuk endpoint autentikasi Identity Service.
     *
     * <p>Path {@code /api/auth/**} bersifat publik — tidak memerlukan token JWT.
     * Request diteruskan langsung ke {@code http://localhost:8081} tanpa
     * melewati {@link GatewayJwtFilter}.
     *
     * @return router function untuk Identity Service auth endpoints
     */
    @Bean
    public RouterFunction<ServerResponse> identityServiceRoute() {
        log.info("[GatewayConfig] Route Identity Service terdaftar: /api/auth/** -> http://localhost:8081");

        return GatewayRouterFunctions.route("identity-service-auth")
                .route(request -> request.path().startsWith("/api/auth/"),
                        HandlerFunctions.http())
                .before(BeforeFilterFunctions.uri(identityUri))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> identityServiceAdminRoute() {
        log.info("[GatewayConfig] Route Identity Admin terdaftar: /api/admin/** -> http://localhost:8081");

        return GatewayRouterFunctions.route("identity-service-admin")
                .route(request -> request.path().startsWith("/api/admin/"),
                        HandlerFunctions.http())
                .before(BeforeFilterFunctions.uri(identityUri))
                .filter(gatewayJwtFilter)
                .build();
    }

    // =========================================================================
    // ROUTE 2: Core Finance Service — DILINDUNGI JWT
    // =========================================================================

    /**
     * Route untuk Core Finance Service dengan proteksi {@link GatewayJwtFilter}.
     *
     * <p>Setiap request ke {@code /api/finance/**} harus membawa JWT yang valid.
     * Filter akan memvalidasi token dan menyuntikkan {@code X-User-Id},
     * {@code X-User-Role}, dan {@code X-User-Permissions} ke dalam header
     * sebelum request diteruskan ke {@code http://localhost:8082}.
     *
     * @return router function untuk Core Finance Service dengan JWT filter
     */
    @Bean
    public RouterFunction<ServerResponse> coreFinanceServiceRoute() {
        log.info("[GatewayConfig] Route Finance Service terdaftar: /api/finance/** -> http://localhost:8082");

        return GatewayRouterFunctions.route("core-finance-service")
                .route(request -> request.path().startsWith("/api/finance/"),
                        HandlerFunctions.http())
                .before(BeforeFilterFunctions.uri(coreUri))
                .filter(gatewayJwtFilter)
                .build();
    }

    // =========================================================================
    // ROUTE 3: Support & Oracle Service — DILINDUNGI JWT
    // =========================================================================

    /**
     * Route untuk Support & Oracle Service dengan proteksi {@link GatewayJwtFilter}.
     *
     * <p>Setiap request ke {@code /api/support/**} harus membawa JWT yang valid.
     * Logika filter identik dengan Finance Service — token divalidasi dan
     * header internal disuntikkan sebelum diteruskan ke {@code http://localhost:8083}.
     *
     * @return router function untuk Support & Oracle Service dengan JWT filter
     */
    @Bean
    public RouterFunction<ServerResponse> supportOracleServiceRoute() {
        log.info("[GatewayConfig] Route Support Service terdaftar: /api/support/** -> http://localhost:8083");

        return GatewayRouterFunctions.route("support-oracle-service")
                .route(request -> request.path().startsWith("/api/support/"),
                        HandlerFunctions.http())
                .before(BeforeFilterFunctions.uri(oracleUri))
                .filter(gatewayJwtFilter)
                .build();
    }
}
