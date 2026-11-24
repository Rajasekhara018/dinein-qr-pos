# Rider DMS (Document Management System)

Copied into `bestride-rider-app` from `2026_RailCab_DMS` (untouched, no README existed there —
see `docs/production-operations.md` for the original ops notes this copy inherited).

Port (9004) and context-path (`/api/v1`) already match `Rider_ApiGateway`'s existing
`/api/dms/**` route exactly, so no config drift to fix here.

Handles document upload/storage/retention (soft-delete purge, legal-hold/retention checks) —
this is the natural home for driver KYC document uploads (`driver_documents` table), which the
Rider App MVP's 5 extracted microservices (Pricing/Location/Dispatch/Ride/Payment) explicitly
left unowned. Wiring Rider_DispatchService's driver-onboarding flow to call this service instead
of leaving `driver_documents` stranded in the monolith is a natural next step.

`railcab.time.legacy-zone` (env `RAILCAB_TIME_LEGACY_ZONE`, IANA id, default `Asia/Kolkata`): the zone in which this service wrote its zone-less timestamps; informational for now, a startup log line and the gauge `railcab.time.legacy_zone.mismatch` warn when the JVM zone's offset differs (see docs/TIMEZONE_BACKEND_PLAN.md, Stage A.1).

## Download links for platform services

`POST /api/v1/dms/internal/documents/{id}/link` (service credential only, never through the gateway) gives another service a
download link to a document: a pre-signed storage URL, or the DMS public-token path when the provider has none, with the
document's content type and size. The organization is required in `X-Tenant-Id` and must own the document; a document of
another organization (or without one) answers 404 like a missing one. Issuing a link is audited (`DOWNLOAD_URL_ISSUED`).
Rider_CommerceService uses it to show product pictures to shoppers.

## Search index

DMS publishes to Rider_SearchAPI in two ways.

- **Save-time events:** a saved document is queued on the Redis search stream (`DOCUMENT` model).
- **Web models, by reindex only:** `POST /api/v1/dms/search/reindex` (`DMS_SEARCH_REINDEX_CREATE`; progress `GET`, `DMS_SEARCH_REINDEX_READ`)
  re-publishes the documents, then the signed-in organization's `WEB_DMS_DOCUMENT_TYPE` (same rows as
  `GET /api/v1/dms/document-types`) and `WEB_DMS_LIFECYCLE_DOCUMENT` (same rows as `GET /api/v1/dms/lifecycle/documents`, deleted
  included) through Rider_SearchAPI's `POST /api/v1/search/internal/projections` (BEGIN, batches of 100, COMPLETE, TENANT_COMPLETE).
  The Document types and Lifecycle screens read these through the Search API `getall`, and fail closed until this has run once for the
  organization. Nothing refreshes them afterwards except another run.
- **Calls:** Rider_SearchAPI (service token, `Rider_ResilientClient`); it is not called by the Search API. Settings:
  `railcab.dms.search.projection.base-url|discovery-url|service-token|timeout-ms` (`SEARCH_API_BASE_URL`, `SEARCH_API_DISCOVERY_URL`,
  `RIDER_INTERNAL_SERVICE_TOKEN`). The slots it fills are pinned to Rider_SearchAPI's `web-search-models.json` by
  `ProjectionCatalogContractTest`.
