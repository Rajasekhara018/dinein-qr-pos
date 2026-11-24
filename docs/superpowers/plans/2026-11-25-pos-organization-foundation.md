# POS Organization Foundation (Identity) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give `heuristiq-core-identity` the restaurant structure the BRD needs — legal entities per country and branches under a restaurant (tenant), staff assigned to branches, and a `branchIds` claim in the login token — so the POS can move its users, roles and tenancy onto the shared identity service.

**Architecture:** A new hexagonal module `pos` inside identity, copied in shape from `organization/legalprofile` (domain → application + ports → PostgreSQL (JDBC + Flyway) and in-memory adapters → REST controllers → config). Identity and the gateway run on PostgreSQL only (Task 1 makes it the default; MongoDB is no longer used by either). Restaurant = existing `Tenant`. Country rules that identity owns (allowed countries, currency, default locale, financial-year start, postal-code and tax-id formats) live in a `PosCountry` enum; tax *rates* are not here (they belong to dinein-core, plan 3). Branch access for a session is read live from the branch-staff store and also written to the JWT for downstream services.

**Tech Stack:** Java 21, Spring Boot 4.1.0, PostgreSQL (JdbcTemplate + Flyway), JUnit 5, AssertJ, Mockito, zonky embedded-postgres for store tests. Gradle 9.2.1 wrapper.

**Spec:** [docs/BRD.md](../../BRD.md) v0.4 — §2.2 (country table), §2.3 RG-01, RG-02, RG-13, RG-18, RG-21; §3 (roles, account rules); §5.1–5.3 (onboarding, legal entity, branch). Compliance hooks: [docs/COMPLIANCE.md](../../COMPLIANCE.md) GL-05, GL-06.

---

## Where this plan sits (program roadmap)

The BRD is split into one plan per slice. Each produces working, tested software. Only plan 1 is written in detail; each later plan is written when the previous one is merged, against the code as it then stands.

| # | Plan | Module(s) | Depends on |
| --- | --- | --- | --- |
| **1** | **POS organization foundation (this plan)** — legal entities, branches, branch staff, `branchIds` claim, POS app + privileges | identity, gateway (privilege.json copy) | — |
| 2 | dinein-core on identity — accept identity JWTs (shared HS256 secret), map `tenantId`/`branchIds`/roles, add `branch_id` to dinein tables, gateway route `/api/v1/dinein/**` + WebSocket, retire dinein's own login | dinein-core, gateway | 1 |
| 3 | Country packs + tax engine + money — tax categories and dated tax rules (IN GST, DE/FR/UK VAT, US sales tax, AU GST), money as minor units + currency, branch time zone everywhere (remove `Asia/Kolkata`) | dinein-core | 2 |
| 4 | Payments on fin-payment — merchant accounts per legal entity, Stripe + Adyen adapters, currency-aware minor units, generic webhooks, de-couple from rides (`ride_id` nullable, order reference), dinein as a source | fin-payment, dinein-core | 1, 3 |
| 5 | Clients — Angular (`heuristiq-web-dinein`) and Flutter apps log in through identity; locale/currency/time-zone formatting from the branch; remove client-side GST maths | web-dinein, kiosk, counter | 2, 3 |
| 6+ | BRD feature slices — branch settings (hours, holidays, charges, counters/terminals), menu planner, KDS, billing screen, kiosk branding, reports, fiscal adapters (DE TSE, FR certified module) | dinein-core + clients | 3, 4, 5 |

Housekeeping done before this plan (separate commits on the same branch): Java packages now match their folders in every core module (identity is `com.heuristiq.identity`, payment `com.heuristiq.payment`, etc., tests mirror main), and `README.md`, `docker-compose.yml`, `docs/DEPLOYMENT.md` point at `heuristiq-dinein-core/` and `heuristiq-web-dinein/`.

---

## Global Constraints

- Work only in `heuristiq-core-identity` and, in the gateway, its persistence configuration (Task 1) and `privilege.json` copy (Task 5).
- **Packages:** main code under folder `src/main/java/com/heuristiq/identity/pos/...` with package `com.heuristiq.identity.pos...`; tests under `src/test/java/com/heuristiq/identity/pos/...` with the same package. Folder and package always match.
- **Persistence: PostgreSQL only.** Schema by Flyway migration `src/main/resources/db/migration/V44__pos_organization.sql` (V43 is the last existing one). Stores use `JdbcTemplate` + `TransactionTemplate`, in the style of `JdbcLegalProfileStore`. Store beans are `@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")`. No Mongo store, no JPA entity.
- Every store query filters on `tenantId` (tenant isolation, BRD §10).
- Supported countries exactly: `IN`, `DE`, `GB`, `FR`, `US`, `AU` (BRD §2.1; the BRD's "UK" is ISO code `GB`).
- Currency per country exactly: IN→`INR`, DE→`EUR`, GB→`GBP`, FR→`EUR`, US→`USD`, AU→`AUD` (BRD §2.2 "Currency").
- Time zones: IANA region ids only (contain `/`, e.g. `Europe/Berlin`); offsets such as `+05:30` are refused (BRD RG-11; same rule as `OrganizationRegionalSettings`).
- Phones: E.164, regex `^\+[1-9][0-9]{6,14}$` (BRD RG-13).
- Error answers: `PosException` kinds map INVALID→400, CONFLICT→409, NOT_FOUND→404 through a `PosErrors.translated(...)` wrapper identical in shape to `LegalProfileErrors`. Refusals for another tenant's or an unreachable branch's ids answer 404, never 403.
- Message keys start with `pos.` (e.g. `pos.branch.codeTaken`).
- `privilege.json` in `heuristiq-core-identity/src/main/resources/` and `heuristiq-core-gateway/src/main/resources/` must stay byte-identical. New privilege ids start at **1284**, privilege codes at **SHARED_0471**, `service` = `uam`, `module` = `ADMINISTRATION`, `submodule` = `MASTER`.
- Tests run with `./gradlew test` from the module folder (Git Bash; `./gradlew test` in cmd). No Docker: JDBC stores are tested against zonky `EmbeddedPostgres` running the real Flyway migrations (as `LegalProfileStoresTest.jdbcStoreSaves...` does); services with in-memory stores.
- Changes are audited through the existing `AuditLogServiceUseCase` (as `AuditLogLegalProfileAudit` does); an audit failure is logged and never fails the change.

## Review Focus

1. **Another restaurant's ids** — a restaurant admin of tenant A calling `GET /organizations/A/branches/{id of B's branch}` or `/organizations/B/...` must get 404 and see nothing (Task 5 test `anotherTenantsBranchIsNotFound`).
2. **Branch-scoped staff overreach** — a branch admin assigned only to branch X must get 404 for branch Y of the same tenant, and cannot list all branches beyond X (Task 3 test `branchScopedCallerSeesOnlyAssignedBranches`, Task 5 test `branchScopedCallerCannotReachOtherBranch`).
3. **Wrong currency or locale for the country** — e.g. a `DE` legal entity sent with `GBP`, or a branch with locale `en-US` under an `FR` entity, is refused with 400 and the field named (Task 2 tests `currencyMustMatchCountry`, `branchLocaleRegionMustMatchCountry`).
4. **Concurrent edits** — two admins saving the same branch from version 3: the second save is refused 409 `pos.staleVersion` (Task 4 test `updateRefusesStaleVersion`, Task 3 test `staleBranchUpdateIsConflict`).
5. **Changing country after branches exist** — changing a legal entity's country once it has branches would silently re-tax every branch; refused 409 `pos.legalEntity.countryLocked` (Task 3 test `countryCannotChangeOnceBranchesExist`).

---

### Task 0: Baseline

**Files:** none changed.

- [ ] **Step 1: Run the identity test suite on the untouched branch**

Run (from `heuristiq-core-identity` and from `heuristiq-core-gateway`): `./gradlew test --console=plain`
Expected: BUILD SUCCESSFUL. Record any tests that already fail; every later "run the suite" step means "no new failures beyond this list". The composite build needs `../heuristiq-core-resilient-client` present.

---

### Task 1: PostgreSQL as the only database for identity and the gateway

**Files:**
- Modify: `heuristiq-core-identity/src/main/resources/application.properties` — base configuration becomes the PostgreSQL configuration now in `application-postgres.properties` (`railcab.uam.persistence.type=postgres`, the Mongo auto-configuration excludes, datasource, Hikari, JPA `validate`, Flyway enabled); remove the Mongo-only properties (`spring.data.mongodb.*` and the Mongo exclude of DataSource/JPA).
- Modify: `heuristiq-core-identity/src/main/resources/application-postgres.properties` — reduce to a comment saying PostgreSQL is now the base configuration (keep the file so existing `SPRING_PROFILES_ACTIVE=postgres` deployments still start).
- Modify: `heuristiq-core-gateway/src/main/resources/application.properties` and `application-postgres.properties` — same change: `railcab.gateway.persistence.type=postgres`, PostgreSQL datasource pointing at the identity database, Mongo auto-configuration excluded, DataSource/JPA no longer excluded. Keep the `bookings-uri` only if a non-privilege feature still reads it (check usages of `bookings-uri` first; if it is used, leave it and its Mongo template in place and say so in the commit message).
- Modify: `heuristiq-core-identity/build.gradle` — add `testRuntimeOnly 'io.zonky.test.postgres:embedded-postgres-binaries-windows-amd64:14.10.1'` and `'io.zonky.test.postgres:embedded-postgres-binaries-linux-amd64:14.10.1'` next to the existing linux-arm64 line, so embedded-PostgreSQL tests run on developer PCs and x64 CI.
- Test: `src/test/java/com/heuristiq/identity/config/PostgresDefaultConfigurationTest.java` (identity) and `src/test/java/com/heuristiq/gateway/config/PostgresDefaultConfigurationTest.java` (gateway)

**Interfaces:**
- Produces: with no profile active, identity resolves `railcab.uam.persistence.type=postgres` and the gateway `railcab.gateway.persistence.type=postgres`; every later task's JDBC stores therefore load by default.

- [ ] **Step 1: Write the failing tests.** Each loads `application.properties` with `java.util.Properties` (no Spring context) and asserts: persistence type is `postgres`; `spring.autoconfigure.exclude` contains `MongoAutoConfiguration` and does not contain `DataSourceAutoConfiguration`; `spring.flyway.enabled` is `true` (identity only).
- [ ] **Step 2: Run them to verify they fail.** `./gradlew test --tests "*PostgresDefaultConfigurationTest"` in each module. Expected: FAIL (type is `mongo`).
- [ ] **Step 3: Make the property and build changes listed above.**
- [ ] **Step 4: Run the full suites.** `./gradlew test --console=plain` in identity and gateway. Expected: the new tests pass, no new failures against the Task 0 list, and `LegalProfileStoresTest`'s embedded-PostgreSQL test now runs on Windows.
- [ ] **Step 5: Commit**

```bash
git add heuristiq-core-identity heuristiq-core-gateway
git commit -m "chore(identity,gateway): PostgreSQL as the only database"
```

---

### Task 2: Country rules and domain model

**Files:**
- Create: `src/main/java/com/heuristiq/identity/pos/domain/PosCountry.java`
- Create: `src/main/java/com/heuristiq/identity/pos/domain/PosAddress.java`
- Create: `src/main/java/com/heuristiq/identity/pos/domain/PosStatus.java`
- Create: `src/main/java/com/heuristiq/identity/pos/domain/LegalEntity.java`, `LegalEntityDraft.java`
- Create: `src/main/java/com/heuristiq/identity/pos/domain/Branch.java`, `BranchDraft.java`
- Create: `src/main/java/com/heuristiq/identity/pos/domain/PosConstants.java`, `PosException.java`, `PosValidator.java`
- Modify: `src/main/java/com/heuristiq/identity/organization/legalprofile/domain/TaxIdType.java` and `LegalProfileConstants.java` (new tax id types)
- Test: `src/test/java/com/heuristiq/identity/pos/domain/PosValidatorTest.java`, `PosCountryTest.java`; extend `src/test/java/com/heuristiq/identity/organization/legalprofile/domain/LegalProfileValidatorTest.java`

**Interfaces:**
- Produces:
  - `enum PosCountry { IN, DE, GB, FR, US, AU; String currency(); String defaultLocale(); int defaultFinancialYearStartMonth(); boolean regionRequired(); boolean acceptsPostalCode(String); static Optional<PosCountry> of(String isoCode) }`
    - currency / defaultLocale / FY start / regionRequired: IN `INR`,`en-IN`,4,true · DE `EUR`,`de-DE`,1,false · GB `GBP`,`en-GB`,4,false · FR `EUR`,`fr-FR`,1,false · US `USD`,`en-US`,1,true · AU `AUD`,`en-AU`,7,true
    - postal code (after trim + uppercase): IN `^[1-9][0-9]{5}$` · DE `^[0-9]{5}$` · FR `^[0-9]{5}$` · GB `^[A-Z]{1,2}[0-9][A-Z0-9]? ?[0-9][A-Z]{2}$` · US `^[0-9]{5}(-[0-9]{4})?$` · AU `^[0-9]{4}$`
  - `record PosAddress(String line1, String line2, String city, String region, String postalCode, String country)`
  - `enum PosStatus { ACTIVE, INACTIVE }`
  - `record LegalEntityDraft(String country, String legalName, String currency, List<TaxId> taxIds, PosAddress address, String invoicePrefix, Integer financialYearStartMonth)` — `TaxId` is the existing `organization.legalprofile.domain.TaxId`
  - `record LegalEntity(String id, String tenantId, String country, String legalName, String currency, List<TaxId> taxIds, PosAddress address, String invoicePrefix, int financialYearStartMonth, PosStatus status, long version, Instant updatedAt, String updatedBy)`
  - `record BranchDraft(String legalEntityId, String code, String name, String timeZone, String locale, List<String> languages, String phone, String email, PosAddress address)`
  - `record Branch(String id, String tenantId, String legalEntityId, String code, String name, String timeZone, String locale, List<String> languages, String phone, String email, PosAddress address, PosStatus status, long version, Instant updatedAt, String updatedBy)`
  - `class PosException extends RuntimeException { enum Kind { INVALID, CONFLICT, NOT_FOUND }; static PosException invalid(String key, String field); static PosException conflict(String key, String field); static PosException notFound(String key); Kind kind(); String field() }`
  - `final class PosValidator { static LegalEntityDraft legalEntity(LegalEntityDraft); static BranchDraft branch(BranchDraft, LegalEntity owner) }` — returns the normalised draft (trimmed, codes uppercased, defaults filled) or throws `PosException.invalid`.
  - `TaxIdType` gains `VAT_DE` (country DE, `^DE[0-9]{9}$`), `VAT_FR` (country FR, `^FR[0-9A-Z]{2}[0-9]{9}$`), `ABN` (country AU, `^[0-9]{11}$`).

Validation rules `PosValidator.legalEntity` must enforce (field name in brackets is the `PosException.field()`):
- `country` known to `PosCountry` [`country`] — key `pos.country.unsupported`
- `currency` null → country's currency; otherwise must equal it [`currency`] — `pos.currency.mismatch`
- `legalName` 1–200 chars after trim [`legalName`] — `pos.legalName.invalid`
- each tax id: `type.country()` null or equal to `country`, and `type.accepts(value)` [`taxIds[i]`] — `pos.taxId.invalid`
- `address` required; `address.country` equals `country`; `line1` and `city` non-blank; `region` non-blank when `regionRequired()`; postal code accepted [`address.<field>`] — `pos.address.invalid`
- `invoicePrefix` `^[A-Z0-9-]{1,10}$` after uppercase [`invoicePrefix`] — `pos.invoicePrefix.invalid`
- `financialYearStartMonth` null → country default; else 1–12 [`financialYearStartMonth`]

`PosValidator.branch(draft, owner)` rules:
- `owner.status()` ACTIVE [`legalEntityId`] — `pos.legalEntity.inactive`
- `code` `^[A-Z0-9-]{2,20}$` after trim + uppercase [`code`]; `name` 1–120 chars [`name`]
- `timeZone` contains `/` and `ZoneId.of` accepts it [`timeZone`] — `pos.timeZone.invalid`
- `locale` null → owner country's default; else `Locale.forLanguageTag` gives a non-empty language and its country equals owner's country [`locale`] — `pos.locale.mismatch`
- `languages` null/empty → `[language of locale]`; each a 2-letter lowercase ISO 639-1 code [`languages[i]`]
- `phone` E.164 [`phone`]; `email` null or contains one `@` with text either side [`email`]
- `address` as for legal entity, country equal to owner's country [`address.<field>`]

- [ ] **Step 1: Write the failing tests**

`PosCountryTest`: `everySupportedCountryHasItsCurrency()` asserts the six currency pairs above; `ofIsCaseInsensitiveAndRefusesUnknown()` → `of("de")` is DE, `of("IT")` empty, `of(null)` empty; `postalCodes()` → GB accepts `"SW1A 1AA"` and `"M1 1AE"`, US accepts `"94105-1234"`, IN refuses `"012345"`, AU refuses `"20000"`.

`PosValidatorTest` (build drafts with a small helper `validDe()` = DE, "Muster GmbH", null currency, `[TaxId(VAT_DE,"DE123456789")]`, address `("Hauptstr. 1", null, "Berlin", null, "10115", "DE")`, prefix `"ber"`, null FY):
- `fillsCurrencyAndFinancialYearFromCountry()` → currency `EUR`, FY 1, prefix `BER`
- `currencyMustMatchCountry()` → DE with `GBP` throws INVALID, field `currency`
- `unsupportedCountryIsRefused()` → `IT` throws field `country`
- `taxIdOfAnotherCountryIsRefused()` → DE entity with `GSTIN` throws field `taxIds[0]`
- `regionRequiredInIndia()` → IN address without region throws field `address.region`
- `branchDefaultsLocaleAndLanguages()` → FR owner, locale null → `fr-FR`, languages `["fr"]`
- `branchLocaleRegionMustMatchCountry()` → FR owner, locale `en-US` throws field `locale`; `en-FR` is accepted
- `offsetTimeZoneIsRefused()` → `+05:30` throws field `timeZone`; `Asia/Kolkata` accepted
- `inactiveLegalEntityCannotGetBranches()` → throws field `legalEntityId`
- `phoneMustBeE164()` → `"98480 12345"` refused, `"+919848012345"` accepted

`LegalProfileValidatorTest`: add `acceptsNewEuropeanAndAustralianTaxIds()` → `VAT_DE "DE123456789"`, `VAT_FR "FR12345678901"`, `ABN "51824753556"` accepted; `VAT_DE "DE12345"` refused.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests "com.heuristiq.identity.pos.domain.*" --tests "*LegalProfileValidatorTest"`
Expected: compilation failure (classes do not exist).

- [ ] **Step 3: Implement the domain types listed under Interfaces**

Patterns go in `LegalProfileConstants` (tax ids) and `PosConstants` (everything else, plus all `pos.*` message keys and field names). Lists in records are copied with `List.copyOf` in the compact constructor, null → empty.

- [ ] **Step 4: Run the tests to verify they pass**

Run: same command as Step 2. Expected: PASS, and `./gradlew test` still green (existing `TaxIdType` users unaffected).

- [ ] **Step 5: Commit**

```bash
git add heuristiq-core-identity/src
git commit -m "feat(identity): POS country rules, legal entity and branch domain"
```

---

### Task 3: Application service and ports

**Files:**
- Create: `src/main/java/com/heuristiq/identity/pos/application/port/LegalEntityStore.java`, `BranchStore.java`, `BranchStaffStore.java`, `PosOrganizationDirectory.java`, `PosAudit.java`
- Create: `src/main/java/com/heuristiq/identity/pos/application/PosOrganizationUseCase.java`, `PosOrganizationService.java`, `PosCaller.java`
- Test: `src/test/java/com/heuristiq/identity/pos/support/InMemoryPosStores.java` (one class implementing all three store ports), `src/test/java/com/heuristiq/identity/pos/application/PosOrganizationServiceTest.java`

**Interfaces:**
- Consumes: Task 2 types.
- Produces:
  - `interface LegalEntityStore { Optional<LegalEntity> find(String tenantId, String id); List<LegalEntity> list(String tenantId); LegalEntity insert(LegalEntity entity); LegalEntity update(LegalEntity entity, long expectedVersion); }` — `update` throws `PosException.conflict("pos.staleVersion","version")` when the stored version differs.
  - `interface BranchStore { Optional<Branch> find(String tenantId, String id); List<Branch> list(String tenantId); boolean anyForLegalEntity(String tenantId, String legalEntityId); Branch insert(Branch branch); Branch update(Branch branch, long expectedVersion); }` — `insert`/`update` throw `PosException.conflict("pos.branch.codeTaken","code")` when another branch of the tenant has the code.
  - `interface BranchStaffStore { Set<String> staffOf(String tenantId, String branchId); void replaceStaff(String tenantId, String branchId, Set<String> userIds, String actor, Instant at); List<String> branchIdsOf(String tenantId, String userId); }`
  - `interface PosOrganizationDirectory { boolean tenantExists(String tenantId); boolean isActiveMember(String tenantId, String userId); }`
  - `interface PosAudit { void changed(String tenantId, String actorUserId, String entityType, String entityId, Map<String,Object> before, Map<String,Object> after); }`
  - `record PosCaller(String userId, String tenantId, boolean tenantWide)` — `tenantWide` true for SUPER_ADMIN, org-wide callers, and holders of the tenant's admin role; false means branch-scoped.
  - `interface PosOrganizationUseCase`:
    - `List<LegalEntity> legalEntities(PosCaller caller, String tenantId)`
    - `LegalEntity legalEntity(PosCaller caller, String tenantId, String id)`
    - `LegalEntity createLegalEntity(PosCaller caller, String tenantId, LegalEntityDraft draft)`
    - `LegalEntity updateLegalEntity(PosCaller caller, String tenantId, String id, LegalEntityDraft draft, long expectedVersion)`
    - `List<Branch> branches(PosCaller caller, String tenantId)`
    - `Branch branch(PosCaller caller, String tenantId, String id)`
    - `Branch createBranch(PosCaller caller, String tenantId, BranchDraft draft)`
    - `Branch updateBranch(PosCaller caller, String tenantId, String id, BranchDraft draft, long expectedVersion)`
    - `Set<String> branchStaff(PosCaller caller, String tenantId, String branchId)`
    - `Set<String> replaceBranchStaff(PosCaller caller, String tenantId, String branchId, Set<String> userIds)`
    - `List<String> branchIdsOf(String tenantId, String userId)` (no caller: used by token issuance)

Rules `PosOrganizationService` enforces (constructor `(PosOrganizationDirectory, LegalEntityStore, BranchStore, BranchStaffStore, PosAudit, Clock)`, ids from `UUID.randomUUID()`):
- Unknown tenant, or caller's `tenantId` ≠ path tenant (unless SUPER_ADMIN/org-wide, which the web layer folds into `tenantWide` plus a matching `tenantId`) → NOT_FOUND `pos.organization.notFound`.
- Legal-entity create/update and branch create/update require `caller.tenantWide()`; otherwise NOT_FOUND.
- Branch-scoped callers: `branches` returns only branches in `branchIdsOf(tenant, caller.userId)`; `branch`/`branchStaff`/`replaceBranchStaff` on any other branch → NOT_FOUND `pos.branch.notFound`.
- `updateLegalEntity`: country change when `anyForLegalEntity` → CONFLICT `pos.legalEntity.countryLocked` field `country`; currency follows country (validator).
- `createBranch`/`updateBranch`: owner legal entity must exist in the tenant (else INVALID field `legalEntityId`); `updateBranch` may not change `legalEntityId` → CONFLICT `pos.branch.legalEntityLocked`.
- `replaceBranchStaff`: every user id must be `isActiveMember(tenant, id)` else INVALID field `userIds[<id>]` key `pos.staff.notMember`.
- Every create/update/staff change calls `PosAudit.changed` with entity type `POS_LEGAL_ENTITY`, `POS_BRANCH` or `POS_BRANCH_STAFF`.

- [ ] **Step 1: Write the failing tests** in `PosOrganizationServiceTest` using `InMemoryPosStores`, a stub directory (tenants `t1`, `t2`; members `u-admin`, `u-cashier` of `t1`) and a recording `PosAudit`. Tests:
  - `createsLegalEntityAndBranchWithVersionOne()`
  - `countryCannotChangeOnceBranchesExist()` → CONFLICT, field `country`
  - `staleBranchUpdateIsConflict()` → update with expectedVersion 0 after one update → CONFLICT `pos.staleVersion`
  - `duplicateBranchCodeIsConflict()` → second `"HYD-01"` in `t1` → CONFLICT `pos.branch.codeTaken`; same code in `t2` succeeds
  - `branchScopedCallerSeesOnlyAssignedBranches()` → caller `u-cashier` tenantWide=false assigned to branch A of two → `branches` returns `[A]`
  - `branchScopedCallerCannotCreateBranches()` → NOT_FOUND
  - `callerOfAnotherTenantGetsNotFound()` → caller tenant `t2` reading `t1` → NOT_FOUND
  - `staffMustBeMembersOfTheTenant()` → `replaceBranchStaff` with `u-stranger` → INVALID field `userIds[u-stranger]`
  - `branchIdsOfListsAssignments()` → after assigning `u-cashier` to A and B → `[A, B]` in any order
  - `everyChangeIsAudited()` → recording audit got entries for create entity, create branch, replace staff

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests "com.heuristiq.identity.pos.application.*"`
Expected: compilation failure.

- [ ] **Step 3: Implement the ports, `PosCaller`, `PosOrganizationUseCase`, `PosOrganizationService`, and `InMemoryPosStores`**

`InMemoryPosStores` keeps maps keyed by `tenantId + "/" + id`, honours the version and code-uniqueness contracts exactly as the port Javadoc states (it is the contract reference the PostgreSQL stores are tested against in Task 4).

- [ ] **Step 4: Run to verify pass**

Run: same as Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add heuristiq-core-identity/src
git commit -m "feat(identity): POS organization service with branch-scoped access"
```

---

### Task 4: PostgreSQL stores, directory and audit adapters, wiring

**Files:**
- Create: `src/main/resources/db/migration/V44__pos_organization.sql`
- Create: `src/main/java/com/heuristiq/identity/pos/adapter/out/JdbcLegalEntityStore.java`, `JdbcBranchStore.java`, `JdbcBranchStaffStore.java`, `PosAdapterConstants.java`
- Create: `src/main/java/com/heuristiq/identity/pos/adapter/out/TenantPosOrganizationDirectory.java` (uses `TenantRepository.findById` and `TenantMembershipRepository.findByTenantIdAndUserId`, member active = status `ACTIVE`)
- Create: `src/main/java/com/heuristiq/identity/pos/adapter/out/AuditLogPosAudit.java` (same shape as `AuditLogLegalProfileAudit`)
- Create: `src/main/java/com/heuristiq/identity/pos/config/PosOrganizationConfig.java`
- Test: `src/test/java/com/heuristiq/identity/pos/adapter/out/PosStoresTest.java`, `TenantPosOrganizationDirectoryTest.java`

**Interfaces:**
- Consumes: Task 3 ports and service.
- Produces: Spring beans `LegalEntityStore`, `BranchStore`, `BranchStaffStore` (JDBC, conditional on `railcab.uam.persistence.type=postgres` as in Global Constraints) and `PosOrganizationUseCase` (unconditional, built with `Clock.systemUTC()`).

Schema `V44__pos_organization.sql` (table/column names also in `PosAdapterConstants`; `tenant_id` is `VARCHAR(64)`, matching how existing identity tables store tenant ids — check `V7__org_structure.sql`):
- `pos_legal_entity`: `id VARCHAR(64) PK`, `tenant_id NOT NULL`, `country CHAR(2) NOT NULL`, `legal_name VARCHAR(200) NOT NULL`, `currency CHAR(3) NOT NULL`, `address_line1`, `address_line2`, `address_city`, `address_region`, `address_postal_code`, `address_country CHAR(2)`, `invoice_prefix VARCHAR(10) NOT NULL`, `financial_year_start_month SMALLINT NOT NULL CHECK (1..12)`, `status VARCHAR(16) NOT NULL`, `version BIGINT NOT NULL`, `updated_at TIMESTAMPTZ NOT NULL`, `updated_by VARCHAR(64)`; index `(tenant_id)`.
- `pos_legal_entity_tax_id`: `legal_entity_id FK → pos_legal_entity ON DELETE CASCADE`, `tenant_id NOT NULL`, `type VARCHAR(16) NOT NULL`, `value VARCHAR(32) NOT NULL`, PK `(legal_entity_id, type)`.
- `pos_branch`: `id PK`, `tenant_id NOT NULL`, `legal_entity_id NOT NULL FK → pos_legal_entity`, `code VARCHAR(20) NOT NULL`, `name VARCHAR(120) NOT NULL`, `time_zone VARCHAR(64) NOT NULL`, `locale VARCHAR(16) NOT NULL`, `languages VARCHAR(64) NOT NULL` (comma-separated), `phone VARCHAR(16) NOT NULL`, `email VARCHAR(254)`, the same six `address_*` columns, `status`, `version`, `updated_at`, `updated_by`; **unique** constraint `uk_pos_branch_tenant_code (tenant_id, code)`; index `(tenant_id, legal_entity_id)`.
- `pos_branch_staff`: `tenant_id NOT NULL`, `branch_id NOT NULL FK → pos_branch ON DELETE CASCADE`, `user_id VARCHAR(64) NOT NULL`, `assigned_by VARCHAR(64)`, `assigned_at TIMESTAMPTZ NOT NULL`, PK `(branch_id, user_id)`; index `(tenant_id, user_id)` for `branchIdsOf`.

Store rules: every statement has `tenant_id = ?` in its `WHERE` clause; updates are `UPDATE ... WHERE id = ? AND tenant_id = ? AND version = ?` and 0 rows updated → `pos.staleVersion`; a unique-violation (`DuplicateKeyException` / SQLState `23505` on `uk_pos_branch_tenant_code`) → `PosException.conflict("pos.branch.codeTaken","code")`; legal-entity save and its tax ids, and `replaceStaff` (delete + insert), each run in one `TransactionTemplate` transaction.

- [ ] **Step 1: Write the failing tests** in `PosStoresTest`, each against a fresh zonky `EmbeddedPostgres` with Flyway applying all identity migrations (copy the setup of `LegalProfileStoresTest.jdbcStoreSavesVersionsAndReplacesSetsPerOrganizationAndNeverMixesOrganizations`):
  - `legalEntityRoundTripsWithTaxIdsAndAddress()` — insert then `find` returns an equal record
  - `storesNeverReturnAnotherTenantsRows()` — entity and branch of `t1` are invisible through `find`/`list` for `t2`
  - `updateRefusesStaleVersion()` — update at version 1 succeeds (→ 2); a second update with expected 1 → CONFLICT `pos.staleVersion`
  - `branchCodeIsUniquePerTenantOnly()` — second `HYD-01` in `t1` → CONFLICT `pos.branch.codeTaken`; `HYD-01` in `t2` succeeds
  - `replaceStaffReplacesTheWholeSet()` — `{u1,u2}` then `{u2,u3}` → `staffOf` is `{u2,u3}`, `branchIdsOf(t1,u1)` empty
  - `anyForLegalEntityIsTrueOnlyWithBranches()`
  - `TenantPosOrganizationDirectoryTest.suspendedMemberIsNotActive()` and `unknownTenantDoesNotExist()` (mocked repositories)

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests "com.heuristiq.identity.pos.adapter.out.*"`
Expected: compilation failure.

- [ ] **Step 3: Implement the stores, directory, audit adapter and `PosOrganizationConfig`.**

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (whole suite, so the new config does not break context-loading tests such as `PermissionCatalogSeedingIntegrationTest`).

- [ ] **Step 5: Commit**

```bash
git add heuristiq-core-identity/src
git commit -m "feat(identity): PostgreSQL stores and wiring for legal entities, branches, branch staff"
```

---

### Task 5: REST endpoints, access rules and privileges

**Files:**
- Create: `src/main/java/com/heuristiq/identity/pos/adapter/in/LegalEntityController.java`, `BranchController.java`, `BranchStaffController.java`
- Create: `src/main/java/com/heuristiq/identity/pos/adapter/in/PosCallers.java` (builds `PosCaller` from the `Authentication` + `TenantContext`), `PosErrors.java`, request/response records `LegalEntityRequest`, `LegalEntityResponse`, `BranchRequest`, `BranchResponse`, `BranchStaffRequest`, `BranchStaffResponse`
- Modify: `src/main/resources/privilege.json` and `heuristiq-core-gateway/src/main/resources/privilege.json` (identical)
- Test: `src/test/java/com/heuristiq/identity/pos/adapter/in/PosControllersTest.java`, `PosEndpointsPrivilegeTest.java`, `PosCallersTest.java`

**Interfaces:**
- Consumes: `PosOrganizationUseCase` (Task 3).
- Produces (all wrapped in `CommonResponseObject`, requests in `CommonRequestObject` with `@RequestObjectType`, as `LegalProfileController`):

| Method | Path | Use case call | Privilege name | Id / code | Operation |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/uam/organizations/{orgId}/legal-entities` | `legalEntities` | `POS_LEGAL_ENTITY_LIST` | 1284 / SHARED_0471 | LIST |
| GET | `/api/v1/uam/organizations/{orgId}/legal-entities/{id}` | `legalEntity` | `POS_LEGAL_ENTITY_READ` | 1285 / SHARED_0472 | READ |
| POST | `/api/v1/uam/organizations/{orgId}/legal-entities` | `createLegalEntity` | `POS_LEGAL_ENTITY_CREATE` | 1286 / SHARED_0473 | CREATE |
| PUT | `/api/v1/uam/organizations/{orgId}/legal-entities/{id}` | `updateLegalEntity` (body carries `version`) | `POS_LEGAL_ENTITY_UPDATE` | 1287 / SHARED_0474 | UPDATE |
| GET | `/api/v1/uam/organizations/{orgId}/branches` | `branches` | `POS_BRANCH_LIST` | 1288 / SHARED_0475 | LIST |
| GET | `/api/v1/uam/organizations/{orgId}/branches/{id}` | `branch` | `POS_BRANCH_READ` | 1289 / SHARED_0476 | READ |
| POST | `/api/v1/uam/organizations/{orgId}/branches` | `createBranch` | `POS_BRANCH_CREATE` | 1290 / SHARED_0477 | CREATE |
| PUT | `/api/v1/uam/organizations/{orgId}/branches/{id}` | `updateBranch` (body carries `version`) | `POS_BRANCH_UPDATE` | 1291 / SHARED_0478 | UPDATE |
| GET | `/api/v1/uam/organizations/{orgId}/branches/{id}/staff` | `branchStaff` | `POS_BRANCH_STAFF_READ` | 1292 / SHARED_0479 | READ |
| PUT | `/api/v1/uam/organizations/{orgId}/branches/{id}/staff` | `replaceBranchStaff` | `POS_BRANCH_STAFF_UPDATE` | 1293 / SHARED_0480 | UPDATE |

Each entry also carries `type` `API`, `service` `uam`, `module` `ADMINISTRATION`, `submodule` `MASTER` and a one-sentence `description`.

`PosCallers.current(Authentication)` rules: `tenantWide` = `TenantGuard.isSuperAdmin()` OR the `OrgWideConstants.AUTHORITY` authority OR a role ending in `_ADMIN` whose prefix is the session tenant's code OR role `POS_RESTAURANT_ADMIN`; `tenantId` = `TenantContext.get()`, except SUPER_ADMIN/org-wide callers take the path `orgId`.

- [ ] **Step 1: Write the failing tests**
  - `PosEndpointsPrivilegeTest` (copy the matcher helper from `LegalProfileEndpointsPrivilegeTest`): for each row above, the deciding entries for (method, path with sample ids) are exactly that row's privilege name; `GET .../branches/b1/staff` is decided by `POS_BRANCH_STAFF_READ`, never `POS_BRANCH_READ`; a READ/LIST privilege never decides a PUT/POST; no existing entry (e.g. `ORGANIZATION_READ` on `/organizations/{id}`) decides any new path; `identityAndGatewayCatalogsAreIdentical()` compares the two files byte for byte.
  - `PosCallersTest`: `superAdminIsTenantWideOnPathTenant()`, `orgAdminRoleIsTenantWide()` (role `ACME_ADMIN`, tenant code `ACME`), `cashierIsBranchScoped()`.
  - `PosControllersTest` (MockMvc standalone with a stub use case, as `LegalProfileControllerTest`): `createBranchReturnsVersion1()`, `invalidDraftAnswers400WithField()`, `staleVersionAnswers409()`, `anotherTenantsBranchIsNotFound()` → 404, `branchScopedCallerCannotReachOtherBranch()` → 404.

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests "com.heuristiq.identity.pos.adapter.in.*"`
Expected: FAIL / compilation failure.

- [ ] **Step 3: Implement controllers, `PosCallers`, `PosErrors`, request/response records; add the 10 catalogue entries to both `privilege.json` files.**

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test` (identity) and, from `heuristiq-core-gateway`, `./gradlew test` (its catalogue tests load the same file).
Expected: both BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add heuristiq-core-identity/src heuristiq-core-gateway/src/main/resources/privilege.json
git commit -m "feat(identity): REST endpoints and privileges for legal entities, branches, branch staff"
```

---

### Task 6: POS application, roles and the `branchIds` token claim

**Files:**
- Modify: `src/main/java/com/heuristiq/identity/application/catalogue/ApplicationCatalogue.java` (new entry)
- Modify: `src/main/java/com/heuristiq/identity/security/SecurityConstants.java` (claim name)
- Modify: `src/main/java/com/heuristiq/identity/security/SessionAccess.java` (branch ids)
- Modify: `src/main/java/com/heuristiq/identity/security/JwtService.java` (lookup + claim)
- Create: `src/main/java/com/heuristiq/identity/pos/application/PosRoles.java` and `src/main/java/com/heuristiq/identity/pos/config/PosRoleBootstrap.java`
- Test: `src/test/java/com/heuristiq/identity/security/JwtServiceBranchClaimTest.java`, `src/test/java/com/heuristiq/identity/pos/config/PosRoleBootstrapTest.java`; extend the existing application-catalogue test if one exists (`grep -rl ApplicationCatalogue src/test`)

**Interfaces:**
- Consumes: `PosOrganizationUseCase.branchIdsOf(String tenantId, String userId)` (Task 3).
- Produces:
  - `ApplicationCatalogue.DINEIN_POS_APP = "DINEIN_POS_APP"`; entry name `"DineIn POS & Kiosk"`, description `"Restaurant POS, kiosk, QR ordering and kitchen display."`, `ApplicationType.WEB`, order 80, icon `"point_of_sale"`, module `"DINEIN"`.
  - `SecurityConstants.BRANCH_IDS_CLAIM = "branchIds"`.
  - `SessionAccess` gains component `List<String> branchIds` (null → empty); `unchanged(roles)` keeps it empty; all existing call sites keep compiling (add a 3-argument constructor delegating with empty branch ids).
  - `JwtService` gains an `ObjectProvider<PosOrganizationUseCase>` (added to the `@Autowired` constructor; the shorter constructors pass `null`). `sessionAccess(user, tenantId)` fills `branchIds` from `branchIdsOf(tenantId, user.getId())` when the provider has a bean; `generateSession` writes `branchIds` only when non-empty (an ordinary token is unchanged).
  - `PosRoles`: shared (tenant-less) system roles seeded idempotently by `PosRoleBootstrap` (an `ApplicationRunner`, using the existing role repository/service the `UamBootstrapRunner` uses):
    - `POS_RESTAURANT_ADMIN`: all ten privileges 1284–1293
    - `POS_BRANCH_ADMIN`: `POS_LEGAL_ENTITY_READ`, `POS_BRANCH_LIST`, `POS_BRANCH_READ`, `POS_BRANCH_STAFF_READ`, `POS_BRANCH_STAFF_UPDATE`
    - `POS_BILLING`, `POS_WAITER`, `POS_KITCHEN`: `POS_BRANCH_LIST`, `POS_BRANCH_READ` (their dinein privileges arrive in plan 2)
    - Privileges are stored on the role in the same form `BootstrapRoleConstants` uses for its seeded roles.
    - `systemRole = true`, `editable = false`; an existing role with the same code keeps its other privileges and gains missing ones (never removes).

- [ ] **Step 1: Write the failing tests**
  - `JwtServiceBranchClaimTest` (style of `JwtServiceFranchiseClaimTest`): `assignedStaffTokenCarriesBranchIds()` → claim `["b1","b2"]`; `unassignedPersonsTokenHasNoBranchClaim()` → claim absent; `noPosModuleMeansNoClaim()` → provider without bean → absent; `branchesOfAnotherTenantAreNotIncluded()` → lookup keyed by session tenant only.
  - `PosRoleBootstrapTest`: `seedsFiveRolesOnce()` (run twice → five roles, no duplicates); `keepsExtraPrivilegesOfAnExistingRole()`.
  - Catalogue: `posApplicationIsInTheCatalogue()` → `ApplicationCatalogue.find("dinein_pos_app")` present with module `DINEIN`.

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests "*JwtServiceBranchClaimTest" --tests "*PosRoleBootstrapTest"`
Expected: compilation failure.

- [ ] **Step 3: Implement the changes listed under Interfaces.**

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, including the existing `JwtServiceFranchiseClaimTest`, `JwtServiceApplicationsClaimTest` and `LoginSessionTimezoneTest` (token shape unchanged for non-POS users).

- [ ] **Step 5: Commit**

```bash
git add heuristiq-core-identity/src
git commit -m "feat(identity): DineIn POS app, POS roles and branchIds claim"
```

---

### Task 7: Documentation

**Files:**
- Modify: `heuristiq-core-identity/README.md` — new section "POS organization (legal entities, branches)" listing the endpoints table from Task 5, the tables from Task 4, the `branchIds` claim and the five POS roles.
- Modify: `docs/BRD.md` — §12 "Impact on the current codebase": mark the "Tenancy" row as moved to identity (legal entities and branches done in plan 1); bump version to 0.5 and add a change-log line.
- Modify: `docs/COMPLIANCE.md` — GL-05 status `In progress` (branch structure exists; regional hosting not yet), change-log line.

- [ ] **Step 1: Make the three edits.**
- [ ] **Step 2: Verify** `./gradlew test` is still green (no code change) and the links in the README section resolve.
- [ ] **Step 3: Commit**

```bash
git add heuristiq-core-identity/README.md docs/BRD.md docs/COMPLIANCE.md
git commit -m "docs: POS organization foundation in identity"
```
