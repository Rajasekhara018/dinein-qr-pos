package com.heuristiq.uam.passwordsHistory;

import com.heuristiq.uam.common.PageResult;

public interface PasswordHistoryServiceUseCase {

    record ListByUserInput(String userId, int page, int size) {}
    record ListByUserOutput(PageResult<PasswordHistory> entries) {}
    ListByUserOutput listByUser(ListByUserInput input);
}
