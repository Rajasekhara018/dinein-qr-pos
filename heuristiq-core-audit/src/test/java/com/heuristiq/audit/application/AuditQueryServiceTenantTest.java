package com.heuristiq.audit.application;

import com.heuristiq.audit.config.AuditProperties;
import com.heuristiq.audit.domain.AuditEvent;
import com.heuristiq.audit.domain.AuditEventFilter;
import com.heuristiq.audit.domain.AuditEventRepository;
import com.heuristiq.audit.domain.AuditException;
import com.heuristiq.audit.domain.AuditFacets;
import com.heuristiq.audit.domain.AuditPayload;
import com.heuristiq.audit.domain.PageResult;
import com.heuristiq.audit.domain.TenantScope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Organization rules of the read side: scope derivation, detail and payload visibility, what reaches the port. */
class AuditQueryServiceTenantTest {

    private static final EventQuery NO_FILTER = new EventQuery(null, null, null, null, null, null, null, null, null,
            null, null, null, null);

    private final AuditEventRepository repository = mock(AuditEventRepository.class);
    private final AuditQueryService service = new AuditQueryService(repository, new AuditProperties(),
            Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC));

    private static CallerIdentity user(String tenantId, boolean allOrganizations) {
        return new CallerIdentity("u", "ADMIN", List.of("ADMIN"), tenantId, allOrganizations);
    }

    private static AuditEvent event(String tenantId) {
        return AuditEvent.builder().id(7L).eventId("evt-0000001").tenantId(tenantId).build();
    }

    @Test
    void scopeOfDistinguishesOwnOrganizationNoOrganizationAndCrossOrganizationView() {
        assertThat(AuditQueryService.scopeOf(user("org-a", false))).isEqualTo(TenantScope.of("org-a"));
        assertThat(AuditQueryService.scopeOf(user(null, false))).isEqualTo(TenantScope.of(null));
        assertThat(AuditQueryService.scopeOf(user("org-a", true))).isEqualTo(TenantScope.ALL);
        assertThat(AuditQueryService.scopeOf(null)).as("no caller never widens").isEqualTo(TenantScope.of(null));
    }

    @Test
    void detailOfAnotherOrganizationLooksNotFound() {
        when(repository.findById(7L)).thenReturn(Optional.of(event("org-b")));
        assertThatThrownBy(() -> service.get(user("org-a", false), 7L)).isInstanceOf(AuditException.class)
                .extracting(e -> ((AuditException) e).getKind()).isEqualTo(AuditException.Kind.NOT_FOUND);
    }

    @Test
    void detailOfOwnLegacyAndCrossOrganizationRecordsIsReturned() {
        when(repository.findById(7L)).thenReturn(Optional.of(event("org-a")));
        assertThat(service.get(user("org-a", false), 7L).id()).isEqualTo(7L);
        when(repository.findById(7L)).thenReturn(Optional.of(event(null)));
        assertThat(service.get(user("org-a", false), 7L).id()).isEqualTo(7L);
        assertThat(service.get(user(null, false), 7L).id()).isEqualTo(7L);
        when(repository.findById(7L)).thenReturn(Optional.of(event("org-b")));
        assertThat(service.get(user("org-a", true), 7L).id()).isEqualTo(7L);
    }

    @Test
    void aUserWithoutOrganizationCanNotReadAnOrganizationsRecord() {
        when(repository.findById(7L)).thenReturn(Optional.of(event("org-a")));
        assertThatThrownBy(() -> service.get(user(null, false), 7L)).isInstanceOf(AuditException.class);
    }

    @Test
    void payloadOfAnotherOrganizationIsNeverLoaded() {
        when(repository.findById(7L)).thenReturn(Optional.of(event("org-b")));
        assertThatThrownBy(() -> service.payload(user("org-a", false), 7L)).isInstanceOf(AuditException.class);
        verify(repository, never()).findPayload(any());
    }

    @Test
    void payloadOfOwnOrganizationIsReturned() {
        when(repository.findById(7L)).thenReturn(Optional.of(event("org-a")));
        when(repository.findPayload("evt-0000001")).thenReturn(Optional.of(AuditPayload.builder().eventId("evt-0000001").build()));
        assertThat(service.payload(user("org-a", false), 7L)).isNotNull();
    }

    @Test
    void searchAndExportPassTheCallersScopeToThePort() {
        when(repository.search(any(), anyInt(), anyInt())).thenReturn(PageResult.of(List.of(), 0, 0, 10));
        when(repository.searchForExport(any(), anyInt())).thenReturn(List.of());
        service.search(user("org-a", false), NO_FILTER, 0, 10);
        service.exportEvents(user(null, false), NO_FILTER);
        service.exportPayloads(user("org-a", true), NO_FILTER);

        ArgumentCaptor<AuditEventFilter> searched = ArgumentCaptor.forClass(AuditEventFilter.class);
        verify(repository).search(searched.capture(), anyInt(), anyInt());
        assertThat(searched.getValue().tenantScope()).isEqualTo(TenantScope.of("org-a"));
        ArgumentCaptor<AuditEventFilter> exported = ArgumentCaptor.forClass(AuditEventFilter.class);
        verify(repository, times(2)).searchForExport(exported.capture(), anyInt());
        assertThat(exported.getAllValues()).extracting(AuditEventFilter::tenantScope)
                .containsExactly(TenantScope.of(null), TenantScope.ALL);
    }

    @Test
    void facetsAndCorrelationAreScopedToo() {
        when(repository.facets(any(), anyInt(), any()))
                .thenReturn(new AuditFacets(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
        when(repository.findByCorrelationId(any(), anyInt(), any())).thenReturn(List.of());
        service.facets(user("org-a", false));
        service.byCorrelation(user(null, false), "chain-1");
        verify(repository).facets(any(), anyInt(), eq(TenantScope.of("org-a")));
        verify(repository).findByCorrelationId(eq("chain-1"), anyInt(), eq(TenantScope.of(null)));
    }

    @Test
    void subjectAndReasonFiltersAreNormalisedValidatedAndComposedWithTheTenantScope() {
        when(repository.search(any(), anyInt(), anyInt())).thenReturn(PageResult.of(List.of(), 0, 0, 10));
        EventQuery q = new EventQuery(null, null, null, null, null, null, null, null, null, null, null, null, null,
                " document ", " doc_1 ", "virus_detected");
        service.search(user("org-a", false), q, 0, 10);
        ArgumentCaptor<AuditEventFilter> seen = ArgumentCaptor.forClass(AuditEventFilter.class);
        verify(repository).search(seen.capture(), anyInt(), anyInt());
        assertThat(seen.getValue().subjectType()).isEqualTo("DOCUMENT");
        assertThat(seen.getValue().subjectId()).isEqualTo("doc_1");
        assertThat(seen.getValue().reasonCode()).isEqualTo("VIRUS_DETECTED");
        assertThat(seen.getValue().tenantScope()).as("new filters never replace the organization limit")
                .isEqualTo(TenantScope.of("org-a"));
        EventQuery bad = new EventQuery(null, null, null, null, null, null, null, null, null, null, null, null, null,
                "DOC;DROP", null, null);
        assertThatThrownBy(() -> service.search(user("org-a", false), bad, 0, 10)).isInstanceOf(AuditException.class);
        EventQuery badId = new EventQuery(null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, "{\"$ne\":1}", null);
        assertThatThrownBy(() -> service.exportEvents(user("org-a", false), badId)).isInstanceOf(AuditException.class);
        EventQuery badReason = new EventQuery(null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, "x".repeat(61));
        assertThatThrownBy(() -> service.search(user("org-a", false), badReason, 0, 10)).isInstanceOf(AuditException.class);
    }
}
