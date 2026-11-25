package com.heuristiq.identity.pos.support;

import static com.heuristiq.identity.pos.domain.PosConstants.*;

import com.heuristiq.identity.pos.application.port.BranchStaffStore;
import com.heuristiq.identity.pos.application.port.BranchStore;
import com.heuristiq.identity.pos.application.port.LegalEntityStore;
import com.heuristiq.identity.pos.domain.Branch;
import com.heuristiq.identity.pos.domain.LegalEntity;
import com.heuristiq.identity.pos.domain.PosException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * In-memory implementation of the three POS stores, the contract reference for the PostgreSQL stores: stale versions and
 * duplicate branch codes are refused exactly as the port Javadoc states. {@code LegalEntityStore.find} and
 * {@code BranchStore.find} share a signature with different return types, so one class cannot implement both; this class
 * holds the shared state and hands out one view per port.
 */
public class InMemoryPosStores {

    private final Map<String, LegalEntity> entities = new LinkedHashMap<>();
    private final Map<String, Branch> branches = new LinkedHashMap<>();
    private final Map<String, Set<String>> staff = new LinkedHashMap<>();

    private final LegalEntityStore legalEntityStore = new LegalEntities();
    private final BranchStore branchStore = new Branches();
    private final BranchStaffStore branchStaffStore = new Staff();

    public LegalEntityStore legalEntities() {
        return legalEntityStore;
    }

    public BranchStore branches() {
        return branchStore;
    }

    public BranchStaffStore staff() {
        return branchStaffStore;
    }

    private static String key(String tenantId, String id) {
        return tenantId + "/" + id;
    }

    private final class LegalEntities implements LegalEntityStore {

        @Override
        public Optional<LegalEntity> find(String tenantId, String id) {
            return Optional.ofNullable(entities.get(key(tenantId, id)));
        }

        @Override
        public List<LegalEntity> list(String tenantId) {
            return entities.values().stream().filter(e -> e.tenantId().equals(tenantId)).toList();
        }

        @Override
        public LegalEntity insert(LegalEntity entity) {
            entities.put(key(entity.tenantId(), entity.id()), entity);
            return entity;
        }

        @Override
        public LegalEntity update(LegalEntity entity, long expectedVersion) {
            LegalEntity stored = entities.get(key(entity.tenantId(), entity.id()));
            if (stored == null || stored.version() != expectedVersion) {
                throw PosException.conflict(ERROR_STALE_VERSION, FIELD_VERSION);
            }
            entities.put(key(entity.tenantId(), entity.id()), entity);
            return entity;
        }
    }

    private final class Branches implements BranchStore {

        @Override
        public Optional<Branch> find(String tenantId, String id) {
            return Optional.ofNullable(branches.get(key(tenantId, id)));
        }

        @Override
        public List<Branch> list(String tenantId) {
            return branches.values().stream().filter(b -> b.tenantId().equals(tenantId)).toList();
        }

        @Override
        public boolean anyForLegalEntity(String tenantId, String legalEntityId) {
            return branches.values().stream()
                    .anyMatch(b -> b.tenantId().equals(tenantId) && b.legalEntityId().equals(legalEntityId));
        }

        @Override
        public Branch insert(Branch branch) {
            requireFreeCode(branch);
            branches.put(key(branch.tenantId(), branch.id()), branch);
            return branch;
        }

        @Override
        public Branch update(Branch branch, long expectedVersion) {
            Branch stored = branches.get(key(branch.tenantId(), branch.id()));
            if (stored == null || stored.version() != expectedVersion) {
                throw PosException.conflict(ERROR_STALE_VERSION, FIELD_VERSION);
            }
            requireFreeCode(branch);
            branches.put(key(branch.tenantId(), branch.id()), branch);
            return branch;
        }

        private void requireFreeCode(Branch branch) {
            boolean taken = branches.values().stream().anyMatch(b -> b.tenantId().equals(branch.tenantId())
                    && !b.id().equals(branch.id()) && b.code().equals(branch.code()));
            if (taken) {
                throw PosException.conflict(ERROR_BRANCH_CODE_TAKEN, FIELD_CODE);
            }
        }
    }

    private final class Staff implements BranchStaffStore {

        @Override
        public Set<String> staffOf(String tenantId, String branchId) {
            return new TreeSet<>(staff.getOrDefault(key(tenantId, branchId), Set.of()));
        }

        @Override
        public void replaceStaff(String tenantId, String branchId, Set<String> userIds, String actor, Instant at) {
            staff.put(key(tenantId, branchId), new TreeSet<>(userIds));
        }

        @Override
        public List<String> branchIdsOf(String tenantId, String userId) {
            List<String> ids = new ArrayList<>();
            staff.forEach((k, users) -> {
                if (k.startsWith(tenantId + "/") && users.contains(userId)) {
                    ids.add(k.substring(tenantId.length() + 1));
                }
            });
            return ids;
        }
    }
}
