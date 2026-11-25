package com.heuristiq.identity.role;

import com.heuristiq.identity.common.PageResult;
import com.heuristiq.identity.dto.UpsertRoleRequest;

import java.util.List;
import java.util.Set;

public interface UamRoleServiceUseCase {

    record CreateInput(UpsertRoleRequest request) {}
    record CreateOutput(UamRole role) {}
    CreateOutput create(CreateInput input);

    record UpdateInput(String id, UpsertRoleRequest request) {}
    record UpdateOutput(UamRole role) {}
    UpdateOutput update(UpdateInput input);

    /**
     * Adds the permissions a role lacks, keeping every one it has; the name, description, flags and organization are not touched.
     * Used for fixed roles whose permission set a service owns (the franchise roles), so it takes the role by code.
     *
     * @param code        the role's code (any case)
     * @param permissions the permissions the role must have afterwards
     */
    record AddPermissionsInput(String code, Set<String> permissions) {}
    /** @param role the role as saved; @param added the permissions that were missing and are now added (empty when nothing changed) */
    record AddPermissionsOutput(UamRole role, List<String> added) {}
    AddPermissionsOutput addPermissions(AddPermissionsInput input);

    record GetInput(String id) {}
    record GetOutput(UamRole role) {}
    GetOutput get(GetInput input);

    /** @param tenantId when set, only the roles that organization may use (shared ones plus its own); null lists every role */
    record ListInput(String tenantId) {
        public ListInput() {
            this(null);
        }
    }
    record ListOutput(List<UamRole> roles) {}
    ListOutput list(ListInput input);

    /** code/name are optional filters (null/blank = no constraint); when both are absent this
     *  behaves exactly like the unfiltered list. */
    record ListPageInput(int page, int size, String code, String name) {
        public ListPageInput(int page, int size) {
            this(page, size, null, null);
        }
    }
    record ListPageOutput(PageResult<UamRole> roles) {}
    ListPageOutput list(ListPageInput input);

    record DeleteInput(String id) {}
    record DeleteOutput() {}
    DeleteOutput delete(DeleteInput input);
}
