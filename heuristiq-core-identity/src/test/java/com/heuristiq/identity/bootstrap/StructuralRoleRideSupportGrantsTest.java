package com.heuristiq.identity.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Drivers and customers get the early-drop, share-link and help-desk privileges by default, and nobody else's roles do. */
class StructuralRoleRideSupportGrantsTest {
    private static final List<String> HELPDESK = List.of("HELPDESK_TICKET_CREATE", "HELPDESK_TICKET_LIST",
            "HELPDESK_TICKET_READ", "HELPDESK_TICKET_MESSAGE_LIST", "HELPDESK_TICKET_MESSAGE_CREATE");

    @Test
    void driverGetsEarlyDropRequestAndTheHelpDesk() {
        assertThat(BootstrapRoleConstants.DRIVER_PERMISSIONS).contains("RIDE_EARLY_DROP_CREATE").containsAll(HELPDESK)
                .doesNotContain("RIDE_EARLY_DROP_ACCEPT_CREATE", "RIDE_EARLY_DROP_DECLINE_CREATE",
                        "RIDE_SHARE_LINK_CREATE", "RIDE_SHARE_LINK_DELETE", "CONFIG_SERVICEABILITY_READ");
    }

    @Test
    void customerGetsTheEarlyDropAnswerPairTogetherShareLinksAndTheHelpDesk() {
        assertThat(BootstrapRoleConstants.CUSTOMER_PERMISSIONS)
                .contains("RIDE_EARLY_DROP_ACCEPT_CREATE", "RIDE_EARLY_DROP_DECLINE_CREATE",
                        "RIDE_SHARE_LINK_CREATE", "RIDE_SHARE_LINK_DELETE")
                .containsAll(HELPDESK)
                .doesNotContain("RIDE_EARLY_DROP_CREATE", "CONFIG_SERVICEABILITY_READ");
    }

    @Test
    void theCustomerMayAskForServiceWhereNoFranchiseServesButNotSeeTheDemand() {
        assertThat(BootstrapRoleConstants.CUSTOMER_PERMISSIONS).contains("FRANCHISE_DEMAND_VOTE_CREATE")
                .doesNotContain("FRANCHISE_DEMAND_SUMMARY_LIST");
        assertThat(BootstrapRoleConstants.DRIVER_PERMISSIONS).doesNotContain("FRANCHISE_DEMAND_VOTE_CREATE");
        assertThat(BootstrapRoleConstants.STRUCTURAL_ROLE_PERMISSIONS_VERSION).isGreaterThanOrEqualTo(10);
    }

    @Test
    void driverReadsTheirOwnFranchiseAndConfirmsDirectCollectionsByDefault() {
        assertThat(BootstrapRoleConstants.DRIVER_PERMISSIONS).contains("FRANCHISE_DRIVER_SELF_READ", "RIDE_COLLECTION_CREATE")
                .doesNotContain("FRANCHISE_ADMIN_DRIVER_READ");
        assertThat(BootstrapRoleConstants.CUSTOMER_PERMISSIONS).doesNotContain("FRANCHISE_DRIVER_SELF_READ", "RIDE_COLLECTION_CREATE");
        assertThat(BootstrapRoleConstants.STRUCTURAL_ROLE_PERMISSIONS_VERSION).isGreaterThanOrEqualTo(13);
    }

    @Test
    void agentRoleIsUntouched() {
        assertThat(BootstrapRoleConstants.AGENT_PERMISSIONS).noneMatch(c -> c.startsWith("RIDE_") || c.startsWith("HELPDESK_"));
    }

    @Test
    void versionWasBumpedSoExistingRolesPickTheCodesUpOnce() {
        assertThat(BootstrapRoleConstants.STRUCTURAL_ROLE_PERMISSIONS_VERSION).isGreaterThanOrEqualTo(8);
    }

    @Test
    void everyGrantedCodeExistsInThePrivilegeCatalogue() throws Exception {
        Set<String> names = new HashSet<>();
        try (InputStream in = new ClassPathResource(BootstrapRoleConstants.CATALOG_RESOURCE).getInputStream()) {
            for (JsonNode node : new ObjectMapper().readTree(in)) {
                names.add(node.path("name").asText());
            }
        }
        for (String code : List.of("RIDE_EARLY_DROP_CREATE", "RIDE_EARLY_DROP_ACCEPT_CREATE", "RIDE_EARLY_DROP_DECLINE_CREATE",
                "RIDE_SHARE_LINK_CREATE", "RIDE_SHARE_LINK_DELETE")) {
            assertThat(names).as(code).contains(code);
        }
        assertThat(names).containsAll(HELPDESK);
    }
}
