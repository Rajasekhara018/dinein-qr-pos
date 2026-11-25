package com.heuristiq.notification.domain.model;

import java.util.ArrayList;
import java.util.List;

import com.heuristiq.notification.domain.model.DefaultNotificationTemplates.Seed;

/**
 * Default in-app, push and (for two events) SMS texts of the ride lifecycle: booking, driver, trip, payment, refund, promotions
 * and operator messages. Without them a rider or driver gets no message at all for these events.
 *
 * <p>Each text uses only the {@code {{placeholders}}} its event declares in {@link EventAttributesConfig}; the two driver events
 * that declare none ({@code DRIVER_OFFERED}, {@code DRIVER_ARRIVING}) carry fixed wording. The texts state facts the event carries and
 * make no promise about time or money the system does not guarantee (no arrival estimate, no refund timing). NMS templates have no
 * language column, so these are English; another language is its own event.
 *
 * <p>They are created once by {@code TemplateService}, for an event and channel that has no template yet; an admin's own or edited
 * template is never replaced. The wording is a draft and needs a review before it is relied on.
 */
public final class RideLifecycleTemplates {

    /** The in-app / push title and text of one event (the same for both channels). */
    private record Message(NotificationEvent event, String title, String body) {
    }

    /** SMS text of an event; it opens with the brand name and has to stay short. */
    private record Sms(NotificationEvent event, String body) {
    }

    private static final List<Message> MESSAGES = List.of(
            new Message(NotificationEvent.BOOKING_CONFIRMED, "Booking confirmed",
                    "Hi {{passengerName}}, your booking {{bookingId}} from {{pickupLocation}} to {{dropLocation}} is confirmed."),
            new Message(NotificationEvent.BOOKING_CANCELLED, "Booking cancelled",
                    "Hi {{passengerName}}, booking {{bookingId}} was cancelled. Reason: {{cancellationReason}}."),
            new Message(NotificationEvent.BOOKING_MODIFIED, "Booking updated",
                    "Hi {{passengerName}}, booking {{bookingId}} was updated. Pickup: {{newPickupLocation}}. Drop: {{newDropLocation}}."),
            new Message(NotificationEvent.DRIVER_OFFERED, "New ride request",
                    "A new ride request is waiting for you. Open the app to accept it."),
            new Message(NotificationEvent.DRIVER_ASSIGNED, "Driver assigned",
                    "{{driverName}} is your driver, in {{vehicleModel}} ({{vehicleNumber}}). Booking {{bookingId}}."),
            new Message(NotificationEvent.DRIVER_ARRIVING, "Your driver is on the way",
                    "Your driver is on the way to the pickup point. Please be ready."),
            new Message(NotificationEvent.DRIVER_ARRIVED, "Your driver has arrived",
                    "{{driverName}} ({{vehicleNumber}}) is at {{pickupLocation}}. Booking {{bookingId}}."),
            new Message(NotificationEvent.RIDE_STARTED, "Ride started",
                    "Your ride {{bookingId}} with {{driverName}} ({{vehicleNumber}}) has started."),
            new Message(NotificationEvent.RIDE_COMPLETED, "Ride completed",
                    "Hi {{passengerName}}, your ride {{bookingId}} to {{dropLocation}} is complete. Distance: {{distance}}. Fare: {{amount}}."),
            new Message(NotificationEvent.TRIP_REMINDER, "Trip reminder",
                    "Hi {{passengerName}}, a reminder for booking {{bookingId}}: pickup at {{pickupTime}} from {{pickupLocation}}."),
            new Message(NotificationEvent.PAYMENT_LINK_SENT, "Payment requested",
                    "Hi {{passengerName}}, please pay {{amount}} for booking {{bookingId}}: {{paymentLink}} (valid till {{expiryTime}})."),
            new Message(NotificationEvent.REFUND_PROCESSED, "Refund processed",
                    "Hi {{passengerName}}, your refund of {{refundAmount}} for booking {{bookingId}} has been processed. Reference: {{refundId}}."),
            new Message(NotificationEvent.PROMO_ALERT, "{{promoTitle}}",
                    "Hi {{passengerName}}, use code {{promoCode}} for {{discountPercent}}% off. Valid till {{validTill}}."),
            new Message(NotificationEvent.SYSTEM_ALERT, "{{alertTitle}}", "{{alertBody}}"),
            new Message(NotificationEvent.ADMIN_BROADCAST, "{{title}}", "{{message}}"));

    private static final List<Sms> SMS_MESSAGES = List.of(
            new Sms(NotificationEvent.BOOKING_CONFIRMED,
                    DefaultNotificationTemplates.BRAND_NAME_TOKEN + ": booking {{bookingId}} is confirmed. Pickup: {{pickupLocation}}."),
            new Sms(NotificationEvent.DRIVER_ARRIVED,
                    DefaultNotificationTemplates.BRAND_NAME_TOKEN + ": your driver {{driverName}} ({{vehicleNumber}}) has arrived. Booking {{bookingId}}."));

    private RideLifecycleTemplates() {
    }

    /** Every default: in-app and push for each event, plus SMS for booking confirmed and driver arrived. */
    public static List<Seed> seeds() {
        List<Seed> seeds = new ArrayList<>();
        for (Message message : MESSAGES) {
            String label = label(message.event());
            seeds.add(new Seed(message.event().name(), NotificationChannel.IN_APP, label + " - In App", message.title(), message.body()));
            seeds.add(new Seed(message.event().name(), NotificationChannel.FCM_PUSH, label + " - Push", message.title(), message.body()));
        }
        for (Sms sms : SMS_MESSAGES) {
            seeds.add(new Seed(sms.event().name(), NotificationChannel.SMS, label(sms.event()) + " - SMS", null, sms.body()));
        }
        return List.copyOf(seeds);
    }

    /** "BOOKING_CONFIRMED" as "Booking Confirmed", for the template name. */
    private static String label(NotificationEvent event) {
        StringBuilder label = new StringBuilder();
        for (String word : event.name().split("_")) {
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(word.charAt(0)).append(word.substring(1).toLowerCase(java.util.Locale.ROOT));
        }
        return label.toString();
    }
}
