package com.heuristiq.documents.document.persistence.postgres;

import com.heuristiq.documents.document.persistence.DocumentSearchCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** The JPA predicate always filters on the organization, and escapes free text for LIKE. */
@SuppressWarnings({"unchecked", "rawtypes"})
class DocumentSearchSpecificationsTest {

    private final Root root = mock(Root.class);
    private final CriteriaQuery query = mock(CriteriaQuery.class);
    private final CriteriaBuilder cb = mock(CriteriaBuilder.class);
    private final Path tenantPath = mock(Path.class);
    private final Path ownerPath = mock(Path.class);
    private final Path namePath = mock(Path.class);
    private final Path checksumPath = mock(Path.class);

    private void run(DocumentSearchCriteria criteria) {
        doReturn(tenantPath).when(root).get("tenantId");
        doReturn(ownerPath).when(root).get("ownerId");
        doReturn(namePath).when(root).get("originalFileName");
        doReturn(checksumPath).when(root).get("checksumSha256");
        doReturn(mock(Path.class)).when(root).get("id");
        doReturn(mock(Path.class)).when(root).get("createdAt");
        doReturn(mock(Path.class)).when(root).get("deleted");
        doReturn(mock(Predicate.class)).when(cb).equal(any(), any());
        doReturn(mock(Predicate.class)).when(cb).and(any(Predicate[].class));
        Specification<DocumentMetadataJpaEntity> spec = DocumentSearchSpecifications.forTenant("org-a", criteria);
        spec.toPredicate(root, query, cb);
    }

    private static DocumentSearchCriteria none() {
        return new DocumentSearchCriteria(null, null, null, null, null, null, null, null, null);
    }

    @Test
    void organizationPredicateIsAlwaysAdded() {
        run(none());

        verify(cb).equal(tenantPath, "org-a");
    }

    @Test
    void organizationPredicateIsAddedEvenWithEveryFilterSet() {
        run(new DocumentSearchCriteria("x", "u1", "kyc", "T", "UPLOADED", "CLEAN", true, Instant.EPOCH, Instant.now()));

        verify(cb).equal(tenantPath, "org-a");
        verify(cb).equal(ownerPath, "u1");
    }

    @Test
    void filtersThatAreNotSetAddNoPredicate() {
        run(none());

        verify(cb, never()).equal(eq(ownerPath), anyString());
    }

    @Test
    void freeTextUsesEscapedLikeWithTheBackslashEscapeCharacter() {
        run(new DocumentSearchCriteria("50%_Off", null, null, null, null, null, null, null, null));

        verify(cb).like((jakarta.persistence.criteria.Expression<String>) any(), eq("%50\\%\\_off%"), eq('\\'));
        verify(cb).like(eq(checksumPath), eq("50\\%\\_off%"), eq('\\'));
    }
}
