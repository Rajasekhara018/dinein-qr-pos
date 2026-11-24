# NMS DTO and API Contract Notes

This service owns the notification API contract for RailCab NMS. Keep request and response shapes explicit in DTO classes inside this microservice instead of using hardcoded maps, controller-local records, or cross-project build dependencies.

## Local DTO Ownership

- Keep NMS request DTOs under `com.railcab.nms.notification.dispatch`.
- Keep NMS response DTOs near the feature that returns them, for example dispatch, event, log, or template packages.
- Keep `NotificationChannel` in the NMS module and reuse it across templates, logs, providers, and dispatch.
- Do not add shared contract Gradle modules or external source-set wiring unless the architecture is intentionally changed for all services.

## Common Envelope

REST and Kafka clients may send the common envelope:

```json
{
  "messageID": "source:event:id",
  "requestObject": {
    "event": "RIDE_COMPLETED",
    "variables": {
      "bookingId": "RC123"
    },
    "fcmTokens": ["token"],
    "channels": ["FCM_PUSH"]
  }
}
```

NMS maps `requestObject` to the proper DTO using `CommonRequestMapper`. If a new endpoint needs a payload, add a concrete DTO and map to it explicitly.

## Response DTO Rule

Do not return hardcoded response shapes such as `Map.of("label", ..., "attributes", ...)` from controllers or services. Add a named DTO or record so API responses are discoverable and type-safe.

Current examples:

- `NotificationDispatchResult`
- `EventConfigResponse`
- `NotificationChannelStatsResponse`

## Build Rule

NMS must compile independently with:

```powershell
.\gradlew.bat compileJava
```

Do not require other microservice projects to be imported for this compile to pass.
