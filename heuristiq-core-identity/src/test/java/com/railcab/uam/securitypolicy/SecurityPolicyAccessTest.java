package com.heuristiq.uam.securitypolicy;

import com.heuristiq.uam.auth.MyPrivilegesServiceUseCase;
import com.heuristiq.uam.dto.MyPrivilegesResponse;
import com.heuristiq.uam.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityPolicyAccessTest {
    final MyPrivilegesServiceUseCase privileges = mock(MyPrivilegesServiceUseCase.class);
    final SecurityPolicyAccess access = new SecurityPolicyAccess(privileges);
    @AfterEach void clear() { TenantContext.clear(); SecurityContextHolder.clearContext(); }
    @Test void organizationAdminCannotModifyPlatformOrAnotherOrganization() {
        TenantContext.set("org");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN"))));
        when(privileges.forUser(any())).thenReturn(new MyPrivilegesServiceUseCase.ForUserOutput(
                new MyPrivilegesResponse(List.of(), List.of("SECURITY_POLICY_UPDATE", "SECURITY_POLICY_LIST"), List.of())));
        assertThatCode(() -> access.require("UPDATE", "org")).doesNotThrowAnyException();
        assertThat(access.listTenant(null)).isEqualTo("org");
        assertThatThrownBy(() -> access.require("UPDATE", null)).hasMessageContaining("denied");
        assertThatThrownBy(() -> access.require("UPDATE", "other")).hasMessageContaining("denied");
        assertThatThrownBy(() -> access.require("DELETE", "org")).hasMessageContaining("denied");
    }
    @Test void anonymousCallerCannotManageAnyScope() {
        assertThatThrownBy(() -> access.require("CREATE", null)).hasMessageContaining("denied");
        assertThatThrownBy(() -> access.listTenant(null)).hasMessageContaining("denied");
    }
}
