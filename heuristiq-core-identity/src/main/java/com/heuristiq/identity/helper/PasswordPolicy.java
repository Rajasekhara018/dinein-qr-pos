package com.heuristiq.uam.helper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordPolicy {

    private Integer minimumLength;

    private Integer maximumLength;

    private Boolean requireUppercase;

    private Boolean requireLowercase;

    private Boolean requireNumber;

    private Boolean requireSpecialCharacter;

    private Integer passwordHistoryCount;

    private Integer expiryDays;

    private Integer minimumAgeDays;

    private Boolean preventUsernameInPassword;
}
