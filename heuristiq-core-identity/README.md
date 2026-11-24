# Rider UAM (User & Access Management)

Copied into `bestride-rider-app` from `2026_RailCab_UAM` (untouched). Port (9003) and
context-path (`/api/v1/uam`) already match `Rider_ApiGateway`'s existing `/api/uam/**` route
exactly, so no config drift to fix here (unlike Rider_AdminAPI/Rider_BookingsAPI's ports, which
needed correcting in the gateway copy). `spring.application.name` was deliberately left as
`RAILCAB-UAM` rather than renamed, since the gateway's route resolves to that Eureka service-id
when discovery is enabled.

**Security note:** the source repo bundled a real Firebase service-account private key at
`src/main/resources/firebase-service-account.json` (gitignored there, but a real secret on disk).
It was deleted from this copy rather than carried over — supply your own via
`FIREBASE_ADMIN_CREDENTIALS_PATH` or `FIREBASE_ADMIN_CREDENTIALS_BASE64` instead. Treat the
original key as exposed and rotate it if this repo or copy is ever shared.

## 2026 RailCab UAM (User & Access Management)

Identity, authentication, and RBAC/ABAC authorization service. Every other RailCab service trusts JWTs this service mints — it is the highest-blast-radius component in the platform. See [`uam.md`](./uam.md) and [`AUTH_PROVIDER_CONFIGURATION.md`](./AUTH_PROVIDER_CONFIGURATION.md) for prior design notes (the latter is **stale** relative to current routes — see Known Issues).

## Tech Stack

| Layer          | Technology                                                              |
|----------------|--------------------------------------------------------------------------|
| Framework      | Spring Boot 4.1.0                                                        |
| Language       | Java 21                                                                   |
| Database       | MongoDB (default) or MySQL/JPA + Flyway (profile-gated)                  |
| Auth           | JWT (jjwt HS256), OAuth2 resource-server mode for external IdP validation |
| Identity providers | Local, Keycloak, generic OIDC, Google Sign-In (Firebase), phone/email OTP |
| MFA            | TOTP (`dev.samstevens.totp`)                                              |
| Device parsing | `UserAgentUtils`                                                          |

## Project Structure

```
src/main/java/com/railcab/uam/
├── application/         # Apps/tenants + API clients
├── auth/                # AuthService, IdentityAuthController
├── bootstrap/           # Seed super-admin
├── device/ session/ mfa/ otp/ lockout/ securitypolicy/ audit/  # Security & account-safety modules
├── identity/            # + keycloak/, oidc/ — provider strategy implementations
├── google/              # Firebase Google ID-token verification (separate from the identity-provider strategy)
├── groups/ role/ permissions/  # Group-mediated RBAC
├── organization/ application/  # Tenant/App/Module/Resource — ABAC-leaning scoping
├── feature/             # management/ + observability/ — feature flags
└── (each module)/persistence/{jpa,mongo}/, repository/{jpa,mongo}/  # Dual-backend, per module
```

## Domain Model & Authorization

- **Identity**: `User`, `UserCredential`, `UserLoginSecurity`, `PasswordHistory`, `UserSession`, `UserDevice`, `UserMfa`, `ApiClient`.
- **RBAC (group-mediated)**: `Permission → RolePermission → Role`; `Group ↔ UserGroup ↔ GroupRole` — roles are assigned to groups, users are assigned to groups (not directly to roles).
- **Tenant/ABAC scoping**: `Tenant`, `Application`, `Module`, `Resource`, `PermissionAction` add resource/action granularity beyond plain RBAC.
- Every entity above is dual-persisted (Mongo doc + JPA entity), selected by `railcab.uam.persistence.type`.

## Auth Providers

`IdentityProviderType` enum (`none`, `keycloak`, `oidc`, `okta` placeholder) + `IdentityProviderAuthRouter`/`SyncRouter` dispatch to `IdentityProviderAuthService` implementations by Spring bean lookup (a clean Strategy pattern, no if/else chains). **Google Sign-In and OTP are separate, parallel mechanisms outside this strategy** — see Known Issues.

`AUTH_PROVIDER_CONFIGURATION.md` describes routes (`/auth/login`, `/auth/provider/login`, etc.) that have since been consolidated into a single `IdentityAuthController` at `/identity/auth/{login,refresh,logout}`, which internally dispatches based on request fields (`idToken`/`providerType` presence).

## Tokens

`JwtService` (jjwt, HS256) builds claims: `sub`, `tenantId`, `username`, `email`, `fullName`, `userType`, `role`, `roles[]`, `iss=railcab-uam`, 60-min default expiry. `JwtAuthenticationFilter` parses the Bearer header into a Spring `Authentication`. Refresh tokens are random strings persisted via `RefreshToken`/`RefreshTokenService` (30-day default), rotated on refresh, revoked on logout.

### Refresh tokens

Only the SHA-256 hash of a refresh token is stored. Each sign-in starts a **family** (`family_id` = the first token's id); every rotation keeps the family.

- **Rotation** (`POST /api/v1/uam/identity/auth/refresh`): the presented token is marked `ROTATED` by a conditional update (`... WHERE id = ? AND revoked = false`, a single-document update in Mongo), so of two racing refreshes exactly one rotates. The successor is stored first, then linked (`successor_token_id`, `revoked_at`). Its raw value is `HMAC(key, presentedRaw | successorTokenId)`, with the key derived from `railcab.uam.auth.jwt-secret` and a fixed label, so the server can recompute it from the previous token but nobody else can.
- **Grace window**: a `ROTATED` token presented again within `railcab.uam.refresh-token.reuse-grace-seconds` (default 30, 0 = off, max 300) gets a fresh access token plus the family's **current** refresh token (the same one the winning request got; the successor links are followed). Nothing new is minted. Each rotated token may do this `railcab.uam.refresh-token.max-grace-redemptions` times (default 1). This covers a lost refresh response and two refreshes racing. The loser of a race returns this same result.
- **Reuse detection**: a `ROTATED` token presented after the window, or more often than allowed, is treated as theft. Every active token of its family is revoked (`REUSE_DETECTED`) and the request fails. For older tokens with no family, the tokens of the same sign-in session are revoked.
- **Logout / admin revoke** (`REVOKED_BY_ADMIN`) tokens fail as before, with no grace.
- **Failure response**: every refused refresh (unknown, expired, revoked, reused) returns **HTTP 401** with the standard envelope, `success=false` and `errorCode=REFRESH_TOKEN_INVALID`. Clients must send the user to sign in again on this response. A missing token in the body is still a 400 validation error. Network errors and 5xx responses are not a reason to sign the user out.
- Schema: `V39__refresh_token_rotation_family.sql` (columns), `V40__refresh_token_family_index.sql` (`idx_refresh_tokens_family`, built CONCURRENTLY). Mongo documents get the same fields, and `familyId` is indexed.

## Cross-Service Token Validation

**No JWKS or shared library exists.** The same `JWT_SECRET` value is copy-pasted into the API Gateway (`HmacJwtAuthProvider`), AdminAPI (`GatewayAuthFilter`), and BookingsAPI (`JwtProperties`) — each service reimplements its own jjwt parsing independently. This is the single largest cross-cutting duplication in the platform; rotating the secret requires a coordinated redeploy of all four services simultaneously.

## Key Endpoints (24 controllers)

`IdentityAuthController` (login/register/phone-otp/email-otp/reset-password/refresh/logout), `UserController`/`UserProfileController`, `RoleController`, `PermissionController`, `GroupController`, `RefreshTokenController`, `UserSessionController`, `UserMfaController`, `UserDeviceController`, `UserLoginSecurityController` (lockout), `OrganizationController`, `ApplicationController`, `AuditLogController`, `SecurityPolicyController`, `NotificationTemplateController`, `UserPreferenceController`, and others.

## Franchises (moved)

Franchises (units, per-application terms, plans, agent-role links, agents and their mails) no longer live in UAM: they moved to `Rider_FranchiseService` (`/api/v1/franchise/**`, see `docs/FRANCHISE_SERVICE_EXTRACTION.md`). The franchise tables created by migrations V23-V27 are kept for now (a later migration drops them once the data copy is verified); no code reads them. UAM keeps two franchise-related things:

- the `franchiseIds` token claim, fetched on sign-in from `GET {franchise}/api/v1/franchise/internal/claims` (`RemoteFranchiseClaims`; fail-closed, so a franchise outage fails the sign-in instead of issuing an unrestricted token). Settings `railcab.uam.franchise.service.enabled` (default `true`; `false` = no claims, for setups without the franchise service) and `railcab.uam.franchise.service.base-url` (plus timeout/attempts/concurrency);
- Deploy note: `UAM_FRANCHISE_SERVICE_BASE_URL` must be set in every environment (the default is `http://localhost:9124` and UAM uses direct service URLs, not the registry). Only users holding a role that matches `railcab.uam.franchise.service.lookup-role-prefixes` (env `UAM_FRANCHISE_LOOKUP_ROLES`, default `FRANCHISE_OWNER,FRANCHISE_AGENT`; case-insensitive; a role matches a prefix when it is that code or that code + `_` + a suffix, so the fixed per-application roles `FRANCHISE_OWNER_<APP>` / `FRANCHISE_AGENT_<APP>` match but `FRANCHISE_OWNERSHIP_AUDITOR` does not) are looked up at sign-in and refresh, so the franchise service must be up for those users to sign in; everyone else (super admins, customers, drivers) has no lookup and no dependency on it. With `railcab.uam.franchise.claims-by-model` on (env `UAM_FRANCHISE_CLAIMS_BY_MODEL`, default `false`), everyone signing in to an organization whose operating model is FRANCHISE or HYBRID today (organization time zone, read in-process from the operating-model use case) is looked up as well, whatever their roles; role-prefix holders stay looked up in every model. Turn it on only after every organization with franchises has been set to FRANCHISE or HYBRID.
- **A franchise role needs an ACTIVE franchise.** The claims lookup answers only ACTIVE franchises (closed and suspended ones are left out by the franchise service). When a franchise role holder's lookup answers none, at sign-in and at every refresh (`JwtService.sessionAccess`, applied in `LoginSessionService`): their franchise roles are left out of the token's `roles`/`role`/`userType`, its `apps` claim and the login response, so the gateway grants none of those roles' privileges (a privilege another kept role grants stays); `GET /api/v1/uam/identity/me/privileges` also counts only the roles the caller's token carries (its cache entry is keyed by them). If no role is left, or the application named in `X-Application-Code` was opened only by those roles, the sign-in or refresh is refused before any session is created: **403, `errorCode` `FRANCHISE_INACTIVE`** (`AuthConstants.ERROR_FRANCHISE_INACTIVE`), audited as a failed sign-in; Rider_Web's login screen shows `login.messages.franchiseInactive`. This holds whatever `claims-by-model` says. A franchise service that is down still fails the sign-in or refresh (503), never "inactive"; with `railcab.uam.franchise.service.enabled=false` no role is taken away. Stored roles are never changed. See `docs/OPERATING_MODEL_DESIGN.md` section 3.
- the internal API `/api/v1/uam/internal/franchise-access/**` (service token only) that the franchise service uses to create logins and roles, add organization members, subscribe organizations and grant application access. FRANCHISE-typed `org_unit` rows from before the move are harmless but still appear in the organization builder until they are removed.
  `POST /roles/ensure` is additive for the fixed franchise roles only (`railcab.uam.franchise-access.fixed-role-bases`, default `FRANCHISE_OWNER,FRANCHISE_AGENT`, each also followed by `_<APPLICATION>`): it creates a missing role and adds any requested privilege an existing role lacks (`UamRoleService.addPermissions`), never removing a privilege or changing name, description or flags, and answers 500 if the role still lacks one afterwards. Privileges must be exactly one of `railcab.uam.franchise-access.allowed-privilege-prefixes` (default: the nine owner privileges `FRANCHISE_MINE_LIST`, `FRANCHISE_AGENT_LIST`, `FRANCHISE_AGENT_CREATE`, `FRANCHISE_AGENT_REQUEST_CREATE`, `FRANCHISE_MEMBER_LIST`, `LEDGER_ENTRY_MINE_LIST`, `COMMISSION_STATEMENT_MINE_READ`, `PAYOUT_REQUEST_CREATE`, `PAYOUT_REQUEST_MINE_LIST`, which include the four agent ones, plus the four own-wallet statement privileges `WALLET_SELF_STATEMENT_READ`, `_MONTH_LIST`, `_SUMMARY_READ`, `_EXPORT_READ`). The same API also answers three read-only questions for the franchise service: `GET /tenants/{tenantId}/subscriptions` (`{codes}`: the applications the organization uses today, upper case, sorted; the set-up checklist), `GET /tenants/{tenantId}/subscriptions/{applicationCode}` (`{subscribed, organizationName, applicationName}`: an ACTIVE, unexpired subscription of that organization only; it never subscribes) and `GET /tenants/{tenantId}/users/{userId}/privileges/{privilege}` (`{granted}`: an ACTIVE member holding the privilege through a shared or own-organization role). The franchise service no longer calls `POST .../subscriptions` or `POST .../application-grants`, so a leaked service token cannot grant anything else.

## Configuration

`server.port=9003`, context-path `/api/v1/uam`. Mongo default (`railcab_uam` db), MySQL/Flyway alternative. JWT secret/expiry/issuer, OTP expiry, lockout thresholds, identity-provider type/config, Keycloak/OIDC client settings, Firebase credentials path.

OTP hardening (`railcab.uam.otp.*`): `UAM_OTP_MAX_VERIFY_ATTEMPTS` (default 5 wrong guesses, then the OTP is invalidated), `UAM_OTP_RESEND_COOLDOWN_SECONDS` (30), `UAM_OTP_MAX_SENDS_PER_HOUR` (5; 429 beyond either limit). OTPs are stored as salted SHA-256 hashes. `UAM_OTP_EXPOSE_OTP=true` returns the OTP in the API response and the service refuses to start unless the active profiles are limited to `local`/`dev`/`test` (plus `postgres`/`vault`). Users with MFA enrolled must send `totpCode` on OTP verify and Google login (otherwise they get the same `mfaRequired` challenge password login returns).

## Audit publishing (outbox)

UAM keeps no audit store; every domain audit event (login, role change, ...) goes to Rider_AuditService (`POST /api/v1/audit/internal/events`, idempotent on `eventId`) through a database **outbox**, package `com.heuristiq.uam.centralaudit`.

- `CentralAuditLogService.record()` maps the entry to an event and `CentralAuditPublisher.publish()` inserts it into `central_audit_outbox` (Flyway V28; a Mongo collection of the same name in mongo mode). The insert joins the business transaction when one is active (PostgreSQL), otherwise it commits immediately. It never fails the request: an insert error is logged at ERROR and counted (`uam.audit.outbox.insert.failed`).
- A scheduled drain (`flush()`, every `interval-ms`) claims due rows under a lease (`locked_until` + `lock_token`, `lease-ms`), so several UAM instances never send the same row, pushes them in batches through `ResilientCaller` and deletes them only after a 2xx. On failure the rows stay, with exponential backoff (`backoff-initial-ms` doubling up to `backoff-max-ms`; metric `uam.audit.outbox.send.failed`). An instance that dies mid-push only delays its rows until the lease expires; a retried batch is harmless because of the idempotent ingest.
- The table is bounded by `max-rows`. Over the limit the drainer drops only the oldest rows **older than `retention-hours`**, logs at ERROR and counts (`uam.audit.outbox.dropped`, `uam.audit.outbox.overflow`); fresh events are never dropped, so the table can exceed `max-rows` during a long audit outage.
- The tenant id of the event travels in the payload and in a `tenant_id` column.

| Property (`railcab.uam.audit.publish.*`) | Default | Meaning |
|---|---|---|
| `enabled` | true | store and push events |
| `base-url`, `service-token` | audit service, `RIDER_INTERNAL_SERVICE_TOKEN` | target and credential (nothing is sent while the token is blank) |
| `max-rows` | 100000 | outbox bound (see above) |
| `retention-hours` | 168 | age after which a row may be dropped when over `max-rows` |
| `batch-size`, `interval-ms` | 100, 2000 | rows per push, delay between drains |
| `lease-ms` | 60000 | reservation of a claimed batch |
| `backoff-initial-ms`, `backoff-max-ms` | 2000, 300000 | retry delay |
| `call-timeout-ms`, `max-attempts`, `max-concurrent-calls` | 3000, 2, 5 | resilient client |

## Docker

**No Dockerfile or docker-compose.yml exists for this service** — unlike every other RailCab service. Add one before this service can be containerized alongside the rest of the platform.

## Testing

Thin: `PermissionCatalogSeedingIntegrationTest`, `FlywayMigrationValidationTest`, `JpaEntityMappingTest`, `PasswordServiceTest`, `UserTypeResolverTest`. **No tests exist for `AuthService`, `JwtService`/its filter, OTP, Google auth, or the identity-provider routers** — the classes every other service's security ultimately depends on.

## Known Issues

- **JWT secret/validation logic duplicated across 4 services** instead of a shared library or JWKS/introspection endpoint.
- **No visible rate limiting on `/identity/auth/login`** beyond the `UserLoginSecurity` lockout counter — no request-level throttle to slow brute-force/credential-stuffing at the network layer.
- **`AUTH_PROVIDER_CONFIGURATION.md` is stale** relative to the consolidated `IdentityAuthController` routes.
- **Google auth sits outside the `IdentityProviderType` strategy** — a second, inconsistent extension mechanism alongside OTP.
- **Every domain module carries parallel Mongo+JPA persistence** — real duplication/maintenance burden if only one backend is active per deployment.
- **No Dockerfile** — a gap relative to every other service in this platform.
- **Near-zero test coverage on auth-critical classes.**

## Design Health Assessment

*Scored 1 (weak) – 5 (strong) against what the current code does — not a formal audit.*

| Dimension | Score | Why |
|---|:---:|---|
| Scalability | 3/5 | Stateless JWT auth scales horizontally fine, but there's no distributed rate limiting on login, and no Dockerfile means it isn't yet deployable the same way as the rest of the platform. |
| Flexibility / Extensibility | 3/5 | `IdentityProviderType` + router is a clean Strategy pattern for new IdPs, but Google Sign-In and OTP bypass it entirely as special cases, so "add an auth method" doesn't have one consistent answer. |
| DRY | 1/5 | The JWT secret and its validation/parsing logic are copy-pasted into three other services — the single biggest cross-cutting duplication anywhere in this codebase. |
| KISS | 3/5 | Group-mediated RBAC plus Tenant/Application/Module/Resource/PermissionAction is real ABAC-level complexity — likely justified by requirements, but heavy for a service still missing basic deployment tooling. |
| SOLID | 3/5 | The identity-provider strategy itself is a good DIP example; Google auth living outside it breaks the substitutability the pattern is meant to provide. |
| Test Coverage | 1/5 | No tests for `AuthService`, `JwtService`, its filter, OTP, or the provider routers — the classes with the largest blast radius in the entire platform are the least tested. |

## Notes for TDD / LLD Authors

- **Bounded context**: UAM should be the single source of truth for identity, authentication, and authorization decisions — every other service should *consume* its tokens/claims, never re-derive them independently. The current duplication (see DRY score) violates this and should be called out explicitly as a design risk in any TDD/LLD for this service.
- **Non-functional requirements to spec**: JWT secret rotation procedure (currently requires coordinated redeploy across 4 services — document the blast radius and target a JWKS-based or introspection-based model instead), login rate-limit thresholds independent of account lockout, and MFA/OTP delivery SLAs (NMS dependency).
- **Sequence diagrams worth drawing**: local login → JWT issuance → downstream validation (showing every service that currently re-implements this), external-provider login (Keycloak/OIDC/Google) → token exchange, and refresh-token rotation.
- **Scalability**: prioritize a Dockerfile/compose entry for this service — it's the identity provider for the whole platform and currently can't be run the same way as everything else.
- **Flexibility**: fold Google Sign-In (and ideally OTP) into the `IdentityProviderType` strategy so "add a login method" has one consistent extension point instead of three different mechanisms (strategy router, Google-specific module, OTP-specific module).
- **DRY**: this is the highest-value fix in the whole platform — replace the copy-pasted JWT secret/validation logic in Gateway/AdminAPI/BookingsAPI with either a shared library or a JWKS/introspection endpoint UAM exposes and the others call. It removes an entire class of "someone updated the secret in one place but not the other three" incidents.
- **KISS**: before adding further ABAC granularity (Tenant/Module/Resource/PermissionAction), confirm which parts are actually consumed by callers today — unused generality here is expensive given the near-zero test coverage protecting it.
- **SOLID**: extend `IdentityProviderType`/`IdentityProviderAuthService` for new providers (Liskov-substitutable) rather than adding parallel special-cased modules; this keeps the router the single place that knows "how many ways can someone log in."
- **Test coverage**: `JwtService`, `AuthService`, and the identity-provider routers should be the first things covered — a regression here breaks authentication platform-wide, not just for UAM's own endpoints.

`railcab.time.legacy-zone` (env `RAILCAB_TIME_LEGACY_ZONE`, IANA id, default `UTC`): the zone in which this service wrote its zone-less timestamps; informational for now, a startup log line and the gauge `railcab.time.legacy_zone.mismatch` warn when the JVM zone's offset differs (see docs/TIMEZONE_BACKEND_PLAN.md, Stage A.1).
