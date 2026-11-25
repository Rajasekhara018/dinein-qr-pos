package com.heuristiq.identity.readiness.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.heuristiq.identity.application.Application;
import com.heuristiq.identity.application.SessionOrganizationResolver;
import com.heuristiq.identity.application.repository.ApplicationRepository;
import com.heuristiq.identity.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.identity.readiness.domain.UamReadinessFactsPort.ApplicationFacts;

/** UAM facts per organization: the organization link and the application row. */
class UamReadinessFactsAdapterTest {

    private static final String APP = "BEST_PURCHASE_APP";

    private SessionOrganizationResolver session;
    private TenantApplicationServiceUseCase subscriptions;
    private ApplicationRepository applications;
    private UamReadinessFactsAdapter adapter;

    @BeforeEach
    void setUp() {
        session = mock(SessionOrganizationResolver.class);
        subscriptions = mock(TenantApplicationServiceUseCase.class);
        applications = mock(ApplicationRepository.class);
        adapter = new UamReadinessFactsAdapter(session, subscriptions, applications);
    }

    @Test
    void sessionOrganizationAndLinksComeFromTheirOwners() {
        when(session.sessionOrganizationId()).thenReturn("org-a");
        when(subscriptions.linkedApplicationCodes("org-a")).thenReturn(List.of("best_purchase_app", " "));
        when(subscriptions.activeApplicationCodes("org-a")).thenReturn(List.of("BEST_PURCHASE_APP"));
        when(subscriptions.activeApplicationCodes("org-b")).thenReturn(null);

        assertThat(adapter.sessionOrganizationId()).isEqualTo("org-a");
        assertThat(adapter.linkedApplicationCodes("org-a")).containsExactly("BEST_PURCHASE_APP");
        assertThat(adapter.activeInOrganization("org-a", "best_purchase_app")).isTrue();
        assertThat(adapter.activeInOrganization("org-b", APP)).isFalse();
    }

    @Test
    void applicationRow() {
        when(applications.findByCodeIgnoreCase(APP)).thenReturn(Optional.of(Application.builder().code(APP).name("Shop")
                .active(false).ownerTeam("Free text owner").build()));
        when(applications.findByCodeIgnoreCase("FOOD_DELIVER_APP")).thenReturn(Optional.of(Application.builder()
                .code("FOOD_DELIVER_APP").name("Food").active(null).ownerTeam("Ops").build()));

        assertThat(adapter.application(APP)).contains(new ApplicationFacts("Shop", false));
        assertThat(adapter.application("FOOD_DELIVER_APP")).contains(new ApplicationFacts("Food", true));
        assertThat(adapter.application("MISSING")).isEmpty();
    }
}
