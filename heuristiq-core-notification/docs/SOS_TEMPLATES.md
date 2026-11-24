# SOS notification events and templates

Rider_RideService sends three SOS events. NMS only dispatches an event that has an active template
(otherwise it is skipped with `no_template`), so an admin must create the templates below.
English drafts of these templates are seeded by `DefaultNotificationTemplates` (only when missing; they never overwrite an admin-edited template). Other languages are admin data.

| Event | Audience | Channel(s) to create | Addressed by | Placeholders |
|---|---|---|---|---|
| `SOS_ALERT_RAISED` | Ops / on-call, sent immediately when an SOS is raised | IN_APP and FCM_PUSH | userIds | `{{alertId}}` `{{rideId}}` `{{userId}}` `{{lat}}` `{{lng}}` |
| `SOS_ALERT_ESCALATED` | Ops, sent when a raised SOS stays unacknowledged past the escalation threshold | IN_APP and FCM_PUSH | userIds | `{{alertId}}` `{{rideId}}` `{{userId}}` `{{lat}}` `{{lng}}` |
| `SOS_CONTACT_ALERT` | The customer's emergency contact | SMS | phone number | `{{alertId}}` `{{rideId}}` `{{contactName}}` `{{lat}}` `{{lng}}` |

## Suggested English wording (drafts)

**SOS_ALERT_RAISED** (title: `SOS raised`)

> SOS alert {{alertId}} raised for ride {{rideId}} by user {{userId}}. Last location: https://maps.google.com/?q={{lat}},{{lng}}. Please acknowledge and respond now.

**SOS_ALERT_ESCALATED** (title: `SOS not acknowledged`)

> SOS alert {{alertId}} for ride {{rideId}} (user {{userId}}) has not been acknowledged and is escalated. Last location: https://maps.google.com/?q={{lat}},{{lng}}. Immediate action required.

**SOS_CONTACT_ALERT** (SMS)

> Hello {{contactName}}, a Best Ride customer who listed you as an emergency contact has raised an SOS alert during ride {{rideId}}. Last known location: https://maps.google.com/?q={{lat}},{{lng}}. If you cannot reach them, please call emergency services.

## Review needed

- These are drafts. The hi, ta, te, kn, ml and ar versions must be written and reviewed by a native speaker before production.
- An admin must create the templates (per channel and language) in NMS.
- The events are seeded only when the events table is empty. An existing deployment must add the three events through the NMS events admin API, or use `reset-defaults`. WARNING: `reset-defaults` DELETES all custom events first, so only use it if you have no custom events you need to keep.
