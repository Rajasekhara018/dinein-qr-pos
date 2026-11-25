package com.heuristiq.identity.userpreference;

import com.heuristiq.identity.common.PageResult;
import com.heuristiq.identity.dto.UpsertUserPreferenceRequest;

import java.util.List;

public interface UserPreferenceServiceUseCase {

    record UpsertInput(UpsertUserPreferenceRequest request) {}
    record UpsertOutput(UserPreference preference) {}
    UpsertOutput upsert(UpsertInput input);

    record GetInput(String id) {}
    record GetOutput(UserPreference preference) {}
    GetOutput get(GetInput input);

    record GetByUserInput(String userId, String applicationId) {}
    record GetByUserOutput(UserPreference preference) {}
    GetByUserOutput getByUser(GetByUserInput input);

    record ListInput() {}
    record ListOutput(List<UserPreference> preferences) {}
    ListOutput list(ListInput input);

    record ListPageInput(int page, int size) {}
    record ListPageOutput(PageResult<UserPreference> preferences) {}
    ListPageOutput list(ListPageInput input);

    record DeleteInput(String id) {}
    record DeleteOutput() {}
    DeleteOutput delete(DeleteInput input);
}
