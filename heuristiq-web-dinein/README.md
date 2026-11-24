# DineIn frontend

Angular 21 app hosting three lazily loaded apps: guest ordering (`/menu`), kitchen display (`/kitchen`) and admin
(`/admin`). The Spring Boot backend in `../backend` is the API contract.

## Commands

| Command | What it does |
| --- | --- |
| `npm start` | Dev server on :4200; `proxy.conf.json` proxies `/api` and `/ws` to `localhost:8080` (no CORS needed) |
| `npx ng build` | Production build (with service worker) into `dist/dinein` |
| `npm run test:ci` | Vitest + TestBed unit tests (`ng test --watch=false`) |
| `npm run lint` | angular-eslint (TS + templates, incl. accessibility rules) |
| `npm run format` | Prettier |
| `npm run e2e:install` / `npm run e2e` | Playwright (starts or reuses `ng serve`; the backend is mocked with `page.route`) |

## Conventions (follow these in kitchen/admin)

- **NgModules, not standalone.** Every component has `standalone: false` and
  `changeDetection: ChangeDetectionStrategy.OnPush`, and is declared in its feature module.
  Files: `x-module.ts`, `x-routing-module.ts` (`RouterModule.forChild`), and per component `name.ts` + `name.html`
  (`templateUrl`), plus `name.css` (`styleUrl`) only when it has real rules. No inline `template:`/`styles:`.
  `ng generate component` already uses these defaults (see `angular.json`).
- Feature modules import `SharedModule` (UI kit + `CommonModule`, `ReactiveFormsModule`, `NgOptimizedImage`, CDK
  `A11yModule`/`DialogModule`/`DragDropModule`/`LayoutModule`/`OverlayModule`). `AppModule` does NOT import it, so it
  stays out of the initial bundle.
- Signals for state (`signal`/`computed`/`effect`, `input()`/`output()`/`model()`), built-in control flow, `inject()`,
  functional guards/interceptors. Services with state are `providedIn: 'root'`.
- Tailwind v4 tokens live in `src/styles.css` (`bg-brand`, `text-brand-contrast`, `text-brand-ink`, `bg-surface`,
  `text-ink(-muted|-subtle)`, `border-line`, `rounded-card`, `min-h-touch`, …). Dark mode = `.dark` class on an
  ancestor. If a component `.css` uses `@apply`, add `@reference "<relative path>/styles.css";` at its top.
- **Unit tests of module-declared components:** import the declaring feature module
  (`TestBed.configureTestingModule({ imports: [GuestModule], providers: [...] })`) and do NOT re-declare the
  component. The AOT test build strips NgModule scope metadata, so re-declaring resets the component's template
  scope ("'app-x' is not a known element").

## Core services (see `src/app/core`)

- API: `PublicApi`, `AuthApi`, `KitchenApi`, `AdminMenuApi`, `AdminTablesApi`, `AdminOrdersApi`, `AdminReportsApi`,
  `AdminSettingsApi`; DTO types in `core/api/models.ts`; errors are `ApiError` (`code`, `message`, `details`,
  `traceId`, `status`).
- Auth: `AuthStore` (admin, in-memory JWT + silent refresh), `DeviceAuthStore` (kitchen device token),
  guards `adminAuthGuard`, `passwordChangeGuard`, `ownerGuard`, `adminGuestOnlyGuard`, `kitchenAuthGuard`,
  paths in `auth-paths.ts`.
- `RealtimeService` (STOMP), `CheckoutService` (payments), `ToastService`, `SheetService`, `BreakpointService`.
