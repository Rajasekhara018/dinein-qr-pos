# UX → backend requests

Frontend UX work that would need a backend change to do properly (rather than faked client-side). Append here;
don't implement the backend side as part of a frontend-only phase.

## Phase 4 (admin)

### Dashboard KPI cards: comparison vs. same day last week

The brief asks for KPI cards with a "▲/▼ vs same day last week" comparison. The dashboard's `Dashboard` DTO
(`report.dto.ReportDtos`, served by `AdminReportController`'s dashboard endpoint) currently returns only:

```
date, ordersToday, revenueToday, averageOrderValue, ordersByStatus, activeKitchenOrders, flaggedOrders, recentOrders
```

— no prior-period figure. The reports date-range endpoint's `SalesSummary` has a `daily` time series but that's a
different endpoint (owner-only, arbitrary range) and computing "the same weekday last week" client-side from it
would mean the dashboard silently makes a second, wider report query just to derive one comparison number — fragile
and easy to get wrong (business-day boundaries are IST, see `BusinessTime`). Cleanest fix: add
`ordersSameDayLastWeek`/`revenueSameDayLastWeek` (or a nested `previousPeriod` object) to the `Dashboard` record,
computed server-side the same way `ordersToday`/`revenueToday` are. Not built this phase — see
`features/admin/dashboard/dashboard-page.ts`.

### Dashboard charts: revenue by hour, top 5 items, payment method split

No backend gap for two of the three — `SalesSummary` (reports date-range endpoint) already has `topItems` and
`paymentMethods`/`paymentChannels`, which the dashboard could reuse for "today" without a new endpoint (not wired
this phase — see below). **Revenue by hour** does need a backend addition: neither `Dashboard` nor `SalesSummary`
buckets revenue by hour of day, only by full day (`daily`). A `revenueByHour: List<{hour: int, amount: Money}>` on
the `Dashboard` DTO (scoped to the business day, IST) would be the natural home.

### Staff "revoke devices with last-seen time"

No gap — already fully built end-to-end (`AdminStaffController`, `DeviceTokenService`, `DeviceResponse.lastSeenAt`,
`GET/DELETE /api/v1/admin/devices`, wired into `features/admin/staff/staff-page.ts`). Listed here only so it isn't
re-flagged as a gap in a future pass.
