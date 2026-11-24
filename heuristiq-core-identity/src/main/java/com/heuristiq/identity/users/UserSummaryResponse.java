package com.heuristiq.uam.users;

import com.heuristiq.uam.helper.RPhoneNumber;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The few user details another service shows next to a user id (never credentials or security settings).
 *
 * @param id          user id
 * @param userName    login name
 * @param displayName name shown across applications
 * @param email       primary email, may be null
 * @param phoneNumber primary phone, may be null
 */
@Schema(description = "Minimal user details for display next to a user id.")
public record UserSummaryResponse(String id, String userName, String displayName, String email, RPhoneNumber phoneNumber) {

    /**
     * @param user the user
     * @return the summary of that user
     */
    public static UserSummaryResponse from(UamUser user) {
        return new UserSummaryResponse(user.getId(), user.getUserName(), user.getDisplayName(), user.getEmail(),
                user.getPhoneNumber());
    }
}
