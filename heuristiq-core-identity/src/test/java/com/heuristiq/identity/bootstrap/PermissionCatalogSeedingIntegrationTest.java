package com.heuristiq.identity.bootstrap;

import com.heuristiq.identity.role.UamRole;
import com.heuristiq.identity.permissions.PermissionRepository;
import com.heuristiq.identity.role.repository.UamRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test for the permission-seeding flow: boots the full Spring context (against a real,
 * embedded MongoDB provided by de.flapdoodle.embed.mongo.spring3x — no Docker/local Mongo needed),
 * lets {@link UamBootstrapRunner} run exactly as it does in production, and asserts against the
 * repository that {@code privilege.json} actually landed in the database and that role
 * seeding's permission-code references still resolve.
 */
@SpringBootTest(properties = {
        "de.flapdoodle.mongodb.embedded.version=7.0.5",
        // railcab.uam.auth.jwt-secret has no default in application.properties (required env var
        // in real deployments, so a genuine misconfiguration fails fast) -- tests need *some*
        // value >= 256 bits (HS256's minimum) to let JwtService construct a signing key.
        "railcab.uam.auth.jwt-secret=test-only-jwt-signing-secret-do-not-use-outside-automated-tests-min-256-bits",
        // spring.config.import=vault:// applies unconditionally, and ClientAuthenticationFactory
        // hard-requires a non-empty token to even construct (before "optional:" can gracefully
        // handle the subsequent connection failure against a Vault that isn't running in tests).
        "spring.cloud.vault.token=test-only-dummy-token-vault-is-not-used-in-tests",
        // railcab.nms.internal-api-key has no default (required env var/Vault value in real
        // deployments); tests need *some* value to let the context load.
        "railcab.nms.internal-api-key=test-only-nms-internal-api-key"
})
class PermissionCatalogSeedingIntegrationTest {

    @Autowired
    private PermissionCatalogLoader permissionCatalogLoader;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UamRoleRepository uamRoleRepository;

    @Autowired
    private UamBootstrapRunner uamBootstrapRunner;

    @Test
    void catalogSeedsEveryPermissionIntoTheDatabase() {
        var catalog = permissionCatalogLoader.loadShared();
        assertThat(catalog).isNotEmpty();

        for (PermissionCatalogEntry entry : catalog) {
            assertThat(permissionRepository.existsByCodeIgnoreCase(entry.code()))
                    .as("permission %s should have been seeded", entry.code())
                    .isTrue();
        }

        assertThat(permissionRepository.findAll(Sort.unsorted())).hasSize(catalog.size());
        assertThat(permissionRepository.findAll(Sort.unsorted())).allSatisfy(permission ->
                assertThat(permission.getAttributes().get("privilegeCode").toString()).startsWith("SHARED_"));
    }

    @Test
    void roleSeedingReferencesResolveAgainstSeededPermissions() {
        UamRole superAdmin = uamRoleRepository.findByCodeIgnoreCase("SUPER_ADMIN")
                .orElseThrow(() -> new AssertionError("SUPER_ADMIN role should have been seeded"));

        assertThat(superAdmin.getPermissions()).isNotEmpty();
        for (String permissionCode : superAdmin.getPermissions()) {
            assertThat(permissionRepository.existsByCodeIgnoreCase(permissionCode))
                    .as("SUPER_ADMIN references permission %s which should exist", permissionCode)
                    .isTrue();
        }
    }

    @Test
    void reRunningTheBootstrapRunnerDoesNotDuplicatePermissions() {
        int before = permissionRepository.findAll(Sort.unsorted()).size();

        uamBootstrapRunner.run();

        int after = permissionRepository.findAll(Sort.unsorted()).size();
        assertThat(after).isEqualTo(before);
    }
}
