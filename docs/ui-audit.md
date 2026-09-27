# UI audit — phase 1 (tokens, typography, core components)

Scope: `frontend/src/app` as of the start of this phase. File:line references are to the pre-phase-1 code
(guest/kitchen/waiter/admin under `features/**`, which this phase does not modify) unless noted.

## 1. Colour & token inconsistency

- **24 template files bypass the semantic tokens** and reach for raw Tailwind palette classes
  (`text-red-900`, `bg-green-100`, `bg-amber-100`, `bg-violet-700`, `bg-stone-200`, …) instead of `text-danger`,
  `bg-success`, etc. Examples: `features/admin/dashboard/dashboard-page.html`, `features/admin/staff/staff-page.html`,
  `features/guest/cart/cart-page.html`, `features/waiter/order/waiter-order-page.html`,
  `features/kitchen/login/kitchen-login.html`. Each of these hand-picks a shade rather than a token, so a future
  rebrand or contrast fix has to be applied file-by-file. `shared/components/order-status-badge.ts` was the worst
  offender — 8 statuses, each with its own hand-tuned `bg-*-100 dark:bg-*-900` pair not derived from any token; this
  phase replaced it with `shared/order-status.ts` (a single status→tone map feeding the new `--success/--warning/
  --danger/--info` `-bg`/`-fg` token pairs), but the 24 other files are unchanged (they're under `features/**`,
  out of scope this phase) and are candidates for phase 2–4 clean-up.
- **Semantic colour aliases were flat, not `-bg/-fg/-border` triples.** Before this phase, `--color-success` etc.
  were single hex values used inconsistently as either text or background color by different call sites, with no
  guaranteed contrast between a "soft fill" and its text. This phase added `-bg`/`-fg`/`-border` for all four
  semantic colours (light and dark), with contrast verified in `tokens.spec.ts`. The old flat aliases are kept
  (mapped to the new `-fg` value) so existing `text-success`/`bg-danger` etc. in `features/**` keep working
  unchanged.
- **No brand or neutral 50–950 ramp existed** — only a handful of named surface/ink tokens. Components that need a
  specific step (e.g. a chart, a lighter/darker brand tint than `brand-soft`/`brand-strong`) had no way to get one
  without a hard-coded hex. Added both ramps, brand's derived via `color-mix()` from the runtime `--brand` so a
  restaurant's brand colour still produces a full ramp.

## 2. Component duplication

- **Order status wording/colour was defined once** (`order-status-badge.ts`) but read by 8 different pages via a
  re-exported helper (`dashboard-page.ts:8`, `orders-page.ts:9`, and 6 templates). That's good — no duplication —
  but it had only one wording per status, used identically for guests (`order-status-page.html`,
  `my-orders-page.html`) and staff (kitchen/waiter/admin). "Awaiting payment" and "Payment failed" are staff
  register, not guest-friendly copy. Consolidated into `shared/order-status.ts` with an `audience` parameter;
  `OrderStatusBadge` defaults to `audience: 'staff'` (unchanged wording/behaviour for every existing call site, all
  of which are unaudited `features/**` templates), so guest-specific wording is available for phase 2 to opt into
  without another rewrite.
- **Toast/inline-notification UI is reimplemented per app**, not shared: `features/guest/cart/cart-page.html`,
  `features/guest/menu/cart-bar.html` and `features/waiter/shell/waiter-shell.html` each have their own
  fixed-position banner markup, and `admin/data/notifications.store.ts` / `admin/shell/notification-bell.html` vs.
  `waiter/shell/waiter-bell.html` are near-duplicate bell+dropdown implementations (unread count, mark-read, list).
  No shared `Toast` service/component exists yet. **Deferred to phase 2+** (see "Deferred" below) — building it
  now without being able to touch `features/**` call sites would only add an unused component.
- **Two loading-spinner patterns**: `app-spinner` (shared, `role="status"`) vs. a raw `[class.animate-spin]` on an
  inline SVG in `features/guest/orders/my-orders-page.html:20`. Low priority, single occurrence.
- **Admin has its own `ToggleSwitch`, `PageHeader`, `StatCard`, `ConfirmDialog`, `FieldError`, `ImageUpload`,
  `OrderList`** (`features/admin/shared/*`) that are generic enough to belong in `shared/`. `ToggleSwitch` in
  particular is a complete, accessible `Switch` (role="switch", CVA, 44px target) that duplicates what the owner's
  brief asks for as a shared component. **Not moved in this phase** — moving it means updating every admin import
  site, which is a `features/admin/**` edit and out of scope; flagging for phase 4 (admin), when the owner's brief
  revisits admin directly.

## 3. Missing states

- `Price`/`inr` pipe already had `from`/struck-through-`original` support before this phase (no gap) — the owner's
  brief's "PriceText" requirement is already met by `shared/components/price.ts`; no new component was needed.
- `DataTable` did not exist. Admin list pages (`orders-page`, `items-page`, `staff-page`, `order-list.html`) each
  hand-roll their own `<table>` with no shared sorting/pagination/density/mobile-card behaviour. Built in this
  phase (`shared/components/data-table.ts`); **not yet wired into `features/admin/**`** (out of scope this phase —
  phase 4 admin work should migrate the existing hand-rolled tables to it).
- No shared `Chip`/`FilterChip` existed; menu/category filters in `features/guest/menu` and `features/admin/catalog`
  presumably use ad-hoc buttons (not audited in detail, out of scope). Built `shared/components/chip.ts`.
- Loading/empty/error states themselves are in good shape already: `EmptyState`, `ErrorState`, `Skeleton` exist and
  are used consistently (`grep` found no bare `data.length === 0` renders without a matching component in a spot
  check of guest/admin list pages).

## 4. Accessibility

- Icon-only buttons: a spot check (`grep` for `appIconButton` without a nearby `aria-label`) found **zero misses** —
  every icon button already carries an `aria-label`. This is a strength to preserve, not a gap.
- Colour-only status indicators: `VegMarker` already pairs colour with shape (square+dot/triangle/ring) — verified
  correct, no gap. `OrderStatusBadge` is colour+text (never colour alone) — no gap. The one thing this phase found
  and fixed: the semantic tone colours behind those badges were not contrast-checked against their own background
  fill before (see `tokens.spec.ts`, all now ≥4.5:1 in both themes).
- Focus handling: a global `:focus-visible` ring (`--app-focus`, 3px, 2px offset) is applied consistently via
  `@layer base` — no gap found. `BottomSheet` already focuses the first heading on open.
- `aria-live` is used in 14 template files already (dashboard counts, notification bells, login errors, cart/menu
  live regions) — reasonable coverage. Not deeply audited beyond presence/absence (a full audit of each region's
  `aria-live` politeness level is deferred; out of scope for a code-only read without a screen reader).
- Kitchen dark mode: verified `kitchen-shell.ts:21,32-33` toggles `.dark` on `<html>` (not just the shell), restores
  the previous state on destroy, and is the *only* place besides `.dark`'s definition in `styles.css` that toggles
  it — dark theme is applied consistently, not just partially. The dark-theme token set was previously a *subset*
  of the light one (no dark equivalents for the new `-bg/-fg/-border` semantic triples existed because those
  triples themselves didn't exist yet) — this phase adds a complete dark set alongside the light one, checked by
  the same contrast test.

## 5. Typography & spacing

- No self-hosted webfont and no responsive type scale existed before this phase — every heading/body size was an
  ad-hoc Tailwind text-size utility (`text-lg`, `text-xl`, …) with no named scale, so two "h2"s in different files
  could be different sizes. Added `display/h1/h2/h3/body-lg/body/body-sm/caption/overline` via `@theme` `--text-*`
  tokens (fluid `clamp()`), matching the owner's brief.
- Radius tokens existed only as three component-shaped names (`control/card/sheet`) with no generic `sm/md/lg/xl`
  scale. Added the generic scale and kept the component names as aliases onto it (`--radius-control: var(--radius-
  md)` etc.) so no existing class name (`rounded-card`, `rounded-control`) changes meaning.
- z-index was ad hoc (no evidence of a scale; the two places needing layering — sheet pane, dialog backdrop — used
  literal `z-*` Tailwind numbers). Added a `--z-index-*` scale (`sticky/dropdown/overlay/dialog/toast`) and moved
  the two existing usages onto it.
- `prefers-reduced-motion` was already handled globally (`@layer base`, `styles.css` — pre-existing, no gap).
  Motion durations (120/200/300ms) are now named tokens (`--duration-fast/base/slow`) rather than only being
  present as literal numbers inside the three existing `@theme` keyframe/animate declarations.

## 6. Icon library

- Added `@lucide/angular` (MIT licence), the actively maintained successor to `lucide-angular` (which npm flags as
  deprecated in favour of `@lucide/angular` — installed and then swapped once that warning surfaced). Confirmed
  Angular 21 / NgModule compatible: **not** via an `LucideAngularModule.forRoot()`/`.pick()` NgModule wrapper (that
  API doesn't exist in this major version — `@lucide/angular` v1 ships each icon as its own standalone Angular
  component, e.g. `svg[lucideChefHat]`). Standalone components can be imported directly into an NgModule's
  `imports` array (Angular has supported this since standalone components shipped), so this is fully compatible
  with the project's NgModule-only rule for code *we* write — we are not authoring a standalone component, we're
  consuming a third-party one, exactly like `NgOptimizedImage` or the CDK modules `SharedModule` already
  re-exports. See `shared/icons.ts` for the picked subset and `shared-module.ts` for how it's imported/exported.

## 7. Font

- Self-hosted Inter via `@fontsource/inter` (OFL-1.1 — confirmed in `node_modules/@fontsource/inter/package.json`
  and its bundled `LICENSE`, copied to `public/fonts/inter/LICENSE.txt`). Only the 4 static weights actually used by
  the type scale (400/500/600/700), Latin subset, woff2 only, `font-display: swap` — **not** the variable font or
  the `latin-ext`/other-script files `@fontsource/inter` also ships, to keep the added weight down. Total added:
  ~96 KB across 4 files (see report for the exact figure), which only loads on pages that render text — i.e.
  always, but once, cached for a year like every other static asset Nginx serves.

## Deferred to a later phase (with why)

- **Moving `features/admin/shared/*` generic components into `shared/`** (ToggleSwitch→Switch, PageHeader,
  StatCard, ConfirmDialog→Dialog, FieldError, ImageUpload) — touching every admin import site is a
  `features/admin/**` change, explicitly out of scope this phase. Flagged for phase 4.
- **Toast service** — building the component without also replacing the three existing ad-hoc implementations
  (guest cart bar, waiter shell, admin/waiter bells) leaves two systems running in parallel with no benefit until
  those call sites move; deferred to whichever phase owns each of those apps (2 for guest, 5 cross-cutting for a
  shared service, or done per-app in 2/3/4).
- **Input/Textarea/Select/Search, Checkbox/Radio/SegmentedControl, Dialog & BottomSheet unified behind one
  `ResponsiveOverlay` service, Drawer, Tabs, Breadcrumbs, Pagination, Tooltip, DropdownMenu, Avatar, ImageUploader
  consolidation** — not built this phase. `BottomSheet` already exists and does the sheet-vs-dialog responsive
  swap itself (via `BreakpointService`), just not as a reusable *service* wrapping arbitrary content the way the
  brief's `ResponsiveOverlay` describes; unifying it with a new Dialog component is a larger design decision (does
  every existing `SheetService.open(...)` call site need to change signature?) better made once phase 2 shows which
  content actually needs it, rather than speculatively. Given the scope of everything else in this phase (tokens,
  contrast test, font, icon library, DataTable, order-status consolidation, dev route) and the explicit priority the
  brief itself gives DataTable ("take the time to do it properly"), these were the components cut to keep what
  shipped fully finished rather than everything half-done.
- **Wiring `DataTable` into any actual admin list** — `features/admin/**` is out of scope this phase; the component
  is built, tested and shown in `/design-system` but not yet adopted anywhere. Phase 4's job.
- **A full manual accessibility pass** (screen reader run-through, `aria-live` politeness review, tab-order check
  per page) — this audit is a code read, not a live assistive-technology test; flagged as a gap in the audit itself
  rather than deferred silently.
