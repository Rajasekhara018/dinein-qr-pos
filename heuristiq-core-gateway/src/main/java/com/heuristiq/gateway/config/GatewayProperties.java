package com.heuristiq.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "railcab.gateway")
@Component("railCabGatewayProperties")
public class GatewayProperties {

    private Auth             auth             = new Auth();
    private IdentityProvider identityProvider = new IdentityProvider();
    private Oidc             oidc             = new Oidc();
    private Internal         internal         = new Internal();
    private Cors             cors             = new Cors();
    private Swagger          swagger          = new Swagger();
    private Rbac             rbac             = new Rbac();
    private RateLimit        rateLimit        = new RateLimit();
    private Request          request          = new Request();
    private Cache            cache            = new Cache();

    @Data
    public static class Auth {
        /** HMAC-SHA256 secret used to verify tokens issued by AdminAPI. */
        private String jwtSecret = "railcab-admin-super-secret-key-change-in-production-min-256bit";
        /** HMAC-SHA256 secret used to verify tokens issued by BookingsAPI (customer/driver). */
        private String customerJwtSecret = "railcab-bookings-super-secret-key-change-in-production";
    }

    @Data
    public static class IdentityProvider {
        /**
         * Active bearer-token auth provider: {@code hmac} (default), {@code keycloak}, or
         * {@code oidc}. Resolved by {@code AuthProviderRouter} against every registered
         * {@code IAuthProvider} bean — switching providers is a config change, not a Spring
         * profile change. Mirrors UAM's {@code railcab.uam.identity-provider.type} so both
         * services configure identity the same way.
         */
        private String type = "hmac";
    }

    @Data
    public static class Oidc {
        /** RFC 7662 token introspection endpoint of any generic OIDC provider (Auth0, Okta, Azure AD, ...). */
        private String introspectionUri = "";
        private String clientId = "";
        private String clientSecret = "";
        /** Introspection response claim holding the caller's subject/user id. */
        private String subjectClaim = "sub";
        /**
         * Introspection response claim holding the caller's role(s) — either a JSON array or a
         * comma/space-separated string, unlike Keycloak's fixed {@code realm_access.roles} path.
         */
        private String rolesClaim = "roles";
        /**
         * Normalised provider role name (lower-case, hyphens→underscores) → RailCab role name.
         * Unlike Keycloak's hardcoded map, this is configurable since "generic OIDC" covers many
         * vendors with different role-naming conventions.
         */
        private Map<String, String> roleMap = new LinkedHashMap<>();
        /** How long an introspection result is cached before re-checking with the provider. */
        private int introspectionCacheTtlSeconds = 15;
    }

    @Data
    public static class Internal {
        /** Comma-separated list of allowed internal service secret keys (inbound from services). */
        private String secretKeys = "";
        /** Secret value the gateway injects as X-Gateway-Token on every forwarded request.
         *  Downstream services verify this header to confirm the request came through the gateway. */
        private String gatewayForwardKey = "";
    }

    @Data
    public static class Cors {
        /** Comma-separated list of allowed origins for CORS. */
        private String allowedOrigins = "http://localhost:4200,http://localhost:3000";
        /**
         * Comma-separated list of allowed origins for payment gateway callback postbacks
         * (/api/payment/callback/**) specifically. Scoped separately from allowedOrigins because
         * gateway redirect chains (Razorpay/PayU) commonly arrive with a literal "null" Origin
         * header (browsers strip the origin on certain cross-origin redirect hops) — allowing
         * "null" globally would be too permissive, so it's confined to just this route.
         */
        private String paymentCallbackAllowedOrigins =
                "null,https://checkout.razorpay.com,https://api.razorpay.com,https://payu.in,https://*.payu.in,"
                        + "https://*.toucanpay.in,https://*.testtoucanpay.in";
    }

    @Data
    public static class Swagger {
        /**
         * Comma-separated list of origins allowed to frame the centralized Swagger UI
         * (/swagger-ui.html, /v3/api-docs, and every /api/&#42;/... proxied variant) via
         * Content-Security-Policy's frame-ancestors directive. Scoped to just those paths (see
         * GatewaySecurityConfig's dedicated swaggerSecurityWebFilterChain) rather than relaxed
         * gateway-wide, since RailCab_Web embeds it inline in an iframe.
         */
        private String frameAncestors = "http://localhost:4200";
    }

    @Data
    public static class Rbac {
        /** Set false to disable route-level authorization checks (e.g., during migration). Read
         *  by {@code PrivilegeBasedAccessFilter} -- the old role-string path-roles map is gone,
         *  replaced entirely by privilege.json's numeric, data-driven path authorization. */
        private boolean enabled = true;
    }

    @Data
    public static class RateLimit {
        /** Set false to disable Redis-backed rate limiting (e.g., no Redis available). */
        private boolean enabled = true;
        /** Tokens added to the bucket per second for general API requests. */
        private int replenishRate = 50;
        /** Maximum burst capacity for general API requests. */
        private int burstCapacity = 100;
        /** Tokens per second for auth endpoints (login, refresh). */
        private int authReplenishRate = 5;
        /** Burst capacity for auth endpoints. */
        private int authBurstCapacity = 10;
        /** Password login only: tokens per second for each client address. */
        private int loginReplenishRate = 2;
        /** Password login only: burst capacity for each client address. */
        private int loginBurstCapacity = 5;
        /**
         * Password login only: tokens per second for ALL callers together. Keep it at or a little above what UAM can hash per
         * second (about 4 logins/s per CPU at bcrypt cost 11), so an overload gets a 429 here instead of a 503 from UAM.
         */
        private int loginGlobalReplenishRate = 10;
        /** Password login only: burst capacity for all callers together. */
        private int loginGlobalBurstCapacity = 20;
        /** Public live-trip tracking ({@code GET /api/v1/ride/public/track}, no sign-in): tokens per second per client address. */
        private int publicTrackReplenishRate = 1;
        /** Public live-trip tracking: burst capacity per client address. */
        private int publicTrackBurstCapacity = 5;
        /**
         * Token refresh only ({@code POST /api/v1/uam/identity/auth/refresh}): tokens per second per client address. Higher than the
         * auth surface because many phones can share one carrier NAT address and a refused refresh logs the user out.
         */
        private int refreshReplenishRate = 30;
        /** Token refresh: burst capacity per client address. */
        private int refreshBurstCapacity = 60;
        /**
         * Public account sign-up only ({@code POST /api/v1/uam/identity/auth/register}): sign-ups allowed per client address per
         * hour (1 to 3600). Sign-up answers 409 for a phone or email that already has an account, so this budget is what stops
         * someone checking a list of numbers. Not lower: many phones share one carrier NAT address.
         */
        private int registerPerHour = 20;
        /** Public account sign-up: how many sign-ups one client address may make back to back before the hourly budget applies. */
        private int registerBurst = 10;
        /**
         * How many proxies in front of the gateway add to {@code X-Forwarded-For} (1 = nginx directly in front, 2 = a load balancer
         * and nginx). 0 = ignore the header and use the connection's address. Only trusted when the connection is from an
         * internal address, see {@link ClientIpResolver}.
         */
        private int trustedProxyHops = 1;
    }

    @Data
    public static class Request {
        /** Maximum allowed Content-Length in bytes (default 10 MB). */
        private long maxBodySizeBytes = 10 * 1024 * 1024L;
    }

    @Data
    public static class Cache {
        /** TTL for account-status cache entries (seconds). Applies to admin, customer, and driver lookups. */
        private int userStatusTtlSeconds = 60;
        /** Max number of userId entries in the account-status Caffeine cache. */
        private int userStatusMaxSize = 10000;
        /** TTL for role -> privilege-numbers cache entries (seconds) -- how stale a role's
         *  permissions can be before {@code PrivilegeBasedAccessFilter} re-reads them from UAM's
         *  database. Short by design: this is meant to track "current" permissions closely. */
        private int rolePrivilegeTtlSeconds = 30;
        /** Max number of role-code entries in the role-privilege Caffeine cache. */
        private int rolePrivilegeMaxSize = 1000;
    }
}
