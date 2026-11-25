package com.heuristiq.registry;

import com.heuristiq.registry.config.CorrelationIdFilter;
import com.heuristiq.registry.config.EurekaSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** The registry application starts with its Eureka server, security configuration and request filter wired. */
@SpringBootTest
@ActiveProfiles("test")
class RailCabServiceRegistryApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void theContextStartsWithTheCorrelationFilterAndSecurityConfig() {
        assertThat(context.getBeansOfType(CorrelationIdFilter.class)).hasSize(1);
        assertThat(context.getBeansOfType(EurekaSecurityConfig.class)).hasSize(1);
    }
}
