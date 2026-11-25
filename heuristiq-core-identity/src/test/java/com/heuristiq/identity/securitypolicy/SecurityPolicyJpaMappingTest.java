package com.heuristiq.identity.securitypolicy;

import com.heuristiq.identity.securitypolicy.persistence.jpa.SecurityPolicyEntity;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.junit.jupiter.api.Test;

class SecurityPolicyJpaMappingTest {
    @Test void policyRevisionDoesNotCollideWithEmbeddedAuditVersion() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false).build();
        try {
            var metadata = (MetadataImplementor) new MetadataSources(registry)
                    .addAnnotatedClass(SecurityPolicyEntity.class).buildMetadata();
            metadata.validate();
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
