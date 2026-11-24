# Rider_PaymentService ops

## Moving old balances to the wallet

`reconcile-wallet-balances.sh` is a one-off, idempotent reconciliation. Rider_WalletService now owns every user balance
and ledger entry; before this release Payment kept customer balances in its own ledger (`wallet_transactions`). Those
balances have to appear in the wallet as ledger entries before Payment's ledger is retired. **It has not been run against
any database.** The two old tables are kept (read-only for reconciliation) by migration V16.

### What it does

1. `report` (default, read-only): computes each user's balance from the whole Payment ledger (SUCCESS credits minus
   SUCCESS debits, per organization), reads the same user's balance in the wallet
   (`GET /api/v1/wallet/internal/users/{userId}/balance`), writes `plan.csv` and prints the BEFORE totals
   (users, total in Payment, total the same users hold in the wallet, users with a negative balance).
2. `apply --confirm` (with `RECONCILE_APPLY_ENABLED=yes`): posts one opening-balance entry per user (key = the user's Payment wallet account id), remembers the
   users it posted in `posted.csv` (a re-run skips them), then re-reads the wallet and writes the AFTER report
   (`report-after-with-header.csv`: `wallet_after - wallet_before` must equal the Payment balance, otherwise
   `MISMATCH`). Users with a negative Payment balance block the run.

### The wallet endpoint it uses

Wallet now has `POST /internal/opening-balances {userId, appCode, amount, sourceSystem, sourceRef}` + `Idempotency-Key`
(service token only). It posts an `OPENING_BALANCE` journal: Dr `SYS_OPENING_BALANCE[app]` (a dedicated clearing account,
created on first use) / Cr `CUSTOMER_WALLET`, new lot, no commission, initiated by SERVICE, tenant from `X-Tenant-Id`. It
rejects amount <= 0, a disabled app, and a second opening balance for the same user and app under another key
(`IDEMPOTENCY_CONFLICT`); a repeated key returns the first journal. Only an admin reversal undoes it. The older endpoints
stay unsuitable: `/internal/grants` books platform-issued credit, `/internal/purchases` means a verified payment and is off,
`/admin/adjustments` needs an admin session.

`apply` sends `sourceSystem=PAYMENT_SERVICE` and `sourceRef` = the user's Payment wallet account id
(`wallet_accounts.user_id`), and uses the same value as the `Idempotency-Key`. Still to decide with finance: whether INR
balances map one to one to credits (wallet design Q1). The script posts as-is.

`apply` is refused unless you pass **both** `--confirm` and `RECONCILE_APPLY_ENABLED=yes`, and set `FINANCE_APPROVAL_REF`.
It prints the before and after totals (and exits 5 if any post failed or any row does not match). Its offline test is
`bash ops/test-reconcile-wallet-balances.sh` (stub `psql`/`curl`, no database).

### Run order

1. Deploy Wallet (with the opening-balance endpoint) and Payment V16 / this release.
2. `./reconcile-wallet-balances.sh report` and keep `report-before.txt`.
3. Finance signs off the totals.
4. `RECONCILE_APPLY_ENABLED=yes FINANCE_APPROVAL_REF=<ticket> ./reconcile-wallet-balances.sh apply --confirm`, check `report-after-with-header.csv` is all `OK`.
5. Refunds to the wallet (`PaymentRefundService`, destination WALLET) now go to Wallet's `/internal/refund-credits`
   (idempotent on the refund id) and no longer write the legacy ledger, so after this release the legacy balances are
   frozen and a second `report` should show the same totals. Refunds booked in the legacy ledger by the OLD code before
   the deploy are part of those balances and are moved by this script.

Needs `psql`, `curl`, `awk`. Environment variables are listed at the top of the script.

### Totals to compare by hand (SQL plan)

```sql
-- Payment, per organization (read-only)
SELECT COALESCE(tenant_id, '') AS tenant, COUNT(DISTINCT user_id) AS users,
       SUM(CASE WHEN type = 'CREDIT' THEN amount ELSE -amount END) AS total
  FROM wallet_transactions WHERE status = 'SUCCESS' GROUP BY 1;
-- top-ups still waiting for their wallet credit (these are NOT in the legacy ledger and must not be moved here)
SELECT status, COUNT(*), SUM(amount) FROM wallet_topup_sessions
 WHERE status IN ('PAID_AWAITING_CREDIT', 'CAPTURED') GROUP BY status;
```

Top-ups captured by the OLD code are `CAPTURED` with no `wallet_journal_id`: their money is in the legacy ledger and is
covered by this script. Top-ups captured by the NEW code have a `wallet_journal_id` and are already in the wallet.
