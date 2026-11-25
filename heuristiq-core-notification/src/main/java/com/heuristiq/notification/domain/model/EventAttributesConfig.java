package com.heuristiq.notification.domain.model;

import java.util.List;
import java.util.Map;

/**
 * Static mapping of every NotificationEvent to its available template
 * placeholder variables.  Used by the admin UI attributes sidebar.
 */
public final class EventAttributesConfig {

    private EventAttributesConfig() {}

    public static final Map<String, List<String>> ATTRIBUTES = Map.ofEntries(
        Map.entry("BOOKING_CONFIRMED",  List.of(
            "{{bookingId}}", "{{passengerName}}", "{{pickupLocation}}",
            "{{dropLocation}}", "{{amount}}", "{{driverName}}",
            "{{vehicleNumber}}", "{{bookingDate}}")),

        Map.entry("BOOKING_CANCELLED",  List.of(
            "{{bookingId}}", "{{passengerName}}", "{{cancellationReason}}",
            "{{refundAmount}}", "{{cancellationTime}}")),

        Map.entry("BOOKING_MODIFIED",   List.of(
            "{{bookingId}}", "{{passengerName}}", "{{newPickupLocation}}",
            "{{newDropLocation}}", "{{modifiedAt}}")),

        Map.entry("DRIVER_ASSIGNED",    List.of(
            "{{bookingId}}", "{{driverName}}", "{{driverPhone}}",
            "{{vehicleNumber}}", "{{vehicleModel}}", "{{eta}}")),

        Map.entry("DRIVER_ARRIVED",     List.of(
            "{{bookingId}}", "{{driverName}}", "{{vehicleNumber}}",
            "{{arrivalTime}}", "{{pickupLocation}}",
            "{{arrivedAtPickupAt}}", "{{freeWaitingMinutes}}", "{{waitingChargePerMin}}")),

        Map.entry("RIDE_STARTED",       List.of(
            "{{bookingId}}", "{{driverName}}", "{{vehicleNumber}}",
            "{{startTime}}", "{{pickupLocation}}",
            "{{waitingMinutes}}", "{{waitingCharge}}")),

        Map.entry("DRIVER_NEARBY",          List.of("{{rideId}}", "{{distanceMeters}}", "{{driverName}}", "{{vehicleNumber}}")),

        Map.entry("WAITING_CHARGE_STARTED", List.of("{{rideId}}", "{{waitingChargePerMin}}")),

        Map.entry("PAYMENT_PENDING",        List.of("{{rideId}}", "{{finalFare}}", "{{paymentMethod}}", "{{waitingCharge}}")),

        Map.entry("PAYMENT_COMPLETED",      List.of("{{rideId}}", "{{finalFare}}", "{{paymentMethod}}")),

        Map.entry("RIDE_CLOSED",            List.of("{{rideId}}", "{{finalFare}}")),

        Map.entry("EARLY_DROP_CONFIRM_CALL", List.of("{{rideId}}", "{{finalFare}}", "{{dropAddress}}")),

        Map.entry("RIDE_DISPUTE_OPENED",    List.of("{{rideId}}", "{{reason}}")),

        Map.entry("RIDE_DISPUTE_RESOLVED",  List.of("{{rideId}}", "{{resolution}}", "{{finalFare}}")),

        Map.entry("NO_DRIVER_FOUND",        List.of("{{rideId}}", "{{searchTimeoutSeconds}}")),

        Map.entry("RIDE_COMPLETED",     List.of(
            "{{bookingId}}", "{{passengerName}}", "{{distance}}",
            "{{duration}}", "{{amount}}", "{{dropLocation}}", "{{completionTime}}")),

        Map.entry("PAYMENT_SUCCESSFUL", List.of(
            "{{bookingId}}", "{{passengerName}}", "{{amount}}",
            "{{transactionId}}", "{{paymentMethod}}", "{{paidAt}}")),

        Map.entry("PAYMENT_FAILED",     List.of(
            "{{bookingId}}", "{{passengerName}}", "{{amount}}",
            "{{failureReason}}", "{{failedAt}}")),

        Map.entry("PAYMENT_LINK_SENT",  List.of(
            "{{bookingId}}", "{{passengerName}}", "{{paymentLink}}",
            "{{amount}}", "{{expiryTime}}")),

        Map.entry("REFUND_PROCESSED",   List.of(
            "{{bookingId}}", "{{passengerName}}", "{{refundAmount}}",
            "{{refundId}}", "{{refundedAt}}", "{{paymentMethod}}")),

        Map.entry("OTP_REQUEST",        List.of(
            "{{otp}}", "{{passengerName}}", "{{expiryMinutes}}", "{{purpose}}")),

        Map.entry("PASSWORD_RESET",     List.of(
            "{{passengerName}}", "{{resetLink}}", "{{expiryHours}}")),

        Map.entry("ACCOUNT_CREATED",    List.of(
            "{{passengerName}}", "{{email}}", "{{phone}}", "{{verificationLink}}")),

        Map.entry("PROMO_ALERT",        List.of(
            "{{passengerName}}", "{{promoCode}}", "{{discountPercent}}",
            "{{validTill}}", "{{promoTitle}}")),

        Map.entry("TRIP_REMINDER",      List.of(
            "{{passengerName}}", "{{pickupTime}}", "{{pickupLocation}}",
            "{{bookingId}}", "{{reminderType}}")),

        Map.entry("SYSTEM_ALERT",       List.of(
            "{{alertTitle}}", "{{alertBody}}", "{{alertTime}}", "{{severity}}")),

        Map.entry("ADMIN_BROADCAST",    List.of(
            "{{title}}", "{{message}}")),

        Map.entry("WORKFLOW_TASK_ASSIGNED",   List.of(
            "{{recipientName}}", "{{taskName}}", "{{processName}}", "{{subject}}", "{{dueDate}}", "{{taskLink}}")),

        Map.entry("WORKFLOW_TASK_REASSIGNED", List.of(
            "{{recipientName}}", "{{taskName}}", "{{processName}}", "{{subject}}", "{{dueDate}}", "{{taskLink}}",
            "{{reassignedBy}}")),

        Map.entry("ONBOARDING_RECEIVED",      List.of(
            "{{applicantName}}", "{{reference}}", "{{typeName}}")),

        Map.entry("ONBOARDING_RETURNED",      List.of(
            "{{applicantName}}", "{{reference}}", "{{typeName}}", "{{reason}}")),

        Map.entry("ONBOARDING_APPROVED",      List.of(
            "{{applicantName}}", "{{reference}}", "{{typeName}}")),

        Map.entry("ONBOARDING_REJECTED",      List.of(
            "{{applicantName}}", "{{reference}}", "{{typeName}}", "{{reason}}")),

        Map.entry("MATRIMONY_ONBOARDING_RECEIVED", List.of(
            "{{applicantName}}", "{{reference}}", "{{typeName}}")),

        Map.entry("MATRIMONY_ONBOARDING_RETURNED", List.of(
            "{{applicantName}}", "{{reference}}", "{{typeName}}", "{{reason}}")),

        Map.entry("MATRIMONY_ONBOARDING_APPROVED", List.of(
            "{{applicantName}}", "{{reference}}", "{{typeName}}",
            // The sign-in details of a newly created account. signInDetails is a ready sentence that also reads correctly
            // for a member who already had an account; prefer it to the two raw values.
            "{{signInDetails}}", "{{loginEmail}}", "{{temporaryPassword}}")),

        // signInDetails is a ready sentence: the email and one-time password of a new account, or how to sign in when the
        // person already had one. The password is never a separate variable, so it cannot end up in a template by itself.
        // signInDetails is a ready sentence: the email and one-time password of a new account, or how to sign in when the
        // person already had one. organizationName is added to every message by the sender's organization.
        Map.entry("ORGANIZATION_ADMIN_WELCOME", List.of(
            "{{adminName}}", "{{organizationName}}", "{{signInDetails}}")),

        // applications is a ready HTML list of what the person was given (each with a link when it has an address); portalUrl the sign-in page's
        // address; signInDetails a ready sentence.
        Map.entry("FRANCHISE_WELCOME", List.of(
            "{{personName}}", "{{roleLabel}}", "{{franchiseCode}}", "{{applications}}", "{{portalUrl}}", "{{signInDetails}}", "{{organizationName}}")),

        Map.entry("FRANCHISE_AGENT_REQUEST", List.of(
            "{{ownerName}}", "{{ownerEmail}}", "{{franchiseCode}}", "{{applicationName}}", "{{requestedAgents}}", "{{currentLimit}}", "{{note}}",
            "{{organizationName}}")),

        Map.entry("FRANCHISE_DEMAND_THRESHOLD", List.of(
            "{{placeName}}", "{{placeCode}}", "{{placeType}}", "{{applicationName}}", "{{votes}}", "{{windowDays}}", "{{threshold}}",
            "{{portalUrl}}", "{{organizationName}}")),

        Map.entry("DRIVER_SUBSCRIPTION_REMINDER", List.of(
            "{{planName}}", "{{periodEnd}}", "{{price}}", "{{currency}}", "{{autoRenew}}", "{{organizationName}}")),

        Map.entry("WALLET_ALERT", List.of(
            "{{alertType}}", "{{alertMessage}}", "{{appCode}}", "{{organizationName}}")),

        Map.entry("MATRIMONY_ACCOUNT_ACTIVATED", List.of(
            "{{customerName}}", "{{signInDetails}}")),

        Map.entry("MATRIMONY_SELF_REGISTER_EXISTING_ACCOUNT", List.of(
            "{{email}}")),

        Map.entry("MATRIMONY_NOTIFICATION", List.of(
            "{{title}}", "{{message}}")),

        Map.entry("MATRIMONY_PHONE_OTP", List.of(
            "{{code}}", "{{minutes}}")),

        Map.entry("MATRIMONY_ONBOARDING_REJECTED", List.of(
            "{{applicantName}}", "{{reference}}", "{{typeName}}", "{{reason}}")),

        Map.entry("WORKFLOW_TASK_DUE_SOON",   List.of(
            "{{recipientName}}", "{{taskName}}", "{{processName}}", "{{subject}}", "{{dueDate}}", "{{taskLink}}")),

        Map.entry("WORKFLOW_TASK_OVERDUE",    List.of(
            "{{recipientName}}", "{{taskName}}", "{{processName}}", "{{subject}}", "{{dueDate}}", "{{taskLink}}")),

        Map.entry("SOS_ALERT_RAISED",         List.of(
            "{{alertId}}", "{{rideId}}", "{{userId}}", "{{lat}}", "{{lng}}")),

        Map.entry("SOS_ALERT_ESCALATED",      List.of(
            "{{alertId}}", "{{rideId}}", "{{userId}}", "{{lat}}", "{{lng}}")),

        Map.entry("SOS_CONTACT_ALERT",        List.of(
            "{{alertId}}", "{{rideId}}", "{{contactName}}", "{{lat}}", "{{lng}}")),

        Map.entry("EARLY_DROP_REQUESTED",     List.of("{{rideId}}", "{{expiresAt}}", "{{finalFare}}", "{{earlyDropExpiresAt}}")),

        Map.entry("EARLY_DROP_ACCEPTED",      List.of("{{rideId}}", "{{automatic}}")),

        Map.entry("EARLY_DROP_DECLINED",      List.of("{{rideId}}", "{{automatic}}")),

        Map.entry("SUPPORT_TICKET_REPLY",     List.of("{{ticketId}}", "{{ticketReference}}", "{{subject}}")),
        Map.entry("SUPPORT_TICKET_DUE_SOON",  List.of("{{ticketId}}", "{{ticketReference}}", "{{deadline}}")),
        Map.entry("SUPPORT_TICKET_OVERDUE",   List.of("{{ticketId}}", "{{ticketReference}}", "{{deadline}}")),

        Map.entry("TEST_NOTIFICATION",  List.of(
            "{{message}}"))
    );

    public static List<String> forEvent(String event) {
        return ATTRIBUTES.getOrDefault(event, List.of());
    }
}
