package com.heuristiq.uam.readiness.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.heuristiq.uam.application.ApplicationConstants;
import com.heuristiq.uam.application.catalogue.ApplicationCatalogue;
import com.heuristiq.uam.application.catalogue.ApplicationCatalogueEntry;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.readiness.domain.ApplicationReadiness;
import com.heuristiq.uam.readiness.domain.ReadinessEvaluator;
import com.heuristiq.uam.readiness.domain.ReadinessProfiles;
import com.heuristiq.uam.readiness.application.port.ReadinessSeverityOverrideRepository;
import com.heuristiq.uam.readiness.domain.ReadinessSeverity;
import com.heuristiq.uam.readiness.domain.ReadinessSource;
import com.heuristiq.uam.readiness.domain.ReadinessStepDefinition;
import com.heuristiq.uam.readiness.domain.ServiceReport;
import com.heuristiq.uam.readiness.domain.UamReadinessFactsPort;
import com.heuristiq.uam.readiness.domain.UamReadinessFactsPort.ApplicationFacts;

/**
 * Readiness of the session organization's applications. The organization is always the session's (the token's
 * {@code tenantId}), never a request value. An application the organization is not linked to is "not found", exactly like
 * the application GET. Results are cached briefly per (organization, application); {@code refresh} re-checks and replaces
 * the cached result. The summary asks every service for every application in one parallel batch under one time budget.
 */
public class ApplicationReadinessService implements ApplicationReadinessUseCase {

    private final UamReadinessFactsPort facts;
    private final UamReadinessChecker uamChecker;
    private final ServiceReportCollector collector;
    private final ReadinessEvaluator evaluator;
    private final ReadinessCache cache;
    private final Clock clock;
    private final ReadinessSeverityOverrideRepository overrides;

    /** Without stored overrides: every organization uses the code defaults. */
    public ApplicationReadinessService(UamReadinessFactsPort facts, UamReadinessChecker uamChecker,
                                       ServiceReportCollector collector, ReadinessEvaluator evaluator,
                                       ReadinessCache cache, Clock clock) {
        this(facts, uamChecker, collector, evaluator, cache, clock, new NoSeverityOverrides());
    }

    public ApplicationReadinessService(UamReadinessFactsPort facts, UamReadinessChecker uamChecker,
                                       ServiceReportCollector collector, ReadinessEvaluator evaluator,
                                       ReadinessCache cache, Clock clock, ReadinessSeverityOverrideRepository overrides) {
        this.facts = facts;
        this.uamChecker = uamChecker;
        this.collector = collector;
        this.evaluator = evaluator;
        this.cache = cache;
        this.clock = clock;
        this.overrides = overrides;
    }

    @Override
    public void invalidate(String tenantId, String applicationCode) {
        cache.evict(tenantId, applicationCode);
    }

    @Override
    public ApplicationReadiness readiness(String applicationCode, boolean refresh) {
        String tenantId = facts.sessionOrganizationId();
        String code = applicationCode == null ? "" : applicationCode.trim().toUpperCase(Locale.ROOT);
        if (ReadinessProfiles.forApplication(code).isEmpty() || !facts.linkedApplicationCodes(tenantId).contains(code)) {
            throw UamException.notFound(ApplicationConstants.NOT_FOUND);
        }
        if (!refresh) {
            Optional<ApplicationReadiness> cached = cache.get(tenantId, code);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        return evaluateAll(tenantId, List.of(code)).get(0);
    }

    @Override
    public List<ReadinessSummary> summaries(boolean refresh) {
        String tenantId = facts.sessionOrganizationId();
        Set<String> linked = new HashSet<>(facts.linkedApplicationCodes(tenantId));
        List<String> codes = ReadinessProfiles.applicationCodes().stream().filter(linked::contains).toList();
        Map<String, ApplicationReadiness> byCode = new LinkedHashMap<>();
        List<String> toEvaluate = new ArrayList<>();
        for (String code : codes) {
            Optional<ApplicationReadiness> cached = refresh ? Optional.empty() : cache.get(tenantId, code);
            byCode.put(code, cached.orElse(null));
            if (cached.isEmpty()) {
                toEvaluate.add(code);
            }
        }
        evaluateAll(tenantId, toEvaluate).forEach(readiness -> byCode.put(readiness.applicationCode(), readiness));
        return byCode.values().stream().map(ReadinessSummary::of).toList();
    }

    @Override
    public ApplicationReadiness evaluateFor(String tenantId, String applicationCode) {
        String code = applicationCode == null ? "" : applicationCode.trim().toUpperCase(Locale.ROOT);
        if (tenantId == null || tenantId.isBlank() || ReadinessProfiles.forApplication(code).isEmpty()) {
            throw UamException.notFound(ApplicationConstants.NOT_FOUND);
        }
        return evaluateAll(tenantId, List.of(code)).get(0);
    }

    /** Evaluates the applications (codes already checked against the organization) and caches each result. */
    private List<ApplicationReadiness> evaluateAll(String tenantId, List<String> codes) {
        if (codes.isEmpty()) {
            return List.of();
        }
        Map<String, List<ReadinessStepDefinition>> profiles = new LinkedHashMap<>();
        Map<String, Set<ReadinessSource>> requests = new LinkedHashMap<>();
        for (String code : codes) {
            List<ReadinessStepDefinition> profile = ReadinessProfiles.forApplication(code).orElseThrow();
            profiles.put(code, profile);
            requests.put(code, ReadinessProfiles.remoteSources(profile));
        }
        Map<String, Map<ReadinessSource, ServiceReport>> reports = collector.collect(tenantId, requests);
        Map<String, Map<String, ReadinessSeverity>> storedOverrides = overrides.findAll(tenantId);
        List<ApplicationReadiness> results = new ArrayList<>();
        profiles.forEach((code, profile) -> {
            ApplicationReadiness readiness = evaluator.evaluate(code, applicationName(code), clock.instant(), profile,
                    uamChecker.check(tenantId, code), reports.getOrDefault(code, Map.of()),
                    storedOverrides.getOrDefault(code, Map.of()));
            cache.put(tenantId, code, readiness);
            results.add(readiness);
        });
        return results;
    }

    private String applicationName(String code) {
        String fallback = ApplicationCatalogue.find(code).map(ApplicationCatalogueEntry::name).orElse(code);
        try {
            return facts.application(code).map(ApplicationFacts::name).filter(name -> !name.isBlank()).orElse(fallback);
        } catch (RuntimeException e) {
            // The name is cosmetic; the UAM checks report the data problem themselves.
            return fallback;
        }
    }

    /** The overrides used when none are stored anywhere (tests, wiring without persistence). */
    private static final class NoSeverityOverrides implements ReadinessSeverityOverrideRepository {
        @Override
        public Map<String, ReadinessSeverity> find(String tenantId, String applicationCode) {
            return Map.of();
        }

        @Override
        public Map<String, Map<String, ReadinessSeverity>> findAll(String tenantId) {
            return Map.of();
        }

        @Override
        public void replace(String tenantId, String applicationCode, Map<String, ReadinessSeverity> overrides,
                            String userId, java.time.Instant at) {
            throw new UnsupportedOperationException("No severity override storage configured");
        }
    }
}
