package com.heuristiq.notification.application.usecase.readiness;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fixed values of the NMS readiness report (docs/APPLICATION_READINESS.md): the check id, its message key, the Rider_Web fix
 * route, param names, the reasons given for UNKNOWN, the business applications and the event-code prefixes that identify an
 * application's notification events.
 */
public final class NmsReadinessConstants {

    /** Service name in the report. */
    public static final String SERVICE = "nms";

    /** Check id (never renamed once released). */
    public static final String CHECK_TEMPLATES_EXIST = "NMS_TEMPLATES_EXIST";
    /** Message key base; the status in lower case is appended. */
    public static final String KEY_TEMPLATES_EXIST = "readiness.nms.templatesExist";

    /** Rider_Web route of the notification templates screen; {@code ?application=<CODE>} is appended. */
    public static final String FIX_PATH_TEMPLATES = "/nms-templates";
    public static final String APPLICATION_QUERY = "?application=";

    /** Param names. */
    public static final String PARAM_TEMPLATE_COUNT = "templateCount";
    public static final String PARAM_ORGANIZATION_TEMPLATES = "organizationTemplates";
    public static final String PARAM_SHARED_TEMPLATES = "sharedTemplates";
    public static final String PARAM_EVENT_COUNT = "eventCount";
    public static final String PARAM_SCOPE = "scope";
    public static final String PARAM_EVENT_PREFIXES = "eventPrefixes";
    public static final String PARAM_REASON = "reason";

    /** Templates matched by the application's event-code prefixes. */
    public static final String SCOPE_EVENT_PREFIX = "EVENT_PREFIX";
    /** The application has no event prefix: any active template of the organization counts. */
    public static final String SCOPE_ORGANIZATION = "ORGANIZATION";

    /** Reasons given in {@link #PARAM_REASON} for UNKNOWN. */
    public static final String REASON_TIMEOUT = "TIMEOUT";
    public static final String REASON_EVALUATION_FAILED = "EVALUATION_FAILED";

    /** Separator of a list inside one param value. */
    public static final String LIST_SEPARATOR = ",";

    public static final String MATRIMONY_APPLICATION = "PASUPU_KUMKUMA_MATRIMONY_APP";
    public static final String RIDER_APPLICATION = "BESTRIDE_RIDER_APP";
    public static final String FOOD_APPLICATION = "FOOD_DELIVER_APP";
    public static final String BEST_PURCHASE_APPLICATION = "BEST_PURCHASE_APP";
    public static final String WALLET_APPLICATION = "CENTRALISED_WALLET_APP";

    /** The business applications of Rider_UAM's catalogue; any other code gets NOT_APPLICABLE. */
    public static final Set<String> BUSINESS_APPLICATIONS = Set.of(
            MATRIMONY_APPLICATION, RIDER_APPLICATION, FOOD_APPLICATION, BEST_PURCHASE_APPLICATION, WALLET_APPLICATION);

    /**
     * Event-code prefixes of each application's own notification events, read from the event catalogue
     * ({@code NotificationEvent}): the matrimony events are {@code MATRIMONY_*} (the prefix onboarding also uses for its
     * matrimony applicant messages), the ride app's are the booking, driver and ride events, the wallet's {@code WALLET_*}.
     * An application not listed here has no events of its own yet and is evaluated organization-wide.
     */
    public static final Map<String, List<String>> EVENT_PREFIXES_BY_APPLICATION = Map.of(
            MATRIMONY_APPLICATION, List.of("MATRIMONY_"),
            RIDER_APPLICATION, List.of("BOOKING_", "DRIVER_", "RIDE_"),
            WALLET_APPLICATION, List.of("WALLET_"));

    private NmsReadinessConstants() {
    }
}
