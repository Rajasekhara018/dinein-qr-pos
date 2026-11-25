package com.heuristiq.identity.securitypolicy;

import com.heuristiq.identity.auth.MyPrivilegesServiceUseCase;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.security.TenantContext;
import com.heuristiq.identity.security.TenantGuard;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Independent of the gateway: direct UAM calls must obey the same tenant and privilege boundaries. */
@Component
public class SecurityPolicyAccess {
    private final MyPrivilegesServiceUseCase privileges;
    public SecurityPolicyAccess(MyPrivilegesServiceUseCase privileges) { this.privileges = privileges; }

    public void require(String action, String tenantId) {
        if (TenantGuard.isSuperAdmin()) return;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!TenantGuard.isSignedInUser() || !StringUtils.hasText(tenantId)
                || !tenantId.equals(TenantContext.get())) throw denied();
        var roles = auth.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        var resolved = privileges.forUser(new MyPrivilegesServiceUseCase.ForUserInput(auth.getName(), roles)).privileges();
        if (resolved == null || resolved.permissions() == null
                || !resolved.permissions().contains("SECURITY_POLICY_" + action)) throw denied();
    }

    public String listTenant(String requestedTenant) {
        if (TenantGuard.isSuperAdmin()) return requestedTenant;
        String tenant = TenantContext.get();
        require("LIST", tenant);
        if (StringUtils.hasText(requestedTenant) && !requestedTenant.equals(tenant)) throw denied();
        return tenant;
    }

    private static UamException denied() { return new UamException("Security policy access denied", HttpStatus.FORBIDDEN); }
}
