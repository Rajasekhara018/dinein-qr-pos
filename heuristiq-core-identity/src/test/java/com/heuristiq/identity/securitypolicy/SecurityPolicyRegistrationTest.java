package com.heuristiq.identity.securitypolicy;

import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityPolicyRegistrationTest {
    final EffectiveSecurityPolicyResolver resolver = mock(EffectiveSecurityPolicyResolver.class);
    final SecurityPolicyRegistration registration = new SecurityPolicyRegistration(resolver, mock(HttpServletRequest.class));
    @AfterEach void clear() { TenantContext.clear(); SecurityContextHolder.clearContext(); }
    void policy(boolean allowed, boolean approval) {
        when(resolver.forApplications(null, List.of())).thenReturn(new EffectiveSecurityPolicy(null, List.of(), "v", Map.of(
                "accountPolicy.selfRegistrationAllowed", new EffectiveSecurityPolicy.Setting(allowed, "ALLOW", List.of()),
                "accountPolicy.adminApprovalRequired", new EffectiveSecurityPolicy.Setting(approval, "REQUIRE", List.of())), List.of()));
    }
    @Test void disabledSignupCannotCreateAnActiveIdentity() {
        policy(false, false);
        assertThatThrownBy(registration::initialStatus).hasMessageContaining("Self registration is disabled");
    }
    @Test void approvalRequiredCreatesAPendingIdentity() {
        policy(true, true);
        assertThat(registration.initialStatus()).isEqualTo(UamEnums.UserStatus.PENDING);
        policy(true, false);
        assertThat(registration.initialStatus()).isEqualTo(UamEnums.UserStatus.ACTIVE);
    }
}
