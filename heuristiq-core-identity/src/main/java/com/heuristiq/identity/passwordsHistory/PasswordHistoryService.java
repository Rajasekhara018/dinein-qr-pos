package com.heuristiq.identity.passwordsHistory;

import com.heuristiq.identity.feature.management.ManagementPageSupport;
import com.heuristiq.identity.common.PageResult;
import org.springframework.stereotype.Service;

@Service
public class PasswordHistoryService implements PasswordHistoryServiceUseCase {
    private final PasswordHistoryRepository passwordHistoryRepository;

    public PasswordHistoryService(PasswordHistoryRepository passwordHistoryRepository) {
        this.passwordHistoryRepository = passwordHistoryRepository;
    }

    public ListByUserOutput listByUser(ListByUserInput input) {
        return new ListByUserOutput(PageResult.from(passwordHistoryRepository.findByUserId(
                input.userId(), ManagementPageSupport.page(input.page(), input.size(), "audit.createdAt"))));
    }
}
