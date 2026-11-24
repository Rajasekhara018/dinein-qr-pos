# RailCab NMS Production Notes

## Required envelope

All write APIs use `CommonRequestObject`. Put the domain payload inside `requestObject`.

```json
{
  "messageID": "booking-123-confirmed-v1",
  "requestObject": {
    "event": "BOOKING_CONFIRMED",
    "toPhone": "9999999999",
    "variables": {
      "customerName": "Raj",
      "bookingId": "123"
    }
  }
}
```

Responses use `CommonResponseObject`:

```json
{
  "success": true,
  "message": "Notification dispatched",
  "responseObject": []
}
```

## Runtime settings

Use `SPRING_PROFILES_ACTIVE=prod` in production. Required environment variables:

- `MONGO_URI`
- `KAFKA_BOOTSTRAP`
- `NMS_INTERNAL_API_KEY`
- `JWT_SECRET`
- provider credentials for the enabled channels

## Kafka

The service provisions:

- `railcab-notification-events`
- `railcab-notification-events.DLT`

Failed messages are retried using `nms.kafka.retry.*` and then published to the DLT.

## Idempotency

Set `messageID` for every notification command. If a message with the same id already has a successful log entry, NMS skips resending it.

## Delivery audit

`nms_logs` stores event, channel, recipient, provider, status, retry count, message id, rendered body, error, and sent timestamp.
