package com.railcab.dms.readiness.application;

import com.railcab.dms.documenttype.application.DocumentTypeQueryService;
import com.railcab.dms.documenttype.domain.DocumentTypeRepository;
import com.railcab.dms.readiness.application.ReadinessCheckRunner.CheckTask;
import com.railcab.dms.readiness.domain.ReadinessCheck;
import com.railcab.dms.readiness.domain.ReadinessReport;
import com.railcab.dms.readiness.domain.ReadinessStatus;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reports the DMS facts of one business application for one organization (docs/APPLICATION_READINESS.md). Read-only, and a
 * missing configuration is a FAIL, never an exception.
 *
 * <p>{@code DMS_DOCUMENT_TYPES_CONFIGURED}: PASS when the organization has at least one ACTIVE document type, else FAIL.
 * Document types are <b>not linked to an application</b>: they carry only a free-text upload {@code category} and an optional
 * business {@code flow}, neither of which names a Rider_UAM application. So the check is evaluated organization-wide and gives
 * the same answer for every business application ({@code scope=ORGANIZATION} in params). Any code that is not a business
 * application gets NOT_APPLICABLE.
 *
 * <p>Organization isolation: the count goes through {@link DocumentTypeQueryService#list}, whose store port only ever returns
 * the given organization's records, using the {@code tenantId} parameter.
 */
public class DmsReadinessService {

    private final DocumentTypeQueryService documentTypes;
    private final Clock clock;
    private final Duration timeBudget;

    public DmsReadinessService(DocumentTypeQueryService documentTypes, Clock clock, Duration timeBudget) {
        this.documentTypes = documentTypes;
        this.clock = clock;
        this.timeBudget = timeBudget;
    }

    /**
     * Evaluates the DMS checks.
     *
     * @param applicationCode business application code
     * @param tenantId        organization id
     * @throws IllegalArgumentException when either value is blank
     */
    public ReadinessReport evaluate(String applicationCode, String tenantId) {
        requireText(applicationCode, "applicationCode");
        requireText(tenantId, "tenantId");
        String application = applicationCode.trim().toUpperCase(Locale.ROOT);
        String organization = tenantId.trim();
        String fixPath = DmsReadinessConstants.FIX_PATH_DOCUMENT_TYPES + DmsReadinessConstants.APPLICATION_QUERY + application;
        List<ReadinessCheck> checks = DmsReadinessConstants.BUSINESS_APPLICATIONS.contains(application)
                ? ReadinessCheckRunner.runAll(List.of(new CheckTask(DmsReadinessConstants.CHECK_DOCUMENT_TYPES_CONFIGURED,
                        DmsReadinessConstants.KEY_DOCUMENT_TYPES_CONFIGURED, fixPath,
                        () -> documentTypesConfigured(organization, fixPath))), timeBudget)
                : List.of(ReadinessCheck.of(DmsReadinessConstants.CHECK_DOCUMENT_TYPES_CONFIGURED,
                        DmsReadinessConstants.KEY_DOCUMENT_TYPES_CONFIGURED, ReadinessStatus.NOT_APPLICABLE, Map.of(), null));
        return new ReadinessReport(DmsReadinessConstants.SERVICE, application, organization, clock.instant(), checks);
    }

    private ReadinessCheck documentTypesConfigured(String organization, String fixPath) {
        DocumentTypeRepository.Page page = documentTypes.list(organization, null, null, Boolean.TRUE,
                DmsReadinessConstants.PROBE_PAGE, DmsReadinessConstants.PROBE_SIZE);
        long active = page.totalElements();
        Map<String, String> params = new LinkedHashMap<>();
        params.put(DmsReadinessConstants.PARAM_ACTIVE_TYPES, Long.toString(active));
        params.put(DmsReadinessConstants.PARAM_SCOPE, DmsReadinessConstants.SCOPE_ORGANIZATION);
        return ReadinessCheck.of(DmsReadinessConstants.CHECK_DOCUMENT_TYPES_CONFIGURED,
                DmsReadinessConstants.KEY_DOCUMENT_TYPES_CONFIGURED,
                active > 0 ? ReadinessStatus.PASS : ReadinessStatus.FAIL, params, fixPath);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
