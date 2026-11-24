package com.heuristiq.uam.cache;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the UAM read caches ({@code railcab.uam.cache.*}). {@code enabled=false} turns every cache into a
 * pass-through (every call reads the database as before); each cache also has its own switch, TTL and size bound.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "railcab.uam.cache")
public class UamCacheProperties {

    /** Global switch; false makes every cache a pass-through. */
    private boolean enabled = true;

    @Valid
    private Spec myPrivileges = new Spec(UamCacheConstants.DEFAULT_MY_PRIVILEGES_TTL_SECONDS, UamCacheConstants.DEFAULT_USER_KEYED_MAX_SIZE);
    @Valid
    private Spec myOrganizations = new Spec(UamCacheConstants.DEFAULT_MY_ORGANIZATIONS_TTL_SECONDS, UamCacheConstants.DEFAULT_USER_KEYED_MAX_SIZE);
    @Valid
    private Spec tenants = new Spec(UamCacheConstants.DEFAULT_TENANTS_TTL_SECONDS, UamCacheConstants.DEFAULT_TENANT_KEYED_MAX_SIZE);
    @Valid
    private Spec tenantApplications = new Spec(UamCacheConstants.DEFAULT_TENANT_APPLICATIONS_TTL_SECONDS, UamCacheConstants.DEFAULT_TENANT_KEYED_MAX_SIZE);

    /** One cache's switch, time to live and size bound. */
    @Getter
    @Setter
    public static class Spec {
        private boolean enabled = true;
        @Min(1)
        @Max(UamCacheConstants.MAX_TTL_SECONDS)
        private int ttlSeconds;
        @Min(1)
        @Max(UamCacheConstants.MAX_SIZE_LIMIT)
        private int maxSize;

        public Spec() {
        }

        public Spec(int ttlSeconds, int maxSize) {
            this.ttlSeconds = ttlSeconds;
            this.maxSize = maxSize;
        }
    }
}
