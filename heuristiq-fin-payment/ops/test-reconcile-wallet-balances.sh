#!/usr/bin/env bash
# Offline test of reconcile-wallet-balances.sh. Uses stub `psql` and `curl` programs on PATH and a temp OUT_DIR: it never
# touches a database or a wallet. Run:  bash ops/test-reconcile-wallet-balances.sh
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$HERE/reconcile-wallet-balances.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
fail=0
check() { if [ "$2" = "$3" ]; then echo "ok   - $1"; else echo "FAIL - $1 (expected '$3', got '$2')"; fail=1; fi; }
contains() { if grep -q -- "$3" <<<"$2"; then echo "ok   - $1"; else echo "FAIL - $1 (no '$3' in output)"; fail=1; fi; }

bash -n "$SCRIPT" && echo "ok   - syntax" || { echo "FAIL - syntax"; fail=1; }

# --- stubs ---------------------------------------------------------------------------------------------------------
mkdir -p "$WORK/bin"
cat > "$WORK/bin/psql" <<'EOS'
#!/usr/bin/env bash
# the plan query: tenant,user,balance  (one org user, one default-org user)
printf 'org-a,u1,100.00\n,u2,40.50\n'
EOS
cat > "$WORK/bin/curl" <<'EOS'
#!/usr/bin/env bash
# Records every call. GET balance returns what $WALLET_STATE holds (default 0.00); POST opening-balances adds the amount.
echo "$*" >> "$CURL_LOG"
method=GET; url=""; out=""; data=""
while [ $# -gt 0 ]; do
  case "$1" in
    -X) method="$2"; shift 2 ;;
    -o) out="$2"; shift 2 ;;
    -d) data="$2"; shift 2 ;;
    -H|-w) shift 2 ;;
    -*) shift ;;
    *) url="$1"; shift ;;
  esac
done
if [ "$method" = "POST" ]; then
  [ -n "${POST_CODE:-}" ] || POST_CODE=200
  user="$(sed -n 's/.*"userId":"\([^"]*\)".*/\1/p' <<<"$data")"
  amount="$(sed -n 's/.*"amount":"\([^"]*\)".*/\1/p' <<<"$data")"
  echo "$data" >> "$POST_LOG"
  if [ "$POST_CODE" = "200" ]; then echo "$amount" > "$WALLET_DIR/$user"; fi
  echo '{"success":true}' > "$out"
  printf '%s' "$POST_CODE"
else
  user="${url%/balance}"; user="${user##*/}"
  bal="0.00"; [ -f "$WALLET_DIR/$user" ] && bal="$(cat "$WALLET_DIR/$user")"
  printf '{"success":true,"responseObject":{"balance":"%s"}}' "$bal"
fi
EOS
chmod +x "$WORK/bin/psql" "$WORK/bin/curl"

run() {   # name, then the script's arguments; environment comes from the caller
  PATH="$WORK/bin:$PATH" PAYMENT_DB_URL=stub WALLET_BASE_URL=http://wallet.test/internal SERVICE_TOKEN=t \
  OUT_DIR="$WORK/out-$1" CURL_LOG="$WORK/curl-$1.log" POST_LOG="$WORK/post-$1.log" WALLET_DIR="$WORK/wallet-$1" \
  bash "$SCRIPT" "${@:2}" 2>&1
}
for n in report refuse noflag noenv apply rerun failing; do mkdir -p "$WORK/wallet-$n"; : > "$WORK/curl-$n.log"; : > "$WORK/post-$n.log"; done

# --- report: read-only ---------------------------------------------------------------------------------------------
out="$(run report report)"; code=$?
check "report exits 0" "$code" "0"
contains "report prints the Payment total" "$out" "total held in Payment's legacy ledger  : 140.50"
check "report never POSTs" "$(grep -c -- '-X POST' "$WORK/curl-report.log")" "0"

# --- apply is refused without the flag, without the env var, and without the approval ref --------------------------
out="$(RECONCILE_APPLY_ENABLED=yes FINANCE_APPROVAL_REF=T-1 run noflag apply)"; code=$?
check "apply without --confirm is refused" "$code" "4"
out="$(FINANCE_APPROVAL_REF=T-1 run noenv apply --confirm)"; code=$?
check "apply without RECONCILE_APPLY_ENABLED is refused" "$code" "4"
out="$(RECONCILE_APPLY_ENABLED=yes run refuse apply --confirm)"; code=$?
check "apply without FINANCE_APPROVAL_REF is refused" "$code" "1"
check "refused applies read and post nothing" "$(cat "$WORK/curl-noflag.log" "$WORK/curl-noenv.log" "$WORK/curl-refuse.log" | wc -l | tr -d ' ')" "0"
out="$(run report apply --nonsense)"; check "an unknown argument is a usage error" "$?" "2"

# --- apply --------------------------------------------------------------------------------------------------------
out="$(RECONCILE_APPLY_ENABLED=yes FINANCE_APPROVAL_REF=T-1 run apply apply --confirm)"; code=$?
check "apply exits 0" "$code" "0"
contains "apply prints before totals" "$out" "BEFORE  total held in Payment's legacy ledger  : 140.50"
contains "apply prints after totals" "$out" "AFTER   total those users hold in the wallet   : 140.50"
contains "apply reports the wallet moved by the Payment total" "$out" "wallet moved by (after - before)       : 140.50"
check "one POST per user" "$(grep -c . "$WORK/post-apply.log")" "2"
contains "key and sourceRef are the account id" "$(cat "$WORK/post-apply.log")" '"sourceRef":"u1"'
contains "source system is named" "$(cat "$WORK/post-apply.log")" '"sourceSystem":"PAYMENT_SERVICE"'
contains "the idempotency key is the account id" "$(cat "$WORK/curl-apply.log")" "Idempotency-Key: u1"
contains "the organization is forwarded" "$(cat "$WORK/curl-apply.log")" "X-Tenant-Id: org-a"
contains "every row is OK" "$(cat "$WORK/out-apply/report-after-with-header.csv")" "org-a,u1,100.00,0.00,100.00,OK"

# --- re-run skips what was posted ----------------------------------------------------------------------------------
cp -r "$WORK/out-apply" "$WORK/out-rerun"; cp "$WORK/wallet-apply/"* "$WORK/wallet-rerun/"
out="$(RECONCILE_APPLY_ENABLED=yes FINANCE_APPROVAL_REF=T-1 run rerun apply --confirm)"; code=$?
check "re-run exits 0" "$code" "0"
check "re-run posts nothing" "$(grep -c . "$WORK/post-rerun.log")" "0"
contains "re-run counts the skipped users" "$out" "0 / 2 / 0"

# --- a failing post is reported and fails the run ------------------------------------------------------------------
out="$(POST_CODE=409 RECONCILE_APPLY_ENABLED=yes FINANCE_APPROVAL_REF=T-1 run failing apply --confirm)"; code=$?
check "failed posts exit 5" "$code" "5"
contains "failure is named" "$out" "FAILED org-a/u1 HTTP 409"
check "a failed post is not remembered as posted" "$(grep -c . "$WORK/out-failing/posted.csv")" "0"

[ "$fail" -eq 0 ] && echo "ALL OK" || { echo "SOME CHECKS FAILED"; exit 1; }
