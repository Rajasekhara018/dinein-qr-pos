package com.heuristiq.identity.pos.application;

import static com.heuristiq.identity.pos.domain.PosConstants.*;

import com.heuristiq.identity.pos.application.port.BranchStaffStore;
import com.heuristiq.identity.pos.application.port.BranchStore;
import com.heuristiq.identity.pos.application.port.LegalEntityStore;
import com.heuristiq.identity.pos.application.port.PosAudit;
import com.heuristiq.identity.pos.application.port.PosOrganizationDirectory;
import com.heuristiq.identity.pos.domain.Branch;
import com.heuristiq.identity.pos.domain.BranchDraft;
import com.heuristiq.identity.pos.domain.LegalEntity;
import com.heuristiq.identity.pos.domain.LegalEntityDraft;
import com.heuristiq.identity.pos.domain.PosException;
import com.heuristiq.identity.pos.domain.PosStatus;
import com.heuristiq.identity.pos.domain.PosValidator;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Enforces tenant isolation, branch scoping, locks and auditing around the POS organization stores. */
public class PosOrganizationService implements PosOrganizationUseCase {

    private static final String ENTITY_LEGAL_ENTITY = "POS_LEGAL_ENTITY";
    private static final String ENTITY_BRANCH = "POS_BRANCH";
    private static final String ENTITY_BRANCH_STAFF = "POS_BRANCH_STAFF";

    private final PosOrganizationDirectory directory;
    private final LegalEntityStore legalEntities;
    private final BranchStore branches;
    private final BranchStaffStore staff;
    private final PosAudit audit;
    private final Clock clock;

    public PosOrganizationService(PosOrganizationDirectory directory, LegalEntityStore legalEntities, BranchStore branches,
            BranchStaffStore staff, PosAudit audit, Clock clock) {
        this.directory = directory;
        this.legalEntities = legalEntities;
        this.branches = branches;
        this.staff = staff;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public List<LegalEntity> legalEntities(PosCaller caller, String tenantId) {
        requireTenant(caller, tenantId);
        return legalEntities.list(tenantId);
    }

    @Override
    public LegalEntity legalEntity(PosCaller caller, String tenantId, String id) {
        requireTenant(caller, tenantId);
        return legalEntities.find(tenantId, id).orElseThrow(() -> PosException.notFound(ERROR_LEGAL_ENTITY_NOT_FOUND));
    }

    @Override
    public LegalEntity createLegalEntity(PosCaller caller, String tenantId, LegalEntityDraft draft) {
        requireTenantWide(caller, tenantId);
        LegalEntityDraft d = PosValidator.legalEntity(draft);
        LegalEntity created = legalEntities.insert(new LegalEntity(UUID.randomUUID().toString(), tenantId, d.country(),
                d.legalName(), d.currency(), d.taxIds(), d.address(), d.invoicePrefix(), d.financialYearStartMonth(),
                PosStatus.ACTIVE, 1L, now(), caller.userId()));
        audit.changed(tenantId, caller.userId(), ENTITY_LEGAL_ENTITY, created.id(), null, snapshot(created));
        return created;
    }

    @Override
    public LegalEntity updateLegalEntity(PosCaller caller, String tenantId, String id, LegalEntityDraft draft,
            long expectedVersion) {
        requireTenantWide(caller, tenantId);
        LegalEntity current = legalEntities.find(tenantId, id)
                .orElseThrow(() -> PosException.notFound(ERROR_LEGAL_ENTITY_NOT_FOUND));
        LegalEntityDraft d = PosValidator.legalEntity(draft);
        if (!d.country().equals(current.country()) && branches.anyForLegalEntity(tenantId, id)) {
            throw PosException.conflict(ERROR_COUNTRY_LOCKED, FIELD_COUNTRY);
        }
        LegalEntity updated = legalEntities.update(new LegalEntity(id, tenantId, d.country(), d.legalName(), d.currency(),
                d.taxIds(), d.address(), d.invoicePrefix(), d.financialYearStartMonth(), current.status(), expectedVersion + 1,
                now(), caller.userId()), expectedVersion);
        audit.changed(tenantId, caller.userId(), ENTITY_LEGAL_ENTITY, id, snapshot(current), snapshot(updated));
        return updated;
    }

    @Override
    public List<Branch> branches(PosCaller caller, String tenantId) {
        requireTenant(caller, tenantId);
        List<Branch> all = branches.list(tenantId);
        if (caller.tenantWide()) {
            return all;
        }
        List<String> assigned = staff.branchIdsOf(tenantId, caller.userId());
        return all.stream().filter(b -> assigned.contains(b.id())).toList();
    }

    @Override
    public Branch branch(PosCaller caller, String tenantId, String id) {
        return visibleBranch(caller, tenantId, id);
    }

    @Override
    public Branch createBranch(PosCaller caller, String tenantId, BranchDraft draft) {
        requireTenantWide(caller, tenantId);
        LegalEntity owner = owner(tenantId, draft.legalEntityId());
        BranchDraft d = PosValidator.branch(draft, owner);
        Branch created = branches.insert(new Branch(UUID.randomUUID().toString(), tenantId, owner.id(), d.code(), d.name(),
                d.timeZone(), d.locale(), d.languages(), d.phone(), d.email(), d.address(), PosStatus.ACTIVE, 1L, now(),
                caller.userId()));
        audit.changed(tenantId, caller.userId(), ENTITY_BRANCH, created.id(), null, snapshot(created));
        return created;
    }

    @Override
    public Branch updateBranch(PosCaller caller, String tenantId, String id, BranchDraft draft, long expectedVersion) {
        requireTenantWide(caller, tenantId);
        Branch current = branches.find(tenantId, id).orElseThrow(() -> PosException.notFound(ERROR_BRANCH_NOT_FOUND));
        if (!current.legalEntityId().equals(draft.legalEntityId())) {
            throw PosException.conflict(ERROR_BRANCH_LEGAL_ENTITY_LOCKED, FIELD_LEGAL_ENTITY_ID);
        }
        BranchDraft d = PosValidator.branch(draft, owner(tenantId, current.legalEntityId()));
        Branch updated = branches.update(new Branch(id, tenantId, current.legalEntityId(), d.code(), d.name(), d.timeZone(),
                d.locale(), d.languages(), d.phone(), d.email(), d.address(), current.status(), expectedVersion + 1, now(),
                caller.userId()), expectedVersion);
        audit.changed(tenantId, caller.userId(), ENTITY_BRANCH, id, snapshot(current), snapshot(updated));
        return updated;
    }

    @Override
    public Set<String> branchStaff(PosCaller caller, String tenantId, String branchId) {
        visibleBranch(caller, tenantId, branchId);
        return staff.staffOf(tenantId, branchId);
    }

    @Override
    public Set<String> replaceBranchStaff(PosCaller caller, String tenantId, String branchId, Set<String> userIds) {
        visibleBranch(caller, tenantId, branchId);
        Set<String> wanted = new TreeSet<>(userIds == null ? Set.of() : userIds);
        for (String userId : wanted) {
            if (!directory.isActiveMember(tenantId, userId)) {
                throw PosException.invalid(ERROR_STAFF_NOT_MEMBER, FIELD_USER_IDS + "[" + userId + "]");
            }
        }
        Set<String> before = new TreeSet<>(staff.staffOf(tenantId, branchId));
        staff.replaceStaff(tenantId, branchId, wanted, caller.userId(), now());
        audit.changed(tenantId, caller.userId(), ENTITY_BRANCH_STAFF, branchId, Map.of(FIELD_USER_IDS, List.copyOf(before)),
                Map.of(FIELD_USER_IDS, List.copyOf(wanted)));
        return wanted;
    }

    @Override
    public List<String> branchIdsOf(String tenantId, String userId) {
        return staff.branchIdsOf(tenantId, userId);
    }

    private void requireTenant(PosCaller caller, String tenantId) {
        if (caller == null || tenantId == null || !tenantId.equals(caller.tenantId()) || !directory.tenantExists(tenantId)) {
            throw PosException.notFound(ERROR_ORGANIZATION_NOT_FOUND);
        }
    }

    private void requireTenantWide(PosCaller caller, String tenantId) {
        requireTenant(caller, tenantId);
        if (!caller.tenantWide()) {
            throw PosException.notFound(ERROR_ORGANIZATION_NOT_FOUND);
        }
    }

    private Branch visibleBranch(PosCaller caller, String tenantId, String id) {
        requireTenant(caller, tenantId);
        Branch branch = branches.find(tenantId, id).orElseThrow(() -> PosException.notFound(ERROR_BRANCH_NOT_FOUND));
        if (!caller.tenantWide() && !staff.branchIdsOf(tenantId, caller.userId()).contains(id)) {
            throw PosException.notFound(ERROR_BRANCH_NOT_FOUND);
        }
        return branch;
    }

    private LegalEntity owner(String tenantId, String legalEntityId) {
        Optional<LegalEntity> found = legalEntityId == null ? Optional.empty() : legalEntities.find(tenantId, legalEntityId);
        return found.orElseThrow(() -> PosException.invalid(ERROR_LEGAL_ENTITY_NOT_FOUND, FIELD_LEGAL_ENTITY_ID));
    }

    private Instant now() {
        return clock.instant();
    }

    private static Map<String, Object> snapshot(LegalEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("country", e.country());
        m.put("legalName", e.legalName());
        m.put("currency", e.currency());
        m.put("taxIds", e.taxIds().stream().map(t -> t.type() + ":" + t.value()).toList());
        m.put("address", e.address());
        m.put("invoicePrefix", e.invoicePrefix());
        m.put("financialYearStartMonth", e.financialYearStartMonth());
        m.put("status", e.status().name());
        m.put("version", e.version());
        return m;
    }

    private static Map<String, Object> snapshot(Branch b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("legalEntityId", b.legalEntityId());
        m.put("code", b.code());
        m.put("name", b.name());
        m.put("timeZone", b.timeZone());
        m.put("locale", b.locale());
        m.put("languages", b.languages());
        m.put("phone", b.phone());
        m.put("email", b.email());
        m.put("address", b.address());
        m.put("status", b.status().name());
        m.put("version", b.version());
        return m;
    }
}
