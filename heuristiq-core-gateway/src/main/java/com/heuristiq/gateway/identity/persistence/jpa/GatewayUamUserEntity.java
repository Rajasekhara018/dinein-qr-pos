package com.heuristiq.gateway.identity.persistence.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Read-only mapping of UAM's {@code uam_users} table -- mirrors UAM's own {@code UamUserEntity}
 * table/column names. {@code status} is read as a plain string (UAM stores it via
 * {@code @Enumerated(EnumType.STRING)}, i.e. the same VARCHAR value) and compared against the
 * literal {@code "ACTIVE"}, avoiding a duplicate of UAM's {@code UamEnums.UserStatus} enum here.
 */
@Data
@Entity
@Table(name = "uam_users")
public class GatewayUamUserEntity {
    @Id
    private String id;

    private String status;
}
