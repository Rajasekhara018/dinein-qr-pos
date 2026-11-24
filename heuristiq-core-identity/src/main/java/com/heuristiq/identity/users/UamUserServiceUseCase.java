package com.heuristiq.uam.users;

import com.heuristiq.uam.dto.UpdateMyProfileRequest;
import com.heuristiq.uam.dto.UpsertUserRequest;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.common.PageResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UamUserServiceUseCase {

    record CreateInput(UpsertUserRequest request) {}
    record CreateOutput(UamUser user) {}
    CreateOutput create(CreateInput input);

    record UpdateInput(String id, UpsertUserRequest request) {}
    record UpdateOutput(UamUser user) {}
    UpdateOutput update(UpdateInput input);

    record UpdateOwnProfileInput(String id, UpdateMyProfileRequest request) {}
    record UpdateOwnProfileOutput(UamUser user) {}
    UpdateOwnProfileOutput updateOwnProfile(UpdateOwnProfileInput input);

    record UpdateStatusInput(String id, UamEnums.UserStatus status) {}
    record UpdateStatusOutput(UamUser user) {}
    UpdateStatusOutput updateStatus(UpdateStatusInput input);

    record SetPasswordInput(String id, String rawPassword, boolean temporary) {}
    record SetPasswordOutput(UamUser user) {}
    SetPasswordOutput setPassword(SetPasswordInput input);

    record SetTotpRequiredInput(String id, boolean required) {}
    record SetTotpRequiredOutput(UamUser user) {}
    SetTotpRequiredOutput setTotpRequired(SetTotpRequiredInput input);

    /** Adds roles to an account and keeps the ones it has: nothing is ever removed. A role the caller may not hand out is refused. */
    record AddRolesInput(String id, java.util.Collection<String> roles) {}
    record AddRolesOutput(UamUser user) {}
    AddRolesOutput addRoles(AddRolesInput input);

    record GetInput(String id) {}
    record GetOutput(UamUser user) {}
    GetOutput get(GetInput input);

    /** Batch lookup for sibling services that only need to show who a user id is (at most
     *  {@link UamUserConstants#MAX_BATCH_LOOKUP} ids; unknown or tenant-inaccessible ids are omitted). */
    record FindByIdsInput(Collection<String> ids) {}
    record FindByIdsOutput(List<UamUser> users) {}
    FindByIdsOutput findByIds(FindByIdsInput input);

    record FindByPhoneInput(String phone) {}
    record FindByPhoneOutput(Optional<UamUser> user) {}
    FindByPhoneOutput findByPhone(FindByPhoneInput input);

    record ListInput() {}
    record ListOutput(List<UamUser> users) {}
    ListOutput list(ListInput input);

    record ListPageInput(int page, int size) {}
    record ListPageOutput(PageResult<UamUser> users) {}
    ListPageOutput list(ListPageInput input);

    /** Optional filters are null/blank when not supplied; when all are absent this behaves like
     *  {@link #list(ListPageInput)}. */
    record SearchInput(int page, int size, String roleCode, UamEnums.UserStatus status, String search, String tenantScope) {}
    record SearchOutput(PageResult<UamUser> users) {}
    SearchOutput search(SearchInput input);

    record DeleteInput(String id) {}
    record DeleteOutput() {}
    DeleteOutput delete(DeleteInput input);
}
