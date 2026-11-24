package com.heuristiq.uam.usercredentials;

import com.heuristiq.uam.common.UamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * A signed-in person changes their own password: the current one must be right and the new one must differ. Setting it this way clears
 * the "must change" flag a temporary password carried, so the next sign-in goes straight in.
 */
@Service
public class OwnPasswordChangeService {
    private static final Logger log = LoggerFactory.getLogger(OwnPasswordChangeService.class);


    private final UserCredentialServiceUseCase credentials;

    public OwnPasswordChangeService(UserCredentialServiceUseCase credentials) {
        this.credentials = credentials;
    }

    /**
     * @param userId      the signed-in account (taken from the token, never from the request body)
     * @param oldPassword the password the person signs in with now (for a first sign-in, the temporary one)
     * @param newPassword the password to set
     * @throws UamException when the current password is wrong or the new one equals it
     */
    public void change(String userId, String oldPassword, String newPassword) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(oldPassword) || !StringUtils.hasText(newPassword)) {
            throw new UamException("Current and new password are required");
        }
        if (!credentials.verifyPassword(new UserCredentialServiceUseCase.VerifyPasswordInput(userId, oldPassword)).verified()) {
            throw new UamException(UserCredentialConstants.CURRENT_INCORRECT);
        }
        if (oldPassword.equals(newPassword)) {
            throw new UamException(UserCredentialConstants.SAME_AS_CURRENT);
        }
        credentials.setPassword(new UserCredentialServiceUseCase.SetPasswordInput(userId, newPassword, false));
        log.info("Password changed by the account owner userId={}", userId);
    }
}
