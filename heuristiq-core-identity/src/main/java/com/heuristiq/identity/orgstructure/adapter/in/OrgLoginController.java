package com.heuristiq.identity.orgstructure.adapter.in;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.CommonRequestObject;
import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.RequestObjectType;
import com.heuristiq.identity.orgstructure.adapter.in.OrgLoginResponses.LoginCandidateResponse;
import com.heuristiq.identity.orgstructure.adapter.in.OrgStructureRequests.CreateLoginsRequest;
import com.heuristiq.identity.orgstructure.adapter.in.OrgStructureRequests.RoleRuleRequest;
import com.heuristiq.identity.orgstructure.application.OrgLoginUseCase;
import com.heuristiq.identity.orgstructure.domain.OrgStructureConstants;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Role-mapping rules and login creation. Rules say which role goes with which department and position; logins are
 * created for employees from those rules, after a preview that changes nothing.
 */
@RestController
@RequestMapping(OrgStructureWebConstants.BASE)
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
@Tag(name = "Organization Logins", description = "Role-mapping rules, and creating logins for employees from them.")
public class OrgLoginController {
    private final OrgLoginUseCase useCase;
    private final CommonRequestMapper requestMapper;
    private final CallerContext caller;

    public OrgLoginController(OrgLoginUseCase useCase, CommonRequestMapper requestMapper, CallerContext caller) {
        this.useCase = useCase;
        this.requestMapper = requestMapper;
        this.caller = caller;
    }

    @Operation(summary = "List role-mapping rules")
    @GetMapping("/role-rules")
    public CommonResponseObject rules() {
        return CommonResponseObject.success(useCase.rules(caller.tenant()));
    }

    @Operation(summary = "Add a role-mapping rule",
            description = "Payload is a RoleRuleRequest: the role goes to employees in the department (and below), the position, or both. "
                    + "At least one of department and position is required.")
    @RequestObjectType(RoleRuleRequest.class)
    @PostMapping("/role-rules")
    public ResponseEntity<CommonResponseObject> createRule(Authentication auth, @Valid @RequestBody CommonRequestObject body) {
        RoleRuleRequest request = requestMapper.requireRequestObject(body, RoleRuleRequest.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponseObject.success(useCase.createRule(
                caller.tenant(), CallerContext.actor(auth), request.unitId(), request.positionId(), request.roleCode())));
    }

    @Operation(summary = "Delete a role-mapping rule", description = "Roles already given by the rule stay; only future logins are affected.")
    @DeleteMapping("/role-rules/{ruleId}")
    public CommonResponseObject deleteRule(@PathVariable String ruleId) {
        useCase.deleteRule(caller.tenant(), ruleId);
        return CommonResponseObject.success("Rule deleted", null);
    }

    @Operation(summary = "Preview logins",
            description = "Employees without a login (optionally in one department and below) with the roles the rules would give "
                    + "them and anything that would stop a login being created. Changes nothing; keyset-paginated by employee id.")
    @GetMapping("/logins/candidates")
    public CommonResponseObject candidates(@RequestParam(required = false) String unitId,
                                           @RequestParam(required = false) String afterId,
                                           @RequestParam(defaultValue = "" + OrgStructureConstants.DEFAULT_PAGE_SIZE) int limit) {
        List<LoginCandidateResponse> page = useCase.candidates(caller.tenant(), unitId, afterId, limit).stream()
                .map(LoginCandidateResponse::of).toList();
        return CommonResponseObject.success(page);
    }

    @Operation(summary = "Create logins",
            description = "Payload is a CreateLoginsRequest. Roles are recomputed from the rules, never taken from the caller. "
                    + "People who cannot get a login are skipped and reported; continue with the returned nextAfterEmployeeId.")
    @RequestObjectType(CreateLoginsRequest.class)
    @PostMapping("/logins")
    public CommonResponseObject createLogins(Authentication auth, @Valid @RequestBody CommonRequestObject body) {
        CreateLoginsRequest request = requestMapper.requireRequestObject(body, CreateLoginsRequest.class);
        int limit = request.limit() == null ? OrgStructureConstants.DEFAULT_PAGE_SIZE : request.limit();
        return CommonResponseObject.success(useCase.createLogins(caller.tenant(), CallerContext.actor(auth), request.unitId(),
                request.employeeIds(), request.afterId(), limit));
    }
}
