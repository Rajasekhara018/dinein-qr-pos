package com.heuristiq.identity.passwordsHistory;

import com.heuristiq.identity.common.PageResult;

public interface PasswordHistoryServiceUseCase {

    record ListByUserInput(String userId, int page, int size) {}
    record ListByUserOutput(PageResult<PasswordHistory> entries) {}
    ListByUserOutput listByUser(ListByUserInput input);
}
