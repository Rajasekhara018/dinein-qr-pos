package com.heuristiq.identity.application.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.heuristiq.identity.search.SearchEventPublisher;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;

@SuppressWarnings("unchecked")
class UserApplicationsIndexEnricherTest {

    private ApplicationAccessServiceUseCase access;
    private UamUserRepository users;
    private SearchEventPublisher publisher;
    private UserApplicationsIndexEnricher enricher;

    @BeforeEach
    void setUp() {
        access = mock(ApplicationAccessServiceUseCase.class);
        users = mock(UamUserRepository.class);
        publisher = mock(SearchEventPublisher.class);
        ObjectProvider<ApplicationAccessServiceUseCase> accessProvider = mock(ObjectProvider.class);
        ObjectProvider<UamUserRepository> userProvider = mock(ObjectProvider.class);
        when(accessProvider.getObject()).thenReturn(access);
        when(userProvider.getObject()).thenReturn(users);
        enricher = new UserApplicationsIndexEnricher(accessProvider, userProvider, publisher);
    }

    private UamUser user(String id, String... roles) {
        UamUser u = new UamUser();
        u.setId(id);
        u.setUserName(id);
        u.setRoles(List.of(roles));
        return u;
    }

    @Test
    void aUsersApplicationsAreIndexedAsAList() {
        UamUser u = user("u1", "CUSTOMER");
        when(access.resolveAccessibleApplications(any())).thenReturn(new ApplicationAccessServiceUseCase.ResolveOutput(List.of("MATRIMONY", "WALLET")));

        Map<String, Object> fields = enricher.enrich(u, u.getSearchIndexFields());

        assertThat(fields).containsEntry("text9", "MATRIMONY,WALLET").containsEntry("text1", "u1");
    }

    @Test
    void aUserWithoutApplicationsGetsNoField() {
        UamUser u = user("u1");
        when(access.resolveAccessibleApplications(any())).thenReturn(new ApplicationAccessServiceUseCase.ResolveOutput(List.of()));

        assertThat(enricher.enrich(u, u.getSearchIndexFields())).doesNotContainKey("text9");
    }

    @Test
    void aUserGrantRefreshesThatUsersEntryOnly() {
        UamUser u = user("u1");
        when(users.findById("u1")).thenReturn(java.util.Optional.of(u));
        when(access.resolveAccessibleApplications(any())).thenReturn(new ApplicationAccessServiceUseCase.ResolveOutput(List.of("MATRIMONY")));

        enricher.onRecordSaved(ApplicationAccess.builder().applicationCode("MATRIMONY").subjectType(AccessSubjectType.USER).subjectId("u1").build());

        verify(publisher).publishIndex(eq("USER"), eq("u1"), any());
    }

    @Test
    void aRoleGrantRefreshesEveryHolderOfTheRole() {
        UamUser holder = user("u1", "agent");
        UamUser other = user("u2", "CUSTOMER");
        when(users.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(holder, other)));
        when(access.resolveAccessibleApplications(any())).thenReturn(new ApplicationAccessServiceUseCase.ResolveOutput(List.of("MATRIMONY")));

        enricher.onRecordSaved(ApplicationAccess.builder().applicationCode("MATRIMONY").subjectType(AccessSubjectType.ROLE).subjectId("AGENT").build());

        verify(publisher).publishIndex(eq("USER"), eq("u1"), any());
        verify(publisher, never()).publishIndex(eq("USER"), eq("u2"), any());
    }
}
