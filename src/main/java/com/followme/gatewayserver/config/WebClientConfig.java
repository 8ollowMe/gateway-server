package com.followme.gatewayserver.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

  @Bean
  @LoadBalanced // Eureka 네임서버를 통해 IP를 동적으로 찾아주는 핵심 어노테이션
  public WebClient.Builder webClientBuilder() {
    return WebClient.builder();
  }
}
