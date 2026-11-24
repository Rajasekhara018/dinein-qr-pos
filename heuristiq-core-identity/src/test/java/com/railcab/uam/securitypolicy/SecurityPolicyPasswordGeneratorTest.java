package com.heuristiq.uam.securitypolicy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.uam.application.access.ApplicationAccessServiceUseCase;
import com.heuristiq.uam.application.repository.ApplicationRepository;
import com.heuristiq.uam.config.UamProperties;
import com.heuristiq.uam.helper.PasswordPolicy;
import com.heuristiq.uam.securitypolicy.repository.SecurityPolicyRepository;
import com.heuristiq.uam.users.UamUser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityPolicyPasswordGeneratorTest {
    @Test void generatedPasswordsFollowStrictAndShortEffectiveLimits() {
        var repository = mock(SecurityPolicyRepository.class);
        var access = mock(ApplicationAccessServiceUseCase.class);
        when(access.resolveAccessibleApplications(any())).thenReturn(new ApplicationAccessServiceUseCase.ResolveOutput(List.of()));
        var resolver = new EffectiveSecurityPolicyResolver(repository, mock(ApplicationRepository.class), new ObjectMapper(), new UamProperties());
        var generator = new SecurityPolicyPasswordGenerator(resolver, access);
        var policy = SecurityPolicy.builder().name("org").tenantId("org").active(true)
                .passwordPolicy(PasswordPolicy.builder().minimumLength(30).maximumLength(32).build()).build();
        when(repository.findByTenantIdAndApplicationId("org", null)).thenReturn(Optional.of(policy));
        var user = UamUser.builder().userName("alice@example.com").build();
        String generated = generator.generate(user, "org", List.of("OPS"), List.of());
        assertThat(generated).hasSize(30).containsPattern("[A-Z]").containsPattern("[a-z]").containsPattern("[0-9]").containsPattern("[^a-zA-Z0-9]");
        policy.setPasswordPolicy(PasswordPolicy.builder().minimumLength(8).maximumLength(8).build());
        assertThat(generator.generate(user, "org", List.of(), List.of())).hasSize(8);
    }
}
