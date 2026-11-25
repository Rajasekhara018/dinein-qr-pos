package com.heuristiq.identity.orgstructure.adapter.out;

import com.heuristiq.identity.common.UamCommonConstants;
import com.heuristiq.phone.PhoneNumbers;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.dto.UpsertUserRequest;
import com.heuristiq.identity.enums.UamEnums.UserStatus;
import com.heuristiq.identity.enums.UamEnums.UserType;
import com.heuristiq.identity.orgstructure.domain.LoginAccounts;
import com.heuristiq.identity.orgstructure.domain.OrgStructureException;
import com.heuristiq.identity.organization.membership.AddMemberRequest;
import com.heuristiq.identity.organization.membership.TenancyConstants;
import com.heuristiq.identity.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.identity.role.UamRole;
import com.heuristiq.identity.role.UamRoleServiceUseCase;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.UamUserServiceUseCase;

/**
 * {@link LoginAccounts} on top of the user and role features' own use cases, so account rules (unique email, password
 * hashing, identity-provider sync) stay in one place. A login created here has a random password nobody is shown and
 * that is marked temporary, so the person signs in with an emailed one-time code (or "forgot password").
 */
@Component
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class UamLoginAccounts implements LoginAccounts {
    @org.springframework.beans.factory.annotation.Autowired
    private com.heuristiq.identity.securitypolicy.SecurityPolicyPasswordGenerator policyPasswords;

    private final UamUserServiceUseCase users;
    private final UamRoleServiceUseCase roles;
    private final TenantMembershipServiceUseCase memberships;
    private final SecureRandom random = new SecureRandom();

    UamLoginAccounts(UamUserServiceUseCase users, UamRoleServiceUseCase roles, TenantMembershipServiceUseCase memberships) {
        this.users = users;
        this.roles = roles;
        this.memberships = memberships;
    }

    @Override
    public Set<String> roleCodes(String tenantId) {
        return roles.list(new UamRoleServiceUseCase.ListInput(tenantId)).roles().stream()
                .map(UamRole::getCode)
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    @Override
    public boolean emailInUse(String email) {
        return users.search(new UamUserServiceUseCase.SearchInput(0, 10, null, null, email, null)).users().content().stream()
                .anyMatch(user -> email.equalsIgnoreCase(user.getEmail()) || email.equalsIgnoreCase(user.getUserName()));
    }

    @Override
    public String createLogin(String tenantId, String email, String fullName, String phone, List<String> roleCodes) {
        String password = policyPasswords == null ? randomPassword() : policyPasswords.generate(
                UamUser.builder().userName(email).build(), tenantId, roleCodes, List.of());
        String mobile = phone != null && PhoneNumbers.isValid(phone) ? phone.trim() : null;
        try {
            UamUser created = users.create(new UamUserServiceUseCase.CreateInput(new UpsertUserRequest(email.toLowerCase(Locale.ROOT),
                    password, fullName, UserType.OPS, email, mobile, roleCodes, UserStatus.ACTIVE))).user();
            // Temporary: it must be changed before use, and nobody has been told it.
            users.setPassword(new UamUserServiceUseCase.SetPasswordInput(created.getId(), password, true));
            // The login belongs to the organization whose structure it was made from.
            memberships.addMember(new TenantMembershipServiceUseCase.AddInput(tenantId, new AddMemberRequest(created.getId()),
                    TenancyConstants.SYSTEM_ACTOR));
            return created.getId();
        } catch (UamException e) {
            throw OrgStructureException.conflict(e.getMessage());
        }
    }

    /** One character from each class (so any reasonable password policy is met), then random, then shuffled. */
    private String randomPassword() {
        String all = UamCommonConstants.PASSWORD_UPPER + UamCommonConstants.PASSWORD_LOWER
                + UamCommonConstants.PASSWORD_DIGITS + OrgStructureAdapterConstants.LOGIN_PASSWORD_SYMBOLS;
        StringBuilder out = new StringBuilder(OrgStructureAdapterConstants.LOGIN_PASSWORD_LENGTH);
        for (String pool : new String[] {UamCommonConstants.PASSWORD_UPPER, UamCommonConstants.PASSWORD_LOWER,
                UamCommonConstants.PASSWORD_DIGITS, OrgStructureAdapterConstants.LOGIN_PASSWORD_SYMBOLS}) {
            out.append(pool.charAt(random.nextInt(pool.length())));
        }
        while (out.length() < OrgStructureAdapterConstants.LOGIN_PASSWORD_LENGTH) {
            out.append(all.charAt(random.nextInt(all.length())));
        }
        char[] chars = out.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char swap = chars[i];
            chars[i] = chars[j];
            chars[j] = swap;
        }
        return new String(chars);
    }
}
