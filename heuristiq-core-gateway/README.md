# Rider API Gateway

Copied into `bestride-rider-app` from `2026_RailCab_ApiGateway` (the original RailCab platform gateway — Rider_BookingsAPI and Rider_AdminAPI are that platform's `RAILCAB-BOOKINGSAPI`/`RAILCAB-ADMIN-API` under different names). The original repo is untouched; this copy additionally routes to the 5 microservices extracted from Rider_BookingsAPI per the Rider App MVP HLD's Option B (see "Rider microservices" in the Routing Map below). Admin/bookings static-URL port defaults were corrected here to match this repo's actual services (9005/9006 — see RouteConfig).

Reactive edge service (Spring Cloud Gateway / WebFlux) that fronts every RailCab backend. It terminates auth, applies RBAC, rate-limits, and routes to the downstream services.

## Tech Stack

| Layer          | Technology                                                              |
|----------------|--------------------------------------------------------------------------|
| Framework      | Spring Boot 4.0.7 + Spring Cloud Gateway (WebFlux, reactive)             |
| Language       | Java 21                                                                   |
| Routing        | `spring-cloud-gateway-server-webflux`, Java `RouteLocator` (not YAML)     |
| Auth           | JWT (jjwt HMAC-SHA256) + pluggable Keycloak / generic OIDC introspection  |
| Resilience     | Resilience4j (circuit breaker + fallback) per downstream route           |
| Rate limiting  | Redis-backed `RequestRateLimiter` (per user/IP)                          |
| Cache          | Caffeine (introspection + user-status + role-privilege cache)            |
| Persistence    | MongoDB (default) or MySQL/JPA — read-only mirror of UAM roles/privileges|
| Build          | Gradle 8.8, Spring Cloud BOM `2025.1.0` ("Oakwood")                       |

> Spring Boot is pinned below 4.1.0 because Spring Cloud 2025.1.0 doesn't support it yet — bump both together when Spring Cloud catches up.

## Project Structure

```
src/main/java/com/railcab/gateway/
├── auth/
│   ├── filter/       # ReactiveJwtAuthenticationFilter — stateless JWT/internal-key auth
│   ├── provider/     # IAuthProvider strategy: Hmac / Keycloak / Oidc + AuthProviderRouter
│   ├── security/     # GatewaySecurityConfig — reactive SecurityWebFilterChain, CORS
│   ├── service/       # UserStatusCacheService (Caffeine-backed active/inactive lookup)
│   └── util/          # AuthErrorMapping, SecurityUtils (internal-key checks)
├── common/            # Constants, GatewayErrorResponse
├── config/            # GatewayProperties, RouteConfig, RateLimiterConfig, PathMatchersConfig
├── filter/            # CorrelationIdFilter, PrivilegeBasedAccessFilter (RBAC), RequestSizeLimitFilter
├── identity/ role/ privilege/   # Dual Mongo+JPA read-only mirrors of UAM's user/role/permission data
└── web/               # FallbackController (circuit-breaker fallback responses)
```

## Routing Map

All routes add `X-Forwarded-By: railcab-gateway` and dedupe CORS headers; each has a named Resilience4j circuit breaker with a `/fallback/*` route.

| Prefix              | Downstream           | Default port | CB name    |
|----------------------|-----------------------|:---:|------------|
| `/api/admin/**`      | Rider_AdminAPI        | 9005 | admin-cb  |
| `/api/bookings/**`   | Rider_BookingsAPI     | 9006 | bookings-cb |
| `/api/irctc/**`      | IRCTC API Integration (not present in this repo) | 9002 | irctc-cb  |
| `/api/uam/**`        | UAM (not present in this repo) | 9003 | uam-cb    |
| `/api/dms/**`        | DMS (not present in this repo) | 9004 | dms-cb    |
| `/api/nms/**`        | NMS (not present in this repo) | 9005 | nms-cb    |
| `/api/payment/**`    | PaymentAPI (not present in this repo — a different service than Rider_PaymentService below) | 9006 | pay-cb (⚠ no resilience4j instance configured — falls back to library defaults) |

### Rider microservices (added in this copy)

| Prefix                  | Downstream              | Default port | CB name           |
|--------------------------|--------------------------|:---:|--------------------|
| `/api/rider/pricing/**`  | Rider_PricingService     | 9010 | rider-pricing-cb  |
| `/api/rider/location/**` | Rider_LocationService    | 9011 | rider-location-cb |
| `/api/rider/dispatch/**` | Rider_DispatchService    | 9012 | rider-dispatch-cb |
| `/api/rider/ride/**`     | Rider_RideService        | 9013 | rider-ride-cb     |
| `/api/rider/payment/**`  | Rider_PaymentService     | 9014 | rider-payment-cb  |
| `/api/rider/customer/**` | Rider_CustomerService    | 9016 | rider-customer-cb |

`Rider_CustomerService` consolidates Customer profile + Rating/feedback + Support tickets — deliberately one service, not three (see its own README for why).

Static-URL routing only (`railcab.gateway.rider-services.discovery-enabled=false` by default) — none of the 5 services register with Eureka yet. The Razorpay webhook (`/api/rider/payment/payments/webhook`) bypasses JWT auth, same as the platform's existing `paymentCallback` bypass (see `pathmatchers.rider-payment-webhook`).

## Auth & Access Control

1. **`ReactiveJwtAuthenticationFilter`** — strips forgeable identity headers, allows public paths (`PathMatchersConfig`), validates internal service calls via `X-Internal-Secret-Key`, otherwise validates the Bearer JWT through `AuthProviderRouter` (HMAC first, then the configured provider), checks `UserStatusCacheService`, and injects `X-User-Id` / `X-User-Role` / `X-Gateway-Token` for downstream services to trust.
2. **`PrivilegeBasedAccessFilter`** — data-driven RBAC sourced from `privilege.json` (privilegeId + path + operation), resolved against live role→privilege sets cached from UAM. Bypassed for `ROLE_INTERNAL_SERVICE`.
3. **Rate limiting** — `apiRateLimiter` (50 req/s, burst 100) and `authRateLimiter` (5 req/s, burst 10), Redis-backed, keyed by `X-User-Id` else remote IP.

## Configuration Highlights (`application.yml`)

- `server.port`: `${GATEWAY_PORT:8999}`
- `services.*` downstream URLs, each overridable via `*_API_URL` env vars
- `railcab.gateway.*`: `persistence.type` (mongo|mysql), `auth.jwt-secret` / `customer-jwt-secret`, `identity-provider.type`, `oidc.*`, `internal.secret-keys`, `cors.*`, `rate-limit.*`, `request.max-body-size-bytes`, `cache.*`, `rbac.enabled`
- Profiles: `dev` (Redis autoconfig excluded, rate limiting off), `keycloak` (verbose logging), `prod` (RBAC/rate-limit forced on, IRCTC simulator path disabled), `mysql` (JPA/MySQL backend for role/privilege mirror)

## Getting Started

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

`docker-compose.yml` brings up MongoDB, Redis, and the gateway together (healthcheck against `/actuator/health`).

## Known Issues to Resolve Before Production Hardening

- **Dockerfile builds with JDK/JRE 17** while `build.gradle` targets a Java 21 toolchain — align both.
- **Port defaults disagree between code and config**: `RouteConfig` Java defaults (uam :9004, dms :9006, nms :8087) don't match `application.yml` (uam :9003, dms :9004, nms :9005). Confirm the real values before deployment.
- **`docker-compose.yml` env var names** (`ADMIN_SERVICE_URL`, `BOOKINGS_SERVICE_URL`, `IRCTC_SERVICE_URL`) don't match the `services.*` / `*_API_URL` naming used by `application.yml`, and don't cover uam/dms/pay/nms at all.
- **`pay-cb`** has no explicit Resilience4j instance config — verify it behaves correctly under the library defaults or add one.

## Design Health Assessment

*Scored 1 (weak) – 5 (strong) against what the current code actually does — not a formal audit.*

| Dimension | Score | Why |
|---|:---:|---|
| Scalability | 4/5 | Fully stateless reactive filter chain + Redis-backed rate limiter and cache scale horizontally without sticky state. |
| Flexibility / Extensibility | 4/5 | `IAuthProvider` strategy and config-driven `RouteConfig` make adding an IdP or downstream route mostly additive. |
| DRY | 3/5 | Downstream URL/port config is defined three different ways (Java defaults, `application.yml`, compose env vars) with drift between them. |
| KISS | 3/5 | Dual Mongo+JPA persistence for the RBAC mirror is real complexity — confirm both are actually needed in production before keeping both. |
| SOLID | 4/5 | Filters and providers are cleanly separated by responsibility; `AuthProviderRouter`'s "try HMAC, then fall back" logic is the one spot mixing resolution strategy with validation. |
| Test Coverage | Not verified | No test directory was inspected during this pass — confirm coverage before relying on this rating for a TDD document. |

## Notes for TDD / LLD Authors

- **Bounded context**: this service owns *edge concerns only* — authentication, authorization, rate limiting, routing. It should never gain business logic; if a rule needs booking/payment domain knowledge, it belongs downstream.
- **Non-functional requirements to spec explicitly**: request throughput ceiling per route, acceptable added latency (gateway hop budget), circuit-breaker open/half-open thresholds per downstream, and the token-validation trust model (who signs, who verifies, rotation procedure).
- **Sequence diagrams worth drawing**: (1) JWT validation + RBAC check + downstream forward, (2) circuit-breaker open → fallback response, (3) new-IdP onboarding through `IAuthProvider`.
- **Scalability**: the Redis dependency is a single point of coordination for rate limiting — document its failure mode (fail-open vs fail-closed) explicitly in the LLD.
- **Flexibility**: treat `privilege.json` and `RouteConfig` as the extension points; any LLD change that requires touching filter internals to add a route/permission is a design smell.
- **DRY**: resolve the service-URL config duplication (see Known Issues) before it causes a prod incident; a single source of truth (e.g., service discovery or one properties file consumed by both app and compose) removes an entire class of "works locally, breaks in prod" bugs.
- **SOLID**: keep `AuthProviderRouter`'s provider selection driven by the token's issuer/type claim rather than sequential trial-and-error, to keep `IAuthProvider` implementations substitutable (LSP) and the router's job single-purpose (SRP).

`railcab.time.legacy-zone` (env `RAILCAB_TIME_LEGACY_ZONE`, IANA id, default `UTC`): the zone in which this service wrote its zone-less timestamps; informational for now, a startup log line and the gauge `railcab.time.legacy_zone.mismatch` warn when the JVM zone's offset differs (see docs/TIMEZONE_BACKEND_PLAN.md, Stage A.1).
