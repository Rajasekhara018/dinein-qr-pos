package com.railcab.nms.adapters.in.web.readiness;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.railcab.nms.domain.model.readiness.ReadinessCheck;
import com.railcab.nms.domain.model.readiness.ReadinessReport;

import java.util.List;
import java.util.Map;

/**
 * Wire shape of the readiness report (docs/APPLICATION_READINESS.md).
 *
 * @param service         {@code nms}
 * @param applicationCode the application asked about
 * @param tenantId        the organization asked about
 * @param evaluatedAt     ISO-8601 UTC instant
 * @param checks          the checks
 */
public record ReadinessResponse(String service, String applicationCode, String tenantId, String evaluatedAt,
                                List<Check> checks) {

    /**
     * One check on the wire; {@code fixPath} is left out when there is none.
     *
     * @param id         check id
     * @param status     PASS, FAIL, WARN, UNKNOWN or NOT_APPLICABLE
     * @param messageKey translation key owned by Rider_Web
     * @param params     string params of the message (empty object when none)
     * @param fixPath    Rider_Web route that fixes it
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Check(String id, String status, String messageKey, Map<String, String> params, String fixPath) {
    }

    /** Maps the report to its wire shape. */
    public static ReadinessResponse from(ReadinessReport report) {
        return new ReadinessResponse(report.service(), report.applicationCode(), report.tenantId(),
                report.evaluatedAt().toString(), report.checks().stream().map(ReadinessResponse::check).toList());
    }

    private static Check check(ReadinessCheck check) {
        return new Check(check.id(), check.status().name(), check.messageKey(), check.params(), check.fixPath());
    }
}
