package com.heuristiq.notification.domain.model;

public enum NotificationEvent {

    // Booking lifecycle
    BOOKING_CONFIRMED,
    BOOKING_CANCELLED,
    BOOKING_MODIFIED,

    // Driver / ride
    DRIVER_ASSIGNED,
    DRIVER_ARRIVING,
    DRIVER_OFFERED,
    DRIVER_ARRIVED,
    RIDE_STARTED,
    RIDE_COMPLETED,

    // Payment
    PAYMENT_SUCCESSFUL,
    PAYMENT_FAILED,
    PAYMENT_LINK_SENT,
    REFUND_PROCESSED,

    // Authentication
    OTP_REQUEST,
    PASSWORD_RESET,
    ACCOUNT_CREATED,

    // Promotions
    PROMO_ALERT,
    TRIP_REMINDER,

    // System / admin
    SYSTEM_ALERT,

    /** Free-form admin broadcast: templates render {{title}} / {{message}} (see DefaultNotificationTemplates). */
    ADMIN_BROADCAST,

    // Workflow (Rider_WorkflowService): approval-step tasks. Variables: {{recipientName}} {{taskName}} {{processName}}
    // {{subject}} {{dueDate}} {{taskLink}} (+ {{reassignedBy}} for WORKFLOW_TASK_REASSIGNED).
    WORKFLOW_TASK_ASSIGNED,
    WORKFLOW_TASK_REASSIGNED,
    WORKFLOW_TASK_OVERDUE,
    WORKFLOW_TASK_DUE_SOON,
    // Onboarding (Rider_OnboardingService): the applicant. Variables: {{applicantName}} {{reference}} {{typeName}} {{reason}}.
    ONBOARDING_RECEIVED,
    ONBOARDING_RETURNED,
    ONBOARDING_APPROVED,
    ONBOARDING_REJECTED,
    // Matrimony onboarding (Rider_OnboardingService, type MATRIMONY_MEMBER): same variables as the ONBOARDING_* events,
    // with matrimony-branded content so the Rider (RailCab) templates are left untouched.
    MATRIMONY_ONBOARDING_RECEIVED,
    MATRIMONY_ONBOARDING_RETURNED,
    MATRIMONY_ONBOARDING_APPROVED,
    MATRIMONY_ONBOARDING_REJECTED,
    /**
     * A matrimony customer's account is active (after the fee was collected by an agent or confirmed by Ops): the message
     * that carries their sign-in details. Variables: {{customerName}} {{signInDetails}}.
     */
    MATRIMONY_ACCOUNT_ACTIVATED,
    /**
     * Someone tried to create a matrimony account with an email address that already has one. Sent to that address only (never
     * shown on the sign-up screen) so a form cannot be used to find out who is a member. Variables: {{email}}.
     */
    MATRIMONY_SELF_REGISTER_EXISTING_ACCOUNT,
    /**
     * An organization was set up and this person is its first administrator: the message that tells them and carries their
     * sign-in details. Variables: {{adminName}} {{signInDetails}}; {{organizationName}} comes with every message.
     */
    ORGANIZATION_ADMIN_WELCOME,
    /** Sent to a restaurant's staff. Variables: {{orderNumber}} when an order is named. */
    FOOD_NEW_ORDER,
    /** Sent to the customer. Variables: {{orderNumber}} when an order is named. */
    FOOD_ORDER_ACCEPTED,
    /** Sent to the customer. Variables: {{orderNumber}} when an order is named. */
    FOOD_ORDER_REJECTED,
    /** Sent to the customer. Variables: {{orderNumber}} when an order is named. */
    FOOD_ORDER_ON_THE_WAY,
    /** Sent to the customer. Variables: {{orderNumber}} when an order is named. */
    FOOD_ORDER_DELIVERED,
    /** Sent to a courier. Variables: {{orderNumber}} when an order is named. */
    FOOD_COURIER_OFFER,
    /** Sent to the customer. Variables: {{orderNumber}} when an order is named. */
    FOOD_REFUND_APPROVED,
    /** Sent to the customer. Variables: {{orderNumber}} when an order is named. */
    FOOD_REFUND_DECLINED,
    /**
     * Someone was made the owner of a franchise, or an agent of one: the message that tells them which applications they
     * were given and how to sign in. Variables: {{personName}} {{roleLabel}} {{franchiseCode}} {{applications}} {{portalUrl}} {{signInDetails}};
     * {{organizationName}} comes with every message.
     */
    FRANCHISE_WELCOME,
    /**
     * A franchise owner asks the organization's administrator for more agent places in one application. Variables: {{ownerName}}
     * {{ownerEmail}} {{franchiseCode}} {{applicationName}} {{requestedAgents}} {{currentLimit}} {{note}}; {{organizationName}} comes with every message.
     */
    FRANCHISE_AGENT_REQUEST,
    /**
     * A place no franchise covers has collected enough "ask for service here" votes for one application within the
     * organization's window (Rider_FranchiseService, settings {@code franchise.demandAlert.*}). Variables: {{placeName}}
     * {{placeCode}} {{placeType}} {{applicationName}} {{votes}} {{windowDays}} {{threshold}} {{portalUrl}}; {{organizationName}}
     * comes with every message.
     */
    FRANCHISE_DEMAND_THRESHOLD,
    /**
     * Wallet alert for an organization's administrators: low agent float, a large posting, a failed ledger check or
     * undelivered events. Variables: {{alertType}} {{alertMessage}} {{appCode}}; {{organizationName}} comes with every message.
     */
    WALLET_ALERT,
    /**
     * Matrimony app (matrimonial-api) member/staff notifications: interest received or accepted, new message, match digest.
     * The caller has already written the text in the recipient's own language, so the templates only frame it. Variables:
     * {{title}} {{message}}.
     */
    MATRIMONY_NOTIFICATION,
    /** The six-digit code a member must send back to prove a phone number is theirs; SMS only. Variables: {{code}} {{minutes}}. */
    MATRIMONY_PHONE_OTP,
    // Safety (Rider_RideService SOS). Variables: {{alertId}} {{rideId}} {{lat}} {{lng}} (+ {{userId}} for the ops events,
    // {{contactName}} for SOS_CONTACT_ALERT). See docs/SOS_TEMPLATES.md.
    /** Ops/on-call: sent immediately when an SOS is raised. */
    SOS_ALERT_RAISED,
    /** Ops: sent when a raised SOS stays unacknowledged past the escalation threshold. */
    SOS_ALERT_ESCALATED,
    /** The customer's emergency contact, sent by SMS. */
    SOS_CONTACT_ALERT,
    // Early drop (Rider_RideService): the driver asks to end the trip before the planned drop point.
    /** To the customer: the driver asks to end the trip early. Variables: {{rideId}} {{expiresAt}} {{finalFare}}. */
    EARLY_DROP_REQUESTED,
    /** To the driver: the customer accepted (or the request was accepted automatically). Variables: {{rideId}} {{automatic}}. */
    EARLY_DROP_ACCEPTED,
    /** To the driver: the customer declined (or the request was declined automatically); the trip goes on. Variables: {{rideId}} {{automatic}}. */
    EARLY_DROP_DECLINED,
    // Ride flow (Rider_RideService, docs/RIDE_FLOW_FIXES_2026-11.md section 3). The event name is also the push "type".
    /** To the customer: the driver is within the nearby radius of the pickup. Variables: {{rideId}} {{distanceMeters}} {{driverName}} {{vehicleNumber}}. */
    DRIVER_NEARBY,
    /** To the customer: the free waiting minutes are over. Variables: {{rideId}} {{waitingChargePerMin}}. */
    WAITING_CHARGE_STARTED,
    /** To the customer: the ride waits for payment. Variables: {{rideId}} {{finalFare}} {{paymentMethod}} {{waitingCharge}}. */
    PAYMENT_PENDING,
    /** To the customer: the ride's payment completed (cash, wallet or gateway). Variables: {{rideId}} {{finalFare}} {{paymentMethod}}. */
    PAYMENT_COMPLETED,
    /** To the customer: the ride is closed. Variables: {{rideId}} {{finalFare}}. */
    RIDE_CLOSED,
    /** Automated IVR call to the customer about an unanswered early drop (VOICE). Variables: {{rideId}} {{finalFare}} {{dropAddress}}. */
    EARLY_DROP_CONFIRM_CALL,
    /** To the customer: the ride went to the operations dispute queue. Variables: {{rideId}} {{reason}}. */
    RIDE_DISPUTE_OPENED,
    /** To the customer: operations resolved the ride's dispute. Variables: {{rideId}} {{resolution}} {{finalFare}}. */
    RIDE_DISPUTE_RESOLVED,
    /** To the customer: nobody accepted the ride within the search time, so it ended (NO_DRIVER). Variables: {{rideId}} {{searchTimeoutSeconds}}. */
    NO_DRIVER_FOUND,
    // Help desk (Rider_AdminAPI).
    /** To the customer or driver: support replied on their ticket. Variables: {{ticketId}} {{ticketReference}} {{subject}}. */
    SUPPORT_TICKET_REPLY,
    SUPPORT_TICKET_DUE_SOON,
    SUPPORT_TICKET_OVERDUE,
    // Driver subscriptions (Rider_FranchiseService, docs/DRIVER_FRANCHISE_SUBSCRIPTION_PLAN.md D11). The event name is also the push "type".
    /** To the driver: their paid subscription period ends tomorrow. Variables: {{planName}} {{periodEnd}} {{price}} {{currency}} {{autoRenew}}. */
    DRIVER_SUBSCRIPTION_REMINDER,
    // Driver document expiry (Rider_DMS, D12). Variables: {{documentName}} {{validUntil}} {{daysLeft}}.
    /** To the driver: a licence, RC, insurance, PUC or permit expires in {{daysLeft}} days (30, 15, 7, 1). */
    DRIVER_DOCUMENT_EXPIRING,
    /** To the driver: the document has expired; they cannot go online until a renewed one is approved. */
    DRIVER_DOCUMENT_LAPSED,
    /** Ad-hoc test trigger (e.g. a "send test notification" demo button): templates render just {{message}}. */
    TEST_NOTIFICATION
}
