package com.heuristiq.documents.document.application;

import com.heuristiq.documents.config.DmsProperties;
import com.heuristiq.documents.document.audit.DocumentReasonCodes;
import com.heuristiq.documents.document.domain.DocumentMetadata;
import com.heuristiq.documents.exception.DmsException;
import com.heuristiq.documents.security.DmsPrincipal;
import com.heuristiq.documents.security.DmsSecurityContext;
import com.heuristiq.documents.security.DmsSecurityConstants;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DocumentAccessService {

    private final DmsProperties properties;

    public DocumentAccessService(DmsProperties properties) {
        this.properties = properties;
    }

    public void assertCanUpload(String ownerId) {
        requireOrganizationForUser();
        if (!properties.getSecurity().isEnforceOwnerAccess()) {
            return;
        }
        assertOwnerOrPrivileged(ownerId);
    }

    public void assertCanAccess(DocumentMetadata metadata) {
        // The organization comes first and is not switched off with the owner check: another organization's document is
        // refused (as forbidden, never as "exists") whoever the owner or the caller's role is.
        if (!inCallersOrganization(metadata)) {
            throw DmsException.forbidden("Document access is not allowed", DocumentReasonCodes.ACCESS_DENIED);
        }
        if (!properties.getSecurity().isEnforceOwnerAccess()) {
            return;
        }
        assertOwnerOrPrivileged(metadata.getOwnerId());
    }

    /**
     * True when the caller's organization may see the document: the same organization, a trusted internal service, or a
     * document stored before organizations existed (it has none yet, so the owner rules alone apply until it is migrated).
     */
    public boolean inCallersOrganization(DocumentMetadata metadata) {
        DmsPrincipal principal = DmsSecurityContext.currentPrincipal();
        if (principal.isInternalService() || !StringUtils.hasText(metadata.getTenantId())) {
            return true;
        }
        return principal.hasTenant() && principal.tenantId().equals(metadata.getTenantId());
    }

    /** A user's upload belongs to their session's organization, so a user without one cannot store documents. */
    private void requireOrganizationForUser() {
        DmsPrincipal principal = DmsSecurityContext.currentPrincipal();
        if (principal.isInternalService() || !principal.hasIdentity()) {
            return;
        }
        if (!principal.hasTenant()) {
            throw DmsException.forbidden("Your session is not in an organization", DocumentReasonCodes.NO_ORGANIZATION);
        }
    }

    private void assertOwnerOrPrivileged(String ownerId) {
        DmsPrincipal principal = DmsSecurityContext.currentPrincipal();
        if (!principal.hasIdentity()) {
            throw DmsException.forbidden("DMS user identity is required", DocumentReasonCodes.ACCESS_DENIED);
        }
        if (principal.userId().equals(ownerId) || principal.isInternalService()
                || privilegedRoles().contains(normalize(principal.role()))) {
            return;
        }
        throw DmsException.forbidden("Document access is not allowed", DocumentReasonCodes.ACCESS_DENIED);
    }

    @SuppressWarnings("deprecation")
    private Set<String> privilegedRoles() {
        return properties.getSecurity().getPrivilegedRoles()
                .stream()
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .collect(Collectors.toSet());
    }

    private String normalize(String role) {
        return role == null ? "" : role.replaceFirst(DmsSecurityConstants.ROLE_PREFIX_REGEX, "").toUpperCase(Locale.ROOT);
    }
}
