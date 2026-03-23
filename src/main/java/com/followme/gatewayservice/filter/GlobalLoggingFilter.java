package com.followme.gatewayservice.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class GlobalLoggingFilter implements GlobalFilter, Ordered {

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();
    long startTime = System.currentTimeMillis();
    exchange.getAttributes().put("startTime", startTime);

    log.info("[Global Pre Filter] Request received -> URL: {}", path);

    return chain
        .filter(exchange)
        .then(
            Mono.fromRunnable(
                () -> {
                  Long start = exchange.getAttribute("startTime");
                  if (start != null) {
                    long executionTime = System.currentTimeMillis() - start;
                    log.info("[Global Post Filter] Response completed -> Execution time: {}ms", executionTime);
                  }
                }));
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
