package com.heuristiq.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties
public class HeuristiqCoreGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(HeuristiqCoreGatewayApplication.class, args);
    }
}
