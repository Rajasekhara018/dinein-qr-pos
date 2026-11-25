package com.heuristiq.identity.users;

import com.heuristiq.identity.enums.UamEnums.UserStatus;
import com.heuristiq.identity.helper.RName;
import com.heuristiq.identity.helper.RPhoneNumber;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UamUserSearchIndexTest {

    @Test
    void mapsAllPresentFieldsIntoGenericSlots() {
        UamUser user = UamUser.builder()
                .id("user-1")
                .tenantId("tenant-1")
                .userName("jane.doe")
                .email("jane@example.com")
                .phoneNumber(new RPhoneNumber("91", "9876543210"))
                .status(UserStatus.ACTIVE)
                .displayName("Jane Doe")
                .fullName(new RName("Jane", "", "Doe"))
                .roles(List.of("ADMIN", "SUPPORT_AGENT"))
                .build();

        assertThat(user.getSearchModel()).isEqualTo("USER");
        assertThat(user.getSearchModelKey()).isEqualTo("user-1");

        Map<String, Object> fields = user.getSearchIndexFields();
        assertThat(fields.get("ownerKey")).isEqualTo("tenant-1");
        assertThat(fields.get("text1")).isEqualTo("jane.doe");
        assertThat(fields.get("text2")).isEqualTo("jane@example.com");
        assertThat(fields.get("text3")).isEqualTo("919876543210");
        assertThat(fields.get("text4")).isEqualTo("ACTIVE");
        assertThat(fields.get("text5")).isEqualTo("tenant-1");
        assertThat(fields.get("text6")).isEqualTo("Jane Doe");
        assertThat(fields.get("text7")).isEqualTo("Jane Doe");
        assertThat(fields.get("text8")).isEqualTo("ADMIN,SUPPORT_AGENT");
    }

    @Test
    void omitsNullFieldsFromTheIndexedPayload() {
        UamUser user = UamUser.builder().id("user-2").userName("bare.user").build();

        Map<String, Object> fields = user.getSearchIndexFields();

        assertThat(fields).containsEntry("text1", "bare.user");
        assertThat(fields).doesNotContainKeys("ownerKey", "text2", "text3", "text4", "text5", "text6", "text7", "text8");
    }
}
