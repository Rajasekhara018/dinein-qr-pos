package com.heuristiq.identity.securitypolicy;

import com.heuristiq.identity.application.access.ApplicationAccessServiceUseCase;
import com.heuristiq.identity.common.UamCommonConstants;
import com.heuristiq.identity.users.UamUser;
import org.springframework.stereotype.Component;
import java.security.SecureRandom;
import java.util.*;

/** Generated onboarding passwords obey the same effective limits as manually chosen ones. */
@Component
public class SecurityPolicyPasswordGenerator {
    private final EffectiveSecurityPolicyResolver resolver;
    private final ApplicationAccessServiceUseCase applications;
    private final SecureRandom random = new SecureRandom();
    public SecurityPolicyPasswordGenerator(EffectiveSecurityPolicyResolver resolver, ApplicationAccessServiceUseCase applications) {
        this.resolver=resolver; this.applications=applications;
    }
    public String generate(UamUser user, String tenantId, List<String> roles, List<String> additionalApplications) {
        var holder = UamUser.builder().id(user.getId()).tenantId(tenantId).roles(roles).build();
        Set<String> codes = new TreeSet<>(applications.resolveAccessibleApplications(new ApplicationAccessServiceUseCase.ResolveInput(holder)).applicationCodes());
        codes.addAll(additionalApplications);
        var policy = resolver.forApplications(tenantId, List.copyOf(codes));
        int length = Math.min(policy.number("passwordPolicy.maximumLength"),
                Math.max(UamCommonConstants.TEMPORARY_PASSWORD_LENGTH, policy.number("passwordPolicy.minimumLength")));
        String[] pools = { UamCommonConstants.PASSWORD_UPPER, UamCommonConstants.PASSWORD_LOWER,
                UamCommonConstants.PASSWORD_DIGITS, UamCommonConstants.PASSWORD_MESSAGE_SAFE_SYMBOLS };
        String all = String.join("", pools);
        while (true) {
            StringBuilder out = new StringBuilder(length);
            for (String pool : pools) out.append(pool.charAt(random.nextInt(pool.length())));
            while (out.length() < length) out.append(all.charAt(random.nextInt(all.length())));
            char[] value = out.toString().toCharArray();
            for (int i=value.length-1; i>0; i--) { int j=random.nextInt(i+1); char swap=value[i]; value[i]=value[j]; value[j]=swap; }
            String password = new String(value);
            if (!policy.enabled("passwordPolicy.preventUsernameInPassword") || user.getUserName() == null || user.getUserName().isBlank()
                    || !password.toLowerCase(Locale.ROOT).contains(user.getUserName().toLowerCase(Locale.ROOT))) return password;
        }
    }
}
