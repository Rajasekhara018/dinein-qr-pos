package com.heuristiq.gateway.identity.persistence.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Read-only projection of UAM's {@code uam_users} collection -- only the field
 * {@code UamUserStatusRepository} actually needs. {@code status} is stored as a plain string
 * (Spring Data Mongo writes enums as their name by default) so this doesn't need to duplicate
 * UAM's {@code UamEnums.UserStatus} enum; compared against the literal {@code "ACTIVE"}, matching
 * UAM's own authoritative definition of "active" (status == ACTIVE), not the separate legacy
 * {@code active} boolean field UamUser also carries.
 */
@Data
@Document(collection = "uam_users")
public class GatewayUamUserDocument {
    @Id
    private String id;
    private String status;
}
