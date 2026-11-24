package com.heuristiq.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.privilege.PrivilegeCatalogLoader;
import com.heuristiq.gateway.privilege.PrivilegeEntry;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;

class PrivilegeIdMigrationTest {
    @Test
    void realCatalogHasConsecutiveGlobalIdsAndTheCorrectElevatedPermission() {
        var catalog = new PrivilegeCatalogLoader(new ObjectMapper()).load();
        assertThat(catalog).isNotEmpty();
        // Version 2 onboarding permissions were retired; their IDs remain reserved and must never be reused.
        // See privileges/retired-privileges.json. Every other allocated ID must still be present.
        // The food audit log permission (190) moved to Rider_AuditService with the food audit trail.
        var retiredIds = List.of(44, 45, 46, 48, 190);
        var activeIds = catalog.stream().map(PrivilegeEntry::privilegeId).toList();
        assertThat(activeIds).doesNotHaveDuplicates().doesNotContainAnyElementsOf(retiredIds);
        var allocatedIds = Stream.concat(activeIds.stream(), retiredIds.stream()).toList();
        assertThat(allocatedIds).containsExactlyInAnyOrderElementsOf(
                IntStream.rangeClosed(1, allocatedIds.size()).boxed().toList());
        assertThat(catalog.stream().filter(entry -> "PLATFORM_ELEVATED_ACCESS".equals(entry.name()))
                .map(PrivilegeEntry::privilegeId).toList()).containsExactly(GatewayConstants.ELEVATED_ACCESS_PRIVILEGE_ID);
        assertThat(GatewayConstants.PERMISSION_CATALOG_VERSION).isEqualTo(2);
    }
}
