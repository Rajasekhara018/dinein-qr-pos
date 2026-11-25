package com.heuristiq.identity.readiness.adapter.in;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.CommonRequestObject;
import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.RequestObjectType;
import com.heuristiq.identity.readiness.adapter.in.GovernanceRequests.GoLiveOverrideRequest;
import com.heuristiq.identity.readiness.adapter.in.GovernanceRequests.OwnerTeamRequest;
import com.heuristiq.identity.readiness.application.ReadinessGovernanceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Go-live override, owner team and governance state of the session organization's applications. Thin: parse, delegate, map.
 * The organization is always the session's. Privileges: APPLICATION_GO_LIVE_OVERRIDE_CREATE, APPLICATION_OWNER_TEAM_UPDATE,
 * APPLICATION_OWNER_TEAM_CANDIDATE_LIST, APPLICATION_READINESS_GOVERNANCE_READ.
 */
@RestController
@RequestMapping(ReadinessWebConstants.BASE_PATH)
@Tag(name = "Application go-live governance", description = "Go-live guard override, owner team and alert state per application.")
public class GovernanceController {

    private final ReadinessGovernanceService governance;
    private final CommonRequestMapper requestMapper;

    public GovernanceController(ReadinessGovernanceService governance, CommonRequestMapper requestMapper) {
        this.governance = governance;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Override the go-live guard (SUPER_ADMIN only)",
            description = "Switches the application on for the session organization although required steps fail, recording the "
                    + "reason on the link, auditing it and alerting super admins and the owner team. Refused with 403 for anyone "
                    + "but a SUPER_ADMIN, 400 for a reason outside the length limits or when nothing blocks.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application switched on; governance state returned"),
            @ApiResponse(responseCode = "400", description = "Reason too short or too long, or nothing is blocking"),
            @ApiResponse(responseCode = "403", description = "Caller is not a SUPER_ADMIN"),
            @ApiResponse(responseCode = "404", description = "The organization does not use this application")
    })
    @RequestObjectType(GoLiveOverrideRequest.class)
    @PostMapping(ReadinessWebConstants.GO_LIVE_OVERRIDE_PATH)
    public ResponseEntity<CommonResponseObject> override(
            @Parameter(description = "Catalogue application code") @PathVariable String code,
            @Valid @RequestBody CommonRequestObject commonRequest) {
        GoLiveOverrideRequest request = requestMapper.requireRequestObject(commonRequest, GoLiveOverrideRequest.class);
        governance.override(code, request.reason());
        return ResponseEntity.ok(CommonResponseObject.success(GovernanceResponse.from(governance.state(code))));
    }

    @Operation(summary = "Set the owner team of an application",
            description = "Replaces the owner team (department heads picked from owner-team-candidates) for the session organization. "
                    + "An empty list clears it. Ids that are not department heads of the organization are refused (400).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Owner team saved; governance state returned"),
            @ApiResponse(responseCode = "400", description = "An id is not a department head, or the team is too large"),
            @ApiResponse(responseCode = "404", description = "The organization does not use this application")
    })
    @RequestObjectType(OwnerTeamRequest.class)
    @PutMapping(ReadinessWebConstants.OWNER_TEAM_PATH)
    public ResponseEntity<CommonResponseObject> updateOwnerTeam(
            @Parameter(description = "Catalogue application code") @PathVariable String code,
            @Valid @RequestBody CommonRequestObject commonRequest) {
        OwnerTeamRequest request = requestMapper.requireRequestObject(commonRequest, OwnerTeamRequest.class);
        governance.updateOwnerTeam(code, request.userIds());
        return ResponseEntity.ok(CommonResponseObject.success(GovernanceResponse.from(governance.state(code))));
    }

    @Operation(summary = "Department heads who can be chosen as owners",
            description = "Active departments of the session organization whose head has an account (from the organization "
                    + "builder). Empty when the organization builder is not available.")
    @GetMapping(ReadinessWebConstants.OWNER_CANDIDATES_PATH)
    public ResponseEntity<CommonResponseObject> ownerCandidates() {
        List<GovernanceResponse.Candidate> candidates = governance.ownerCandidates().stream()
                .map(GovernanceResponse.Candidate::from).toList();
        return ResponseEntity.ok(CommonResponseObject.success(candidates));
    }

    @Operation(summary = "Owner team, last override and alert state of an application",
            description = "For the session organization only. Does not list who was alerted.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "State returned"),
            @ApiResponse(responseCode = "404", description = "The organization does not use this application")
    })
    @GetMapping(ReadinessWebConstants.GOVERNANCE_PATH)
    public ResponseEntity<CommonResponseObject> state(@Parameter(description = "Catalogue application code") @PathVariable String code) {
        return ResponseEntity.ok(CommonResponseObject.success(GovernanceResponse.from(governance.state(code))));
    }
}
