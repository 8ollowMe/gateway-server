package com.followme.gatewayserver.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

  @Value("${cors.allowed-origins:http://localhost:8080}")
  private String allowedOrigins;

  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    http
        // [추가 1] WebFlux Security에 CORS 설정 적용
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())

        // 1. 경로별 접근 통제
        .authorizeExchange(
            exchanges ->
                exchanges
                    // [추가 2] 브라우저의 CORS Preflight (OPTIONS) 요청은 토큰 없이 무조건 통과!
                    .pathMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()

                    // 기존에 설정하신 예외 경로들
                    .pathMatchers("/api/v1/auth/**", "/api/v1/public/**", "/api/v1/users/register")
                    .permitAll()
                    .anyExchange()
                    .authenticated() // 나머지는 무조건 인증(토큰) 필요
            )

        // 2. application.yml에 적어둔 Keycloak 주소로 JWT 서명 검증 수행
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

    return http.build();
  }

  // [추가 3] 명시적인 CORS Configuration Bean 등록
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
    configuration.setAllowedMethods(
        Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(Arrays.asList("*"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration); // 모든 경로에 대해 위 CORS 설정 적용
    return source;
  }
}
