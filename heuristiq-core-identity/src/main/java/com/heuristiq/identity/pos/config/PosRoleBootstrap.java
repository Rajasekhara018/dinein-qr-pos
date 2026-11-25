package com.heuristiq.identity.pos.config;

import com.heuristiq.identity.helper.AuditInfo;
import com.heuristiq.identity.pos.application.PosRoles;
import com.heuristiq.identity.role.UamRole;
import com.heuristiq.identity.role.repository.UamRoleRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the shared POS system roles ({@link PosRoles}) at start-up, in either persistence mode (roles live in the role store
 * of both). Idempotent: a missing role is created; an existing shared role keeps every privilege it has and gains the missing
 * ones (a privilege is never removed), and nothing is saved when nothing is missing. A role of the same code that belongs to an
 * organization is left alone: it is not the shared role and must not be handed POS privileges. A failure only logs, since a
 * start-up step must never stop UAM.
 */
@Slf4j
@Component
public class PosRoleBootstrap implements ApplicationRunner {

    private final UamRoleRepository roles;

    public PosRoleBootstrap(UamRoleRepository roles) {
        this.roles = roles;
    }

    @Override
    public void run(ApplicationArguments args) {
        PosRoles.PRIVILEGES.forEach((code, privileges) -> {
            try {
                seedOrMerge(code, privileges);
            } catch (RuntimeException e) {
                log.warn("Could not seed POS role {}: {}", code, e.getMessage());
            }
        });
    }

    private void seedOrMerge(String code, List<String> privileges) {
        LocalDateTime now = LocalDateTime.now();
        Optional<UamRole> existing = roles.findByCodeIgnoreCase(code);
        if (existing.isEmpty()) {
            roles.save(UamRole.builder()
                    .code(code)
                    .name(code)
                    .description("POS " + code + " role")
                    .permissions(new ArrayList<>(privileges))
                    .active(true)
                    .systemRole(true)
                    .editable(false)
                    .audit(AuditInfo.builder().createdAt(now).updatedAt(now).build())
                    .build());
            return;
        }
        UamRole role = existing.get();
        if (role.getTenantId() != null) {
            log.warn("POS role {} exists as a role of organization {}; it is not seeded", code, role.getTenantId());
            return;
        }
        Set<String> merged = new LinkedHashSet<>(role.getPermissions() == null ? List.of() : role.getPermissions());
        boolean changed = merged.addAll(privileges);
        if (!Boolean.TRUE.equals(role.getSystemRole()) || !Boolean.FALSE.equals(role.getEditable())) {
            role.setSystemRole(true);
            role.setEditable(false);
            changed = true;
        }
        if (!changed) {
            return;
        }
        role.setPermissions(new ArrayList<>(merged));
        if (role.getAudit() != null) {
            role.getAudit().setUpdatedAt(now);
        }
        roles.save(role);
    }
}
