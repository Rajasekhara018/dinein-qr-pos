package com.heuristiq.gateway.privilege;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.heuristiq.gateway.config.GatewayProperties;

/**
 * The class has a second, test-only constructor, so Spring must be told which one to use: without it the container falls
 * back to a no-argument constructor and the gateway fails to start ("No default constructor found").
 */
class RolePrivilegeCacheServiceWiringTest {

    @Test
    void springBuildsTheServiceFromItsPublicConstructorEvenWithTheTestOnlyOneAround() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(GatewayProperties.class, GatewayProperties::new);
            context.registerBean(RolePrivilegeRepository.class, () -> roleCode -> Set.of(7));
            context.register(RolePrivilegeCacheService.class);

            context.refresh();

            assertThat(context.getBean(RolePrivilegeCacheService.class).resolvePrivilegeIds("ADMIN")).containsExactly(7);
        }
    }
}
