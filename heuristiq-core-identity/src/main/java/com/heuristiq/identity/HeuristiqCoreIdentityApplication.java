package com.heuristiq.uam;

import com.heuristiq.uam.config.UamProperties;
import com.heuristiq.uam.organization.PlatformOrganizationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableConfigurationProperties({UamProperties.class, PlatformOrganizationProperties.class,
        com.heuristiq.uam.selfregister.SelfRegisterProperties.class})
@EnableAsync
public class HeuristiqCoreIdentityApplication {
    public static void main(String[] args) {
        SpringApplication.run(HeuristiqCoreIdentityApplication.class, args);
    }
}
