package com.followme.gatewayserver.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CustomAuthFilter extends AbstractGatewayFilterFactory<CustomAuthFilter.Config> {

  public CustomAuthFilter() {
    super(Config.class);
  }

  public static class Config {
    // YML에서 전달받을 설정값이 있다면 여기에 추가
  }

  @Override
  public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
      log.info("[CustomAuth Filter] KeyCloak authentication filter started.");

      // 요청 헤더에서 Authorization 추출
      String authorizationHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

      // 임시 로직: 헤더가 없거나 Bearer로 시작하지 않으면 거부 (401 Unauthorized)
      if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
        log.warn("[CustomAuth Filter] Authentication failed: Invalid token.");
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
      }

      log.info("[CustomAuth Filter] Authentication successful, proceeding to next filter.");
      return chain.filter(exchange);
    };
  }
}
