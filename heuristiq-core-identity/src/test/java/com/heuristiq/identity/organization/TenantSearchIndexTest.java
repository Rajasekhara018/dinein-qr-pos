package com.heuristiq.identity.organization;

import com.heuristiq.identity.enums.UamEnums.TenantStatus;
import com.heuristiq.identity.enums.UamEnums.TenantType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TenantSearchIndexTest {

    @Test
    void mapsAllPresentFieldsIntoGenericSlots() {
        Tenant tenant = Tenant.builder()
                .id("tenant-1")
                .code("ACME")
                .name("Acme Corp")
                .legalName("Acme Corporation Pvt Ltd")
                .status(TenantStatus.ACTIVE)
                .type(TenantType.CUSTOMER)
                .emailDomain("acme.com")
                .country("IN")
                .build();

        assertThat(tenant.getSearchModel()).isEqualTo("TENANT");
        assertThat(tenant.getSearchModelKey()).isEqualTo("tenant-1");

        Map<String, Object> fields = tenant.getSearchIndexFields();
        assertThat(fields.get("text1")).isEqualTo("ACME");
        assertThat(fields.get("text2")).isEqualTo("Acme Corp");
        assertThat(fields.get("text3")).isEqualTo("Acme Corporation Pvt Ltd");
        assertThat(fields.get("text4")).isEqualTo("ACTIVE");
        assertThat(fields.get("text5")).isEqualTo("CUSTOMER");
        assertThat(fields.get("text6")).isEqualTo("acme.com");
        assertThat(fields.get("text7")).isEqualTo("IN");
        // Owned by itself, so the search index shows an organization's administrator their own organization only.
        assertThat(fields).containsEntry("ownerKey", "tenant-1").containsEntry("tenantId", "tenant-1");
    }

    @Test
    void omitsNullFieldsFromTheIndexedPayload() {
        Tenant tenant = Tenant.builder().id("tenant-2").code("BARE").build();

        Map<String, Object> fields = tenant.getSearchIndexFields();

        assertThat(fields).containsEntry("text1", "BARE");
        assertThat(fields).doesNotContainKeys("text2", "text3", "text4", "text5", "text6", "text7");
    }
}
