package com.enterprise.knowledge.mcp.config;

import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
public class BackendClientsConfig {

    /** Plain builder for everything else (kept primary so infrastructure clients are not load-balanced). */
    @Bean
    @Primary
    public RestClient.Builder restClientBuilder(RestClientBuilderConfigurer configurer) {
        return configurer.configure(RestClient.builder());
    }

    /** Resolves {@code http://SERVICE-NAME} through Eureka. */
    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient documentServiceClient(@LoadBalanced RestClient.Builder loadBalancedRestClientBuilder) {
        return loadBalancedRestClientBuilder.clone().baseUrl("http://DOCUMENT-SERVICE").build();
    }

    @Bean
    public RestClient userServiceClient(@LoadBalanced RestClient.Builder loadBalancedRestClientBuilder) {
        return loadBalancedRestClientBuilder.clone().baseUrl("http://USER-SERVICE").build();
    }
}
