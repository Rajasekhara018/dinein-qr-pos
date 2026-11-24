package com.heuristiq.search.scope;

import com.heuristiq.search.document.SearchDocument;
import com.heuristiq.search.model.SearchModel;
import com.heuristiq.search.query.SearchQuery;
import com.heuristiq.search.service.SearchIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Which models are organization data is configuration: the built-in rules, overrides, and the fail-closed default. */
class TenantScopingPropertiesTest {

    private static TenantScopingProperties bind(Map<String, String> source) {
        return new Binder(new MapConfigurationPropertySource(source))
                .bind("railcab.search.tenant-scoping", TenantScopingProperties.class)
                .orElseGet(TenantScopingProperties::defaults);
    }

    @Test
    void theBuiltInRulesApplyWithNoConfiguration() {
        TenantScopingProperties props = bind(Map.of());

        assertThat(props.enabled()).isTrue();
        assertThat(props.scopingOf("USER")).isEqualTo(TenantScoping.OWN);
        assertThat(props.scopingOf("role")).isEqualTo(TenantScoping.OWN_OR_SHARED);
        assertThat(props.scopingOf("RIDE")).isEqualTo(TenantScoping.OWN);
        assertThat(props.scopingOf("DRIVER")).isEqualTo(TenantScoping.OWN);
        // everything the report service indexes belongs to one organization, so no organization sees another's
        for (String reportModel : java.util.List.of("REPORT", "REPORT_CONFIGURATION", "REPORT_SYSTEM_CONFIG", "REPORT_CUSTOM_FIELD",
                "REPORT_TEMPLATE", "REPORT_AUDIT_LOG", "REPORT_MERCHANT")) {
            assertThat(props.scopingOf(reportModel)).as(reportModel).isEqualTo(TenantScoping.OWN);
        }
        assertThat(props.scopingOf("APPLICATION")).isEqualTo(TenantScoping.NONE);
    }

    @Test
    void aNewModelIsOneLineOfConfigurationAndCanOverrideABuiltInRule() {
        TenantScopingProperties props = bind(Map.of(
                "railcab.search.tenant-scoping.models.vendor", "OWN",
                "railcab.search.tenant-scoping.models.matrimony-profile", "OWN_OR_SHARED",
                "railcab.search.tenant-scoping.models.SECURITY_POLICY", "NONE"));

        assertThat(props.scopingOf("VENDOR")).isEqualTo(TenantScoping.OWN);
        assertThat(props.scopingOf("MATRIMONY_PROFILE")).isEqualTo(TenantScoping.OWN_OR_SHARED);
        assertThat(props.scopingOf("SECURITY_POLICY")).isEqualTo(TenantScoping.NONE);
        assertThat(props.scopingOf("USER")).isEqualTo(TenantScoping.OWN);
    }

    @Test
    void theDefaultForAnUnlistedModelCanBeSwitchedToFailClosed() {
        TenantScopingProperties props = bind(Map.of("railcab.search.tenant-scoping.default-scoping", "OWN"));

        assertThat(props.scopingOf("RIDE")).isEqualTo(TenantScoping.OWN);
        assertThat(props.scopingOf("DOCUMENT")).isEqualTo(TenantScoping.OWN);
    }

    @Test
    void theSwitchTurnsScopingOffForEveryModel() {
        assertThat(bind(Map.of("railcab.search.tenant-scoping.enabled", "false")).enabled()).isFalse();
        assertThat(new TenantScopePolicy(TenantScopingProperties.disabled()).enabled()).isFalse();
    }

    @Test
    void aConfiguredModelIsFilteredAndAnUnknownIndexedModelDoesNotBreakTheRead() {
        TenantScopingProperties props = bind(Map.of("railcab.search.tenant-scoping.models.VENDOR", "OWN"));
        CurrentCaller caller = mock(CurrentCaller.class);
        TenantScopedSearch search = new TenantScopedSearch(mock(SearchIndexService.class), caller, new TenantScopePolicy(props));

        SearchQuery scoped = search.scoped(new SearchQuery(SearchModel.VENDOR, null), new CallerScope("org-a", false));
        assertThat(scoped.group().conditions()).singleElement().satisfies(c -> assertThat(c.value()).isEqualTo("org-a"));

        SearchDocument unknown = SearchDocument.builder().model("SOME_FUTURE_MODEL").modelKey("1").ownerKey("org-b").build();
        assertThat(search.visibleTo(unknown, new CallerScope("org-a", false))).isTrue();
        assertThatThrownBy(() -> search.scoped(new SearchQuery(SearchModel.VENDOR, null), new CallerScope(null, false)))
                .isInstanceOf(TenantRequiredException.class);
    }
}
