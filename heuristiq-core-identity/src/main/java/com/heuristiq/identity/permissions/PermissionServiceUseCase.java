package com.heuristiq.identity.permissions;

import com.heuristiq.identity.common.PageResult;
import com.heuristiq.identity.dto.UpsertPermissionRequest;

import java.util.List;

public interface PermissionServiceUseCase {

    record CreateInput(UpsertPermissionRequest request) {}
    record CreateOutput(Permission permission) {}
    CreateOutput create(CreateInput input);

    record UpdateInput(String id, UpsertPermissionRequest request) {}
    record UpdateOutput(Permission permission) {}
    UpdateOutput update(UpdateInput input);

    record GetInput(String id) {}
    record GetOutput(Permission permission) {}
    GetOutput get(GetInput input);

    record ListInput() {}
    record ListOutput(List<Permission> permissions) {}
    ListOutput list(ListInput input);

    record ListPageInput(int page, int size) {}
    record ListPageOutput(PageResult<Permission> permissions) {}
    ListPageOutput list(ListPageInput input);

    record DeleteInput(String id) {}
    record DeleteOutput() {}
    DeleteOutput delete(DeleteInput input);
}
