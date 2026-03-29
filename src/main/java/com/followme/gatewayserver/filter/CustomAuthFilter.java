package com.followme.gatewayserver.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class CustomAuthFilter extends AbstractGatewayFilterFactory<CustomAuthFilter.Config> {

  // 인증을 면제할 공개(Public) API 경로 목록 정의 (화이트리스트)
  private static final List<String> EXCLUDE_PATHS = List.of(
      "/api/v1/users/register"
  );

  public CustomAuthFilter() {
    super(Config.class);
  }

  public static class Config {
  }

  @Override
  public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {

      String path = exchange.getRequest().getURI().getPath();
      log.info("[CustomAuth Filter] KeyCloak authentication filter started. Path: {}", path);

      boolean isExcluded = EXCLUDE_PATHS.stream().anyMatch(path::startsWith);
      if (isExcluded) {
        log.info("[CustomAuth Filter] Excluded path detected: {}. Proceeding without authentication.", path);
        return chain.filter(exchange);
      }

      String authorizationHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

      if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
        log.warn("[CustomAuth Filter] Authentication failed: Invalid token for path {}", path);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
      }

      log.info("[CustomAuth Filter] Authentication successful, proceeding to next filter.");
      return chain.filter(exchange);
    };
  }
}