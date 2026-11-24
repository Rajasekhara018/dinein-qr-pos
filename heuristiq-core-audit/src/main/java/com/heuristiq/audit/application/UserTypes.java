package com.railcab.audit.application;

import static com.railcab.audit.application.IngestConstants.INTERNAL_SERVICE_ROLE;
import static com.railcab.audit.application.IngestConstants.USER_TYPE_ANONYMOUS;
import static com.railcab.audit.application.IngestConstants.USER_TYPE_CUSTOMER;
import static com.railcab.audit.application.IngestConstants.USER_TYPE_DRIVER;
import static com.railcab.audit.application.IngestConstants.USER_TYPE_SERVICE;
import static com.railcab.audit.application.IngestConstants.USER_TYPE_STAFF;

import java.util.List;
import java.util.Locale;

/** Maps role names to the coarse user type shown in the audit screen. */
public final class UserTypes {

    private UserTypes() {
    }

    /**
     * @param roles    the caller's roles without the ROLE_ prefix
     * @param hasUser  whether a user id is known
     * @return CUSTOMER, DRIVER, SERVICE, STAFF (any other role) or ANONYMOUS
     */
    public static String infer(List<String> roles, boolean hasUser) {
        if (roles != null) {
            for (String r : roles) {
                String u = r.toUpperCase(Locale.ROOT);
                if (u.equals(INTERNAL_SERVICE_ROLE) || u.equals(USER_TYPE_SERVICE)) {
                    return USER_TYPE_SERVICE;
                }
            }
            for (String r : roles) {
                String u = r.toUpperCase(Locale.ROOT);
                if (u.equals(USER_TYPE_CUSTOMER) || u.equals(USER_TYPE_DRIVER)) {
                    return u;
                }
            }
            if (!roles.isEmpty()) {
                return USER_TYPE_STAFF;
            }
        }
        return hasUser ? USER_TYPE_STAFF : USER_TYPE_ANONYMOUS;
    }
}
