package com.heuristiq.identity.search;

import com.heuristiq.identity.role.UamRole;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.application.client.ApiClient;
import com.heuristiq.identity.securitypolicy.SecurityPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OrganizationMetadataTest {
    @Test
    void organizationOwnedIdentityRecordsPublishTheirPersistedTenant() {
        List<SearchIndexable> records = List.of(
                UamRole.builder().tenantId("org-a").build(),
                UamUser.builder().tenantId("org-a").build(),
                ApiClient.builder().tenantId("org-a").build(),
                SecurityPolicy.builder().tenantId("org-a").build());
        for (SearchIndexable record : records) {
            assertThat(record.getSearchIndexFields()).containsEntry("tenantId", "org-a").containsEntry("ownerKey", "org-a");
        }
    }
}
