package com.heuristiq.uam.securitypolicy;

import com.heuristiq.uam.common.PageResult;

import java.util.List;

public interface SecurityPolicyServiceUseCase {

    record CreateInput(UpsertSecurityPolicyRequest request) {}
    record CreateOutput(SecurityPolicy policy) {}
    CreateOutput create(CreateInput input);

    record UpdateInput(String id, UpsertSecurityPolicyRequest request) {}
    record UpdateOutput(SecurityPolicy policy) {}
    UpdateOutput update(UpdateInput input);

    record GetInput(String id) {}
    record GetOutput(SecurityPolicy policy) {}
    GetOutput get(GetInput input);

    record ListInput() {}
    record ListOutput(List<SecurityPolicy> policies) {}
    ListOutput list(ListInput input);

    /** name/tenantId are optional filters (null/blank = no constraint); when both are absent this
     *  behaves exactly like the unfiltered list. */
    record ListPageInput(int page, int size, String name, String tenantId) {
        public ListPageInput(int page, int size) {
            this(page, size, null, null);
        }
    }
    record ListPageOutput(PageResult<SecurityPolicy> policies) {}
    ListPageOutput list(ListPageInput input);

    record DeleteInput(String id) {}
    record DeleteOutput() {}
    DeleteOutput delete(DeleteInput input);
}
