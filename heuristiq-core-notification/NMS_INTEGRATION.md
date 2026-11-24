# NMS Integration Notes

BookingsAPI sends ride notification events to the NMS service through `/api/nms/notify`.

Keep NMS client payload DTOs local to BookingsAPI under:

```text
com.railcab.bookingsapi.shared.notification.dto
```

Do not add Gradle dependencies on the NMS service or a separate contracts project for this integration. The client DTOs should mirror the NMS wire contract and remain small.

## Request Envelope

BookingsAPI sends:

```json
{
  "messageID": "bookings:RIDE_COMPLETED:rideId",
  "requestObject": {
    "event": "RIDE_COMPLETED",
    "variables": {
      "bookingId": "rideId",
      "rideId": "rideId"
    },
    "fcmTokens": ["token"],
    "channels": ["FCM_PUSH"]
  }
}
```

The current local DTOs are:

- `CommonNmsRequest`
- `NmsNotificationRequest`

## Origin (who asked, and why)

Anything that sends a notification should say where it came from, so each message can be traced from origin to
destination across its attempts, channel and failure reason. All fields are optional inside `requestObject` (REST
and Kafka alike); a producer that sends none keeps working.

```json
{
  "messageID": "payment:PAYMENT_CAPTURED:pay-123",
  "requestObject": {
    "event": "PAYMENT_CAPTURED",
    "sourceService": "rider-payment",
    "eventCode": "PAYMENT_CAPTURED",
    "triggeringUserId": "u-42",
    "entityType": "PAYMENT",
    "entityId": "pay-123",
    "correlationId": "c0ffee00-...",
    "toPhone": "9876543210"
  }
}
```

- `sourceService`: the calling service (also accepted as header `X-Source-Service` on REST, or Kafka record header
  `X-Source-Service`, when the payload has none).
- `eventCode`: the caller's event or template code; defaults to `event`.
- `triggeringUserId`, or `entityType` + `entityId`: the user or entity the message is about.
- `correlationId`: pass the request's correlation id; defaults to `messageID`.

NMS keeps one record per provider attempt (retries included) and pushes a `COMMUNICATION` event per attempt and per
provider delivery receipt to the audit service. Admin read endpoints:
`GET /api/v1/nms/logs/attempts/message/{messageId}` and `GET /api/v1/nms/logs/attempts/correlation/{correlationId}`.

## Event Variables

Keep template variables as `Map<String, String>` because NMS templates use dynamic placeholders such as `{{bookingId}}`, `{{rideId}}`, and `{{amount}}`.

When adding a new notification method, use the existing `notifyNms(...)` helper and keep the event name aligned with NMS templates.

## Build Rule

BookingsAPI must compile independently with:

```powershell
.\gradlew.bat compileJava
```
