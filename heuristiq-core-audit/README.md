# Rider_AuditService

The platform's central audit trail. One store (`audit_events` plus `audit_event_payloads`) for:

| `eventType` | Written by | What it records |
|---|---|---|
| `ACCESS` | API gateway (automatic) | who called which API: user, roles, matched privilege ids or a denial, feature/module, method, normalised path, status, duration, correlation id, IP (last octet masked), user agent, channel; optionally redacted headers and bodies |
| `SCREEN_VIEW` | Angular and Flutter clients | a screen a signed-in user opened (`screenKey`) |
| `COMMUNICATION` | NMS (later phase) | one delivery attempt: channel, template/event code, origin service, masked recipient, provider, attempt, providerStatus, failureReason |
| `PAYMENT_GATEWAY` | Payment service (later phase) | one gateway call: gateway, operation, gatewayRef, paymentId, failureReason |
| `DOMAIN` | any service | a business action (approve, refund, config change) |

Port `9023` (`AUDIT_PORT`), package `com.heuristiq.audit`, Eureka name `Rider-AuditService`, API prefix `/api/v1/audit`,
through the gateway `/api/rider/audit/audit/...` (the first `audit` is the service prefix, the second the controller path,
exactly like `/api/rider/config/config/...`). Swagger: `/api/rider/audit/swagger-ui/index.html`; Javadoc: `/api/rider/audit/javadoc/`.

## Endpoints

All answer the usual `CommonResponseObject` envelope (`success`, `message`, `responseObject`, `errorCode`), except the CSV exports.

| Method and path | Auth | Purpose |
|---|---|---|
| `POST /api/v1/audit/internal/events` | service token only (`X-Rider-Service-Token`, or a SERVICE bearer token). **Blocked at the gateway.** | batch of 1..500 events, each validated, redacted and stored on its own, idempotent on `eventId` |
| `POST /api/v1/audit/screen-views` | any signed-in user (user JWT). No privilege entry on purpose | batch of 1..50 screen views; identity from the token, never the body; 60 events per user per minute, the excess is dropped and the call answers 429 (`dropped` in the body; accepted events are kept) |
| `GET /api/v1/audit/events` | privilege `AUDIT_EVENT_LIST` | filters `userId, role, feature, screenKey, eventType, outcome, channel, privilegeId, method, path (contains), correlationId, from, to, subjectType, subjectId, reasonCode` (the last three exact match), `page`, `size` (max 200), newest first. Never contains headers or bodies |
| `GET /api/v1/audit/events/{id}` | `AUDIT_EVENT_READ` | one event, without headers and bodies |
| `GET /api/v1/audit/events/{id}/payload` | **`AUDIT_EVENT_PAYLOAD_READ`** | the stored redacted headers and bodies of that event |
| `GET /api/v1/audit/events/correlation/{id}` | `AUDIT_EVENT_BY_CORRELATION_READ` | every event of one request chain, oldest first |
| `GET /api/v1/audit/events/export` | `AUDIT_EVENT_EXPORT` | CSV of the filtered events (cap 10,000 rows), formula-injection safe |
| `GET /api/v1/audit/events/export/payloads` | **`AUDIT_EVENT_PAYLOAD_EXPORT`** | the same CSV with the payload columns (cap 1,000 rows) |
| `GET /api/v1/audit/events/facets` | `AUDIT_EVENT_FACETS_READ` | distinct roles, features, screen keys, event types, outcomes, channels of the last 30 days, for dropdowns |

Why `/payload` is a separate path and not a field of `/events/{id}`: authorization is privilege-based at the gateway and
matches on path and method, so only a separate path can carry a separate privilege.

## Event contract (internal ingest)

```json
{ "events": [ {
  "eventId": "6f1c2b0e-8a54-4d0e-9d1a-3c1f6a2b7e10",
  "eventType": "ACCESS",
  "occurredAt": "2026-10-06T10:15:30.123Z",
  "sourceService": "api-gateway",
  "userId": "u-42", "userType": "STAFF", "role": "SUPER_ADMIN", "roles": ["SUPER_ADMIN", "SUPPORT"],
  "privilegeIds": [660],
  "feature": "rider-config", "module": "CONFIGURATION",
  "method": "PUT", "path": "/api/rider/config/config/settings/{id}",
  "statusCode": 200, "outcome": "SUCCESS", "durationMs": 37,
  "correlationId": "c0ffee00-...", "requestId": "7d1a...",
  "ipAddress": "203.0.113.57", "userAgent": "Mozilla/5.0 ...", "appChannel": "ADMIN_WEB",
  "subjectType": "DOCUMENT", "subjectId": "doc_01HZX", "reasonCode": "VIRUS_DETECTED",
  "details": { "denyReason": "PRIVILEGE" },
  "requestHeaders": { "Content-Type": "application/json", "Authorization": "***" },
  "responseHeaders": { "Content-Type": "application/json" },
  "requestBody": "{\"value\":5,\"password\":\"***\"}", "responseBody": "{\"id\":9}",
  "requestBodyTruncated": false, "responseBodyTruncated": false,
  "requestBodySize": 31, "responseBodySize": 8, "bodyContentType": "application/json"
} ] }
```

Response: `{"received":1,"accepted":1,"duplicates":0,"rejected":0,"dropped":0,"rejections":[]}`. Only `eventType` and a valid
`sourceService` are required (ACCESS also needs `outcome` or `statusCode`; SCREEN_VIEW needs `screenKey`). `userType`,
`outcome`, `eventId` are derived when absent.

## What is stored, masked and refused

* **Event row**: bounded strings only, control characters stripped, query strings cut from paths, client IP masked
  (`railcab.audit.ingest.mask-ip`), `details` limited to 20 string attributes; keys that look like credentials
  (`password`, `token`, `otp`, `pin`, `cvv`, `card`, `secret`, `apiKey`, `signature`, ...) are dropped, token-looking values
  (JWT, `Bearer ...`) are replaced by `[redacted]`, `recipient/email/phone/mobile` values are masked
  (`j***@example.com`, `******3210`).
* **Payload row** (`audit_event_payloads`, same `expireAt`, deleted together with the event): see below.
* **Subject and reason** (optional, all event types): `subjectType` (what the event is about, UPPER_SNAKE, max 40, e.g.
  `DOCUMENT`, `NOTIFICATION_MESSAGE`, `PAYMENT`), `subjectId` (max 128 of `A-Z a-z 0-9 _ . : -`, ids only) and `reasonCode`
  (stable UPPER_SNAKE code, max 60, e.g. `VIRUS_DETECTED`). Lower case is upper-cased; a malformed value rejects that
  event (like `screenKey`). They are not secrets and are not redacted. Indexed, filterable on the query and export endpoints
  (`subjectType`, `subjectId`, `reasonCode`, exact match), listed in the facets (`subjectTypes`, `reasonCodes`) and appended as
  the last CSV columns. DMS sends DOMAIN events (feature `dms`, module `DOCUMENTS`, subjectType `DOCUMENT`) whose `details`
  may carry `documentType`, `category`, `action`, `storageProvider`, `scanVerdict`, `sizeBytes`.
* **Screen keys**: lower-case dotted, `^[a-z][a-z0-9_-]*(\.[a-z0-9_-]+)*$`, max 100 characters.

### Headers and bodies

Redaction runs in the gateway before an event leaves it and **again here at ingest** (`PayloadRedactor`, the two copies
must be kept in step).

* Headers redacted (name kept, value `***`): `Authorization`, `Proxy-Authorization`, anything containing `cookie`
  (`Cookie`, `Set-Cookie`), and any header whose name contains `token`, `secret`, `key` or `password`
  (covers `X-Rider-Service-Token`, `X-Gateway-Token`, `X-NMS-API-Key`, `X-Internal-Secret-Key`, `X-Api-Key`).
  Other values are scrubbed for JWT / Bearer-looking text.
* JSON body keys fully redacted to `***` (case-insensitive, `_ - .` ignored, recursively through objects and arrays):
  `password`, `passwd`, `pwd`, `pin`, `mpin`, `upiPin`, `otp` (and anything starting with `otp`), `token` (and anything
  ending in `token`: `accessToken`, `refreshToken`, `idToken`, `fcmToken`), `secret` (and anything containing it:
  `clientSecret`), anything containing `password`, `apiKey`, `cvv`, `cvv2`, `cvc`, `authorization`, `signature` (and
  anything ending in it), `passcode`, `privateKey`, `sessionId`, `cookie`.
* Keeping the last four digits (`***1234`): `cardNumber`, `card_number`, `cardNo`, `accountNumber`, `accountNo`,
  `bankAccount(Number)`, `debitCardNumber`, `creditCardNumber`.
* Masked like elsewhere: `phone`, `phoneNumber`, `mobile`, `mobileNumber`, `msisdn`, `email`, `emailId`, `recipient`
  (`j***@example.com`, `******3210`); UPI `vpa`, `upi`, `upiId`, `upiAddress`, `payeeVpa`, `payerVpa` keep two characters
  and the handle (`jo***@okbank`). `ifsc` is kept. Any string value that looks like a JWT or `Bearer ...` is replaced.
* `application/x-www-form-urlencoded` bodies are redacted with the same keys; non-JSON text and JSON cut at the size cap
  (which cannot be parsed) are redacted with a text scan of `key: value` / `key=value` pairs.
* **Bodies are not stored at all** (only headers, content type and size): for content types other than `application/json`
  (and `+json`), `text/*`, `application/x-www-form-urlencoded` (so multipart, images, PDFs, octet-stream are skipped), and
  for paths matching `railcab.audit.payload.skip-body-paths` (default `/**/identity/auth/**`, `/**/auth/**`,
  `/**/*otp*`, `/**/*password*`, `/**/*token*`, `/**/*card*` and their `/**` variants: login, register, OTP, password
  change/reset, token refresh, card endpoints).
* Each body is capped at `railcab.audit.payload.max-body-bytes` (16 KB); longer ones are cut and flagged
  `requestBodyTruncated` / `responseBodyTruncated`, with the original `...BodySize` kept.
* Stored payloads can still contain personal data that is not in a redacted field (names, addresses, free text).
  Treat `AUDIT_EVENT_PAYLOAD_READ` and `AUDIT_EVENT_PAYLOAD_EXPORT` as sensitive privileges.

## Retention

`expireAt` is set at ingest from the event's class (counted from the receive time): `ACCESS` reads (GET/HEAD/OPTIONS) and
`SCREEN_VIEW` 30 days, `ACCESS` writes 90 days, `COMMUNICATION`, `PAYMENT_GATEWAY`, `DOMAIN` 365 days
(`railcab.audit.retention.*-days`). MongoDB removes expired events and payloads with TTL indexes on `expireAt`; both
databases also run `AuditRetentionJob` (hourly, under a `SchedulerLock` so one instance purges) which deletes events and
their payloads together. Changing a setting affects new events only.

## Persistence

MongoDB by default (`railcab.audit.persistence.type=mongo`, `AUDIT_MONGODB_URI`); `SPRING_PROFILES_ACTIVE=prod,postgres`
switches to PostgreSQL/JPA (`AUDIT_POSTGRES_URL/USERNAME/PASSWORD`) with Flyway migrations `V1` (events), `V2`
(scheduler locks), `V3` (payloads). Ids come from `MongoSequence` / identity. `eventId` is unique in both.

## Configuration (environment)

`AUDIT_PORT`, `SPRING_PROFILES_ACTIVE`, `JWT_SECRET` (= UAM's secret), `RIDER_INTERNAL_SERVICE_TOKEN` (shared service
token), `CORS_ALLOWED_ORIGINS`, `EUREKA_*`, `AUDIT_MONGODB_URI`/`AUDIT_POSTGRES_*`, and the tunables in
`application.properties` (`AUDIT_INGEST_MAX_BATCH`, `AUDIT_SCREEN_VIEWS_PER_MINUTE`, `AUDIT_MASK_IP`,
`AUDIT_QUERY_MAX_PAGE_SIZE`, `AUDIT_EXPORT_MAX_ROWS`, `AUDIT_EXPORT_PAYLOAD_MAX_ROWS`, `AUDIT_RETENTION_*_DAYS`,
`AUDIT_PURGE_ENABLED`, `AUDIT_PAYLOAD_MAX_BODY_BYTES`).

## Gateway side (Rider_ApiGateway)

`AuditEventPublisher` (a `GlobalFilter` at order -80, wrapping `PrivilegeBasedAccessFilter`) builds one ACCESS event per
request after the chain finishes and puts it on a bounded queue (`gateway.audit.queue-capacity`, 5000 events and
64 MB; the oldest are dropped and counted when full). `AuditBatcher` posts batches of 100 (or every 2 s) to
`/api/v1/audit/internal/events` with the service token, through the `audit-publisher` circuit breaker and a 3 s timeout;
failed batches are dropped, never retried, with at most one warning per 30 s. Switch off with
`GATEWAY_AUDIT_ENABLED=false`; capture of headers/bodies separately with `GATEWAY_AUDIT_CAPTURE_HEADERS=false` /
`GATEWAY_AUDIT_CAPTURE_BODIES=false`; `gateway.audit.capture-exclude-paths` skips capture for chosen path patterns.
Not audited: health/docs/static paths, `OPTIONS`, the screen-view posts, and requests the JWT filter refuses (401).
The internal ingest path is answered 404 by the gateway route in every spelling.

`railcab.time.legacy-zone` (env `RAILCAB_TIME_LEGACY_ZONE`, IANA id, default `Asia/Kolkata`): the zone in which this service wrote its zone-less timestamps; informational for now, a startup log line and the gauge `railcab.time.legacy_zone.mismatch` warn when the JVM zone's offset differs (see docs/TIMEZONE_BACKEND_PLAN.md, Stage A.1).
