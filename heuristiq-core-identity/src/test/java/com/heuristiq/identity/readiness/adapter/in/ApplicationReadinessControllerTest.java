package com.heuristiq.identity.readiness.adapter.in;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.heuristiq.identity.common.GlobalExceptionHandler;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.readiness.application.ApplicationReadinessUseCase;
import com.heuristiq.identity.readiness.application.ReadinessSummary;
import com.heuristiq.identity.readiness.domain.ApplicationReadiness;
import com.heuristiq.identity.readiness.domain.OverallReadinessStatus;
import com.heuristiq.identity.readiness.domain.ReadinessPhase;
import com.heuristiq.identity.readiness.domain.ReadinessSeverity;
import com.heuristiq.identity.readiness.domain.ReadinessStatus;
import com.heuristiq.identity.readiness.domain.ReadinessStep;

/** The HTTP shape of the readiness endpoints, the refresh flag and the 404 for an unlinked application. */
class ApplicationReadinessControllerTest {

    private static final ReadinessStep FAILING = new ReadinessStep("WALLET_APP_REGISTERED", ReadinessPhase.BUSINESS, 10, "wallet",
            ReadinessSeverity.BLOCK, ReadinessStatus.FAIL, "readiness.wallet.appRegistered.fail", Map.of("displayName", "Shop"),
            "/wallet/admin/apps?application=BEST_PURCHASE_APP", List.of("UAM_APPLICATION_IN_ORGANIZATION"), false);
    private static final ApplicationReadiness READINESS = new ApplicationReadiness("BEST_PURCHASE_APP", "Best Purchase",
            Instant.parse("2026-10-25T10:00:00Z"), OverallReadinessStatus.BLOCKED, 9, 7, 2, "WALLET_APP_REGISTERED", List.of(FAILING));

    private ApplicationReadinessUseCase useCase;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        useCase = mock(ApplicationReadinessUseCase.class);
        mvc = MockMvcBuilders.standaloneSetup(new ApplicationReadinessController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void oneApplicationsGuide() throws Exception {
        when(useCase.readiness("BEST_PURCHASE_APP", false)).thenReturn(READINESS);

        mvc.perform(get("/api/v1/uam/applications/BEST_PURCHASE_APP/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.responseObject.applicationCode").value("BEST_PURCHASE_APP"))
                .andExpect(jsonPath("$.responseObject.applicationName").value("Best Purchase"))
                .andExpect(jsonPath("$.responseObject.evaluatedAt").value("2026-10-25T10:00:00Z"))
                .andExpect(jsonPath("$.responseObject.status").value("BLOCKED"))
                .andExpect(jsonPath("$.responseObject.requiredTotal").value(9))
                .andExpect(jsonPath("$.responseObject.requiredPassed").value(7))
                .andExpect(jsonPath("$.responseObject.warningCount").value(2))
                .andExpect(jsonPath("$.responseObject.nextStepId").value("WALLET_APP_REGISTERED"))
                .andExpect(jsonPath("$.responseObject.steps[0].id").value("WALLET_APP_REGISTERED"))
                .andExpect(jsonPath("$.responseObject.steps[0].phase").value("BUSINESS"))
                .andExpect(jsonPath("$.responseObject.steps[0].order").value(10))
                .andExpect(jsonPath("$.responseObject.steps[0].service").value("wallet"))
                .andExpect(jsonPath("$.responseObject.steps[0].severity").value("BLOCK"))
                .andExpect(jsonPath("$.responseObject.steps[0].status").value("FAIL"))
                .andExpect(jsonPath("$.responseObject.steps[0].messageKey").value("readiness.wallet.appRegistered.fail"))
                .andExpect(jsonPath("$.responseObject.steps[0].params.displayName").value("Shop"))
                .andExpect(jsonPath("$.responseObject.steps[0].fixPath").value("/wallet/admin/apps?application=BEST_PURCHASE_APP"))
                .andExpect(jsonPath("$.responseObject.steps[0].needs[0]").value("UAM_APPLICATION_IN_ORGANIZATION"))
                .andExpect(jsonPath("$.responseObject.steps[0].locked").value(false));
    }

    @Test
    void refreshIsPassedOn() throws Exception {
        when(useCase.readiness("BEST_PURCHASE_APP", true)).thenReturn(READINESS);

        mvc.perform(get("/api/v1/uam/applications/BEST_PURCHASE_APP/readiness").param("refresh", "true"))
                .andExpect(status().isOk());

        verify(useCase).readiness("BEST_PURCHASE_APP", true);
    }

    @Test
    void anUnlinkedApplicationIs404() throws Exception {
        when(useCase.readiness("FOOD_DELIVER_APP", false)).thenThrow(UamException.notFound("Application not found"));

        mvc.perform(get("/api/v1/uam/applications/FOOD_DELIVER_APP/readiness"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void theSummary() throws Exception {
        when(useCase.summaries(true)).thenReturn(List.of(ReadinessSummary.of(READINESS),
                new ReadinessSummary("CENTRALISED_WALLET_APP", "Centralised Wallet", OverallReadinessStatus.READY, 10, 10, 0, null)));

        mvc.perform(get("/api/v1/uam/applications/readiness").param("refresh", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject[0].applicationCode").value("BEST_PURCHASE_APP"))
                .andExpect(jsonPath("$.responseObject[0].status").value("BLOCKED"))
                .andExpect(jsonPath("$.responseObject[0].requiredTotal").value(9))
                .andExpect(jsonPath("$.responseObject[0].requiredPassed").value(7))
                .andExpect(jsonPath("$.responseObject[0].warningCount").value(2))
                .andExpect(jsonPath("$.responseObject[0].nextStep.id").value("WALLET_APP_REGISTERED"))
                .andExpect(jsonPath("$.responseObject[0].nextStep.messageKey").value("readiness.wallet.appRegistered.fail"))
                .andExpect(jsonPath("$.responseObject[0].nextStep.fixPath").value("/wallet/admin/apps?application=BEST_PURCHASE_APP"))
                .andExpect(jsonPath("$.responseObject[1].applicationName").value("Centralised Wallet"))
                .andExpect(jsonPath("$.responseObject[1].nextStep").value(org.hamcrest.Matchers.nullValue()));
    }
}
