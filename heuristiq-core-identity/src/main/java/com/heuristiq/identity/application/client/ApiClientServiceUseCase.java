package com.heuristiq.uam.application.client;

import com.heuristiq.uam.common.PageResult;
import com.heuristiq.uam.enums.UamEnums.ApiClientType;

import java.util.List;

public interface ApiClientServiceUseCase {

    record CreateInput(UpsertClientRequest request) {}
    record CreateOutput(ApiClientCreated created) {}
    CreateOutput create(CreateInput input);

    record UpdateInput(String id, UpsertClientRequest request) {}
    record UpdateOutput(ApiClient client) {}
    UpdateOutput update(UpdateInput input);

    record RotateSecretInput(String id) {}
    record RotateSecretOutput(String secret) {}
    RotateSecretOutput rotateSecret(RotateSecretInput input);

    record GetInput(String id) {}
    record GetOutput(ApiClient client) {}
    GetOutput get(GetInput input);

    record ListInput() {}
    record ListOutput(List<ApiClient> clients) {}
    ListOutput list(ListInput input);

    /** clientId/clientType/active are optional filters (null = no constraint); when all are
     *  absent this behaves exactly like the unfiltered list. */
    record ListPageInput(int page, int size, String clientId, ApiClientType clientType, Boolean active) {
        public ListPageInput(int page, int size) {
            this(page, size, null, null, null);
        }
    }
    record ListPageOutput(PageResult<ApiClient> clients) {}
    ListPageOutput list(ListPageInput input);

    record DeleteInput(String id) {}
    record DeleteOutput() {}
    DeleteOutput delete(DeleteInput input);
}
