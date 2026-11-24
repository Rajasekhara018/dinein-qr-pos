# Shared-first privilege provisioning

UAM retains the complete, versioned `privilege.json` as its immutable definition
catalog. Loading definitions does not automatically insert all of them into the
database. A fresh installation with no registered business applications stores only
the **459 SHARED privileges**. The default organization still starts with no business
application subscriptions.

Creating an application through the Applications API registers/links it first, then
upserts only its matching privilege-code prefix. Each permission keeps its global
numeric ID, readable code, name and API rule. Business permissions get the real
application code in `Permission.applicationCode`; shared permissions retain the
existing `UAM` classification. Readable codes and backend service are also recorded
in permission attributes, along with catalog version 2.

| Application code | Prefix | Business privileges imported |
| --- | --- | ---: |
| BESTRIDE_RIDER_APP | RIDE | 259 |
| TRAIN_BOOKING_APP | TRAIN | 75 |
| PASUPU_KUMKUMA_MATRIMONY_APP | MATRIMONY | 92 |
| FOOD_DELIVER_APP | FOOD | 16 |
| FRANCHISE_MANAGEMENT_APP | FRANCHISE | 110 |
| CENTRALISED_WALLET_APP | WALLET | 88 |
| BEST_PURCHASE_APP | IMS | 4 |

Train, Franchise and Best Purchase are selectable application catalogue entries. Best Purchase
(formerly E-commerce, merged with Inventory Management) imports the inventory privileges (IMS_*).
For example, a fresh installation followed by Ride creation has **718** permission
rows: 459 shared plus 259 ride permissions, including Ride's AdminAPI operations.

## Roles and access

Startup SUPER_ADMIN grants cover only shared and already registered applications.
New structural DRIVER/CUSTOMER/AGENT roles receive only defaults whose definitions
have been provisioned. On the first successful import of an application, its matching
structural defaults are added without removing custom grants. Subsequent imports
preserve administrators' later changes. Organization subscriptions, role access and
user application grants remain separate controls; importing definitions does not
give every user access to the application.

The gateway continues to load all 1,105 definitions so unprovisioned business paths
remain protected. Roles cannot satisfy those numeric privileges until corresponding
permission rows are provisioned. The existing role-cache TTL still applies after
role changes; no gateway catalog reload is needed.

## Idempotency and interrupted imports

Application attributes track `privilegeCatalogPrefix`, `privilegeCatalogStatus`
(`PENDING` or `COMPLETE`) and `privilegeCatalogVersion`. A definition is upserted by
permission name, so repeated imports or another organization registering the same
product do not create duplicate permission records.

If provisioning fails after registration, the application/link remain registered
with a pending import. Saving that application again retries the import; restarting
UAM also reconciles every registered application's definitions. Completion is marked
only after permission writes and default-role grants succeed. No cross-database
transaction is assumed, so operations should inspect pending status after a failed
creation request rather than retrying creation and treating an existing-row conflict
as successful provisioning.

Every startup re-upserts shared definitions and those for existing application rows,
which also backfills version 2 IDs/attributes. A missing or malformed catalog fails
startup instead of silently leaving permissions unseeded. Catalog IDs are globally
allocated: a database containing only shared entries intentionally has gaps for
business permissions that have not been imported. IDs are never reassigned on import.

This changes fresh-install behavior and future imports. Existing permission rows,
custom grants and application data are not deleted. Removing an application does
not automatically delete historical permissions. Existing installations that need
cleanup require a separately reviewed migration. The coordinated numeric-ID rollout
in `privileges/ID-MIGRATION.md` still applies to upgrades from version 1.
