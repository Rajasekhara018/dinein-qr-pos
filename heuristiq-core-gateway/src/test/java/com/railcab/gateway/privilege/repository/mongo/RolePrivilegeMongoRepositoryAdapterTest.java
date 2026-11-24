package com.heuristiq.gateway.privilege.repository.mongo;

import com.heuristiq.gateway.privilege.persistence.mongo.GatewayPermissionDocument;
import com.heuristiq.gateway.privilege.persistence.mongo.GatewayUamRoleDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RolePrivilegeMongoRepositoryAdapterTest {

    private final MongoTemplate template = mock(MongoTemplate.class);
    private final RolePrivilegeMongoRepositoryAdapter adapter = new RolePrivilegeMongoRepositoryAdapter(template);

    private static GatewayUamRoleDocument role(List<String> permissions) {
        GatewayUamRoleDocument r = new GatewayUamRoleDocument();
        r.setCode("OPS");
        r.setPermissions(permissions);
        return r;
    }

    private static GatewayPermissionDocument permission(String code, Map<String, Object> attrs) {
        GatewayPermissionDocument p = new GatewayPermissionDocument();
        p.setCode(code);
        p.setAttributes(attrs);
        return p;
    }

    @Test
    void unknownRoleHasNoPrivilegesAndPermissionsAreNotQueried() {
        when(template.findOne(any(Query.class), eq(GatewayUamRoleDocument.class))).thenReturn(null);

        assertThat(adapter.resolvePrivilegeIds("NOPE")).isEmpty();
        verify(template, never()).find(any(Query.class), eq(GatewayPermissionDocument.class));
    }

    @Test
    void roleWithNullOrEmptyPermissionsHasNoPrivileges() {
        when(template.findOne(any(Query.class), eq(GatewayUamRoleDocument.class)))
                .thenReturn(role(null)).thenReturn(role(List.of()));

        assertThat(adapter.resolvePrivilegeIds("OPS")).isEmpty();
        assertThat(adapter.resolvePrivilegeIds("OPS")).isEmpty();
    }

    @Test
    void numericPrivilegeIdsAreCollectedAndNonNumericOrMissingOnesIgnored() {
        when(template.findOne(any(Query.class), eq(GatewayUamRoleDocument.class)))
                .thenReturn(role(List.of("A", "B", "C", "D", "E")));
        Map<String, Object> noAttrs = null;
        when(template.find(any(Query.class), eq(GatewayPermissionDocument.class))).thenReturn(List.of(
                permission("A", Map.of("privilegeId", 7, "privilegeCatalogVersion", 2)),
                permission("B", Map.of("privilegeId", 9L, "privilegeCatalogVersion", 2)),
                permission("C", Map.of("privilegeId", "12", "privilegeCatalogVersion", 2)),
                permission("D", new HashMap<>(Map.of("other", 1))),
                permission("E", noAttrs)));

        assertThat(adapter.resolvePrivilegeIds("OPS")).containsExactlyInAnyOrder(7, 9);

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(template).find(q.capture(), eq(GatewayPermissionDocument.class));
        assertThat(q.getValue().getQueryObject().toJson()).contains("code");
    }

    @Test
    void legacyOrUnversionedNumbersCannotGrantRenumberedPrivileges() {
        when(template.findOne(any(Query.class), eq(GatewayUamRoleDocument.class))).thenReturn(role(List.of("OLD", "CURRENT")));
        when(template.find(any(Query.class), eq(GatewayPermissionDocument.class))).thenReturn(List.of(
                permission("OLD", Map.of("privilegeId", 444)),
                permission("LEGACY", Map.of("privilegeId", 444, "privilegeCatalogVersion", 1)),
                permission("CURRENT", Map.of("privilegeId", 7, "privilegeCatalogVersion", 2))));
        assertThat(adapter.resolvePrivilegeIds("OPS")).containsExactly(7);
    }
}
