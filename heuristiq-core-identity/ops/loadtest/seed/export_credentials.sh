#!/usr/bin/env bash
# TEST ONLY. Exports a sample of the seeded test users to the CSV that the k6 scripts read
# (columns user_id,username,phone). The password is NOT written: k6 gets it from -e PASSWORD=...
#
#   UAM_TEST_DB_URL=postgresql://railcab_uam@localhost:5432/uam_loadtest \
#   ./export_credentials.sh [sample_size] [output_file]
#
# sample_size   default 100000 (use at least MAX_VUS, so no two VUs share a refresh-token chain)
# output_file   default ../data/credentials.csv (git-ignored)
# TENANT_ID     env, default loadtest (must match the seed)
# TOTAL_USERS   env, default = number of seeded users, counted with an index-only count
#
# The sample is spread over the whole id range with a stride that is coprime to the total, so the 10M-row
# indexes are exercised at random places rather than at one end. It reads the rows back from the database, so a
# user that was not seeded cannot end up in the file.
set -euo pipefail

: "${UAM_TEST_DB_URL:?Set UAM_TEST_DB_URL, e.g. postgresql://user@host:5432/uam_loadtest (password via PGPASSWORD or ~/.pgpass)}"

SAMPLE="${1:-100000}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="${2:-$HERE/../data/credentials.csv}"
TENANT_ID="${TENANT_ID:-loadtest}"

case "$SAMPLE" in (*[!0-9]*|'') echo "sample_size must be a positive integer" >&2; exit 2;; esac
case "$TENANT_ID" in (loadtest*) ;; (*) echo "TENANT_ID must start with 'loadtest'" >&2; exit 2;; esac
case "$TENANT_ID" in (*[!a-z0-9_-]*) echo "TENANT_ID has characters outside a-z 0-9 _ -" >&2; exit 2;; esac

if [ -z "${TOTAL_USERS:-}" ]; then
  TOTAL_USERS="$(psql "$UAM_TEST_DB_URL" -X -At -c "select count(*) from tenant_memberships where tenant_id = '$TENANT_ID'")"
fi
case "$TOTAL_USERS" in (*[!0-9]*|''|0) echo "No seeded users found for tenant $TENANT_ID. Run seed_loadtest.sql first." >&2; exit 3;; esac
if [ "$SAMPLE" -gt "$TOTAL_USERS" ]; then SAMPLE="$TOTAL_USERS"; fi

# A stride coprime to TOTAL_USERS makes (1 + (g * stride) mod total) distinct for g < total.
STRIDE=""
for p in 7919 7907 7901 7883 7877; do
  if [ $(( TOTAL_USERS % p )) -ne 0 ]; then STRIDE="$p"; break; fi
done
[ -n "$STRIDE" ] || { echo "Could not pick a stride for TOTAL_USERS=$TOTAL_USERS" >&2; exit 4; }

mkdir -p "$(dirname "$OUT")"
QUERY="select u.id as user_id, u.user_name as username, u.phone_e164 as phone
from uam_users u
join (select 'lt-u-' || lpad((1 + ((g::bigint * $STRIDE) % $TOTAL_USERS))::text, 8, '0') as id
      from generate_series(0, $SAMPLE - 1) g) s on s.id = u.id
where u.tenant_id = '$TENANT_ID'"

psql "$UAM_TEST_DB_URL" -X -q -c "COPY ($QUERY) TO STDOUT WITH (FORMAT csv, HEADER true)" > "$OUT"
ROWS=$(( $(wc -l < "$OUT") - 1 ))
echo "Wrote $ROWS credentials to $OUT (stride $STRIDE over $TOTAL_USERS users)"
echo "Run k6 with: -e PASSWORD=<the test password> -e CREDENTIALS_FILE=$OUT"
