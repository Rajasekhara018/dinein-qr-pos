package com.heuristiq.identity.orgstructure.adapter.in;

import java.time.LocalDate;
import java.util.Map;

import com.heuristiq.identity.orgstructure.domain.ChangeType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request payloads (carried inside the standard CommonRequestObject envelope) of the org-structure API. */
public final class OrgStructureRequests {
    private OrgStructureRequests() {
    }

    public record OpenDraftRequest(@Size(max = 500) String note) {
    }

    public record AddChangeRequest(@NotNull ChangeType type, Map<String, Object> payload) {
    }

    /** Starts an import from a file already uploaded to the document store. */
    public record ImportRequest(@NotBlank @Size(max = 64) String documentId) {
    }

    /** A role-mapping rule: the role goes to employees in the department (and below), the position, or both. */
    public record RoleRuleRequest(@Size(max = 64) String unitId, @Size(max = 64) String positionId,
                                  @NotBlank @Size(max = 100) String roleCode) {
    }

    /**
     * Create logins for the chosen employees, or - when {@code employeeIds} is empty - for one page of the
     * department's employees after {@code afterId}.
     */
    public record CreateLoginsRequest(@Size(max = 64) String unitId, @Size(max = 200) java.util.List<String> employeeIds,
                                      @Size(max = 64) String afterId, @jakarta.validation.constraints.Min(1)
                                      @jakarta.validation.constraints.Max(200) Integer limit) {
    }

    public record EmployeeRequest(@NotBlank @Size(max = 100) String employeeCode,
                                  @NotBlank @Size(max = 200) String fullName,
                                  @Size(max = 200) String email,
                                  @Size(max = 50) String phone,
                                  @Size(max = 20) String status,
                                  @Size(max = 64) String userId,
                                  LocalDate joinedOn) {
    }
}
