package com.heuristiq.identity.readiness.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.heuristiq.identity.audit.AuditLog;
import com.heuristiq.identity.audit.AuditLogServiceUseCase;
import com.heuristiq.identity.enums.UamEnums.AuditAction;
import com.heuristiq.identity.enums.UamEnums.AuditStatus;
import com.heuristiq.identity.enums.UamEnums.LanguageEnum;
import com.heuristiq.identity.enums.UamEnums.UserStatus;
import com.heuristiq.identity.helper.RName;
import com.heuristiq.identity.organization.membership.TenantMembership;
import com.heuristiq.identity.organization.membership.TenantMembershipStatus;
import com.heuristiq.identity.organization.membership.repository.TenantMembershipRepository;
import com.heuristiq.identity.orgstructure.application.OrgStructureUseCase;
import com.heuristiq.identity.orgstructure.domain.Models;
import com.heuristiq.identity.readiness.application.port.AlertRecipient;
import com.heuristiq.identity.readiness.application.port.OwnerCandidate;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;
import com.heuristiq.identity.users.repository.UamUserSearchCriteria;

/** The adapters of the governance feature: recipients, owner candidates, NMS sender, audit, caller and both leases. */
class GovernanceAdaptersTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    // ---- recipients ----

    @Test
    void superAdminsAreActiveSuperAdminAccountsWithTheirLanguageMapped() {
        UamUserRepository users = mock(UamUserRepository.class);
        UamUser sa = UamUser.builder().id("sa-1").userName("root").email("root@example.org").active(true)
                .displayName("Root").preferredLanguage(LanguageEnum.KA).build();
        UamUser inactive = UamUser.builder().id("sa-2").userName("old").email("old@example.org").active(false).build();
        UamUser noName = UamUser.builder().id("sa-3").userName("nameless").email("n@example.org")
                .fullName(new RName("Jane", null, "Doe")).preferredLanguage(LanguageEnum.OD).build();
        ArgumentCaptor<UamUserSearchCriteria> criteria = ArgumentCaptor.forClass(UamUserSearchCriteria.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        when(users.search(criteria.capture(), page.capture())).thenReturn(new PageImpl<>(List.of(sa, inactive, noName)));

        List<AlertRecipient> found = new UamAlertRecipientDirectory(users, mock(TenantMembershipRepository.class)).superAdmins(5000);

        assertThat(criteria.getValue().roleCode()).isEqualTo("SUPER_ADMIN");
        assertThat(criteria.getValue().status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(page.getValue().getPageSize()).isEqualTo(200);
        assertThat(found).extracting(AlertRecipient::userId).containsExactly("sa-1", "sa-3");
        assertThat(found.get(0)).isEqualTo(new AlertRecipient("sa-1", "Root", "root@example.org", "kn"));
        assertThat(found.get(1).name()).isEqualTo("Jane Doe");
        // OD has no template language: the default (English) is used.
        assertThat(found.get(1).language()).isNull();
    }

    @Test
    void ownerTeamMembersMustBeActiveMembersOfThatOrganization() {
        UamUserRepository users = mock(UamUserRepository.class);
        TenantMembershipRepository memberships = mock(TenantMembershipRepository.class);
        UamUser member = UamUser.builder().id("u-1").userName("a").email("a@example.org").status(UserStatus.ACTIVE)
                .preferredLanguage(LanguageEnum.AR).build();
        UamUser removed = UamUser.builder().id("u-2").userName("b").email("b@example.org").status(UserStatus.ACTIVE).build();
        UamUser locked = UamUser.builder().id("u-3").userName("c").email("c@example.org").status(UserStatus.LOCKED).build();
        UamUser foreign = UamUser.builder().id("u-4").userName("d").email("d@example.org").build();
        when(users.findAllByIds(List.of("u-1", "u-2", "u-3", "u-4"))).thenReturn(List.of(member, removed, locked, foreign));
        when(memberships.findByTenantIdAndUserId("org-a", "u-1")).thenReturn(Optional.of(membership(TenantMembershipStatus.ACTIVE)));
        when(memberships.findByTenantIdAndUserId("org-a", "u-2")).thenReturn(Optional.of(membership(TenantMembershipStatus.REMOVED)));
        when(memberships.findByTenantIdAndUserId("org-a", "u-3")).thenReturn(Optional.of(membership(TenantMembershipStatus.ACTIVE)));
        when(memberships.findByTenantIdAndUserId("org-a", "u-4")).thenReturn(Optional.empty());

        List<AlertRecipient> found = new UamAlertRecipientDirectory(users, memberships)
                .activeMembers("org-a", List.of("u-1", "u-2", "u-3", "u-4"));

        assertThat(found).extracting(AlertRecipient::userId).containsExactly("u-1");
        assertThat(found.get(0).language()).isEqualTo("ar");
        assertThat(new UamAlertRecipientDirectory(users, memberships).activeMembers("org-a", List.of())).isEmpty();
        assertThat(new UamAlertRecipientDirectory(users, memberships).activeMembers("org-a", null)).isEmpty();
    }

    private static TenantMembership membership(TenantMembershipStatus status) {
        return TenantMembership.builder().status(status).build();
    }

    // ---- owner candidates ----

    @Test
    @SuppressWarnings("unchecked")
    void ownerCandidatesAreTheDepartmentHeadsOfTheOrganizationBuilder() {
        OrgStructureUseCase structure = mock(OrgStructureUseCase.class);
        when(structure.departmentHeads("org-a")).thenReturn(List.of(new Models.DepartmentHead("u1", "FIN", "Finance", "head-1", "Head One")));
        ObjectProvider<OrgStructureUseCase> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(structure);

        OrgStructureOwnerCandidateDirectory directory = new OrgStructureOwnerCandidateDirectory(provider);

        assertThat(directory.departmentHeads("org-a")).containsExactly(new OwnerCandidate("head-1", "Head One", "FIN", "Finance"));
        assertThat(directory.departmentHeads(" ")).isEmpty();
        assertThat(directory.departmentHeads(null)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void withoutTheOrganizationBuilderThereAreNoCandidates() {
        ObjectProvider<OrgStructureUseCase> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        assertThat(new OrgStructureOwnerCandidateDirectory(provider).departmentHeads("org-a")).isEmpty();
    }

    // ---- alert sender ----

    @Test
    void senderDeliversNothingBecauseNmsHasNoGoLiveTemplates() {
        boolean sent = new NoopReadinessAlertSender().send("APPLICATION_GO_LIVE_REFUSED",
                new AlertRecipient("u-1", "N", "u@example.org", "hi"), "org-a", Map.of(), "org-a:WALLET", "u-9", "alert-1");

        assertThat(sent).isFalse();
    }

    // ---- audit ----

    @Test
    void overrideIsAuditedAsAWarningWithBeforeAndAfter() {
        AuditLogServiceUseCase logs = mock(AuditLogServiceUseCase.class);
        AuditLogGovernanceAudit audit = new AuditLogGovernanceAudit(logs);

        audit.record("org-a", "sa-1", "org-a:WALLET", true, Map.of("status", "SUSPENDED"), Map.of("status", "ACTIVE"), "remark");
        audit.record("org-a", "u-2", "org-a:WALLET", false, Map.of(), Map.of("x", "y"), "refused");

        ArgumentCaptor<AuditLogServiceUseCase.RecordInput> captor = ArgumentCaptor.forClass(AuditLogServiceUseCase.RecordInput.class);
        verify(logs, org.mockito.Mockito.times(2)).record(captor.capture());
        AuditLog override = captor.getAllValues().get(0).entry();
        assertThat(override.getAction()).isEqualTo(AuditAction.EXECUTE);
        assertThat(override.getStatus()).isEqualTo(AuditStatus.WARNING);
        assertThat(override.getTenantId()).isEqualTo("org-a");
        assertThat(override.getUserId()).isEqualTo("sa-1");
        assertThat(override.getEntityType()).isEqualTo("TENANT_APPLICATION");
        assertThat(override.getEntityId()).isEqualTo("org-a:WALLET");
        assertThat(override.getOldValue()).containsEntry("status", "SUSPENDED");
        assertThat(override.getNewValue()).containsEntry("status", "ACTIVE");
        AuditLog refused = captor.getAllValues().get(1).entry();
        assertThat(refused.getAction()).isEqualTo(AuditAction.REJECT);
        assertThat(refused.getStatus()).isEqualTo(AuditStatus.FAILURE);
    }

    @Test
    void aBrokenAuditWriteNeverFailsTheAction() {
        AuditLogServiceUseCase logs = mock(AuditLogServiceUseCase.class);
        doThrow(new IllegalStateException("db down")).when(logs).record(any());

        new AuditLogGovernanceAudit(logs).record("org-a", "u", "e", true, Map.of(), Map.of(), "r");

        verify(logs).record(any());
    }

    // ---- caller ----

    @Test
    void callerIsReadFromTheSecurityContext() {
        SecurityGovernanceCaller caller = new SecurityGovernanceCaller();
        assertThat(caller.userId()).isNull();
        assertThat(caller.isSuperAdmin()).isFalse();

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("sa-1", null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
        assertThat(caller.userId()).isEqualTo("sa-1");
        assertThat(caller.isSuperAdmin()).isTrue();

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u-2", null,
                List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN"))));
        assertThat(caller.userId()).isEqualTo("u-2");
        assertThat(caller.isSuperAdmin()).isFalse();
    }

    // ---- leases ----

    @Test
    void jdbcLeaseIsTakenOnlyWhenTheUpsertTouchedOneRow() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcSchedulerLease lease = new JdbcSchedulerLease(jdbc, "holder-1");
        when(jdbc.update(anyString(), eq("job"), eq("holder-1"), anyLong())).thenReturn(1, 0);

        assertThat(lease.tryAcquire("job", Duration.ofMinutes(30))).isTrue();
        assertThat(lease.tryAcquire("job", Duration.ofMinutes(30))).isFalse();
        verify(jdbc, org.mockito.Mockito.times(2)).update(GovernanceAdapterConstants.LEASE_ACQUIRE_SQL, "job", "holder-1", 1_800_000L);
    }

    @Test
    void jdbcLeaseCountsADatabaseProblemAsNotTakenAndReleaseNeverThrows() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcSchedulerLease lease = new JdbcSchedulerLease(jdbc, "holder-1");
        when(jdbc.update(anyString(), any(), any(), anyLong())).thenThrow(new IllegalStateException("down"));
        when(jdbc.update(GovernanceAdapterConstants.LEASE_RELEASE_SQL, "job", "holder-1")).thenThrow(new IllegalStateException("down"));

        assertThat(lease.tryAcquire("job", Duration.ofMinutes(1))).isFalse();
        lease.release("job");
        assertThat(new JdbcSchedulerLease(jdbc)).isNotNull();
    }

    @Test
    void jdbcLeaseReleaseGivesItBackForThisHolder() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new JdbcSchedulerLease(jdbc, "holder-1").release("job");
        verify(jdbc).update(GovernanceAdapterConstants.LEASE_RELEASE_SQL, "job", "holder-1");
    }

    @Test
    void mongoLeaseWinsWhenTheUpsertReturnsTheDocumentAndLosesOnDuplicateKey() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC);
        MongoSchedulerLease lease = new MongoSchedulerLease(mongo, clock, "holder-1");
        when(mongo.findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(org.bson.Document.class),
                eq("scheduler_locks"))).thenReturn(new org.bson.Document("_id", "job"))
                .thenThrow(new DuplicateKeyException("held by someone else"))
                .thenThrow(new IllegalStateException("mongo down"));

        assertThat(lease.tryAcquire("job", Duration.ofMinutes(30))).isTrue();
        assertThat(lease.tryAcquire("job", Duration.ofMinutes(30))).isFalse();
        assertThat(lease.tryAcquire("job", Duration.ofMinutes(30))).isFalse();
    }

    @Test
    void mongoLeaseReleaseExpiresOnlyOurOwnLeaseAndNeverThrows() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC);
        MongoSchedulerLease lease = new MongoSchedulerLease(mongo, clock, "holder-1");

        lease.release("job");
        verify(mongo).updateFirst(any(Query.class), any(UpdateDefinition.class), eq("scheduler_locks"));

        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq("scheduler_locks"))).thenThrow(new IllegalStateException("down"));
        lease.release("job");
        assertThat(new MongoSchedulerLease(mongo, clock)).isNotNull();
    }
}
