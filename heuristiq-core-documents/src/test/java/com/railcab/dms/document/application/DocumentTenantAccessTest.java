package com.railcab.dms.document.application;

import com.railcab.dms.config.DmsProperties;
import com.railcab.dms.document.domain.DocumentMetadata;
import com.railcab.dms.exception.DmsException;
import com.railcab.dms.security.DmsPrincipal;
import com.railcab.dms.security.DmsSecurityConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A document belongs to the organization of whoever uploaded it, and only that organization (or the platform) can reach it. */
class DocumentTenantAccessTest {

    private final DmsProperties properties = new DmsProperties();
    private final DocumentAccessService service = new DocumentAccessService(properties);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(String userId, String role, String tenantId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new DmsPrincipal(userId, role, tenantId), null, List.of()));
    }

    private static DocumentMetadata document(String ownerId, String tenantId) {
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setOwnerId(ownerId);
        metadata.setTenantId(tenantId);
        return metadata;
    }

    @Test
    void theOwnerReachesTheirDocumentInTheirOwnOrganization() {
        authenticate("owner-1", "CUSTOMER", "org-a");

        assertDoesNotThrow(() -> service.assertCanAccess(document("owner-1", "org-a")));
    }

    @Test
    void aDocumentOfAnotherOrganizationIsRefusedEvenForTheSameUserIdOrAPrivilegedRole() {
        authenticate("owner-1", "CUSTOMER", "org-a");
        assertThrows(DmsException.class, () -> service.assertCanAccess(document("owner-1", "org-b")));

        authenticate("admin-1", "SUPER_ADMIN", "org-a");
        assertThrows(DmsException.class, () -> service.assertCanAccess(document("owner-1", "org-b")));
    }

    @Test
    void theOrganizationCheckStaysOnWhenTheOwnerCheckIsSwitchedOff() {
        properties.getSecurity().setEnforceOwnerAccess(false);
        authenticate("someone", "CUSTOMER", "org-a");

        assertThrows(DmsException.class, () -> service.assertCanAccess(document("owner-1", "org-b")));
        assertDoesNotThrow(() -> service.assertCanAccess(document("owner-1", "org-a")));
    }

    @Test
    void aTrustedInternalServiceIsNotLimitedToOneOrganization() {
        authenticate("gateway", DmsSecurityConstants.INTERNAL_SERVICE_ROLE, null);

        assertDoesNotThrow(() -> service.assertCanAccess(document("owner-1", "org-b")));
        assertDoesNotThrow(() -> service.assertCanUpload("owner-1"));
    }

    @Test
    void aDocumentStoredBeforeOrganizationsFallsBackToTheOwnerRules() {
        authenticate("owner-1", "CUSTOMER", "org-a");

        assertThat(service.inCallersOrganization(document("owner-1", null))).isTrue();
        assertDoesNotThrow(() -> service.assertCanAccess(document("owner-1", null)));
        authenticate("owner-2", "CUSTOMER", "org-a");
        assertThrows(DmsException.class, () -> service.assertCanAccess(document("owner-1", null)));
    }

    @Test
    void aUserWhoseSessionHasNoOrganizationCannotUpload() {
        authenticate("owner-1", "CUSTOMER", null);

        assertThrows(DmsException.class, () -> service.assertCanUpload("owner-1"));
    }

    @Test
    void theOrganizationIsWhatSearchIndexesAsTheOwnerKeyAndTheUserStaysInText3() {
        DocumentMetadata metadata = document("owner-1", "org-a");

        assertThat(metadata.getSearchIndexFields()).containsEntry("ownerKey", "org-a").containsEntry("text3", "owner-1");
        assertThat(document("owner-1", null).getSearchIndexFields()).doesNotContainKey("ownerKey");
    }
}
