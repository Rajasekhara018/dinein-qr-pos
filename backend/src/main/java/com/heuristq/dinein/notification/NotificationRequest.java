package com.heuristq.dinein.notification;

import com.heuristq.dinein.staff.domain.StaffRole;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provider-neutral notification: what happened, who should hear about it and the values the templates need
 * ({@code token}, {@code orderNumber}, {@code table}, {@code reason}). Channels are implied by the recipients.
 *
 * @param orderId related order, stored on the log and in-app rows; may be null
 * @param restaurantId tenant the notification belongs to, used to look up its display name
 */
public record NotificationRequest(NotificationEvent event, Long orderId, Long restaurantId, Map<String, String> data,
                                  Recipients recipients) {

    public NotificationRequest {
        data = data == null ? Map.of() : Map.copyOf(data);
        recipients = recipients == null ? new Recipients(null, null, null, null) : recipients;
    }

    /**
     * @param staffRoles IN_APP inbox of every staff user with one of these roles
     * @param emails     EMAIL addresses
     * @param phones     SMS numbers (10-digit local numbers get the default country code)
     * @param push       PUSH device registrations
     */
    public record Recipients(Set<StaffRole> staffRoles, List<String> emails, List<String> phones,
                             List<PushTarget> push) {

        public Recipients {
            staffRoles = staffRoles == null ? Set.of() : Set.copyOf(staffRoles);
            emails = emails == null ? List.of() : List.copyOf(emails);
            phones = phones == null ? List.of() : List.copyOf(phones);
            push = push == null ? List.of() : List.copyOf(push);
        }
    }

    /** A stored push subscription; the id lets the dispatcher deactivate tokens the provider rejects. */
    public record PushTarget(Long subscriptionId, String provider, String token) {
    }
}
