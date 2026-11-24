# Organization metadata in the search index

Organization-owned documents store tenantId as a keyword in Elasticsearch and as tenantId_s in Solr.
ownerKey remains the partition field used by existing organization filters. For these models, both values identify
the same organization. getall returns tenantId alongside the model and model key.

UAM publishes the persisted tenant ID for users, roles, API clients and security policies. Search API also supports
older events that only carry the organization in ownerKey. A legacy organization-owned record with neither value
belongs to default; indexing now writes that ownership explicitly. Conflicting nonempty values are rejected.

A user partition (such as a matrimony profile's ownerKey) is not treated as an organization. Such a document only
gets organization metadata when its producer supplies an explicit tenant ID. SearchSync uses the job's declared
ownership when deciding whether its source partition is an organization.

Both Elasticsearch writers apply the keyword mapping to existing indexes before writing the new field. Deploy the
updated Search API and UAM, plus SearchSync where batch synchronization is used.

Existing indexed records need reindexing to acquire this metadata. An authorized operator can re-publish UAM roles
with POST /api/v1/uam/search/reindex?model=ROLE; the same endpoint supports USER, API_CLIENT and SECURITY_POLICY.
The reindex reads ownership from the source database. It cannot recover historical organization assignments that
were never stored; unowned source records remain the default organization's.
