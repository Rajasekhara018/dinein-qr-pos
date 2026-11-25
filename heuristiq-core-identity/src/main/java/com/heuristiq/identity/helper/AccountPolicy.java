package com.heuristiq.identity.helper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountPolicy {

    private Boolean selfRegistrationAllowed;

    private Boolean emailVerificationRequired;

    private Boolean phoneVerificationRequired;

    private Boolean adminApprovalRequired;

    private Boolean autoUnlockEnabled;

    private Integer autoUnlockAfterMinutes;
}
