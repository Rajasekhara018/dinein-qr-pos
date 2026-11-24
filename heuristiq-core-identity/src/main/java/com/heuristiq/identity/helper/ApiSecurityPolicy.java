package com.heuristiq.uam.helper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiSecurityPolicy {

    private Boolean apiKeyAllowed;

    private Boolean jwtAllowed;

    private Boolean oauthAllowed;

    private Integer rateLimitPerMinute;

    private Boolean ipWhitelistEnabled;
}
