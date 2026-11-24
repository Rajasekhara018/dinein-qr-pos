package com.heuristiq.uam.orgstructure.adapter.in;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.orgstructure.application.OrgStructureUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Who is in a department, for other Rider services (the workflow service offers a step to a department). Service-to-service
 * only: reachable with the shared {@code X-Rider-Service-Token} (see InternalServiceSecurityConfig), never with an end-user
 * token. The organization is always named by the caller, since a service has no organization of its own.
 */
@RestController
@RequestMapping("/api/v1/uam/internal/org")
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
@Tag(name = "Organization directory (internal)", description = "Service-to-service lookups of departments and their people.")
public class InternalOrgDirectoryController {
    private final OrgStructureUseCase structure;
    private final com.heuristiq.uam.organization.membership.TenantMembershipServiceUseCase memberships;
    private final com.heuristiq.uam.users.repository.UamUserRepository users;

    public InternalOrgDirectoryController(OrgStructureUseCase structure,
                                          com.heuristiq.uam.organization.membership.TenantMembershipServiceUseCase memberships,
                                          com.heuristiq.uam.users.repository.UamUserRepository users) {
        this.structure = structure;
        this.memberships = memberships;
        this.users = users;
    }

    @Operation(summary = "A department and the user accounts in it",
            description = "Finds the active department by its code in the organization; the members are everyone placed in it or in a department below it, as user ids. found=false when there is no such department.")
    @GetMapping("/departments/{code}/members")
    public ResponseEntity<CommonResponseObject> members(@PathVariable String code, @RequestParam String tenantId) {
        return ResponseEntity.ok(CommonResponseObject.success(structure.departmentMembers(tenantId, code)));
    }

    @Operation(summary = "The departments a user belongs to",
            description = "Codes of the departments the user currently sits in and of every department above them, in the organization.")
    @GetMapping("/users/{userId}/departments")
    public ResponseEntity<CommonResponseObject> departmentsOfUser(@PathVariable String userId, @RequestParam String tenantId) {
        List<String> codes = structure.departmentsOfUser(tenantId, userId);
        return ResponseEntity.ok(CommonResponseObject.success(codes));
    }

    @Operation(summary = "Everyone below a user in the reporting structure",
            description = "User accounts of everyone who reports to this user directly or through others, each with the account of the person they report to. Used by the wallet to show where a manager's credits went down the structure.")
    @GetMapping("/users/{userId}/team-tree")
    public ResponseEntity<CommonResponseObject> teamTreeOfUser(@PathVariable String userId, @RequestParam String tenantId) {
        return ResponseEntity.ok(CommonResponseObject.success(structure.teamTreeOfUser(tenantId, userId)));
    }

    @Operation(summary = "The departments of an organization and their heads",
            description = "Every active department that has a head with a user account. Used by the wallet so a super admin can add credits to the heads of the organization's departments.")
    @GetMapping("/department-heads")
    public ResponseEntity<CommonResponseObject> departmentHeads(@RequestParam String tenantId) {
        return ResponseEntity.ok(CommonResponseObject.success(structure.departmentHeads(tenantId)));
    }

    @Operation(summary = "The people who report directly to a user",
            description = "User accounts of the people with an open reporting line to this user in the organization (their team); empty when the user manages nobody. Used by the wallet to let a manager distribute credits to their own team.")
    @GetMapping("/users/{userId}/team")
    public ResponseEntity<CommonResponseObject> teamOfUser(@PathVariable String userId, @RequestParam String tenantId) {
        return ResponseEntity.ok(CommonResponseObject.success(structure.teamOfUser(tenantId, userId)));
    }

    @Operation(summary = "Which of these accounts are active members of the organization",
            description = "Ids (comma separated, at most 100) that are ACTIVE members of the organization. Used by the wallet so a lookup or a grant never reaches a person of another organization.")
    @GetMapping("/members/active")
    public ResponseEntity<CommonResponseObject> activeMembers(@RequestParam String tenantId, @RequestParam String ids) {
        List<String> wanted = java.util.Arrays.stream(ids.split(",")).map(String::trim).filter(id -> !id.isEmpty()).toList();
        return ResponseEntity.ok(CommonResponseObject.success(List.copyOf(memberships.activeMembersAmong(tenantId, wanted))));
    }

    @Operation(summary = "The organization each account belongs to",
            description = "For up to 100 account ids (comma separated): id to the organization of the account, left out when the account has none or does not exist. Used by other services to record which organization their existing members belong to.")
    @GetMapping("/users/tenants")
    public ResponseEntity<CommonResponseObject> tenantsOf(@RequestParam String ids) {
        java.util.Map<String, String> tenants = new java.util.LinkedHashMap<>();
        java.util.Arrays.stream(ids.split(",")).map(String::trim).filter(id -> !id.isEmpty()).distinct().limit(100)
                .forEach(id -> users.findById(id).map(com.heuristiq.uam.users.UamUser::getTenantId)
                        .filter(t -> t != null && !t.isBlank()).ifPresent(t -> tenants.put(id, t)));
        return ResponseEntity.ok(CommonResponseObject.success(tenants));
    }
}
