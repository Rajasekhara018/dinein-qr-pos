package com.heuristiq.gateway.privilege.repository.mongo;

import com.heuristiq.gateway.privilege.RolePrivilegeRepository;
import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.privilege.persistence.mongo.GatewayPermissionDocument;
import com.heuristiq.gateway.privilege.persistence.mongo.GatewayUamRoleDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@ConditionalOnProperty(prefix = "railcab.gateway.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class RolePrivilegeMongoRepositoryAdapter implements RolePrivilegeRepository {

    private final MongoTemplate uamMongoTemplate;

    RolePrivilegeMongoRepositoryAdapter(@Qualifier("uamMongoTemplate") MongoTemplate uamMongoTemplate) {
        this.uamMongoTemplate = uamMongoTemplate;
    }

    @Override
    public Set<Integer> resolvePrivilegeIds(String roleCode) {
        Optional<GatewayUamRoleDocument> role = findRole(roleCode);
        if (role.isEmpty() || role.get().getPermissions() == null || role.get().getPermissions().isEmpty()) {
            return Set.of();
        }

        Query query = new Query(Criteria.where("code").in(role.get().getPermissions()));
        List<GatewayPermissionDocument> permissions = uamMongoTemplate.find(query, GatewayPermissionDocument.class);

        return permissions.stream()
                .map(this::extractPrivilegeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Optional<GatewayUamRoleDocument> findRole(String roleCode) {
        Query query = new Query(Criteria.where("code").is(roleCode));
        return Optional.ofNullable(uamMongoTemplate.findOne(query, GatewayUamRoleDocument.class));
    }

    private Integer extractPrivilegeId(GatewayPermissionDocument permission) {
        if (permission.getAttributes() == null) {
            return null;
        }
        Object version = permission.getAttributes().get(GatewayConstants.PERMISSION_CATALOG_VERSION_ATTRIBUTE);
        if (!(version instanceof Number catalogNumber) || catalogNumber.doubleValue() != GatewayConstants.PERMISSION_CATALOG_VERSION) {
            return null;
        }
        Object privilegeId = permission.getAttributes().get("privilegeId");
        return privilegeId instanceof Number number ? number.intValue() : null;
    }
}
