package com.heuristiq.uam.securitypolicy;

import com.heuristiq.uam.auth.AuthConstants;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.security.SecurityConstants;
import com.heuristiq.uam.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.util.List;

/** Public signup gates; service-authenticated onboarding already passed its approval workflow. */
@Component
public class SecurityPolicyRegistration {
    private final EffectiveSecurityPolicyResolver resolver;
    private final HttpServletRequest request;
    public SecurityPolicyRegistration(EffectiveSecurityPolicyResolver resolver, HttpServletRequest request) {
        this.resolver = resolver; this.request = request;
    }
    public UamEnums.UserStatus initialStatus() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                .anyMatch(a -> SecurityConstants.SERVICE_AUTHORITY.equals(a.getAuthority())))
            return UamEnums.UserStatus.ACTIVE;
        var policy = requireSignup(request.getHeader(AuthConstants.APPLICATION_CODE_HEADER));
        return policy.enabled("accountPolicy.adminApprovalRequired") ? UamEnums.UserStatus.PENDING : UamEnums.UserStatus.ACTIVE;
    }
    public EffectiveSecurityPolicy requireSignup(String applicationCode) {
        var policy = resolver.forApplications(TenantContext.get(), StringUtils.hasText(applicationCode)
                ? List.of(applicationCode.trim()) : List.of());
        if (!policy.enabled("accountPolicy.selfRegistrationAllowed"))
            throw new UamException("Self registration is disabled by the security policy", HttpStatus.FORBIDDEN, "SELF_REGISTRATION_DISABLED");
        return policy;
    }
}
