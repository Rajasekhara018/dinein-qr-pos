package com.heuristiq.documents.document.application;

import com.heuristiq.documents.config.DmsProperties;
import com.heuristiq.documents.document.domain.DocumentMetadata;
import com.heuristiq.documents.exception.DmsException;
import com.heuristiq.documents.security.DmsPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentAccessServiceTest {

    private final DmsProperties properties = new DmsProperties();
    private final DocumentAccessService service = new DocumentAccessService(properties);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsOwnerAccess() {
        authenticate("owner-1", "CUSTOMER");
        DocumentMetadata metadata = metadata("owner-1");

        assertDoesNotThrow(() -> service.assertCanAccess(metadata));
    }

    @Test
    void anAdminRoleNoLongerReachesOtherPeoplesDocuments() {
        authenticate("admin-1", "SUPER_ADMIN");
        DocumentMetadata metadata = metadata("owner-1");

        assertThrows(DmsException.class, () -> service.assertCanAccess(metadata));
    }

    @Test
    void aTrustedInternalServiceReachesAnyDocument() {
        authenticate("onboarding-service", "INTERNAL_SERVICE");
        DocumentMetadata metadata = metadata("owner-1");

        assertDoesNotThrow(() -> service.assertCanAccess(metadata));
    }

    @Test
    @SuppressWarnings("deprecation")
    void theLegacyRoleListStillWorksWhenAnOperatorSetsIt() {
        properties.getSecurity().setPrivilegedRoles(List.of("OPS_ADMIN"));
        authenticate("ops-1", "OPS_ADMIN");
        DocumentMetadata metadata = metadata("owner-1");

        assertDoesNotThrow(() -> service.assertCanAccess(metadata));
    }

    @Test
    @SuppressWarnings("deprecation")
    void thereAreNoBuiltInPrivilegedRoles() {
        org.junit.jupiter.api.Assertions.assertTrue(new DmsProperties().getSecurity().getPrivilegedRoles().isEmpty());
    }

    @Test
    void rejectsDifferentOwner() {
        authenticate("owner-2", "CUSTOMER");
        DocumentMetadata metadata = metadata("owner-1");

        assertThrows(DmsException.class, () -> service.assertCanAccess(metadata));
    }

    @Test
    void canDisableOwnerEnforcement() {
        properties.getSecurity().setEnforceOwnerAccess(false);
        DocumentMetadata metadata = metadata("owner-1");

        assertDoesNotThrow(() -> service.assertCanAccess(metadata));
    }

    private static DocumentMetadata metadata(String ownerId) {
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setOwnerId(ownerId);
        return metadata;
    }

    private static void authenticate(String userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new DmsPrincipal(userId, role),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        ));
    }
}
