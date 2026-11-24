package com.heuristiq.uam.readiness.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.heuristiq.uam.common.CommonRequestMapper;
import com.heuristiq.uam.common.CommonRequestObject;
import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.GlobalExceptionHandler;
import com.heuristiq.uam.readiness.adapter.in.GovernanceRequests.GoLiveOverrideRequest;
import com.heuristiq.uam.readiness.adapter.in.GovernanceRequests.OwnerTeamRequest;
import com.heuristiq.uam.readiness.application.ReadinessGovernanceService;
import com.heuristiq.uam.readiness.application.ReadinessGovernanceService.Alerts;
import com.heuristiq.uam.readiness.application.ReadinessGovernanceService.OverrideInfo;
import com.heuristiq.uam.readiness.application.ReadinessGovernanceService.State;
import com.heuristiq.uam.readiness.application.port.OwnerCandidate;
import com.heuristiq.uam.readiness.domain.GoLiveBlocker;
import com.heuristiq.uam.readiness.domain.GoLiveRefusedException;
import com.heuristiq.uam.readiness.domain.GovernanceConstants;
import com.heuristiq.uam.readiness.domain.ReadinessStatus;

/** The governance endpoints are thin; the 409 of the guard has the documented shape. */
class GovernanceControllerTest {

    private static final String APP = "CENTRALISED_WALLET_APP";

    private ReadinessGovernanceService governance;
    private CommonRequestMapper mapper;
    private GovernanceController controller;
    private final CommonRequestObject envelope = new CommonRequestObject();

    @BeforeEach
    void setUp() {
        governance = mock(ReadinessGovernanceService.class);
        mapper = mock(CommonRequestMapper.class);
        controller = new GovernanceController(governance, mapper);
        LocalDateTime at = LocalDateTime.of(2026, 10, 25, 10, 0);
        when(governance.state(APP)).thenReturn(new State(APP, List.of("head-1"),
                new OverrideInfo("sa-1", at, "Board approved", List.of("A", "B")), new Alerts(at, null), true));
    }

    @Test
    void overrideDelegatesWithTheReasonAndReturnsTheState() {
        when(mapper.requireRequestObject(envelope, GoLiveOverrideRequest.class)).thenReturn(new GoLiveOverrideRequest("Board approved"));

        ResponseEntity<CommonResponseObject> response = controller.override(APP, envelope);

        verify(governance).override(APP, "Board approved");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        GovernanceResponse body = (GovernanceResponse) response.getBody().getResponseObject();
        assertThat(body.applicationCode()).isEqualTo(APP);
        assertThat(body.ownerTeam()).containsExactly("head-1");
        assertThat(body.override().by()).isEqualTo("sa-1");
        assertThat(body.override().blockingSteps()).containsExactly("A", "B");
        assertThat(body.alerts().lastBlockedAt()).isNotNull();
        assertThat(body.alerts().lastRefusedAt()).isNull();
        assertThat(body.guardEnabled()).isTrue();
    }

    @Test
    void ownerTeamUpdateDelegatesTheIds() {
        when(mapper.requireRequestObject(envelope, OwnerTeamRequest.class)).thenReturn(new OwnerTeamRequest(List.of("head-1")));

        ResponseEntity<CommonResponseObject> response = controller.updateOwnerTeam(APP, envelope);

        verify(governance).updateOwnerTeam(APP, List.of("head-1"));
        assertThat(response.getBody().getSuccess()).isTrue();
    }

    @Test
    void candidatesAndStateAreMappedWithoutOverrideWhenThereIsNone() {
        when(governance.ownerCandidates()).thenReturn(List.of(new OwnerCandidate("head-1", "Head One", "FIN", "Finance")));
        when(governance.state("OTHER")).thenReturn(new State("OTHER", List.of(), null, new Alerts(null, null), false));

        @SuppressWarnings("unchecked")
        List<GovernanceResponse.Candidate> candidates = (List<GovernanceResponse.Candidate>) controller.ownerCandidates().getBody().getResponseObject();
        GovernanceResponse state = (GovernanceResponse) controller.state("OTHER").getBody().getResponseObject();

        assertThat(candidates).containsExactly(new GovernanceResponse.Candidate("head-1", "Head One", "FIN", "Finance"));
        assertThat(state.override()).isNull();
        assertThat(state.guardEnabled()).isFalse();
    }

    @Test
    void theRefusalIsA409WithTheBlockingStepsAndAMessageKey() {
        GoLiveRefusedException refusal = new GoLiveRefusedException(APP, List.of(
                new GoLiveBlocker("WALLET_APP_REGISTERED", ReadinessStatus.FAIL, "readiness.wallet.appRegistered.fail", Map.of("a", "b"), "/fix"),
                new GoLiveBlocker("REPORTS_CONNECTION_TESTED", ReadinessStatus.UNKNOWN, "readiness.reports.connectionTested.unknown", Map.of(), null)));

        ResponseEntity<CommonResponseObject> response = new GlobalExceptionHandler().handleGoLiveRefused(refusal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        CommonResponseObject body = response.getBody();
        assertThat(body.getSuccess()).isFalse();
        assertThat(body.getErrorCode()).isEqualTo(GovernanceConstants.ERROR_GO_LIVE_BLOCKED);
        assertThat(body.getMessage()).isEqualTo(GovernanceConstants.GO_LIVE_REFUSED_MESSAGE);
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) body.getResponseObject();
        assertThat(details).containsEntry("applicationCode", APP).containsEntry("messageKey", GovernanceConstants.KEY_GO_LIVE_REFUSED);
        assertThat(details.get("blockers")).isEqualTo(refusal.getBlockers());
    }
}
