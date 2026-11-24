package com.heuristiq.uam.securitypolicy;

import com.heuristiq.uam.helper.PasswordPolicy;
import com.heuristiq.uam.securitypolicy.repository.SecurityPolicyRepository;
import com.heuristiq.uam.users.PasswordMinimumLookup;
import com.heuristiq.uam.users.UamUserConstants;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The effective minimum password length: the organization's own security policy, else the platform-wide one, else the
 * default; never below the default, so a loose policy cannot weaken the floor.
 */
@Component
public class SecurityPolicyPasswordMinimum implements PasswordMinimumLookup {

    private final SecurityPolicyRepository policies;
    @org.springframework.beans.factory.annotation.Autowired
    private EffectiveSecurityPolicyResolver resolver;

    public SecurityPolicyPasswordMinimum(SecurityPolicyRepository policies) {
        this.policies = policies;
    }

    @Override
    public int minimumLength(String tenantId) {
        if (resolver != null) return resolver.resolve(tenantId, null).number("passwordPolicy.minimumLength");
        Optional<SecurityPolicy> policy = tenantId == null ? Optional.empty() : policies.findByTenantIdAndApplicationId(tenantId, null);
        if (policy.isEmpty()) {
            policy = policies.findByTenantIdAndApplicationId(null, null);
        }
        int configured = policy.map(SecurityPolicy::getPasswordPolicy).map(PasswordPolicy::getMinimumLength).orElse(0);
        return Math.max(UamUserConstants.DEFAULT_MIN_PASSWORD_LENGTH, configured);
    }
}
