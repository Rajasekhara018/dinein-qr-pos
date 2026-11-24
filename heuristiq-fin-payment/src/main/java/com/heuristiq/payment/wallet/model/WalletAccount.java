package com.heuristiq.paymentservice.wallet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One row per customer, used only as the row lock that serialises wallet postings for that customer.
 * The balance itself is always derived from the full ledger ({@code wallet_transactions}).
 */
@Entity
@Table(name = "wallet_accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletAccount {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
