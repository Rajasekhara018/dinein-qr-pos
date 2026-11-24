package com.railcab.dms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// spring.config.import=vault:// applies unconditionally, and ClientAuthenticationFactory
// hard-requires a non-empty token to even construct (before "optional:" can gracefully handle
// the subsequent connection failure against a Vault that isn't running in tests). The dummy
// token gets a real 403 back if a real Vault happens to be reachable (e.g. a developer's local
// dev instance from docs/design/Platform/SecretsManagement.md), so gateway-forward-key -- which
// has no hardcoded fallback since it's a required env var in real deployments -- also needs its
// own test-only value here rather than relying on the Vault-contributed property resolving.
@SpringBootTest(properties = {
        "spring.cloud.vault.token=test-only-dummy-token-vault-is-not-used-in-tests",
        "gateway-forward-key=test-only-gateway-forward-key-do-not-use-outside-tests",
        // Starts an embedded MongoDB for this test, so it passes on a machine that has no database running.
        "de.flapdoodle.mongodb.embedded.version=7.0.5"
})
class RailCabDmsApplicationTests {

    @Test
    void contextLoads() {
    }
}
