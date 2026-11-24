package com.heuristiq.uam.config;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "railcab.uam")
public class UamProperties {
    private Auth auth = new Auth();
    private IdentityProvider identityProvider = new IdentityProvider();
    private Keycloak keycloak = new Keycloak();
    private Oidc oidc = new Oidc();
    private Tenancy tenancy = new Tenancy();
    private Franchise franchise = new Franchise();

    @Data
    public static class Franchise {
        private Service service = new Service();

        /**
         * When true, the franchiseIds claim is also looked up for everyone signing in to an organization whose operating model
         * today is FRANCHISE or HYBRID, whatever their roles (role-prefix holders are always looked up). Off by default: switch it
         * on only once every organization that has franchises has been set to FRANCHISE or HYBRID (docs/OPERATING_MODEL_DESIGN.md, section 3).
         */
        private boolean claimsByModel = true;

        @Data
        public static class Service {
            /**
             * Role code prefixes whose holders can be in a franchise. Only holders trigger the franchise-claims lookup at
             * sign-in and refresh; everyone else gets no lookup, no claim and no dependency on the franchise service. A role
             * matches a prefix when it is that code or that code followed by an underscore and a suffix (case-insensitive):
             * {@code FRANCHISE_OWNER} matches FRANCHISE_OWNER and FRANCHISE_OWNER_MATRIMONY, never FRANCHISE_OWNERSHIP_AUDITOR.
             */
            private List<String> lookupRolePrefixes = new ArrayList<>(List.of("FRANCHISE_OWNER", "FRANCHISE_AGENT"));
        }
    }

    @Data
    public static class Auth {
        private String jwtSecret;
        private long expiryMinutes = 60;
        private long refreshExpiryDays = 30;
        private String issuer = "railcab-uam";
    }

    @Data
    public static class Tenancy {
        /**
         * When true, the applications in a token are limited to those the signed-in organization has a current
         * subscription for (a super admin is exempt). Off by default so nothing changes until every organization has
         * been given its applications.
         */
        private boolean enforceSubscriptions = false;
    }

    @Data
    public static class IdentityProvider {
        /**
         * none, keycloak, okta, etc. UAM service code depends on a generic sync
         * contract; provider-specific implementations are selected by this value.
         */
        private boolean enabled = false;
        private String type = "none";
        private String tokenValidation = "local";
        private String unsupportedSyncAction = "fail";
    }

    @Data
    public static class Keycloak {
        private boolean enabled;
        private String serverUrl = "http://localhost:8180";
        private String realm = "railcab";
        private String clientId = "railcab-uam";
        private String clientSecret = "";
        private String adminUsername = "";
        private String adminPassword = "";
    }

    @Data
    public static class Oidc {
        private String issuerUri = "";
        private String tokenUri = "";
        private String userInfoUri = "";
        private String logoutUri = "";
        private String revocationUri = "";
        private String clientId = "";
        private String clientSecret = "";
        private String scope = "openid profile email";
        private String audience = "";
    }
}
