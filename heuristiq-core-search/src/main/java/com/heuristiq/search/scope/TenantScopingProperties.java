package com.heuristiq.search.scope;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How search results are limited to the caller's organization ({@code railcab.search.tenant-scoping.*}).
 *
 * <p>Which models hold per-organization data is configuration, not code: a new tenant-owned model is one line in
 * {@code models} ({@code railcab.search.tenant-scoping.models.VENDOR=OWN}), with no change to the code. The built-in
 * entries (Rider_UAM's users, roles, API clients and security policies) apply unless overridden.
 *
 * @param enabled        false switches the filter off for every model; for the dev profile only
 * @param defaultScoping what a model that is not listed gets; {@link TenantScoping#NONE} (not filtered) keeps the
 *                       system-wide models working, {@link TenantScoping#OWN} hides an unlisted model from every other
 *                       organization (fail closed)
 * @param models         model code (case-insensitive) to its scoping, merged over the built-in entries
 */
@ConfigurationProperties(prefix = "railcab.search.tenant-scoping")
public record TenantScopingProperties(Boolean enabled, TenantScoping defaultScoping, Map<String, TenantScoping> models) {

    /** The organization data that Rider_UAM indexes with {@code ownerKey} = organization id. */
    private static final Map<String, TenantScoping> BUILT_IN = Map.ofEntries(
            Map.entry("USER", TenantScoping.OWN),
            Map.entry("FRANCHISE_FRANCHISE", TenantScoping.OWN),
            Map.entry("FRANCHISE_PLAN", TenantScoping.OWN),
            Map.entry("FRANCHISE_AGENT", TenantScoping.OWN),
            Map.entry("FRANCHISE_ADMIN_MEMBER", TenantScoping.OWN),
            Map.entry("FRANCHISE_MEMBER", TenantScoping.OWN),
            Map.entry("FRANCHISE_ADMIN_DRIVER", TenantScoping.OWN),
            Map.entry("FRANCHISE_DRIVER", TenantScoping.OWN),
            Map.entry("FRANCHISE_FEE_SCHEDULE", TenantScoping.OWN),
            Map.entry("FRANCHISE_COMMISSION_PLAN", TenantScoping.OWN),
            Map.entry("FRANCHISE_REFERRAL_PLAN", TenantScoping.OWN),
            Map.entry("FRANCHISE_PAYOUT_RULE", TenantScoping.OWN),
            Map.entry("FRANCHISE_EVENT_TYPE", TenantScoping.OWN),
            Map.entry("FRANCHISE_LOCATION", TenantScoping.NONE),
            Map.entry("FRANCHISE_LOCATION_CHILD", TenantScoping.NONE),
            Map.entry("FRANCHISE_PLACE_OPTION", TenantScoping.NONE),
            Map.entry("FRANCHISE_PLACE_OPTION_CHILD", TenantScoping.NONE),
            Map.entry("FRANCHISE_PACKAGE_TEMPLATE", TenantScoping.OWN),
            // An organization record is owned by itself (UAM stamps ownerKey = its id): an organization's administrator sees
            // their own organization only; the platform's all-organizations view is not filtered.
            Map.entry("TENANT", TenantScoping.OWN),
            Map.entry("FRANCHISE_DRIVER_SUBSCRIPTION_PLAN", TenantScoping.OWN),
            Map.entry("FRANCHISE_DRIVER_SUBSCRIPTION", TenantScoping.OWN),
            Map.entry("FRANCHISE_DRIVER_SUBSCRIPTION_CHARGE", TenantScoping.OWN),
            Map.entry("FRANCHISE_LEDGER_ENTRY", TenantScoping.OWN),
            Map.entry("FRANCHISE_PAYOUT", TenantScoping.OWN),
            Map.entry("FRANCHISE_SALE_RECONCILIATION", TenantScoping.OWN),
            Map.entry("FRANCHISE_MINE", TenantScoping.OWN),
            Map.entry("API_CLIENT", TenantScoping.OWN),
            Map.entry("SECURITY_POLICY", TenantScoping.OWN),
            // Records a service indexes for one organization; fail closed so that, when a producer starts indexing them,
            // another organization cannot see them even before a configuration line is added. DOCUMENT is not
            // listed: existing DMS documents have no owner until the backfill and reindex, so it is switched on by configuration.
            Map.entry("RIDE", TenantScoping.OWN),
            Map.entry("DRIVER", TenantScoping.OWN),
            Map.entry("VEHICLE", TenantScoping.OWN),
            Map.entry("SUPPORT_TICKET", TenantScoping.OWN),
            Map.entry("REFUND_REQUEST", TenantScoping.OWN),
            Map.entry("VENDOR", TenantScoping.OWN),
            // Everything Rider_ReportService indexes belongs to one organization.
            Map.entry("REPORT", TenantScoping.OWN),
            Map.entry("REPORT_CONFIGURATION", TenantScoping.OWN),
            Map.entry("REPORT_SYSTEM_CONFIG", TenantScoping.OWN),
            Map.entry("REPORT_CUSTOM_FIELD", TenantScoping.OWN),
            Map.entry("REPORT_TEMPLATE", TenantScoping.OWN),
            Map.entry("REPORT_AUDIT_LOG", TenantScoping.OWN),
            Map.entry("REPORT_MERCHANT", TenantScoping.OWN),
            // Legacy unowned roles belong only to default; an organization's own role has an owner.
            Map.entry("ROLE", TenantScoping.OWN_OR_SHARED),
            // Unowned notification templates likewise belong only to default.
            Map.entry("NOTIFICATION_TEMPLATE", TenantScoping.OWN_OR_SHARED));

    public TenantScopingProperties {
        enabled = enabled == null || enabled;
        defaultScoping = defaultScoping == null ? TenantScoping.NONE : defaultScoping;
        Map<String, TenantScoping> merged = new HashMap<>(BUILT_IN);
        if (models != null) {
            models.forEach((model, scoping) -> merged.put(normalise(model), scoping));
        }
        models = Map.copyOf(merged);
    }

    /** The built-in rules, scoping on. */
    public static TenantScopingProperties defaults() {
        return new TenantScopingProperties(true, null, null);
    }

    /** Scoping off (development). */
    public static TenantScopingProperties disabled() {
        return new TenantScopingProperties(false, null, null);
    }

    /** The scoping of a model by its code; the default for one that is not listed. */
    public TenantScoping scopingOf(String model) {
        if (com.heuristiq.search.projection.WebSearchCatalog.contains(model)) {
            return "GLOBAL".equals(com.heuristiq.search.projection.WebSearchCatalog.require(model).path("scope").asText())
                    ? TenantScoping.NONE : TenantScoping.OWN;
        }
        return model == null ? defaultScoping : models.getOrDefault(normalise(model), defaultScoping);
    }

    private static String normalise(String model) {
        return model.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
