# Sample: running the search API on Apache Solr

The search service talks to its database through `SearchIndexPort`. Elasticsearch is the default; `provider/solr` is a
second implementation, a sample of how another engine plugs in. Nothing above the port changes: the same
`POST /api/v1/search` and `POST /api/v1/search/elastic/{model}/getall` work on either.

## Try it

1. Start Solr and create the collection (Docker):

   ```bash
   docker run -d --name solr -p 8983:8983 solr:9 solr-precreate railcab-search
   ```

   `solr-precreate` uses the default configset, whose dynamic fields already cover what the provider writes:
   `*_s` (string), `*_t` (text), `*_d` (double), `*_l` (long), `*_dt` (date). `id` is the unique key.

2. Start the search service with:

   ```properties
   railcab.search.provider=solr
   railcab.search.solr.base-url=http://localhost:8983/solr
   railcab.search.solr.collection=railcab-search
   ```

   (`SEARCH_PROVIDER=solr` as an environment variable.) Index events from the other services go through the same Redis
   stream and listener as before; only the store behind the port differs.

3. Check a query:

   ```bash
   curl -s -X POST localhost:9116/api/v1/search/elastic/application/getall \
     -H 'Content-Type: application/json' -H 'Authorization: Bearer <token>' \
     -d '{"requestType":"LIST","pageNo":0,"pageSize":"25","sort":{"field1":"date1","directionField1":"DESC"}}'
   ```

## How a document is stored

Before indexing franchise list payloads, add a stored-only field to the collection. Other models do not use it:

```bash
curl -X POST http://localhost:8983/solr/railcab-search/schema \
  -H 'Content-Type: application/json' \
  -d '{"add-field":{"name":"franchisePayload","type":"string","indexed":false,"stored":true}}'
```

This field retains the public list DTO as JSON; queries still use the ordinary EAV slots.

| Slot | Solr field(s) |
|---|---|
| `id` | `id` (composite `model:modelKey`) |
| `model`, `modelKey`, `ownerKey` | `model_s`, `modelKey_s`, `ownerKey_s` |
| `text1..15` | `text1_s` (exact: EQ / NE / IN / sort / LIKE) and `text1_t` (analysed copy) |
| `amount1..5` | `amount1_d` |
| `number1..5` | `number1_l` |
| `date1..5`, `datetime1..5`, `createdAt`, `updatedAt` | `date1_dt` ... (ISO-8601) |

## Operators

Same meaning as the Elasticsearch provider: `EQ`/`NE` exact, `LIKE` substring, `IN` a comma-separated list,
`GE/GT/LE/LT` ranges on amounts, numbers and dates; nested groups become parentheses. `LIKE` is a case-sensitive
wildcard on the exact-value field, which Solr evaluates slowly on large collections: for heavy substring search add a
lower-casing field type and query that instead (the mapping lives in `SolrDocumentMapper` / `SolrQueryTranslator`).

## Writing another provider

Implement `SearchIndexPort` (index, findOne, delete, search), gate it with
`@ConditionalOnProperty(prefix = "railcab.search", name = "provider", havingValue = "<name>")`, keep the engine's own
storage class and query translation inside its package, and add translator and provider tests like the ones here.
