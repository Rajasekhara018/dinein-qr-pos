package com.heuristiq.notification.application.usecase.readiness;

import com.heuristiq.notification.application.usecase.readiness.ReadinessCheckRunner.CheckTask;
import com.heuristiq.notification.domain.model.NotificationTemplate;
import com.heuristiq.notification.domain.model.readiness.ReadinessCheck;
import com.heuristiq.notification.domain.model.readiness.ReadinessReport;
import com.heuristiq.notification.domain.model.readiness.ReadinessStatus;
import com.heuristiq.notification.domain.repository.ITemplateRepository;
import com.heuristiq.notification.domain.repository.TenantScope;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reports the NMS facts of one business application for one organization (docs/APPLICATION_READINESS.md). Read-only, and a
 * missing configuration is a FAIL, never an exception.
 *
 * <p>{@code NMS_TEMPLATES_EXIST}: PASS when at least one ACTIVE notification template the organization would send with exists
 * for the application's events, else FAIL. Templates are keyed by event code and channel only (no application attribute), so
 * the application is recognised by its event-code prefixes ({@link NmsReadinessConstants#EVENT_PREFIXES_BY_APPLICATION}, e.g.
 * {@code MATRIMONY_}); an application with no prefix mapping is evaluated organization-wide (any active template counts,
 * {@code scope=ORGANIZATION}). Any code that is not a business application gets NOT_APPLICABLE.
 *
 * <p>Organization isolation: a template counts for an organization when that organization owns it or when it is a shared
 * template (no organization), exactly the templates the dispatcher would use for it ({@link TenantScope#limitedTo}); another
 * organization's own templates are never counted. The template table is configuration (events times channels, per
 * organization), read once per evaluation.
 */
public class NmsReadinessService {

    private final ITemplateRepository templates;
    private final Clock clock;
    private final Duration timeBudget;

    public NmsReadinessService(ITemplateRepository templates, Clock clock, Duration timeBudget) {
        this.templates = templates;
        this.clock = clock;
        this.timeBudget = timeBudget;
    }

    /**
     * Evaluates the NMS checks.
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
        String fixPath = NmsReadinessConstants.FIX_PATH_TEMPLATES + NmsReadinessConstants.APPLICATION_QUERY + application;
        List<ReadinessCheck> checks;
        if (NmsReadinessConstants.BUSINESS_APPLICATIONS.contains(application)) {
            List<String> prefixes = NmsReadinessConstants.EVENT_PREFIXES_BY_APPLICATION.getOrDefault(application, List.of());
            checks = ReadinessCheckRunner.runAll(List.of(new CheckTask(NmsReadinessConstants.CHECK_TEMPLATES_EXIST,
                    NmsReadinessConstants.KEY_TEMPLATES_EXIST, fixPath,
                    () -> templatesExist(organization, prefixes, fixPath))), timeBudget);
        } else {
            checks = List.of(ReadinessCheck.of(NmsReadinessConstants.CHECK_TEMPLATES_EXIST,
                    NmsReadinessConstants.KEY_TEMPLATES_EXIST, ReadinessStatus.NOT_APPLICABLE, Map.of(), null));
        }
        return new ReadinessReport(NmsReadinessConstants.SERVICE, application, organization, clock.instant(), checks);
    }

    private ReadinessCheck templatesExist(String organization, List<String> prefixes, String fixPath) {
        TenantScope scope = TenantScope.limitedTo(organization);
        List<NotificationTemplate> usable = templates.findByActiveTrue().stream()
                .filter(t -> t != null && t.isActive() && t.getEvent() != null)
                .filter(t -> scope.allows(t.getTenantId()))
                .filter(t -> prefixes.isEmpty() || prefixes.stream().anyMatch(p -> t.getEvent().startsWith(p)))
                .toList();
        long own = usable.stream().filter(t -> organization.equals(t.getTenantId())).count();
        long events = usable.stream().map(NotificationTemplate::getEvent).distinct().count();
        Map<String, String> params = new LinkedHashMap<>();
        params.put(NmsReadinessConstants.PARAM_TEMPLATE_COUNT, Integer.toString(usable.size()));
        params.put(NmsReadinessConstants.PARAM_ORGANIZATION_TEMPLATES, Long.toString(own));
        params.put(NmsReadinessConstants.PARAM_SHARED_TEMPLATES, Long.toString(usable.size() - own));
        params.put(NmsReadinessConstants.PARAM_EVENT_COUNT, Long.toString(events));
        if (prefixes.isEmpty()) {
            params.put(NmsReadinessConstants.PARAM_SCOPE, NmsReadinessConstants.SCOPE_ORGANIZATION);
        } else {
            params.put(NmsReadinessConstants.PARAM_SCOPE, NmsReadinessConstants.SCOPE_EVENT_PREFIX);
            params.put(NmsReadinessConstants.PARAM_EVENT_PREFIXES, String.join(NmsReadinessConstants.LIST_SEPARATOR, prefixes));
        }
        return ReadinessCheck.of(NmsReadinessConstants.CHECK_TEMPLATES_EXIST, NmsReadinessConstants.KEY_TEMPLATES_EXIST,
                usable.isEmpty() ? ReadinessStatus.FAIL : ReadinessStatus.PASS, params, fixPath);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
