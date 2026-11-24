package com.heuristiq.payment.wallet.repository.jpa;

import com.heuristiq.payment.wallet.model.WalletAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Spring Data JPA binding for {@code wallet_accounts}. Not injected directly. */
interface JpaWalletAccountRepository extends JpaRepository<WalletAccount, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM WalletAccount a WHERE a.userId = :userId")
    Optional<WalletAccount> lockByUserId(@Param("userId") String userId);
}
