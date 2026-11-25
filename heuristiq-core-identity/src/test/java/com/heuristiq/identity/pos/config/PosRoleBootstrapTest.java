package com.heuristiq.identity.pos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.heuristiq.identity.pos.application.PosRoles;
import com.heuristiq.identity.role.UamRole;
import com.heuristiq.identity.role.repository.UamRoleRepository;

/** The five POS roles are seeded once as shared system roles and only ever gain privileges. */
class PosRoleBootstrapTest {

    private final InMemoryRoles roles = new InMemoryRoles();
    private final PosRoleBootstrap bootstrap = new PosRoleBootstrap(roles);

    @Test
    void seedsFiveRolesOnce() {
        bootstrap.run(null);
        bootstrap.run(null);

        assertThat(roles.rows.values()).extracting(UamRole::getCode).containsExactlyInAnyOrder(
                "POS_RESTAURANT_ADMIN", "POS_BRANCH_ADMIN", "POS_BILLING", "POS_WAITER", "POS_KITCHEN");
        assertThat(roles.saves).as("the second run changes nothing").isEqualTo(5);
        for (UamRole role : roles.rows.values()) {
            assertThat(role.getTenantId()).as(role.getCode()).isNull();
            assertThat(role.getSystemRole()).as(role.getCode()).isTrue();
            assertThat(role.getEditable()).as(role.getCode()).isFalse();
            assertThat(role.getActive()).as(role.getCode()).isTrue();
        }
        assertThat(roles.find("POS_RESTAURANT_ADMIN").getPermissions()).containsExactly(
                "POS_LEGAL_ENTITY_LIST", "POS_LEGAL_ENTITY_READ", "POS_LEGAL_ENTITY_CREATE", "POS_LEGAL_ENTITY_UPDATE",
                "POS_BRANCH_LIST", "POS_BRANCH_READ", "POS_BRANCH_CREATE", "POS_BRANCH_UPDATE",
                "POS_BRANCH_STAFF_READ", "POS_BRANCH_STAFF_UPDATE");
        assertThat(roles.find("POS_BRANCH_ADMIN").getPermissions()).containsExactly(
                "POS_LEGAL_ENTITY_READ", "POS_BRANCH_LIST", "POS_BRANCH_READ", "POS_BRANCH_STAFF_READ", "POS_BRANCH_STAFF_UPDATE");
        for (String code : List.of("POS_BILLING", "POS_WAITER", "POS_KITCHEN")) {
            assertThat(roles.find(code).getPermissions()).as(code).containsExactly("POS_BRANCH_LIST", "POS_BRANCH_READ");
        }
        assertThat(PosRoles.PRIVILEGES).hasSize(5);
    }

    @Test
    void keepsExtraPrivilegesOfAnExistingRole() {
        roles.save(UamRole.builder().code("pos_waiter").name("Waiter").active(true).systemRole(false).editable(true)
                .permissions(new ArrayList<>(List.of("DINEIN_ORDER_CREATE", "POS_BRANCH_READ"))).build());

        bootstrap.run(null);

        UamRole waiter = roles.find("POS_WAITER");
        assertThat(waiter.getPermissions()).containsExactly("DINEIN_ORDER_CREATE", "POS_BRANCH_READ", "POS_BRANCH_LIST");
        assertThat(waiter.getSystemRole()).isTrue();
        assertThat(waiter.getEditable()).isFalse();
        assertThat(roles.rows.values().stream().filter(r -> r.getCode().equalsIgnoreCase("POS_WAITER"))).hasSize(1);
    }

    @Test
    void anOrganizationsOwnRoleOfTheSameCodeIsNotGrantedAnything() {
        roles.save(UamRole.builder().code("POS_RESTAURANT_ADMIN").tenantId("org-1").active(true).systemRole(false).editable(true)
                .permissions(new ArrayList<>(List.of("X"))).build());

        bootstrap.run(null);

        assertThat(roles.find("POS_RESTAURANT_ADMIN").getPermissions()).containsExactly("X");
        assertThat(roles.find("POS_RESTAURANT_ADMIN").getSystemRole()).isFalse();
    }

    /** Role codes are unique (any case), as in both real stores. */
    static final class InMemoryRoles implements UamRoleRepository {
        final Map<String, UamRole> rows = new LinkedHashMap<>();
        int saves;
        private int ids;

        UamRole find(String code) {
            return findByCodeIgnoreCase(code).orElseThrow();
        }

        @Override
        public UamRole save(UamRole role) {
            saves++;
            if (role.getId() == null) {
                role.setId("r" + (++ids));
            }
            rows.put(role.getId(), role);
            return role;
        }

        @Override
        public Optional<UamRole> findById(String id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public List<UamRole> findAll(Sort sort) {
            return List.copyOf(rows.values());
        }

        @Override
        public Page<UamRole> findAll(Pageable pageable) {
            return new PageImpl<>(List.copyOf(rows.values()));
        }

        @Override
        public void delete(UamRole role) {
            rows.remove(role.getId());
        }

        @Override
        public Optional<UamRole> findByCodeIgnoreCase(String code) {
            return rows.values().stream().filter(r -> r.getCode().equalsIgnoreCase(code)).findFirst();
        }

        @Override
        public boolean existsByCodeIgnoreCase(String code) {
            return findByCodeIgnoreCase(code).isPresent();
        }

        @Override
        public Page<UamRole> search(String code, String name, Pageable pageable) {
            return findAll(pageable);
        }

        @Override
        public List<UamRole> findVisible(String tenantScope, Sort sort) {
            return findAll(sort);
        }

        @Override
        public Page<UamRole> searchVisible(String tenantScope, String code, String name, Pageable pageable) {
            return findAll(pageable);
        }
    }
}
