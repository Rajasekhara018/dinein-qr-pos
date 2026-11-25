package com.heuristiq.identity.passwordsHistory;

import com.heuristiq.identity.passwordsHistory.PasswordHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PasswordHistoryRepository {
    PasswordHistory save(PasswordHistory passwordHistory);

    Page<PasswordHistory> findByUserId(String userId, Pageable pageable);
}
