package com.heuristiq.identity.organization;

import com.heuristiq.identity.common.PageResult;
import com.heuristiq.identity.enums.UamEnums.TenantStatus;
import com.heuristiq.identity.enums.UamEnums.TenantType;

import java.util.List;

public interface TenantServiceUseCase {

    record CreateInput(UpsertOrganizationRequest request) {}
    record CreateOutput(Tenant tenant) {}
    CreateOutput create(CreateInput input);

    record UpdateInput(String id, UpsertOrganizationRequest request) {}
    record UpdateOutput(Tenant tenant) {}
    UpdateOutput update(UpdateInput input);

    record GetInput(String id) {}
    record GetOutput(Tenant tenant) {}
    GetOutput get(GetInput input);

    record ListInput() {}
    record ListOutput(List<Tenant> tenants) {}
    ListOutput list(ListInput input);

    /** code/name/type/status are optional filters (null/blank = no constraint); when all are absent
     *  this behaves exactly like the unfiltered list. */
    record ListPageInput(int page, int size, String code, String name, TenantType type, TenantStatus status) {
        public ListPageInput(int page, int size) {
            this(page, size, null, null, null, null);
        }
    }
    record ListPageOutput(PageResult<Tenant> tenants) {}
    ListPageOutput list(ListPageInput input);

    record DeleteInput(String id) {}
    record DeleteOutput() {}
    DeleteOutput delete(DeleteInput input);
}
