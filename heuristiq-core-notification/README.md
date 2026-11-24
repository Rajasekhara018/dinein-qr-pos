# Rider NMS (Notification Management Service)

Copied into `bestride-rider-app` from `2026_RailCab_NMS`. **Port changed from the source repo's
9005 to 9015** (hardcoded, no env override there) — 9005 collides with `Rider_AdminAPI`'s actual
port in this repo; now `${NMS_PORT:9015}`, matching `Rider_ApiGateway`'s corrected
`nms-api-url` default. This is also the natural home for the "no real notification
dispatch anywhere" gap flagged in the 5 extracted rider microservices (Pricing/Location/
Dispatch/Ride/Payment never call an FCM/SMS provider directly) — wiring Ride/Dispatch/Payment's
critical events (driver assigned, ride started, payment result, etc.) through this service
instead of leaving them WebSocket-only is the natural next step.

## 2026 RailCab NMS (Notification Management Service)

Central multi-channel notification service: push (FCM), SMS/WhatsApp (Twilio/Digimile/Meta), email (SMTP/Mailgun/SES), and in-app (WebSocket). Consumed synchronously over REST and asynchronously over Kafka by other RailCab services. See [`NMS_DTO_CONTRACTS.md`](./NMS_DTO_CONTRACTS.md), [`NMS_INTEGRATION.md`](./NMS_INTEGRATION.md), and [`PRODUCTION.md`](./PRODUCTION.md) for the contracts this README summarizes operationally.

## Tech Stack

| Layer          | Technology                                                              |
|----------------|--------------------------------------------------------------------------|
| Framework      | Spring Boot 4.1.0                                                        |
| Language       | Java 21                                                                   |
| Database       | MongoDB (default) or MySQL/JPA (`sql` profile — mutually exclusive)      |
| Messaging      | Kafka (consumes `railcab-notification-events` + DLT; publishes `nms-voice-responses`) |
| Voice          | Exotel Connect API (IVR flow), off by default                            |
| Real-time      | WebSocket/STOMP (in-app notifications, JWT-authenticated at CONNECT)     |
| Push           | Firebase Admin SDK (FCM)                                                  |
| SMS/WhatsApp   | Twilio, with Digimile/Meta alternates per channel                        |
| Email          | SMTP, Mailgun (WebClient), AWS SES                                        |
| Docs           | springdoc-openapi                                                        |

## Project Structure

```
src/main/java/com/railcab/nms/
├── config/                # NmsProperties, KafkaConfig (incl. DLT), WebSocketConfig, NmsSecurityConfig, FcmConfig
├── domain/model/          # Persistence-agnostic POJOs: NotificationTemplate, NotificationLog, NotificationMessage,
│                           #  NotificationEventDefinition, DeviceToken, InAppNotification, Broadcast
├── domain/repository/     # Interfaces + parallel jpa/ and mongo/ adapters (7 aggregates × 2 backends)
├── integration/uam/       # UamUserClient, UamServiceTokenProvider (broadcast audience resolution)
├── notification/
│   ├── broadcast/         # Broadcast scheduling/dispatch
│   ├── channel/           # email/ fcm/ inapp/ sms/ whatsapp/ — per-provider implementations
│   ├── device/ dispatch/ event/ inapp/ kafka/ log/ message/ template/
├── persistence/{jpa,mongo}/  # Entity/document classes
└── shared/                # CommonRequestObject/ResponseObject envelope, enums, GlobalExceptionHandler
```

## Domain Model

- **Channels**: `EMAIL`, `SMS`, `WHATSAPP`, `FCM_PUSH`, `IN_APP`, `VOICE` (automated IVR call to `toPhone`, see below).
- **Templates**: keyed by event + channel, Base64-encoded body with `{{var}}` placeholders, soft-versioned via an `active` flag.
- **Delivery tracking**: `NotificationLog` records status (`SENT`/`FAILED`/`SKIPPED`) per recipient with provider, masked recipient, retry count, rendered body.
- **Idempotency**: `NotificationMessage` is a per-`messageId` ledger (`PROCESSING`/`SENT`/`FAILED`) preventing duplicate dispatch on retry/redelivery.

## Delivery Mechanism

Hybrid: synchronous REST (`POST /api/nms/notify`) and async Kafka consumption both funnel into `NotificationDispatchService`, which reserves idempotency, resolves channels, and dispatches concurrently per channel via `CompletableFuture` on a bounded executor — with per-channel semaphore throttling, retry+backoff, and a simple in-memory circuit breaker per provider. In-app notifications are also pushed live over WebSocket/STOMP. Kafka failures route to a Dead Letter Topic via `DefaultErrorHandler` + fixed backoff.

## Push data (apps route pushes by it)

Every FCM push carries a `data` map built by `PushDataBuilder` from the `NotificationRequest` (contract:
`docs/RIDE_FLOW_FIXES_2026-11.md` section 2):

| Key | Value |
|---|---|
| `type` | the event, upper case (e.g. `DRIVER_NEARBY`) |
| `eventCode`, `entityType`, `entityId` | from the request, when set |
| `rideId` | `entityId` when `entityType == RIDE`, else `variables.rideId` |
| `url` | always `/notifications` (Rider_Web's service worker) |
| every template variable | as a string, except names containing `otp`, `password`, `secret`, `token`, `pin`, `cvv` (any case); each value at most 512 characters, whole map at most 3500 bytes (last variables dropped first) |

Android: priority HIGH; `entityType == RIDE` pushes use notification channel `ride_updates`. The template test-send sends no data.

## Ride flow events (November 2026)

`DRIVER_NEARBY`, `WAITING_CHARGE_STARTED`, `PAYMENT_PENDING`, `PAYMENT_COMPLETED`, `RIDE_CLOSED`, `RIDE_DISPUTE_OPENED`,
`RIDE_DISPUTE_RESOLVED` (default in-app and push templates), `EARLY_DROP_CONFIRM_CALL` (default VOICE template) and an SMS
template for `EARLY_DROP_REQUESTED`. `DRIVER_ARRIVED` and `RIDE_STARTED` also offer the waiting variables
(`arrivedAtPickupAt`, `freeWaitingMinutes`, `waitingChargePerMin`, `waitingMinutes`, `waitingCharge`). Defaults are in
`RideFlowTemplates` (English drafts, seeded once, never replacing an admin's template).

## VOICE channel (Exotel IVR)

- `nms.provider.voice` = `none` (default: channel off, every attempt recorded as `NOT_CONFIGURED`) or `exotel`.
- `ExotelVoiceProvider` calls Exotel's v1 `Calls/connect` API (`From`=customer, `CallerId`=ExoPhone, `Url`=the call flow,
  `CustomField`=`{entityType, entityId, eventCode, messageId}`, `StatusCallback`). Never retried (a retry would ring twice);
  credentials go in a basic `Authorization` header and are never logged; phone numbers are logged masked.
- Exotel reports to `GET|POST /api/v1/nms/callbacks/voice/exotel` (call status, and the flow's Passthru applet with the pressed
  `digits`). It authenticates with the callback shared secret, either `X-NMS-Callback-Secret` or as the password of basic
  credentials: NMS adds `nms:<NMS_VOICE_CALLBACK_SECRET>@` to `EXOTEL_STATUS_CALLBACK_URL`; give the Passthru applet the same
  URL with those credentials. The attempt becomes DELIVERED (answered) or FAILED, and NMS publishes Kafka topic
  `nms-voice-responses`: `{eventId, entityType, entityId, eventCode, digit, outcome: ANSWERED|NO_ANSWER|FAILED, occurredAt,
  tenantId}` keyed by `entityId`; `eventId` = call id + status (or digit), so consumers dedupe on it. A failed publish answers
  503 so Exotel retries.

| Variable | Meaning |
|---|---|
| `NMS_PROVIDER_VOICE` | `exotel` or `none` (default) |
| `EXOTEL_ACCOUNT_SID`, `EXOTEL_API_KEY`, `EXOTEL_API_TOKEN` | account credentials |
| `EXOTEL_SUBDOMAIN` | API host, default `api.exotel.com` (`api.in.exotel.com` for the Mumbai cluster) |
| `EXOTEL_CALLER_ID` | ExoPhone shown to the customer |
| `EXOTEL_FLOW_ID` | id of the IVR flow (prompt, "press 1 to accept, 2 to dispute", Passthru to the callback) |
| `EXOTEL_STATUS_CALLBACK_URL` | public gateway URL of `/api/v1/nms/callbacks/voice/exotel` |
| `NMS_VOICE_CALLBACK_SECRET` | voice-only callback secret for the Exotel callback (kept apart from the shared secret because Exotel stores it in the callback URL; rotate it on its own) |
| `NMS_KAFKA_TOPIC_VOICE_RESPONSES` | default `nms-voice-responses` |
| `NMS_RATE_VOICE_CONCURRENCY` | concurrent calls being placed, default 5 |

The callback path is already public at the gateway (`pathmatchers.nms-callbacks=/api/v1/nms/callbacks/**`).

## Key Endpoints

| Controller | Routes |
|---|---|
| `NotificationController` | `POST /api/nms/notify` |
| `TemplateController` | CRUD at `/api/nms/templates`, lookup by event/channel |
| `EventController` | CRUD at `/api/nms/events`, `/config`, `/reset-defaults` |
| `NotificationLogController` | `/api/nms/logs`, `/logs/event`, `/logs/correlation/{id}`, `/logs/stats` |
| `DeviceTokenController` | `POST/DELETE /api/nms/devices/fcm-token` |
| `InAppNotificationController` | `/api/nms/in-app/notifications`, `/unread-count`, `PATCH /{id}/read` |
| `BroadcastController` | `POST/GET /api/nms/broadcasts`, `PATCH /{id}/cancel` |

## Configuration

Default port **9005** (`application.properties`). Mongo is the default profile; `sql` profile switches to MySQL/JPA (mutually exclusive autoconfig). Provider selection per channel via `nms.provider.email|sms|whatsapp|push|voice`. Rate limits, circuit breaker, idempotency/audit retention, and JWT secret (must match UAM's) are all configurable. `nms.security.enabled` gates internal API-key security (off by default).

## Docker

`Dockerfile` exposes **8087**; `docker-compose.yml` maps `8087:8087`. **This does not match `server.port=9005`** in every properties file — the container will not serve on the mapped port unless `SERVER_PORT` is set explicitly. Fix before relying on the compose file for local testing.

## Testing

Minimal: a single `JpaEntityMappingTest` (Hibernate/H2 mapping only). No coverage for dispatch orchestration, retry/circuit-breaker behavior, Kafka consumption, or channel providers.

## Known Issues

- **Port mismatch** between `server.port=9005` and Docker `EXPOSE`/compose `8087:8087`.
- **Dual persistence stack duplication**: every aggregate has a domain model, a Mongo document, a JPA entity, and two repository adapters (28 files for 7 aggregates) — real maintenance burden if only one backend is ever active per deployment.
- **In-memory circuit breaker/rate limiter** (plain `ConcurrentHashMap`/`Semaphore`) is per-instance only — won't coordinate across NMS replicas.
- **Retry blocks the dispatch thread** (`Thread.sleep` inside `sendWithRetry`) instead of using async backoff.
- **Broadcast scheduler** has no visible distributed locking — a double-send risk if scaled horizontally (compare to BookingsAPI's use of ShedLock for the same problem).
- **Hardcoded prod credentials** (Mongo URI with plaintext user/pass) in `application-prod.properties`.
- **No automatic provider failover** on circuit-open, despite three provider implementations existing per channel.

## Design Health Assessment

*Scored 1 (weak) – 5 (strong) against what the current code does — not a formal audit.*

| Dimension | Score | Why |
|---|:---:|---|
| Scalability | 3/5 | Kafka + DLT handles async load well, but the in-memory circuit breaker/rate limiter and un-locked broadcast scheduler won't coordinate correctly once you run more than one replica. |
| Flexibility / Extensibility | 4/5 | Channel/provider separation (Twilio/Digimile/Meta, SMTP/Mailgun/SES) is genuinely pluggable via config — a strong example in this codebase. |
| DRY | 2/5 | Every aggregate is duplicated across Mongo and JPA persistence layers (28 files for 7 aggregates) with no clear indication both are needed in any single deployment. |
| KISS | 3/5 | The hybrid REST+Kafka dispatch path is justified by the requirements and reasonably organized, though the in-house circuit breaker/rate limiter reinvents what a library already does elsewhere in this platform (Resilience4j). |
| SOLID | 3/5 | Channel/provider abstraction respects OCP well; the blocking `Thread.sleep` retry and lack of a shared resilience abstraction are the weak points. |
| Test Coverage | 1/5 | Only a JPA mapping test exists — the dispatch/retry/circuit-breaker logic that this service exists to run reliably has no tests at all. |

## Notes for TDD / LLD Authors

- **Bounded context**: NMS should remain a pure delivery layer (channel routing, retry, delivery tracking) — template/event definitions and audience resolution (via UAM) are its legitimate configuration surface, but ride/payment/admin business logic should never leak in here.
- **Non-functional requirements to spec**: per-channel throughput/rate limits, Kafka consumer lag targets, DLT alerting/replay procedure, and the intended behavior when a provider's circuit is open (queue, drop, or fail over to a secondary provider).
- **Sequence diagrams worth drawing**: REST-triggered dispatch vs Kafka-triggered dispatch (showing they converge on the same `NotificationDispatchService`), broadcast scheduling → per-recipient dispatch, and the DLT recovery path.
- **Scalability**: before horizontally scaling NMS, move circuit-breaker/rate-limiter state to Redis (already a dependency elsewhere in this platform) and add ShedLock (already used in BookingsAPI) to the broadcast scheduler — otherwise replicas will double-send and independently flap circuits.
- **Flexibility**: the channel/provider split is the right extension point — keep new providers behind the existing interfaces rather than branching inside dispatch logic.
- **DRY**: decide on one persistence backend for production and either delete the other adapter set or isolate it clearly behind a build profile/module — carrying both permanently doubles the surface area for every future schema change.
- **KISS**: replace the hand-rolled retry/circuit-breaker with Resilience4j (already a proven dependency in PaymentAPI and the Gateway) for consistency and to get async backoff for free instead of blocking dispatch threads.
- **SOLID**: introduce automatic provider failover per channel (e.g., a chain-of-responsibility over the existing provider interfaces) so a circuit opening on the primary provider doesn't silently stop delivery.

`railcab.time.legacy-zone` (env `RAILCAB_TIME_LEGACY_ZONE`, IANA id, default `Asia/Kolkata`): the zone in which this service wrote its zone-less timestamps; informational for now, a startup log line and the gauge `railcab.time.legacy_zone.mismatch` warn when the JVM zone's offset differs (see docs/TIMEZONE_BACKEND_PLAN.md, Stage A.1).
