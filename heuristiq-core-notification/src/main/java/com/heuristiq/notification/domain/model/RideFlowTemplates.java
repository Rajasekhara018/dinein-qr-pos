package com.railcab.nms.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.railcab.nms.domain.model.DefaultNotificationTemplates.Seed;

/**
 * Default texts of the November 2026 ride flow events (docs/RIDE_FLOW_FIXES_2026-11.md section 3): driver nearby, waiting
 * charge, payment pending/completed, ride closed, the ride dispute, no driver found and the early-drop escalation (an SMS for
 * {@code EARLY_DROP_REQUESTED} and the {@code EARLY_DROP_CONFIRM_CALL} voice call).
 *
 * <p>Each text uses only the {@code {{placeholders}}} its event declares in {@link EventAttributesConfig}. Enum codes such as
 * the dispute reason are not shown to the customer. English drafts (templates have no language column); created once by
 * {@code TemplateService} for an event and channel that has no template yet, never replacing an admin's template. The
 * wording needs a review before it is relied on.
 */
public final class RideFlowTemplates {

    /** Indian rupee sign used before fares in in-app, push and voice texts. */
    private static final String RUPEE = "₹";

    /** The in-app / push title and text of one event (the same for both channels). */
    private record Message(NotificationEvent event, String title, String body) {
    }

    private static final List<Message> MESSAGES = List.of(
            new Message(NotificationEvent.DRIVER_NEARBY, "Your driver is nearby",
                    "{{driverName}} ({{vehicleNumber}}) is about {{distanceMeters}} m from your pickup point. Please be ready."),
            new Message(NotificationEvent.WAITING_CHARGE_STARTED, "Waiting charge started",
                    "Your free waiting time is over. A waiting charge of " + RUPEE + "{{waitingChargePerMin}} per minute now applies."),
            new Message(NotificationEvent.PAYMENT_PENDING, "Payment due",
                    "Your ride is complete. Please pay " + RUPEE + "{{finalFare}} by {{paymentMethod}}."),
            new Message(NotificationEvent.PAYMENT_COMPLETED, "Payment received",
                    "We received your payment of " + RUPEE + "{{finalFare}} by {{paymentMethod}}. Thank you for riding with us."),
            new Message(NotificationEvent.RIDE_CLOSED, "Ride closed",
                    "Your ride is closed. Total fare: " + RUPEE + "{{finalFare}}."),
            new Message(NotificationEvent.RIDE_DISPUTE_OPENED, "Ride under review",
                    "Our support team is reviewing the end of your ride. We will update you once the review is complete."),
            new Message(NotificationEvent.RIDE_DISPUTE_RESOLVED, "Ride review complete",
                    "The review of your ride is complete. Final fare: " + RUPEE + "{{finalFare}}."),
            new Message(NotificationEvent.NO_DRIVER_FOUND, "Unable to book your ride",
                    "No drivers accepted your ride. Please try again."));

    /** SMS for the early drop request; opens with the brand name, plain "Rs." so it stays a single GSM-7 message. */
    private static final String EARLY_DROP_SMS = DefaultNotificationTemplates.BRAND_NAME_TOKEN
            + ": your driver asks to end the trip early. Fare Rs.{{finalFare}}. Open the app to accept or decline.";

    /** What the IVR call is about; the spoken prompt itself lives in the provider's call flow (press 1 / press 2). */
    private static final String EARLY_DROP_CALL = "Your driver asked to end your trip before {{dropAddress}}. The fare is "
            + RUPEE + "{{finalFare}}. Press 1 to accept, or press 2 if you did not agree.";

    private RideFlowTemplates() {
    }

    /** In-app and push for each event above, plus the early drop SMS and the confirmation voice call. */
    public static List<Seed> seeds() {
        List<Seed> seeds = new ArrayList<>();
        for (Message message : MESSAGES) {
            String label = label(message.event());
            seeds.add(new Seed(message.event().name(), NotificationChannel.IN_APP, label + " - In App", message.title(), message.body()));
            seeds.add(new Seed(message.event().name(), NotificationChannel.FCM_PUSH, label + " - Push", message.title(), message.body()));
        }
        seeds.add(new Seed(NotificationEvent.EARLY_DROP_REQUESTED.name(), NotificationChannel.SMS,
                label(NotificationEvent.EARLY_DROP_REQUESTED) + " - SMS", null, EARLY_DROP_SMS));
        seeds.add(new Seed(NotificationEvent.EARLY_DROP_CONFIRM_CALL.name(), NotificationChannel.VOICE,
                label(NotificationEvent.EARLY_DROP_CONFIRM_CALL) + " - Voice", null, EARLY_DROP_CALL));
        return List.copyOf(seeds);
    }

    /** "DRIVER_NEARBY" as "Driver Nearby", for the template name. */
    private static String label(NotificationEvent event) {
        StringBuilder label = new StringBuilder();
        for (String word : event.name().split("_")) {
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return label.toString();
    }
}
