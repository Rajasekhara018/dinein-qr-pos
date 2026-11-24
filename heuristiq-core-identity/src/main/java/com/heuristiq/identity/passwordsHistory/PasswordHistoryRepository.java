package com.heuristiq.uam.passwordsHistory;

import com.heuristiq.uam.passwordsHistory.PasswordHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PasswordHistoryRepository {
    PasswordHistory save(PasswordHistory passwordHistory);

    Page<PasswordHistory> findByUserId(String userId, Pageable pageable);
}
