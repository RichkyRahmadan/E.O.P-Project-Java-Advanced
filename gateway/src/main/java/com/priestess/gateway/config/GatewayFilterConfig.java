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

    @Bean
    public RouterFunction<ServerResponse> identityServiceProtectedRoute() {
        log.info("[GatewayConfig] Route Identity Protected terdaftar: /api/identity/** -> http://localhost:8081");

        return GatewayRouterFunctions.route("identity-service-protected")
                .route(request -> request.path().startsWith("/api/identity/"),
                        HandlerFunctions.http())
                .before(BeforeFilterFunctions.uri(identityUri))
                .filter(gatewayJwtFilter)
                .build();
    }

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
