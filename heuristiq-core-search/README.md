# Rider Search API

Copied into `bestride-rider-app` from `2026_RailCab_SearchAPI` (untouched, no README existed
there). Elasticsearch-backed search service. Port 9007, matching `Rider_ApiGateway`'s existing
`/api/search/**` route default exactly — no port fix needed, no config drift.

Not currently consumed by any of the 5 rider microservices (Pricing/Location/Dispatch/Ride/
Payment) or by `Rider_BookingsAPI`'s own address-search endpoint (which calls an external
Photon/OSRM maps provider directly, per `Rider_LocationService`'s `/location/geocode`) — wiring
rider-app address/place search through this service instead is a possible follow-up, not done
in this pass.

## Report models (Rider_ReportService)

Rider_ReportService keeps no Elasticsearch of its own. What it saves is published to this service's index through the Redis stream
(`railcab.search.stream.name`, fields `operation`, `model`, `modelKey`, `payload`), and its screens read here with
`POST /api/v1/search/elastic/{model}/getall`. The models and the slot of every field are written in `SearchModel`:

| Model (URL) | What it is |
|---|---|
| `report` | a generated report (status, format, product, times) |
| `report-configuration` | a product's report configuration |
| `report-system-config` | a product's system configuration (its keys, tokens and connection details are never indexed) |
| `report-custom-field` | a product's custom-field configuration |
| `report-template` | a report template |
| `report-audit-log` | the report service's audit log (request and response bodies are never indexed) |
| `report-merchant` | the merchant directory (e-mail and mobile number are never indexed) |

All of them belong to one organization (`ownerKey`) and are scoped like the other organization data: a caller only sees its own
organization's records. The index holds the searchable fields only; the full record is read from the report service.
