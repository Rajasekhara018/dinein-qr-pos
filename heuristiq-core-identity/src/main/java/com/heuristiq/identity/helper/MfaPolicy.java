package com.heuristiq.identity.helper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaPolicy {

    private Boolean enabled;

    private Boolean mandatory;

    private Boolean emailOtpAllowed;

    private Boolean smsOtpAllowed;

    private Boolean authenticatorAppAllowed;

    private Boolean backupCodesAllowed;
}
