# Re-indexing the search index

Rider_SearchAPI owns the Elasticsearch index. Each service publishes its records to it when they are saved. A record saved
while search or Redis was down is missing from the index until its service **re-publishes** it. A re-index never wipes the index.

## Reindex endpoints

All go through the API gateway with the bearer token of a user who holds the privilege. Each acts on the **signed-in
organization** (the token's `tenantId`); sign in with another `X-Organization-Code` to re-index another organization.

| Service | Start (202) / progress | Models | Privileges (start / progress) |
|---|---|---|---|
| UAM | `POST` / `GET /api/v1/uam/search/reindex` | USER, ROLE, TENANT, APPLICATION, API_CLIENT, SECURITY_POLICY | `UAM_SEARCH_REINDEX_CREATE` / `_READ` |
| DMS | `POST` / `GET /api/v1/dms/search/reindex` | DOCUMENT, then `WEB_DMS_DOCUMENT_TYPE` and `WEB_DMS_LIFECYCLE_DOCUMENT` for the signed-in organization | `DMS_SEARCH_REINDEX_CREATE` / `_READ` |
| NMS | `POST` / `GET /api/v1/nms/search/reindex` | NOTIFICATION_TEMPLATE | `NMS_SEARCH_REINDEX_CREATE` / `_READ` |
| Franchise | `POST` / `GET /api/v1/franchise/search/reindex[?model=]` | every `FRANCHISE_*` model | `FRANCHISE_SEARCH_REINDEX_CREATE` / `_READ` |
| Reports | `POST` / `GET /api/v1/reports/search/reindex` | REPORT, REPORT_CONFIGURATION, REPORT_SYSTEM_CONFIG, REPORT_CUSTOM_FIELD, REPORT_TEMPLATE, REPORT_AUDIT_LOG, REPORT_MERCHANT | `REPORTS_SEARCH_REINDEX_CREATE` / `_READ` |
| Config | `POST` / `GET /api/v1/config/search/reindex` | for the signed-in organization: `WEB_CONFIG_SETTING`, `WEB_CONFIG_SETTING_HISTORY`, `WEB_CONFIG_BANNER`, `WEB_CONFIG_FAQ`, `WEB_CONFIG_APP_VERSION`, `WEB_CONFIG_CITY`, `WEB_CONFIG_VEHICLE_TYPE`, `WEB_CONFIG_VEHICLE_CITY`; then the platform's `WEB_QA_RUN`, `WEB_QA_DOCUMENT`, `WEB_QA_STUDIO_CASE`, `WEB_QA_TEST_SET` | `CONFIG_SEARCH_REINDEX_CREATE` / `_READ` |
| Matrimony | no API: set `app.search.reindex-on-start=true` and restart | MATRIMONY_PROFILE | none |

New privileges are granted to SUPER_ADMIN only until an admin grants them to a role.

```bash
GATEWAY=https://<api-gateway-host>
TOKEN=<access token from POST $GATEWAY/api/v1/uam/identity/auth/login>

curl -s -X POST "$GATEWAY/api/v1/reports/search/reindex" -H "Authorization: Bearer $TOKEN"   # 202 started, 409 already running
curl -s        "$GATEWAY/api/v1/reports/search/reindex" -H "Authorization: Bearer $TOKEN"   # state, times, counts per model
```

## What a run does

- **UAM, DMS, NMS, Reports** read the organization's records page by page and publish each to the Redis stream SearchAPI
  consumes. One run at a time; a second `POST` while one runs returns 409 with its progress. A record that cannot be
  published is counted as `failed` and the run carries on. **Nothing is deleted from the index**, so a record deleted in the
  database stays searchable until its own delete event is published.
- **DMS web models** (`WEB_DMS_DOCUMENT_TYPE`, `WEB_DMS_LIFECYCLE_DOCUMENT`) are published after the documents, for the signed-in
  organization only, through the projection ingress as one leased generation per model: `BEGIN`, the rows in batches of
  100 (each carrying the public DTO the list API returns, plus its catalogue slots and its position in `number5`),
  `COMPLETE` (removes rows that were not re-sent), then `TENANT_COMPLETE` (sets the per-organization ready flag; `COMPLETE`
  alone does not). Nothing is completed after a failed batch, so a failed run deletes nothing. The progress shows a
  `counts` entry per model; `state: FAILED` with an `error` such as `WEB_DMS_LIFECYCLE_DOCUMENT: another projection run is
  in progress ...` names the model that did not finish (a lease held by another run, 300 s; retry after a few minutes).
  These two models are refreshed **only** by this run: creating or approving a document type, or uploading, deleting or
  scanning a document, does not update them, so run it again after bulk changes. Needs `RIDER_INTERNAL_SERVICE_TOKEN` on DMS
  (`railcab.dms.search.projection.*`).
- **Config web models** are published through the projection ingress like the DMS ones (`BEGIN`, batches of 100, `COMPLETE`, then
  the ready step), one model after the other, the organization's first. The run carries the caller's own session onto its
  thread, so every list it reads (settings, banners, cities ...) is what `GET` returns for that caller: the organization's own
  rows plus the shared ones, never another organization's. Two kinds of model:
  - **Organization models** (the eight `WEB_CONFIG_*`) end with `TENANT_COMPLETE`, which sets the ready flag for the signed-in
    organization only: **run it once per organization**. `WEB_CONFIG_SETTING_HISTORY` is published key after key,
    `WEB_CONFIG_VEHICLE_CITY` type after type, and `WEB_CONFIG_VEHICLE_TYPE` once for the "every city" view (`text12 = __ALL__`,
    what the screen asks for by default) and once per city of the organization (key = code and city), because the list API
    answers a different array per city.
  - **Platform models** (the four `WEB_QA_*`, scope `GLOBAL`) are the same for every organization. They are published under the
    platform identity (`default`) whichever organization asked, and end with `MODEL_COMPLETE`, which sets the one global ready
    flag. Every run republishes them (idempotent, one shared lease); the QA run list is published in full, newest first.
  None of these models has a save-time hook, so they are only as fresh as the last run: run it again after bulk changes. Progress
  is kept per organization (another organization running at the same time only sees that a run is under way, and gets a 409).
  Needs `RIDER_INTERNAL_SERVICE_TOKEN` on Config, which it already requires (`railcab.config.search.projection.*`); a run reports
  `FAILED` with "service token missing" while it is blank.
- **Franchise** queues each model and publishes through SearchAPI's projection ingress, whose `COMPLETE` step also removes
  documents that were not re-sent, so it does clean up.
- **Reports** reads through the organization-scoped Mongo template, by key (not offset), in pages of
  `reports.search.reindex-batch-size` (default 200, at most 1,000). It exists only when the report service runs on MongoDB
  (`railcab.reports.persistence.type=mongo`, the default); with the `jpa` profile the endpoint is not registered.
- Progress is visible only to the organization that started the run.

## Not covered by any reindex API

- The other `WEB_*` models (Admin, Wallet, Food, Train, IMS, NMS broadcast and event models and the `WEB_MATRIMONY_*`
  ones) are accepted through the projection ingress (`POST /api/v1/search/internal/projections`: `BEGIN`, batches of
  at most 100 records each carrying its public DTO, `COMPLETE`). No code in the service folders calls that endpoint, and
  there is no reindex API for them. Some of them (`WEB_WALLET_*`, `WEB_ADMIN_TICKET`) are present in a running index, so
  something outside what was searched is publishing those; find it before building another publisher.
- RIDE, DRIVER, VEHICLE, SUPPORT_TICKET, REFUND_REQUEST, GROUP and VENDOR have no producer in the monorepo.

`Rider_SearchSync` covers USER, ROLE, TENANT, APPLICATION, API_CLIENT, SECURITY_POLICY, DOCUMENT, NOTIFICATION_TEMPLATE and
MATRIMONY_PROFILE from the databases directly (`--mode=FULL`, and `--mode=RECONCILE --confirm` to delete documents whose row
is gone). See its README.

## Checking the result

```json
{"size":0,"aggs":{"unique_models":{"terms":{"field":"model","size":200}}}}
```

Every model of the services above should appear under `unique_models` once its run is `COMPLETED`.
