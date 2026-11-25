package com.heuristiq.identity.readiness.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.CommonRequestObject;
import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.GlobalExceptionHandler;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.readiness.adapter.in.GovernanceRequests.SeverityRequest;
import com.heuristiq.identity.readiness.application.ReadinessSeverityUseCase;
import com.heuristiq.identity.readiness.domain.ApplicationReadiness;
import com.heuristiq.identity.readiness.domain.OverallReadinessStatus;
import com.heuristiq.identity.readiness.domain.ReadinessPhase;
import com.heuristiq.identity.readiness.domain.ReadinessSeverity;
import com.heuristiq.identity.readiness.domain.ReadinessSeverityException;
import com.heuristiq.identity.readiness.domain.ReadinessStatus;
import com.heuristiq.identity.readiness.domain.ReadinessStep;

/** The HTTP shape of the severity endpoints: the readiness body with the new step fields, and the typed errors. */
class ReadinessSeverityControllerTest {

    private static final ReadinessStep CUSTOMIZED = new ReadinessStep("WALLET_APP_ENABLED", ReadinessPhase.BUSINESS, 20, "wallet",
            ReadinessSeverity.WARN, ReadinessStatus.FAIL, "readiness.wallet.appEnabled.fail", Map.of(), "/fix",
            List.of("WALLET_APP_REGISTERED"), false, ReadinessSeverity.BLOCK, true, false);
    private static final ReadinessStep LOCKED = new ReadinessStep("GO_LIVE_REVIEW", ReadinessPhase.GO_LIVE, 10, "uam",
            ReadinessSeverity.BLOCK, ReadinessStatus.PASS, "readiness.uam.goLiveReview.pass", Map.of(), null, List.of(), false,
            ReadinessSeverity.BLOCK, false, true);
    private static final ApplicationReadiness READINESS = new ApplicationReadiness("BEST_PURCHASE_APP", "Best Purchase",
            Instant.parse("2026-10-25T10:00:00Z"), OverallReadinessStatus.READY, 5, 5, 1, "WALLET_APP_ENABLED",
            List.of(CUSTOMIZED, LOCKED));

    private ReadinessSeverityUseCase useCase;
    private CommonRequestMapper mapper;
    private ReadinessSeverityController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        useCase = mock(ReadinessSeverityUseCase.class);
        mapper = mock(CommonRequestMapper.class);
        controller = new ReadinessSeverityController(useCase, mapper);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void putDelegatesAndReturnsTheFreshReadinessWithTheNewStepFields() {
        CommonRequestObject envelope = new CommonRequestObject();
        when(mapper.requireRequestObject(envelope, SeverityRequest.class)).thenReturn(new SeverityRequest("WALLET_APP_ENABLED", "WARN"));
        when(useCase.change("BEST_PURCHASE_APP", "WALLET_APP_ENABLED", "WARN")).thenReturn(READINESS);

        ResponseEntity<CommonResponseObject> response = controller.change("BEST_PURCHASE_APP", envelope);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ApplicationReadinessResponse body = (ApplicationReadinessResponse) response.getBody().getResponseObject();
        assertThat(body.applicationCode()).isEqualTo("BEST_PURCHASE_APP");
        assertThat(body.steps().get(0).severity()).isEqualTo("WARN");
        assertThat(body.steps().get(0).defaultSeverity()).isEqualTo("BLOCK");
        assertThat(body.steps().get(0).customized()).isTrue();
        assertThat(body.steps().get(0).severityLocked()).isFalse();
        assertThat(body.steps().get(1).severityLocked()).isTrue();
        assertThat(body.steps().get(1).customized()).isFalse();
    }

    @Test
    void deleteOneStepAndDeleteAllReturnTheFreshReadiness() throws Exception {
        when(useCase.reset("BEST_PURCHASE_APP", "WALLET_APP_ENABLED")).thenReturn(READINESS);
        when(useCase.resetAll("BEST_PURCHASE_APP")).thenReturn(READINESS);

        mvc.perform(delete("/api/v1/uam/applications/BEST_PURCHASE_APP/readiness-severity/WALLET_APP_ENABLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.responseObject.steps[0].id").value("WALLET_APP_ENABLED"))
                .andExpect(jsonPath("$.responseObject.steps[0].defaultSeverity").value("BLOCK"))
                .andExpect(jsonPath("$.responseObject.steps[0].customized").value(true))
                .andExpect(jsonPath("$.responseObject.steps[1].severityLocked").value(true));
        mvc.perform(delete("/api/v1/uam/applications/BEST_PURCHASE_APP/readiness-severity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.applicationCode").value("BEST_PURCHASE_APP"));

        verify(useCase).reset("BEST_PURCHASE_APP", "WALLET_APP_ENABLED");
        verify(useCase).resetAll("BEST_PURCHASE_APP");
    }

    @Test
    void aLockedStepIs409WithItsErrorCodeAndMessageKey() throws Exception {
        when(useCase.reset("BEST_PURCHASE_APP", "GO_LIVE_REVIEW")).thenThrow(ReadinessSeverityException.locked("GO_LIVE_REVIEW"));

        mvc.perform(delete("/api/v1/uam/applications/BEST_PURCHASE_APP/readiness-severity/GO_LIVE_REVIEW"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("READINESS_SEVERITY_LOCKED"))
                .andExpect(jsonPath("$.responseObject.messageKey").value("readiness.severity.locked"))
                .andExpect(jsonPath("$.responseObject.stepId").value("GO_LIVE_REVIEW"));
    }

    @Test
    void anUnknownStepIs404AnInvalidSeverityIs400AndAnUnlinkedApplicationIs404() throws Exception {
        when(useCase.reset("BEST_PURCHASE_APP", "NOPE")).thenThrow(ReadinessSeverityException.unknownStep("NOPE"));
        when(useCase.resetAll("FOOD_DELIVER_APP")).thenThrow(UamException.notFound("Application not found"));

        mvc.perform(delete("/api/v1/uam/applications/BEST_PURCHASE_APP/readiness-severity/NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("READINESS_STEP_NOT_FOUND"));
        mvc.perform(delete("/api/v1/uam/applications/FOOD_DELIVER_APP/readiness-severity"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        CommonRequestObject envelope = new CommonRequestObject();
        when(mapper.requireRequestObject(envelope, SeverityRequest.class)).thenReturn(new SeverityRequest("WALLET_APP_ENABLED", "MAYBE"));
        when(useCase.change("BEST_PURCHASE_APP", "WALLET_APP_ENABLED", "MAYBE")).thenThrow(ReadinessSeverityException.invalid());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.change("BEST_PURCHASE_APP", envelope))
                .isInstanceOfSatisfying(ReadinessSeverityException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getErrorCode()).isEqualTo("READINESS_SEVERITY_INVALID");
                    assertThat(e.getMessageKey()).isEqualTo("readiness.severity.invalid");
                    assertThat(e.getStepId()).isNull();
                });
    }
}
