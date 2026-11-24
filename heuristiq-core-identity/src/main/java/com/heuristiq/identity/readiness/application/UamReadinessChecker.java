package com.heuristiq.uam.readiness.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heuristiq.uam.readiness.domain.ReadinessCheck;
import com.heuristiq.uam.readiness.domain.ReadinessCheckIds;
import com.heuristiq.uam.readiness.domain.ReadinessConstants;
import com.heuristiq.uam.readiness.domain.ReadinessMessageKeys;
import com.heuristiq.uam.readiness.domain.ReadinessSource;
import com.heuristiq.uam.readiness.domain.ReadinessStatus;
import com.heuristiq.uam.readiness.domain.UamReadinessFactsPort;
import com.heuristiq.uam.readiness.domain.UamReadinessFactsPort.ApplicationFacts;

/**
 * Rider_UAM's own readiness checks for one application in one organization, computed from UAM data. A check whose data
 * could not be read is UNKNOWN ({@code reason=ERROR}) on its own; the others are still evaluated.
 */
public class UamReadinessChecker {

    private static final Logger log = LoggerFactory.getLogger(UamReadinessChecker.class);

    private final UamReadinessFactsPort facts;

    public UamReadinessChecker(UamReadinessFactsPort facts) {
        this.facts = facts;
    }

    /** The two UAM checks, in profile order. */
    public List<ReadinessCheck> check(String tenantId, String applicationCode) {
        return List.of(
                evaluate(ReadinessCheckIds.UAM_APPLICATION_ACTIVE, applicationCode, ReadinessConstants.FIX_PATH_APPLICATIONS,
                        () -> applicationActive(applicationCode)),
                evaluate(ReadinessCheckIds.UAM_APPLICATION_IN_ORGANIZATION, applicationCode, ReadinessConstants.FIX_PATH_APPLICATIONS,
                        () -> result(facts.activeInOrganization(tenantId, applicationCode), Map.of())));
    }

    private Result applicationActive(String applicationCode) {
        Optional<ApplicationFacts> application = facts.application(applicationCode);
        if (application.isEmpty()) {
            return new Result(ReadinessStatus.FAIL, Map.of(ReadinessConstants.PARAM_REASON, ReadinessConstants.REASON_NOT_REGISTERED));
        }
        return result(application.get().active(), Map.of());
    }

    private static Result result(boolean passed, Map<String, String> params) {
        return new Result(passed ? ReadinessStatus.PASS : ReadinessStatus.FAIL, params);
    }

    private ReadinessCheck evaluate(String id, String applicationCode, String fixRoute, Supplier<Result> evaluation) {
        Result result;
        try {
            result = evaluation.get();
        } catch (RuntimeException e) {
            // Degrade this one check only: the guide must still show the others.
            log.warn("Readiness check {} for {} could not be evaluated: {}", id, applicationCode, e.toString());
            result = new Result(ReadinessStatus.UNKNOWN, Map.of(ReadinessConstants.PARAM_REASON, ReadinessConstants.REASON_ERROR));
        }
        String fixPath = result.status() == ReadinessStatus.PASS ? null
                : fixRoute + ReadinessConstants.FIX_PATH_APPLICATION_PARAM + applicationCode;
        return new ReadinessCheck(id, result.status(), ReadinessMessageKeys.of(ReadinessSource.UAM, id, result.status()),
                result.params(), fixPath);
    }

    private record Result(ReadinessStatus status, Map<String, String> params) {
    }
}
