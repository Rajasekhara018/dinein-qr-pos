package com.heuristiq.identity.role;

import com.heuristiq.identity.enums.UamEnums.AssignmentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UamRoleSearchIndexTest {

    @Test
    void mapsAllPresentFieldsIntoGenericSlots() {
        UamRole role = UamRole.builder()
                .id("role-1")
                .tenantId("tenant-1")
                .code("SUPPORT_AGENT")
                .name("Support Agent")
                .description("Handles support tickets")
                .active(true)
                .assignmentType(AssignmentType.DIRECT)
                .build();

        assertThat(role.getSearchModel()).isEqualTo("ROLE");
        assertThat(role.getSearchModelKey()).isEqualTo("role-1");

        Map<String, Object> fields = role.getSearchIndexFields();
        assertThat(fields.get("ownerKey")).isEqualTo("tenant-1");
        assertThat(fields.get("text1")).isEqualTo("SUPPORT_AGENT");
        assertThat(fields.get("text2")).isEqualTo("Support Agent");
        assertThat(fields.get("text3")).isEqualTo("Handles support tickets");
        assertThat(fields.get("text4")).isEqualTo("tenant-1");
        assertThat(fields.get("text5")).isEqualTo("true");
        assertThat(fields.get("text6")).isEqualTo("DIRECT");
    }

    @Test
    void omitsNullFieldsFromTheIndexedPayload() {
        UamRole role = UamRole.builder().id("role-2").code("BARE").build();

        Map<String, Object> fields = role.getSearchIndexFields();

        assertThat(fields).containsEntry("text1", "BARE");
        assertThat(fields).doesNotContainKeys("ownerKey", "text2", "text3", "text4", "text5", "text6", "date1", "date2");
    }
}
