package com.heuristiq.identity.bootstrap;

import java.util.List;
import java.util.Map;

/**
 * Default permissions of the structural roles (DRIVER, CUSTOMER, AGENT) that UAM seeds at start-up, and the
 * version stamp that lets an existing role pick up newly added defaults once.
 */
public final class BootstrapRoleConstants {

    // CUSTOMER and DRIVER are structural user types (referenced directly by UamEnums.UserType,
    // registration/Google-auth default to them) -- every signup needs a working role immediately,
    // so unlike admin-panel business roles (MANAGER/ACCOUNTANT/SUPPORT/AUDITOR, no longer seeded
    // here at all -- a SUPER_ADMIN creates those dynamically via RoleController with whichever
    // privilege.json codes fit) these two are seeded once, structurally, same as SUPER_ADMIN.
    //
    // The gateway authorizes against the *most specific* privilege.json entry matching a request,
    // so broad grants do not cover endpoints with their own more specific entries.
    // Every endpoint the Driver/Customer apps call needs its own code listed here. Deliberately left out:
    // admin/oversight codes, per-service catch-all *_READ/*_MANAGE (/api/v1/**) codes, pre-login
    // auth/OTP codes (public paths, the caller has no role yet), service-to-service callbacks and
    // webhooks, and catalog/config management (stations, fare configs, NMS templates/events).
    public static final List<String> DRIVER_PERMISSIONS = List.of(
            // NMS -- in-app notifications + device tokens
            "NMS_IN_APP_ACCESS", "NMS_DEVICES_ACCESS",
            "NMS_DEVICE_FCM_TOKEN_REGISTER", "NMS_DEVICE_FCM_TOKEN_DELETE",
            "NMS_IN_APP_NOTIFICATION_LIST", "NMS_IN_APP_NOTIFICATION_UNREAD_COUNT",
            "NMS_IN_APP_NOTIFICATION_MARK_READ", "NMS_IN_APP_NOTIFICATION_MARK_ALL_READ", "NMS_IN_APP_WS_CONNECT",
            // Rider_RideService -- driver side of the ride lifecycle
            "RIDER_RIDE_DRIVER_ACCESS", "RIDE_DRIVER_ACTIVE_READ",
            "RIDE_CALL_INITIATE", "RIDE_CALL_DECLINE", "RIDE_CALL_ANSWER", "RIDE_CALL_END",
            "RIDE_CHAT_SEND", "RIDE_CHAT_READ", "RIDE_CANCELLATION_REASON_LIST",
            "RIDE_READ", "RIDE_HISTORY_LIST", "RIDE_ACTIVE_READ", "RIDE_CANCEL", "RIDE_ACCEPT",
            "RIDE_DRIVER_ARRIVING", "RIDE_DRIVER_ARRIVED", "RIDE_START", "RIDE_COMPLETE",
            "RIDE_SOS_ALERT", "RIDE_DRIVER_LOCATION_UPDATE", "RIDE_CASH_CONFIRM",
            // The driver asks to end a trip early; the customer's accept/decline pair is in CUSTOMER_PERMISSIONS
            "RIDE_EARLY_DROP_CREATE",
            // Help desk (Rider_AdminAPI) -- the driver's own support tickets and the chat with support
            "HELPDESK_TICKET_CREATE", "HELPDESK_TICKET_LIST", "HELPDESK_TICKET_READ",
            "HELPDESK_TICKET_MESSAGE_LIST", "HELPDESK_TICKET_MESSAGE_CREATE",
            // Rider_DispatchService -- offers + the driver's own dispatch record/KYC documents
            "DISPATCH_OFFER_ACCEPT", "DISPATCH_OFFER_DECLINE", "DISPATCH_OFFER_PENDING_LIST",
            "DISPATCH_DRIVER_CREATE", "DISPATCH_DRIVER_UPDATE", "DISPATCH_DRIVER_READ",
            "DISPATCH_DRIVER_STATUS_UPDATE", "DISPATCH_DRIVER_ACCEPTANCE_RATE_READ",
            "DISPATCH_DRIVER_DOCUMENT_UPLOAD", "DISPATCH_DRIVER_DOCUMENTS_READ",
            "DISPATCH_DRIVER_PAYOUT_ACCOUNT_READ", "DISPATCH_DRIVER_PAYOUT_ACCOUNT_UPDATE",
            // Rider_LocationService
            "LOCATION_DRIVER_PING", "LOCATION_GEOCODE_READ", "LOCATION_REVERSE_GEOCODE", "LOCATION_ROUTE_COMPUTE",
            // Rider_PaymentService -- the driver's own earnings/payouts/incentives
            "RIDER_PAYMENT_DRIVER_INCENTIVE_READ", "RIDER_PAYMENT_DRIVER_PAYOUT_LIST",
            "RIDER_PAYMENT_DRIVER_EARNINGS_READ", "RIDER_PAYMENT_RECEIPT_READ",
            // Rider_DriverEarningsService -- the driver's own earnings, trips, performance, incentives, payouts
            "DRIVER_EARNINGS_READ", "DRIVER_EARNINGS_TRIPS_TODAY_LIST", "DRIVER_EARNINGS_PAYMENT_LIST",
            "DRIVER_EARNINGS_PERFORMANCE_READ", "DRIVER_INCENTIVE_CALCULATE", "DRIVER_INCENTIVE_LIST",
            "DRIVER_INCENTIVE_WEEKLY_LIST", "DRIVER_PAYOUT_REQUEST_CREATE", "DRIVER_PAYOUT_REQUEST_LIST",
            // Rider_CustomerService -- ratings summary + support
            "RIDER_CUSTOMER_DRIVER_RATING_SUMMARY_READ", "RIDER_CUSTOMER_CUSTOMER_RATING_CREATE",
            "RIDER_CUSTOMER_SUPPORT_TICKET_CREATE", "RIDER_CUSTOMER_SUPPORT_TICKET_READ",
            // Rider_OnboardingService -- the driver's own onboarding submission
            "ONBOARDING_ACTIVE_SCHEMA_READ", "ONBOARDING_SUBMISSION_CREATE",
            "ONBOARDING_SUBMISSION_UPDATE", "ONBOARDING_SUBMISSION_SUBMIT",
            // Rider_FranchiseService -- the driver's own franchise (profile card, go-online explanation)
            "FRANCHISE_DRIVER_SELF_READ",
            // Rider_RideService -- cash or direct-UPI payment received by the driver
            "RIDE_COLLECTION_CREATE"
    );

    public static final List<String> CUSTOMER_PERMISSIONS = List.of(
            // Payment (monolith gateway integration) -- pay for own rides, no refunds
            "PAYMENT_CREATE_SESSION", "PAYMENT_CHECK_STATUS", "PAYMENT_READ",
            // NMS -- in-app notifications + device tokens
            "NMS_IN_APP_ACCESS", "NMS_DEVICES_ACCESS",
            "NMS_DEVICE_FCM_TOKEN_REGISTER", "NMS_DEVICE_FCM_TOKEN_DELETE",
            "NMS_IN_APP_NOTIFICATION_LIST", "NMS_IN_APP_NOTIFICATION_UNREAD_COUNT",
            "NMS_IN_APP_NOTIFICATION_MARK_READ", "NMS_IN_APP_NOTIFICATION_MARK_ALL_READ", "NMS_IN_APP_WS_CONNECT",
            // Rider_PricingService
            "PRICING_ESTIMATE", "PRICING_QUOTE_VERIFY", "PRICING_PROMO_VALIDATE",
            // Rider_LocationService
            "LOCATION_DRIVERS_NEARBY_READ", "LOCATION_DRIVER_READ", "LOCATION_GEOCODE_READ",
            "LOCATION_GEOCODE_CREATE", "LOCATION_REVERSE_GEOCODE", "LOCATION_ROUTE_COMPUTE",
            // Rider_RideService -- customer side of the ride lifecycle
            "RIDE_CALL_INITIATE", "RIDE_CALL_DECLINE", "RIDE_CALL_ANSWER", "RIDE_CALL_END",
            "RIDE_CHAT_SEND", "RIDE_CHAT_READ", "RIDE_CANCELLATION_REASON_LIST",
            "RIDE_CREATE", "RIDE_READ", "RIDE_HISTORY_LIST", "RIDE_ACTIVE_READ", "RIDE_CANCEL",
            "RIDE_SCHEDULED_CREATE", "RIDE_SCHEDULED_LIST", "RIDE_SCHEDULED_CANCEL", "RIDE_SOS_ALERT",
            // Rider_PaymentService -- payments, receipts, wallet, split fare (no refunds)
            "RIDER_PAYMENT_CREATE", "RIDER_PAYMENT_RECORD_READ", "RIDER_PAYMENT_RECEIPT_READ",
            "RIDER_PAYMENT_SPLIT_FARE_CREATE", "RIDER_PAYMENT_SPLIT_FARE_PAY", "RIDER_PAYMENT_SPLIT_FARE_READ",
            "RIDER_PAYMENT_WALLET_BALANCE_READ", "RIDER_PAYMENT_WALLET_TOPUP", "RIDER_PAYMENT_WALLET_TOPUP_READ",
            "RIDER_PAYMENT_WALLET_TRANSACTIONS_READ",
            // Rider_WalletService -- the member's own credits balance and history (the matrimony Credits screen)
            "WALLET_SELF_READ", "WALLET_SELF_TRANSACTION_LIST", "WALLET_SELF_TRANSACTION_READ",
            "WALLET_SELF_STATEMENT_READ", "WALLET_SELF_STATEMENT_MONTH_LIST", "WALLET_SELF_STATEMENT_SUMMARY_READ",
            "WALLET_SELF_STATEMENT_EXPORT_READ",
            // Rider_CustomerService -- own profile, corporate enrolment, ratings, support
            "RIDER_CUSTOMER_CREATE", "RIDER_CUSTOMER_PROFILE_READ", "RIDER_CUSTOMER_PROFILE_UPDATE",
            "RIDER_CUSTOMER_PROFILE_DELETE", "RIDER_CUSTOMER_PRIVACY_PREFERENCES_UPDATE",
            "RIDER_CUSTOMER_PROFILE_IMAGE_UPLOAD", "RIDER_CUSTOMER_PROFILE_IMAGE_READ",
            "RIDER_CUSTOMER_SAVED_PLACES_READ", "RIDER_CUSTOMER_SAVED_PLACES_UPDATE",
            "RIDER_CUSTOMER_SEARCH_HISTORY_CREATE", "RIDER_CUSTOMER_SEARCH_HISTORY_READ",
            "RIDER_CUSTOMER_FCM_TOKEN_REGISTER", "RIDER_CUSTOMER_FCM_TOKEN_DELETE",
            "RIDER_CUSTOMER_EMERGENCY_CONTACT_UPDATE", "RIDER_CUSTOMER_INVOICE_READ",
            "RIDER_CUSTOMER_EMERGENCY_CONTACTS_READ", "RIDER_CUSTOMER_EMERGENCY_CONTACTS_UPDATE",
            "RIDER_CUSTOMER_OFFER_LIST",
            "RIDER_CUSTOMER_CORPORATE_ENROLL", "RIDER_CUSTOMER_CORPORATE_CUSTOMER_READ",
            "RIDER_CUSTOMER_CORPORATE_CUSTOMER_UNENROLL",
            "RIDER_CUSTOMER_RATING_CREATE", "RIDER_CUSTOMER_DRIVER_RATING_SUMMARY_READ",
            "RIDER_CUSTOMER_RATINGS_LIST",
            "RIDER_CUSTOMER_SUPPORT_TICKET_CREATE", "RIDER_CUSTOMER_SUPPORT_TICKET_READ",
            "RIDER_CUSTOMER_SUPPORT_TICKET_LIST",
            // Early drop: the customer answers the driver's request (accept and decline are granted together)
            "RIDE_EARLY_DROP_ACCEPT_CREATE", "RIDE_EARLY_DROP_DECLINE_CREATE",
            // Live trip tracking link the customer shares
            "RIDE_SHARE_LINK_CREATE", "RIDE_SHARE_LINK_DELETE",
            // Help desk (Rider_AdminAPI) -- the customer's own support tickets and the chat with support
            "HELPDESK_TICKET_CREATE", "HELPDESK_TICKET_LIST", "HELPDESK_TICKET_READ",
            "HELPDESK_TICKET_MESSAGE_LIST", "HELPDESK_TICKET_MESSAGE_CREATE",
            // Rider_OnboardingService -- the customer's own onboarding submission
            "ONBOARDING_ACTIVE_SCHEMA_READ", "ONBOARDING_SUBMISSION_CREATE",
            "ONBOARDING_SUBMISSION_UPDATE", "ONBOARDING_SUBMISSION_SUBMIT",
            // Rider_FranchiseService -- "Ask for service here" where no franchise serves the area yet (a demand vote)
            "FRANCHISE_DEMAND_VOTE_CREATE"
    );

    // The matrimony sales agent's own area (their dashboard, referrals, onboarding fee, and adding a customer on someone's
    // behalf). The all-agents listing (MATRIMONY_AGENT_LIST) is an operations privilege and stays out. A role of this name that
    // an admin created by hand gets these merged in once (it has no defaults version yet); later changes are the admin's.
    public static final List<String> AGENT_PERMISSIONS = List.of(
            "MATRIMONY_AGENT_PROFILE_READ", "MATRIMONY_AGENT_REFERRAL_LIST",
            "MATRIMONY_AGENT_ONBOARDING_FEE_READ", "MATRIMONY_AGENT_CUSTOMER_CREATE"
    );

    public static final Map<String, List<String>> STRUCTURAL_ROLE_PERMISSIONS = Map.of(
            "DRIVER", DRIVER_PERMISSIONS,
            "CUSTOMER", CUSTOMER_PERMISSIONS,
            "AGENT", AGENT_PERMISSIONS
    );

    // Bump whenever DRIVER_PERMISSIONS/CUSTOMER_PERMISSIONS gain codes, so environments where the
    // roles already exist pick the new codes up once on next boot (see seedOrMergeStructuralRole).
    // v7: the member's own wallet (WALLET_SELF_*) reached CUSTOMER after environments had already applied v6, so their
    // CUSTOMER role lacked it and the matrimony Credits calls were refused with 403.
    // v8: early drop (RIDE_EARLY_DROP_*), trip share links (RIDE_SHARE_LINK_*) and the help desk (HELPDESK_TICKET_*).
    // v9: a customer can no longer confirm that a cash or UPI payment arrived (only the driver receives it); the two codes are
    // taken back from CUSTOMER roles that already had them (see STRUCTURAL_ROLE_REVOKED_PERMISSIONS).
    // v10: the customer's demand vote (FRANCHISE_DEMAND_VOTE_CREATE), so "Ask for service here" is not refused with 403.
    // v11: the driver rates the rider (RIDER_CUSTOMER_CUSTOMER_RATING_CREATE) and keeps a payout bank account on the
    // Dispatch profile (DISPATCH_DRIVER_PAYOUT_ACCOUNT_READ/_UPDATE). Its own number, so environments that already applied
    // v10 still pick these codes up.
    // v12: retire all BOOKINGS_* defaults; existing structural roles drop legacy monolith grants once.
    // v14: a customer can read and replace their own emergency-contact list (R2).
    public static final int STRUCTURAL_ROLE_PERMISSIONS_VERSION = 14;

    /**
     * Codes taken away from an existing structural role when its defaults version is raised, because they were wrongly seeded
     * before. Applied once with the same version bump as the additions; a SUPER_ADMIN may grant one back by hand afterwards.
     */
    public static final Map<String, List<String>> STRUCTURAL_ROLE_REVOKED_PERMISSIONS = Map.of(
            "CUSTOMER", List.of("BOOKINGS_CONFIRM_CASH_PAYMENT", "BOOKINGS_CONFIRM_UPI_PAYMENT")
    );
    public static final String PERMISSIONS_VERSION_ATTRIBUTE = "defaultPermissionsVersion";

    /** Classpath resource holding the privilege catalogue (kept byte-identical with the gateway copy). */
    public static final String CATALOG_RESOURCE = "privilege.json";

    private BootstrapRoleConstants() {
    }
}
