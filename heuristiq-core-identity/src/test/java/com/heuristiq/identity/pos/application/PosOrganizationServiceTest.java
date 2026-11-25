package com.heuristiq.identity.pos.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.heuristiq.identity.organization.legalprofile.domain.TaxId;
import com.heuristiq.identity.organization.legalprofile.domain.TaxIdType;
import com.heuristiq.identity.pos.application.port.PosAudit;
import com.heuristiq.identity.pos.application.port.PosOrganizationDirectory;
import com.heuristiq.identity.pos.domain.Branch;
import com.heuristiq.identity.pos.domain.BranchDraft;
import com.heuristiq.identity.pos.domain.LegalEntity;
import com.heuristiq.identity.pos.domain.LegalEntityDraft;
import com.heuristiq.identity.pos.domain.PosAddress;
import com.heuristiq.identity.pos.domain.PosException;
import com.heuristiq.identity.pos.support.InMemoryPosStores;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The POS organization rules: tenant isolation, branch scoping, locks, versions and audit. */
class PosOrganizationServiceTest {

    record AuditEntry(String tenantId, String actor, String type, String entityId, Map<String, Object> before,
            Map<String, Object> after) {
    }

    private final List<AuditEntry> audit = new ArrayList<>();
    private final PosAudit recordingAudit = (t, a, type, id, before, after) -> audit.add(new AuditEntry(t, a, type, id, before, after));
    private final PosOrganizationDirectory directory = new PosOrganizationDirectory() {
        @Override
        public boolean tenantExists(String tenantId) {
            return Set.of("t1", "t2").contains(tenantId);
        }

        @Override
        public boolean isActiveMember(String tenantId, String userId) {
            return "t1".equals(tenantId) && Set.of("u-admin", "u-cashier").contains(userId);
        }
    };

    private InMemoryPosStores stores;
    private PosOrganizationService service;
    private final PosCaller admin = new PosCaller("u-admin", "t1", true);
    private final PosCaller cashier = new PosCaller("u-cashier", "t1", false);

    @BeforeEach
    void setUp() {
        stores = new InMemoryPosStores();
        service = new PosOrganizationService(directory, stores.legalEntities(), stores.branches(), stores.staff(), recordingAudit,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    private static LegalEntityDraft entityDraft(String country) {
        if ("IN".equals(country)) {
            return new LegalEntityDraft("IN", "Acme Foods Pvt Ltd", null, List.of(),
                    new PosAddress("1 MG Road", null, "Hyderabad", "Telangana", "500001", "IN"), "acme", null);
        }
        return new LegalEntityDraft("DE", "Muster GmbH", null, List.of(new TaxId(TaxIdType.VAT_DE, "DE123456789")),
                new PosAddress("Hauptstr. 1", null, "Berlin", null, "10115", "DE"), "ber", null);
    }

    private static BranchDraft branchDraft(String legalEntityId, String code) {
        return new BranchDraft(legalEntityId, code, "Main", "Asia/Kolkata", null, null, null, null,
                new PosAddress("2 Park St", null, "Hyderabad", "Telangana", "500002", "IN"));
    }

    private static void failsWith(ThrowingCallable call, PosException.Kind kind, String key, String field) {
        assertThatThrownBy(call).isInstanceOf(PosException.class).satisfies(e -> {
            PosException p = (PosException) e;
            assertThat(p.kind()).isEqualTo(kind);
            assertThat(p.getMessage()).isEqualTo(key);
            assertThat(p.field()).isEqualTo(field);
        });
    }

    private LegalEntity inEntity() {
        return service.createLegalEntity(admin, "t1", entityDraft("IN"));
    }

    @Test
    void createsLegalEntityAndBranchWithVersionOne() {
        LegalEntity entity = inEntity();
        assertThat(entity.version()).isEqualTo(1L);
        assertThat(entity.currency()).isEqualTo("INR");
        assertThat(entity.id()).isNotBlank();
        Branch branch = service.createBranch(admin, "t1", branchDraft(entity.id(), "hyd-01"));
        assertThat(branch.version()).isEqualTo(1L);
        assertThat(branch.code()).isEqualTo("HYD-01");
        assertThat(service.branch(admin, "t1", branch.id())).isEqualTo(branch);
        assertThat(service.legalEntities(admin, "t1")).containsExactly(entity);
    }

    @Test
    void countryCannotChangeOnceBranchesExist() {
        LegalEntity entity = inEntity();
        service.updateLegalEntity(admin, "t1", entity.id(), entityDraft("IN"), 1L);
        service.createBranch(admin, "t1", branchDraft(entity.id(), "HYD-01"));
        failsWith(() -> service.updateLegalEntity(admin, "t1", entity.id(), entityDraft("DE"), 2L), PosException.Kind.CONFLICT,
                "pos.legalEntity.countryLocked", "country");
    }

    @Test
    void staleBranchUpdateIsConflict() {
        LegalEntity entity = inEntity();
        Branch branch = service.createBranch(admin, "t1", branchDraft(entity.id(), "HYD-01"));
        Branch updated = service.updateBranch(admin, "t1", branch.id(), branchDraft(entity.id(), "HYD-01"), 1L);
        assertThat(updated.version()).isEqualTo(2L);
        failsWith(() -> service.updateBranch(admin, "t1", branch.id(), branchDraft(entity.id(), "HYD-01"), 0L),
                PosException.Kind.CONFLICT, "pos.staleVersion", "version");
    }

    @Test
    void duplicateBranchCodeIsConflict() {
        LegalEntity entity = inEntity();
        service.createBranch(admin, "t1", branchDraft(entity.id(), "HYD-01"));
        failsWith(() -> service.createBranch(admin, "t1", branchDraft(entity.id(), "HYD-01")), PosException.Kind.CONFLICT,
                "pos.branch.codeTaken", "code");
        PosCaller other = new PosCaller("x", "t2", true);
        LegalEntity otherEntity = service.createLegalEntity(other, "t2", entityDraft("IN"));
        assertThat(service.createBranch(other, "t2", branchDraft(otherEntity.id(), "HYD-01")).code()).isEqualTo("HYD-01");
    }

    @Test
    void branchScopedCallerSeesOnlyAssignedBranches() {
        LegalEntity entity = inEntity();
        Branch a = service.createBranch(admin, "t1", branchDraft(entity.id(), "BR-A"));
        Branch b = service.createBranch(admin, "t1", branchDraft(entity.id(), "BR-B"));
        service.replaceBranchStaff(admin, "t1", a.id(), Set.of("u-cashier"));
        assertThat(service.branches(cashier, "t1")).containsExactly(a);
        assertThat(service.branches(admin, "t1")).hasSize(2);
        assertThat(service.branch(cashier, "t1", a.id())).isEqualTo(a);
        failsWith(() -> service.branch(cashier, "t1", b.id()), PosException.Kind.NOT_FOUND, "pos.branch.notFound", null);
        failsWith(() -> service.branchStaff(cashier, "t1", b.id()), PosException.Kind.NOT_FOUND, "pos.branch.notFound", null);
        failsWith(() -> service.replaceBranchStaff(cashier, "t1", b.id(), Set.of()), PosException.Kind.NOT_FOUND,
                "pos.branch.notFound", null);
    }

    @Test
    void branchScopedCallerCannotCreateBranches() {
        LegalEntity entity = inEntity();
        failsWith(() -> service.createBranch(cashier, "t1", branchDraft(entity.id(), "HYD-01")), PosException.Kind.NOT_FOUND,
                "pos.organization.notFound", null);
        failsWith(() -> service.createLegalEntity(cashier, "t1", entityDraft("IN")), PosException.Kind.NOT_FOUND,
                "pos.organization.notFound", null);
    }

    @Test
    void callerOfAnotherTenantGetsNotFound() {
        PosCaller other = new PosCaller("x", "t2", true);
        failsWith(() -> service.legalEntities(other, "t1"), PosException.Kind.NOT_FOUND, "pos.organization.notFound", null);
        failsWith(() -> service.branches(other, "t1"), PosException.Kind.NOT_FOUND, "pos.organization.notFound", null);
        failsWith(() -> service.branches(admin, "nope"), PosException.Kind.NOT_FOUND, "pos.organization.notFound", null);
    }

    @Test
    void staffMustBeMembersOfTheTenant() {
        LegalEntity entity = inEntity();
        Branch a = service.createBranch(admin, "t1", branchDraft(entity.id(), "BR-A"));
        failsWith(() -> service.replaceBranchStaff(admin, "t1", a.id(), Set.of("u-cashier", "u-stranger")),
                PosException.Kind.INVALID, "pos.staff.notMember", "userIds[u-stranger]");
    }

    @Test
    void branchIdsOfListsAssignments() {
        LegalEntity entity = inEntity();
        Branch a = service.createBranch(admin, "t1", branchDraft(entity.id(), "BR-A"));
        Branch b = service.createBranch(admin, "t1", branchDraft(entity.id(), "BR-B"));
        service.replaceBranchStaff(admin, "t1", a.id(), Set.of("u-cashier"));
        service.replaceBranchStaff(admin, "t1", b.id(), Set.of("u-cashier"));
        assertThat(service.branchIdsOf("t1", "u-cashier")).containsExactlyInAnyOrder(a.id(), b.id());
        assertThat(service.branchStaff(admin, "t1", a.id())).containsExactly("u-cashier");
    }

    @Test
    void branchOwnerMustExistAndCannotChange() {
        LegalEntity entity = inEntity();
        failsWith(() -> service.createBranch(admin, "t1", branchDraft("missing", "HYD-01")), PosException.Kind.INVALID,
                "pos.legalEntity.notFound", "legalEntityId");
        Branch branch = service.createBranch(admin, "t1", branchDraft(entity.id(), "HYD-01"));
        LegalEntity second = inEntity();
        failsWith(() -> service.updateBranch(admin, "t1", branch.id(), branchDraft(second.id(), "HYD-01"), 1L),
                PosException.Kind.CONFLICT, "pos.branch.legalEntityLocked", "legalEntityId");
    }

    @Test
    void everyChangeIsAudited() {
        LegalEntity entity = inEntity();
        Branch a = service.createBranch(admin, "t1", branchDraft(entity.id(), "BR-A"));
        service.replaceBranchStaff(admin, "t1", a.id(), Set.of("u-cashier"));
        assertThat(audit).extracting(AuditEntry::type).containsExactly("POS_LEGAL_ENTITY", "POS_BRANCH", "POS_BRANCH_STAFF");
        assertThat(audit).allSatisfy(e -> {
            assertThat(e.tenantId()).isEqualTo("t1");
            assertThat(e.actor()).isEqualTo("u-admin");
        });
        assertThat(audit.get(0).before()).isNull();
        assertThat(audit.get(0).after()).isNotNull();
    }
}
