package com.heuristiq.identity.helper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionPolicy {

    private Integer idleTimeoutMinutes;

    private Integer absoluteTimeoutMinutes;

    private Boolean singleSessionOnly;

    private Boolean invalidateOnPasswordChange;

    private Boolean invalidateOnRoleChange;
}