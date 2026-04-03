package com.followme.gatewayserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(csrf -> csrf.disable())
            
            // 1. 경로별 접근 통제
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/api/v1/auth/**", "/api/v1/public/**", "/api/v1/users/register").permitAll()
                .anyExchange().authenticated() // 나머지는 무조건 인증(토큰) 필요
            )
            
            // 2. application.yml에 적어둔 Keycloak 주소로 JWT 서명 검증 수행
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}