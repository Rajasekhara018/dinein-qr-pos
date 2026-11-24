package com.heuristiq.uam.organization.membership;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.heuristiq.uam.users.UamUser;

public interface TenantMembershipServiceUseCase {

    /** @param search when set, only active members whose account matches it (user name, email, phone or name) */
    record ListInput(String tenantId, Pageable pageable, String search) {
        public ListInput(String tenantId, Pageable pageable) {
            this(tenantId, pageable, null);
        }
    }
    /** Members of the organization, newest first, with who they are. */
    Page<MemberView> listMembers(ListInput input);

    record AddInput(String tenantId, AddMemberRequest request, String actor) {}
    /** Makes the account a member, or turns a removed or paused membership back on. Refused when the organization is full. */
    TenantMembership addMember(AddInput input);

    record UpdateInput(String tenantId, String userId, UpdateMemberRequest request, String actor) {}
    TenantMembership updateMember(UpdateInput input);

    /** The organizations the account belongs to (removed ones left out), in the order the sign-in prefers them. */
    List<OrganizationView> organizationsOf(String userId);

    /** The ids among {@code userIds} that are ACTIVE members of the organization (at most {@value #MAX_MEMBERSHIP_CHECK} are looked at). */
    java.util.Set<String> activeMembersAmong(String tenantId, java.util.Collection<String> userIds);

    /** Most ids checked in one call. */
    int MAX_MEMBERSHIP_CHECK = 100;

    /**
     * The organization a sign-in or refresh works in, for the token's tenantId. The caller may name one (its code or id); an
     * account that is not an active member of it is refused. Otherwise the account's own organization, the default
     * organization, then the oldest membership. An account with no membership yet is given one first, so every way of
     * creating an account is covered without changing it.
     */
    String resolveForLogin(UamUser user, String requestedOrganization);

    /**
     * Makes sure a freshly provisioned account belongs to at least one organization: when it has no membership it gets one
     * in its own organization (when it names one) or the default organization. An account that already belongs somewhere is
     * left as it is. Without it a new person is missing from the organization's lists, and cannot choose an organization
     * to sign in to, until the next start of the service runs the backfill.
     *
     * @return the id of the organization the account belongs to (its first membership), or null when there is none to give
     */
    String ensureAnyMembership(UamUser user);

    /** Gives every account that has no membership one in the default organization (or its own organization, when it names one). */
    int backfillMemberships();
}
