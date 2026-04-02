package com.followme.gatewayserver.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.followme.gatewayserver.response.User;
import com.followme.gatewayserver.response.UserResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class CustomAuthFilter extends AbstractGatewayFilterFactory<CustomAuthFilter.Config> {

  private static final List<String> EXCLUDE_PATHS =
      List.of("/api/v1/users/register", "/v1/users/register", "/register");

  private final ObjectMapper objectMapper;
  private final WebClient webClient;

  public CustomAuthFilter(ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
    super(Config.class);
    this.objectMapper = objectMapper;
    // Eureka에 등록된 User Server의 이름(대소문자 주의)으로 베이스 URL 세팅
    this.webClient = webClientBuilder.baseUrl("http://user-server").build();
  }

  public static class Config {}

  @Override
  public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
      String path = exchange.getRequest().getURI().getPath();

      if (EXCLUDE_PATHS.stream().anyMatch(path::startsWith)) {
        return chain.filter(exchange);
      }

      // 외부에서 들어온 X-Header 위조(Spoofing) 방지: 모든 X-Header 제거 후, 인증된 사용자 정보로 재설정
      ServerHttpRequest secureRequest =
          exchange
              .getRequest()
              .mutate()
              .headers(
                  headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-Role");
                    headers.remove("X-Username");
                    headers.remove("X-User-Name");
                    headers.remove("X-User-Email");
                  })
              .build();

      // 안전해진 요청으로 교체
      var secureExchange = exchange.mutate().request(secureRequest).build();

      // 토큰 존재 여부 확인
      String authHeader = secureRequest.getHeaders().getFirst("Authorization");
      if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        return unauthorizedResponse(secureExchange, "Token not found.");
      }

      String token = authHeader.substring(7);
      String userId;

      try {
        // 토큰에서 UUID(sub)만 빠르게 추출
        String[] parts = token.split("\\.");
        String payload =
            new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        JsonNode jsonNode = objectMapper.readTree(payload);
        userId = jsonNode.path("sub").asText();

      } catch (Exception e) {
        return unauthorizedResponse(secureExchange, "잘못된 토큰 형식입니다.");
      }

      // WebClient로 User Server에 '진짜' 권한 상태 물어보기
      return webClient
          .get()
          .uri("/internal/v1/users/" + userId) // 내부 단일 유저 조회 API
          .retrieve()
          .bodyToMono(UserResponse.class)
          .timeout(Duration.ofMillis(500)) // 0.5초 타임아웃 (장애 전파 방지)
          .switchIfEmpty(Mono.error(new RuntimeException("EMPTY_RESPONSE")))
          .flatMap(
              response -> {
                // 유저 정보가 없거나, 성공 응답이 아니거나, 정지(DELETED 등) 상태인 경우 차단
                if (response == null || !response.success() || response.data() == null) {
                  return unauthorizedResponse(
                      secureExchange, "Server authentication failed or user not found.");
                }

                User user = response.data();

                // 상태가 APPROVED가 아니라면 접근 차단 (비즈니스 요구사항에 맞게 수정)
                if (!"APPROVED".equals(user.status())) {
                  return unauthorizedResponse(
                      secureExchange, "Server authentication failed or user not found.");
                }

                try {
                  String encodedName =
                      URLEncoder.encode(user.name(), StandardCharsets.UTF_8.toString());

                  // 최종적으로 최신 정보 기반의 X-Header 세팅
                  ServerHttpRequest authenticatedRequest =
                      secureExchange
                          .getRequest()
                          .mutate()
                          .header("X-User-Id", user.userId().toString())
                          .header("X-Role", user.role())
                          .header("X-Username", user.username())
                          .header("X-User-Name", encodedName)
                          .header("X-User-Email", user.email())
                          .headers(h -> h.remove("Authorization")) // 원본 토큰 삭제
                          .build();

                  log.info(
                      "[CustomAuth Filter] Authenticated -> User: {}, Role: {}",
                      user.username(),
                      user.role());
                  return chain.filter(
                      secureExchange.mutate().request(authenticatedRequest).build());

                } catch (Exception e) {
                  return unauthorizedResponse(secureExchange, "Header encoding error.");
                }
              })
          .onErrorResume(
              e -> {
                log.error(
                    "[CustomAuth Filter] User Server connection error (ID: {}): {}",
                    userId,
                    e.getMessage());
                return unauthorizedResponse(
                    secureExchange, "Authentication server did not respond.");
              });
    };
  }

  // 401 에러 응답을 처리하는 유틸리티 메서드
  private Mono<Void> unauthorizedResponse(
      org.springframework.web.server.ServerWebExchange exchange, String message) {
    log.warn("[CustomAuth Filter] Authentication failed: {}", message);
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    return exchange.getResponse().setComplete();
  }
}
