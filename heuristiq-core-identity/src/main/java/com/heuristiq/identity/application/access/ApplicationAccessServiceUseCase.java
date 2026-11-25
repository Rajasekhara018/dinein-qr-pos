package com.heuristiq.identity.application.access;

import java.util.List;

import com.heuristiq.identity.users.UamUser;

public interface ApplicationAccessServiceUseCase {

    /** {@code grantedBy} is the id of the administrator making the change (taken from the token, never the body). */
    record GrantInput(GrantApplicationAccessRequest request, String grantedBy) {}
    record GrantOutput(ApplicationAccess access) {}
    /** Creates the grant, or re-activates a previously revoked one. Granting an already-active grant is a no-op. */
    GrantOutput grant(GrantInput input);

    record RevokeInput(String id, String revokedBy) {}
    record RevokeOutput(ApplicationAccess access) {}
    /** Withdraws a grant. The row is kept (marked revoked) so history stays readable. Revoking twice is a no-op. */
    RevokeOutput revoke(RevokeInput input);

    record ListForApplicationInput(String applicationCode) {}
    record ListOutput(List<ApplicationAccess> grants) {}
    ListOutput listForApplication(ListForApplicationInput input);

    record ListForSubjectInput(AccessSubjectType subjectType, String subjectId) {}
    ListOutput listForSubject(ListForSubjectInput input);

    record ResolveInput(UamUser user) {}
    record ResolveOutput(List<String> applicationCodes) {}
    /**
     * The codes (upper-case, sorted) of every active application the user may sign in to: grants made to the
     * user directly plus grants made to any role the user holds. Applications that no longer exist or are
     * switched off are left out.
     */
    ResolveOutput resolveAccessibleApplications(ResolveInput input);

    record ResolveForUserIdInput(String userId) {}
    /** Same as {@link #resolveAccessibleApplications} for a user looked up by id; fails with 404 when the user does not exist. */
    ResolveOutput resolveAccessibleApplicationsForUserId(ResolveForUserIdInput input);
}
