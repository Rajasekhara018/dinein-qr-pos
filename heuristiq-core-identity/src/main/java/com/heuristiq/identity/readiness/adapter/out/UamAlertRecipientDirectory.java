package com.heuristiq.identity.readiness.adapter.out;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.util.StringUtils;

import com.heuristiq.identity.common.UamCommonConstants;
import com.heuristiq.identity.enums.UamEnums.UserStatus;
import com.heuristiq.identity.organization.membership.TenantMembershipStatus;
import com.heuristiq.identity.organization.membership.repository.TenantMembershipRepository;
import com.heuristiq.identity.readiness.application.port.AlertRecipient;
import com.heuristiq.identity.readiness.application.port.AlertRecipientDirectory;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;
import com.heuristiq.identity.users.repository.UamUserSearchCriteria;

/**
 * Finds alert recipients in UAM: ACTIVE SUPER_ADMIN accounts (one bounded page) and, for an organization's owner team, the
 * listed accounts that are ACTIVE members of that organization (a removed or foreign account is never alerted).
 */
public class UamAlertRecipientDirectory implements AlertRecipientDirectory {

    private final UamUserRepository users;
    private final TenantMembershipRepository memberships;

    public UamAlertRecipientDirectory(UamUserRepository users, TenantMembershipRepository memberships) {
        this.users = users;
        this.memberships = memberships;
    }

    @Override
    public List<AlertRecipient> superAdmins(int limit) {
        int size = Math.max(1, Math.min(limit, GovernanceAdapterConstants.SUPER_ADMIN_PAGE_MAX));
        UamUserSearchCriteria criteria = UamUserSearchCriteria.of(UamCommonConstants.SUPER_ADMIN_ROLE, UserStatus.ACTIVE, null, null);
        return users.search(criteria, PageRequest.of(0, size)).getContent().stream()
                .filter(user -> !Boolean.FALSE.equals(user.getActive()))
                .map(UamAlertRecipientDirectory::toRecipient)
                .toList();
    }

    @Override
    public List<AlertRecipient> activeMembers(String tenantId, Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return users.findAllByIds(userIds).stream()
                .filter(user -> user.getStatus() == null || user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> memberships.findByTenantIdAndUserId(tenantId, user.getId())
                        .filter(membership -> membership.getStatus() == TenantMembershipStatus.ACTIVE).isPresent())
                .map(UamAlertRecipientDirectory::toRecipient)
                .toList();
    }

    private static AlertRecipient toRecipient(UamUser user) {
        String name = StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName()
                : user.getFullName() != null ? user.getFullName().getFullName() : user.getUserName();
        String language = user.getPreferredLanguage() == null ? null
                : GovernanceAdapterConstants.LANGUAGE_BY_ACCOUNT_LANGUAGE.get(user.getPreferredLanguage().name().toUpperCase(Locale.ROOT));
        return new AlertRecipient(user.getId(), name, user.getEmail(), language);
    }
}
