#!/usr/bin/env bash
# One-off, idempotent reconciliation: move the customer balances held in Rider_PaymentService's legacy ledger
# (wallet_transactions) into Rider_WalletService as OPENING_BALANCE journals, with a before/after totals report.
#
# NOT RUN BY THE BUILD. Read ops/README.md first. Nothing here is run against any database until an operator runs it.
#
#   report  (default)  read-only. Writes <out>/plan.csv and prints totals: Payment's balances, the wallet's current
#                      balances for the same users, and what is still to be moved. Never posts anything.
#   apply --confirm    posts one opening balance per user to the wallet
#                      (POST <WALLET_BASE_URL>/opening-balances, Idempotency-Key = sourceRef = the user's Payment wallet
#                      account id, i.e. wallet_accounts.user_id). REFUSES unless BOTH the --confirm flag AND the
#                      environment variable RECONCILE_APPLY_ENABLED=yes are given, and FINANCE_APPROVAL_REF is set.
#                      It never uses /internal/grants (platform-issued credits) or /internal/purchases (a payment).
#
# Environment
#   PAYMENT_DB_URL      libpq URL of the Payment database (read-only account is enough)       [required]
#   WALLET_BASE_URL     e.g. http://wallet:9121/api/v1/wallet/internal                          [required]
#   SERVICE_TOKEN       the shared rider.internal.service-token                                 [required]
#   WALLET_APP_CODE     wallet app the opening balances belong to          [default BESTRIDE_RIDER_APP]
#   OUT_DIR             where plan.csv / state / report go                 [default ./reconcile-out]
#   RECONCILE_APPLY_ENABLED       must be "yes" for apply (default: refuse)
#   FINANCE_APPROVAL_REF          ticket id of the finance sign-off; apply only (kept in the run log, not sent)
#
# Idempotency: the Idempotency-Key and the sourceRef of every post is the user's Payment wallet account id, so a repeated
# post returns the first journal and the wallet also refuses a second opening balance for the same user and app (409
# IDEMPOTENCY_CONFLICT). The script records the users it has posted in <out>/posted.csv and skips them on a re-run, and
# re-reads the Payment ledger each run, so a half-finished run is simply run again. A post that is not a 2xx is reported
# as FAILED (never assumed done) and makes the script exit 5 at the end.
set -euo pipefail

MODE="report"
CONFIRM="no"
for arg in "$@"; do
  case "$arg" in
    report|apply) MODE="$arg" ;;
    --confirm) CONFIRM="yes" ;;
    *) echo "usage: $0 [report | apply --confirm]" >&2; exit 2 ;;
  esac
done
: "${PAYMENT_DB_URL:?set PAYMENT_DB_URL}"
: "${WALLET_BASE_URL:?set WALLET_BASE_URL}"
: "${SERVICE_TOKEN:?set SERVICE_TOKEN}"
APP_CODE="${WALLET_APP_CODE:-BESTRIDE_RIDER_APP}"
OUT_DIR="${OUT_DIR:-./reconcile-out}"
SOURCE_SYSTEM="PAYMENT_SERVICE"
OPENING_BALANCES_PATH="/opening-balances"

# apply is refused before anything is read: both the flag and the environment variable are needed
if [ "$MODE" = "apply" ]; then
  [ "$CONFIRM" = "yes" ] || { echo "apply refused: pass --confirm (and set RECONCILE_APPLY_ENABLED=yes)" >&2; exit 4; }
  [ "${RECONCILE_APPLY_ENABLED:-no}" = "yes" ] || { echo "apply refused: RECONCILE_APPLY_ENABLED is not 'yes'" >&2; exit 4; }
  : "${FINANCE_APPROVAL_REF:?apply refused: set FINANCE_APPROVAL_REF to the finance sign-off ticket}"
fi

mkdir -p "$OUT_DIR"
PLAN="$OUT_DIR/plan.csv"
POSTED="$OUT_DIR/posted.csv"
touch "$POSTED"

# 1. Payment's balance per user and organization: the same formula the old WalletService.getBalance used
#    (successful credits minus successful debits over the WHOLE ledger).
psql "$PAYMENT_DB_URL" --no-psqlrc -v ON_ERROR_STOP=1 -At -F ',' -c "
  SELECT COALESCE(tenant_id, ''), user_id,
         SUM(CASE WHEN type = 'CREDIT' THEN amount ELSE -amount END)::numeric(14,2)
    FROM wallet_transactions
   WHERE status = 'SUCCESS'
   GROUP BY COALESCE(tenant_id, ''), user_id
  HAVING SUM(CASE WHEN type = 'CREDIT' THEN amount ELSE -amount END) <> 0
   ORDER BY 1, 2" > "$OUT_DIR/payment-balances.csv"

# 2. The wallet's current balance for each of those users (read-only call; fails closed on any HTTP error).
wallet_balance() {   # tenant user -> balance as plain number, 0 when the wallet has no account
  local tenant="$1" user="$2" tenant_header=()
  [ -n "$tenant" ] && tenant_header=(-H "X-Tenant-Id: $tenant")
  curl -fsS "${tenant_header[@]}" -H "X-Rider-Service-Token: $SERVICE_TOKEN" -H "X-Rider-Service-Name: rider-payment-ops" \
       "$WALLET_BASE_URL/users/$user/balance" \
    | sed -n 's/.*"balance"[[:space:]]*:[[:space:]]*"\{0,1\}\([0-9.]*\)"\{0,1\}.*/\1/p' | head -n1
}

echo "tenant,user_id,payment_balance,wallet_balance_before,already_posted" > "$PLAN"
total_payment=0; total_wallet=0; users=0; negative=0
while IFS=',' read -r tenant user balance; do
  [ -z "$user" ] && continue
  users=$((users + 1))
  w="$(wallet_balance "$tenant" "$user")"; w="${w:-0}"
  done_flag="no"; grep -qx "$tenant,$user" "$POSTED" && done_flag="yes"
  echo "$tenant,$user,$balance,$w,$done_flag" >> "$PLAN"
  total_payment="$(awk -v a="$total_payment" -v b="$balance" 'BEGIN{printf "%.2f", a+b}')"
  total_wallet="$(awk -v a="$total_wallet" -v b="$w" 'BEGIN{printf "%.2f", a+b}')"
  awk -v b="$balance" 'BEGIN{exit !(b<0)}' && negative=$((negative + 1)) || true
done < "$OUT_DIR/payment-balances.csv"

print_before() {
  echo "BEFORE  users with a non-zero Payment balance : $users"
  echo "BEFORE  total held in Payment's legacy ledger  : $total_payment"
  echo "BEFORE  total those users hold in the wallet   : $total_wallet"
  echo "Users whose Payment balance is NEGATIVE (investigate, never moved) : $negative"
}

if [ "$MODE" = "report" ]; then
  { print_before; echo "Plan: $PLAN"; } | tee "$OUT_DIR/report-before.txt"
  exit 0
fi

# ---- apply -------------------------------------------------------------------------------------------------------
print_before | tee "$OUT_DIR/report-before.txt"
[ "$negative" -eq 0 ] || { echo "apply refused: $negative user(s) have a negative Payment balance; resolve them first" >&2; exit 3; }
echo "apply: finance approval ref $FINANCE_APPROVAL_REF; app $APP_CODE; posting to $WALLET_BASE_URL$OPENING_BALANCES_PATH"

posted_now=0; skipped=0; failed=0
while IFS=',' read -r tenant user balance _wallet done_flag; do
  [ -z "$user" ] && continue
  if [ "$done_flag" = "yes" ]; then skipped=$((skipped + 1)); continue; fi
  source_ref="$user"      # the Payment wallet account id (wallet_accounts.user_id); also the Idempotency-Key
  headers=(-H "X-Rider-Service-Token: $SERVICE_TOKEN" -H "X-Rider-Service-Name: rider-payment-ops"
           -H "Idempotency-Key: $source_ref" -H "Content-Type: application/json")
  [ -n "$tenant" ] && headers+=(-H "X-Tenant-Id: $tenant")
  body=$(printf '{"userId":"%s","appCode":"%s","amount":"%s","sourceSystem":"%s","sourceRef":"%s"}' \
         "$user" "$APP_CODE" "$balance" "$SOURCE_SYSTEM" "$source_ref")
  code="$(curl -sS -o "$OUT_DIR/last-response.json" -w '%{http_code}' -X POST "${headers[@]}" -d "$body" \
          "$WALLET_BASE_URL$OPENING_BALANCES_PATH" || echo 000)"
  case "$code" in
    2??) echo "$tenant,$user" >> "$POSTED"; posted_now=$((posted_now + 1)) ;;
    *)   failed=$((failed + 1))
         echo "FAILED $tenant/$user HTTP $code: $(head -c 300 "$OUT_DIR/last-response.json" 2>/dev/null)" >&2 ;;
  esac
done < <(tail -n +2 "$PLAN")

# 3. After: re-read the wallet for the same users and compare with the plan.
total_after=0; mismatches=0
: > "$OUT_DIR/report-after.csv"
while IFS=',' read -r tenant user balance wallet_before was_posted; do
  [ -z "$user" ] && continue
  w="$(wallet_balance "$tenant" "$user")"; w="${w:-0}"
  if [ "$was_posted" = "yes" ]; then
    # posted by an earlier run: the wallet already held it when this run read "before", so it must hold at least that
    result="$(awk -v b="$balance" -v c="$w" 'BEGIN{print (c>=b)?"OK":"MISMATCH"}')"
  else
    result="$(awk -v a="$wallet_before" -v b="$balance" -v c="$w" 'BEGIN{print (c-a==b)?"OK":"MISMATCH"}')"
  fi
  [ "$result" = "OK" ] || mismatches=$((mismatches + 1))
  total_after="$(awk -v a="$total_after" -v b="$w" 'BEGIN{printf "%.2f", a+b}')"
  echo "$tenant,$user,$balance,$wallet_before,$w,$result" >> "$OUT_DIR/report-after.csv"
done < <(tail -n +2 "$PLAN")
echo "tenant,user_id,payment_balance,wallet_before,wallet_after,result" | cat - "$OUT_DIR/report-after.csv" > "$OUT_DIR/report-after-with-header.csv"

{
  print_before
  echo "AFTER   posted now / skipped (already posted) / failed : $posted_now / $skipped / $failed"
  echo "AFTER   total those users hold in the wallet   : $total_after"
  echo "AFTER   wallet moved by (after - before)       : $(awk -v a="$total_after" -v b="$total_wallet" 'BEGIN{printf "%.2f", a-b}')   (Payment total: $total_payment)"
  echo "AFTER   rows that do not match the plan        : $mismatches   (see $OUT_DIR/report-after-with-header.csv; every row must read OK)"
} | tee "$OUT_DIR/report-after.txt"
[ "$failed" -eq 0 ] && [ "$mismatches" -eq 0 ] || exit 5
