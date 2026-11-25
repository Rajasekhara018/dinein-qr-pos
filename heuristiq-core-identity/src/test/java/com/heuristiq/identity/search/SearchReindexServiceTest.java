package com.heuristiq.identity.search;

import com.heuristiq.identity.application.client.repository.ApiClientRepository;
import com.heuristiq.identity.application.repository.ApplicationRepository;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.role.repository.UamRoleRepository;
import com.heuristiq.identity.securitypolicy.repository.SecurityPolicyRepository;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchReindexServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC);

    private UamUserRepository users;
    private UamRoleRepository roles;
    private TenantRepository tenants;
    private ApplicationRepository applications;
    private ApiClientRepository apiClients;
    private SecurityPolicyRepository securityPolicies;
    private SearchEventPublisher publisher;

    @BeforeEach
    void setUp() {
        users = mock(UamUserRepository.class);
        roles = mock(UamRoleRepository.class);
        tenants = mock(TenantRepository.class);
        applications = mock(ApplicationRepository.class);
        apiClients = mock(ApiClientRepository.class);
        securityPolicies = mock(SecurityPolicyRepository.class);
        publisher = mock(SearchEventPublisher.class);
        when(roles.findAll(any(Pageable.class))).thenAnswer(call -> new PageImpl<>(List.of(), call.getArgument(0), 0));
        when(tenants.findAll(any(Pageable.class))).thenAnswer(call -> new PageImpl<>(List.of(), call.getArgument(0), 0));
        when(applications.findAll(any(Pageable.class))).thenAnswer(call -> new PageImpl<>(List.of(), call.getArgument(0), 0));
        when(apiClients.findAll(any(Pageable.class))).thenAnswer(call -> new PageImpl<>(List.of(), call.getArgument(0), 0));
        when(securityPolicies.findAll(any(Pageable.class))).thenAnswer(call -> new PageImpl<>(List.of(), call.getArgument(0), 0));
    }

    private SearchReindexService service(int batchSize, Executor executor) {
        return new SearchReindexService(users, roles, tenants, applications, apiClients, securityPolicies,
                publisher, CLOCK, batchSize, executor);
    }

    private static UamUser user(String id) {
        UamUser user = mock(UamUser.class);
        when(user.getSearchModel()).thenReturn("USER");
        when(user.getSearchModelKey()).thenReturn(id);
        when(user.getSearchIndexFields()).thenReturn(Map.of("text1", id));
        return user;
    }

    /** Serves {@code all} in pages of the requested size. */
    private void usersAre(List<UamUser> all) {
        when(users.findAll(any(Pageable.class))).thenAnswer(call -> {
            Pageable pageable = call.getArgument(0);
            int from = (int) pageable.getOffset();
            int to = Math.min(from + pageable.getPageSize(), all.size());
            List<UamUser> slice = from >= all.size() ? List.of() : all.subList(from, to);
            Page<UamUser> page = new PageImpl<>(slice, pageable, all.size());
            return page;
        });
    }

    @Test
    void publishesEveryRecordAcrossPagesAndCountsThem() {
        usersAre(List.of(user("u1"), user("u2"), user("u3")));
        when(publisher.publishIndex(any(), any(), anyMap())).thenReturn(true);

        SearchReindexService service = service(2, Runnable::run);
        SearchReindexServiceUseCase.StartOutput out = service.start(new SearchReindexServiceUseCase.StartInput(Optional.empty()));

        assertTrue(out.started());
        SearchReindexReport report = service.status();
        assertEquals(SearchReindexReport.State.COMPLETED, report.state());
        assertEquals(new SearchReindexReport.Counts(3, 0), report.models().get("USER"));
        assertEquals(new SearchReindexReport.Counts(0, 0), report.models().get("ROLE"));
        assertEquals(CLOCK.instant(), report.startedAt());
        verify(publisher, times(3)).publishIndex(eq("USER"), any(), anyMap());
        ArgumentCaptor<Pageable> pages = ArgumentCaptor.forClass(Pageable.class);
        verify(users, times(2)).findAll(pages.capture());
        assertEquals(2, pages.getAllValues().get(0).getPageSize());
        assertEquals("id", pages.getAllValues().get(0).getSort().iterator().next().getProperty());
    }

    @Test
    void aRecordThatCannotBePublishedIsCountedAsFailedAndTheRunCarriesOn() {
        usersAre(List.of(user("u1"), user("u2")));
        when(publisher.publishIndex(any(), eq("u1"), anyMap())).thenReturn(false);
        when(publisher.publishIndex(any(), eq("u2"), anyMap())).thenReturn(true);

        SearchReindexService service = service(10, Runnable::run);
        service.start(new SearchReindexServiceUseCase.StartInput(Optional.empty()));

        assertEquals(SearchReindexReport.State.COMPLETED, service.status().state());
        assertEquals(new SearchReindexReport.Counts(1, 1), service.status().models().get("USER"));
    }

    @Test
    void aSingleModelCanBeReindexedAndOthersAreLeftAlone() {
        usersAre(List.of(user("u1")));
        when(publisher.publishIndex(any(), any(), anyMap())).thenReturn(true);

        SearchReindexService service = service(10, Runnable::run);
        service.start(new SearchReindexServiceUseCase.StartInput(Optional.of("user")));

        assertEquals(Map.of("USER", new SearchReindexReport.Counts(1, 0)), service.status().models());
        verify(roles, never()).findAll(any(Pageable.class));
    }

    @Test
    void anUnknownModelIsRejectedAndNothingStarts() {
        SearchReindexService service = service(10, Runnable::run);

        assertThrows(UamException.class, () -> service.start(new SearchReindexServiceUseCase.StartInput(Optional.of("PLANET"))));

        assertEquals(SearchReindexReport.State.IDLE, service.status().state());
    }

    @Test
    void onlyOneRunAtATime() {
        usersAre(List.of(user("u1")));
        when(publisher.publishIndex(any(), any(), anyMap())).thenReturn(true);
        List<Runnable> queued = new ArrayList<>();

        SearchReindexService service = service(10, queued::add);
        SearchReindexServiceUseCase.StartOutput first = service.start(new SearchReindexServiceUseCase.StartInput(Optional.empty()));
        SearchReindexServiceUseCase.StartOutput second = service.start(new SearchReindexServiceUseCase.StartInput(Optional.empty()));

        assertTrue(first.started());
        assertFalse(second.started());
        assertEquals(SearchReindexReport.State.RUNNING, second.report().state());
        assertEquals(1, queued.size());

        queued.get(0).run();
        assertEquals(SearchReindexReport.State.COMPLETED, service.status().state());
        assertTrue(service.start(new SearchReindexServiceUseCase.StartInput(Optional.empty())).started(), "a new run may start once the last finished");
    }

    @Test
    void aReadFailureMarksTheRunFailedAndFreesItForARetry() {
        when(users.findAll(any(Pageable.class))).thenThrow(new IllegalStateException("database down"));

        SearchReindexService service = service(10, Runnable::run);
        service.start(new SearchReindexServiceUseCase.StartInput(Optional.empty()));

        assertEquals(SearchReindexReport.State.FAILED, service.status().state());
        assertEquals("IllegalStateException", service.status().error());
        assertTrue(service.start(new SearchReindexServiceUseCase.StartInput(Optional.empty())).started());
    }
}
