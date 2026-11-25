package com.heuristiq.identity.helper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginPolicy {

    private Integer maxFailedAttempts;

    private Integer lockDurationMinutes;

    private Integer captchaAfterAttempts;

    private Boolean allowConcurrentSessions;

    private Integer maxConcurrentSessions;

    private Boolean rememberMeAllowed;
}
