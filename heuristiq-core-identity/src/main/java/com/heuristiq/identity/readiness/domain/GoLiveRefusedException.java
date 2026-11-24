package com.heuristiq.uam.readiness.domain;

import java.util.List;

import org.springframework.http.HttpStatus;

import com.heuristiq.uam.common.UamException;

/**
 * An application cannot be switched on for the organization while blocking readiness steps fail or are unknown (409). Carries
 * the blocking steps so the response can list their ids and message keys.
 */
public class GoLiveRefusedException extends UamException {

    private final String applicationCode;
    private final transient List<GoLiveBlocker> blockers;

    public GoLiveRefusedException(String applicationCode, List<GoLiveBlocker> blockers) {
        super(GovernanceConstants.GO_LIVE_REFUSED_MESSAGE, HttpStatus.CONFLICT);
        this.applicationCode = applicationCode;
        this.blockers = List.copyOf(blockers);
    }

    public String getApplicationCode() {
        return applicationCode;
    }

    public List<GoLiveBlocker> getBlockers() {
        return blockers;
    }
}
