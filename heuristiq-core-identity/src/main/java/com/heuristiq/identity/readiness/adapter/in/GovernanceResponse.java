package com.heuristiq.uam.readiness.adapter.in;

import java.time.LocalDateTime;
import java.util.List;

import com.heuristiq.uam.readiness.application.ReadinessGovernanceService;
import com.heuristiq.uam.readiness.application.port.OwnerCandidate;

/**
 * Governance state as the API returns it.
 *
 * @param applicationCode catalogue code
 * @param ownerTeam       user ids of the owner team (department heads)
 * @param override        the last go-live override, or null when there was none
 * @param alerts          when the last alerts were sent
 * @param guardEnabled    false when the environment switched the go-live guard off
 */
public record GovernanceResponse(String applicationCode, List<String> ownerTeam, Override override, Alerts alerts, boolean guardEnabled) {

    /** The last override. */
    public record Override(String by, LocalDateTime at, String reason, List<String> blockingSteps) {
    }

    /** Times of the last alerts (null: never). */
    public record Alerts(LocalDateTime lastBlockedAt, LocalDateTime lastRefusedAt) {
    }

    /** A selectable department head. */
    public record Candidate(String userId, String name, String departmentCode, String departmentName) {
        static Candidate from(OwnerCandidate candidate) {
            return new Candidate(candidate.userId(), candidate.name(), candidate.departmentCode(), candidate.departmentName());
        }
    }

    static GovernanceResponse from(ReadinessGovernanceService.State state) {
        Override override = state.override() == null ? null : new Override(state.override().by(), state.override().at(),
                state.override().reason(), state.override().blockingSteps());
        return new GovernanceResponse(state.applicationCode(), state.ownerTeamUserIds(), override,
                new Alerts(state.alerts().lastBlockedAt(), state.alerts().lastRefusedAt()), state.guardEnabled());
    }
}
