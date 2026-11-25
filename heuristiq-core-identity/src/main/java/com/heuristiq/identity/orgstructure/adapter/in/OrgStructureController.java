package com.heuristiq.identity.orgstructure.adapter.in;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.CommonRequestObject;
import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.RequestObjectType;
import com.heuristiq.identity.orgstructure.adapter.in.OrgStructureRequests.AddChangeRequest;
import com.heuristiq.identity.orgstructure.adapter.in.OrgStructureRequests.EmployeeRequest;
import com.heuristiq.identity.orgstructure.adapter.in.OrgStructureRequests.OpenDraftRequest;
import com.heuristiq.identity.orgstructure.application.OrgStructureUseCase;
import com.heuristiq.identity.orgstructure.domain.Models.Employee;
import com.heuristiq.identity.orgstructure.domain.OrgStructureConstants;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Organization structure API. Edits go into a draft that is previewed and then published; the
 * tenant always comes from the caller's token, never from the request.
 */
@RestController
@RequestMapping(OrgStructureWebConstants.BASE)
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
@Tag(name = "Organization Structure", description = "Departments, position ladders, people and reporting lines, edited as a draft and published after preview.")
public class OrgStructureController {

    private final OrgStructureUseCase useCase;
    private final CommonRequestMapper requestMapper;
    private final CallerContext caller;
    private final com.heuristiq.identity.orgstructure.application.DataScopeResolver scopes;

    public OrgStructureController(OrgStructureUseCase useCase, CommonRequestMapper requestMapper, CallerContext caller,
                                  com.heuristiq.identity.orgstructure.application.DataScopeResolver scopes) {
        this.scopes = scopes;
        this.useCase = useCase;
        this.requestMapper = requestMapper;
        this.caller = caller;
    }

    // ---- drafts ---------------------------------------------------------------------------------

    @Operation(summary = "Open the draft", description = "Returns the open draft, creating it if none exists. Payload is an OpenDraftRequest.")
    @RequestObjectType(OpenDraftRequest.class)
    @PostMapping("/drafts")
    public ResponseEntity<CommonResponseObject> openDraft(Authentication auth,
                                                          @Valid @RequestBody CommonRequestObject body) {
        OpenDraftRequest request = requestMapper.requireRequestObject(body, OpenDraftRequest.class);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponseObject.success(useCase.openDraft(caller.tenant(), CallerContext.actor(auth), request.note())));
    }

    @Operation(summary = "Get the open draft")
    @GetMapping("/drafts/current")
    public CommonResponseObject currentDraft() {
        return CommonResponseObject.success(useCase.currentDraft(caller.tenant()));
    }

    @Operation(summary = "Add a change to a draft", description = "Payload is an AddChangeRequest; the change type decides which payload keys are required.")
    @RequestObjectType(AddChangeRequest.class)
    @PostMapping("/drafts/{draftId}/changes")
    public ResponseEntity<CommonResponseObject> addChange(@PathVariable String draftId,
                                                          @Valid @RequestBody CommonRequestObject body) {
        AddChangeRequest request = requestMapper.requireRequestObject(body, AddChangeRequest.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponseObject.success(
                useCase.addChange(caller.tenant(), draftId, request.type(), request.payload())));
    }

    @Operation(summary = "List the changes of a draft")
    @GetMapping("/drafts/{draftId}/changes")
    public CommonResponseObject changes(@PathVariable String draftId) {
        return CommonResponseObject.success(useCase.changes(caller.tenant(), draftId));
    }

    @Operation(summary = "Remove a change from a draft")
    @DeleteMapping("/drafts/{draftId}/changes/{changeId}")
    public CommonResponseObject removeChange(@PathVariable String draftId, @PathVariable String changeId) {
        useCase.removeChange(caller.tenant(), draftId, changeId);
        return CommonResponseObject.success("Change removed", null);
    }

    @Operation(summary = "Preview a draft", description = "Shows the resulting top-level units and any problems without changing the live organization.")
    @PostMapping("/drafts/{draftId}/preview")
    public CommonResponseObject preview(@PathVariable String draftId) {
        return CommonResponseObject.success(useCase.preview(caller.tenant(), draftId));
    }

    @Operation(summary = "Publish a draft", description = "Makes the draft live. Nothing is changed if the result lists ERROR issues.")
    @PostMapping("/drafts/{draftId}/publish")
    public CommonResponseObject publish(Authentication auth, @PathVariable String draftId) {
        return CommonResponseObject.success(useCase.publish(caller.tenant(), draftId, CallerContext.actor(auth)));
    }

    @Operation(summary = "Roll back the last publish", description = "Puts the structure back as it was just before the most recent publish. "
            + "Refused when a later publish, an employee import or an open draft with changes exists.")
    @PostMapping("/rollback")
    public CommonResponseObject rollback(Authentication auth) {
        useCase.rollbackLatest(caller.tenant(), CallerContext.actor(auth));
        return CommonResponseObject.success("Publish rolled back", null);
    }

    @Operation(summary = "Discard a draft")
    @PostMapping("/drafts/{draftId}/discard")
    public CommonResponseObject discard(Authentication auth, @PathVariable String draftId) {
        useCase.discard(caller.tenant(), draftId, CallerContext.actor(auth));
        return CommonResponseObject.success("Draft discarded", null);
    }

    // ---- live tree ------------------------------------------------------------------------------

    @Operation(summary = "List sub-units", description = "Direct children of a unit (top-level units when parentId is omitted), with child count and headcount.")
    @GetMapping("/units")
    public CommonResponseObject units(@RequestParam(required = false) String parentId) {
        String tenant = caller.tenant();
        return CommonResponseObject.success(useCase.children(tenant, parentId, scope(tenant)));
    }

    @Operation(summary = "Search units", description = "Active units whose code or name starts with q (all when omitted), capped; used by pickers.")
    @GetMapping("/units/search")
    public CommonResponseObject searchUnits(@RequestParam(required = false) String q,
                                            @RequestParam(defaultValue = "" + OrgStructureConstants.DEFAULT_PAGE_SIZE) int limit) {
        String tenant = caller.tenant();
        return CommonResponseObject.success(useCase.searchUnits(tenant, q, limit, scope(tenant)));
    }

    @Operation(summary = "List people under a unit", description = "Everyone assigned to the unit or any sub-unit, keyset-paginated by employee id.")
    @GetMapping("/units/{unitId}/people")
    public CommonResponseObject people(@PathVariable String unitId, @RequestParam(required = false) String afterId,
                                       @RequestParam(defaultValue = "" + OrgStructureConstants.DEFAULT_PAGE_SIZE) int limit) {
        String tenant = caller.tenant();
        return CommonResponseObject.success(useCase.people(tenant, unitId, afterId, limit, scope(tenant)));
    }

    @Operation(summary = "List position ladders")
    @GetMapping("/ladders")
    public CommonResponseObject ladders() {
        return CommonResponseObject.success(useCase.ladders(caller.tenant()));
    }

    @Operation(summary = "List the positions of a ladder, highest rank first")
    @GetMapping("/ladders/{ladderId}/positions")
    public CommonResponseObject positions(@PathVariable String ladderId) {
        return CommonResponseObject.success(useCase.positions(caller.tenant(), ladderId));
    }

    // ---- employees ------------------------------------------------------------------------------

    @Operation(summary = "Create an employee", description = "Payload is an EmployeeRequest. An employee needs no login; userId links one later.")
    @RequestObjectType(EmployeeRequest.class)
    @PostMapping("/employees")
    public ResponseEntity<CommonResponseObject> createEmployee(@Valid @RequestBody CommonRequestObject body) {
        EmployeeRequest request = requestMapper.requireRequestObject(body, EmployeeRequest.class);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponseObject.success(useCase.createEmployee(caller.tenant(), toEmployee(null, request))));
    }

    @Operation(summary = "Update an employee", description = "Payload is an EmployeeRequest.")
    @RequestObjectType(EmployeeRequest.class)
    @PutMapping("/employees/{id}")
    public CommonResponseObject updateEmployee(@PathVariable String id, @Valid @RequestBody CommonRequestObject body) {
        EmployeeRequest request = requestMapper.requireRequestObject(body, EmployeeRequest.class);
        return CommonResponseObject.success(useCase.updateEmployee(caller.tenant(), toEmployee(id, request)));
    }

    @Operation(summary = "Get an employee")
    @GetMapping("/employees/{id}")
    public CommonResponseObject employee(@PathVariable String id) {
        return CommonResponseObject.success(useCase.employee(caller.tenant(), id));
    }

    @Operation(summary = "Search employees", description = "Prefix search on code, name or email; keyset-paginated by id.")
    @GetMapping("/employees")
    public CommonResponseObject employees(@RequestParam(required = false) String search,
                                          @RequestParam(required = false) String afterId,
                                          @RequestParam(defaultValue = "" + OrgStructureConstants.DEFAULT_PAGE_SIZE) int limit) {
        String tenant = caller.tenant();
        return CommonResponseObject.success(useCase.listEmployees(tenant, search, afterId, limit, scope(tenant)));
    }

    // ---- helpers --------------------------------------------------------------------------------

    /** What the signed-in caller may see of the organization (everything unless the organization limits people to their department). */
    private com.heuristiq.identity.orgstructure.domain.DataScope scope(String tenant) {
        return scopes.resolve(tenant, CallerContext.userId(), CallerContext.roles());
    }

    private static Employee toEmployee(String id, EmployeeRequest r) {
        return new Employee(id, r.employeeCode(), r.fullName(), r.email(), r.phone(), r.status(), r.userId(),
                r.joinedOn());
    }
}
