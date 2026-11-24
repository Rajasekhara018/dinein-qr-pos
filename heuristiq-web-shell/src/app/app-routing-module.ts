import { NgModule } from '@angular/core';
import { SYSTEM_COVERAGE_PERMISSION, SYSTEM_COVERAGE_SCREEN_KEY } from './system-coverage/system-coverage.constants';
import { SYSTEM_QA_PERMISSION, SYSTEM_QA_SCREEN_KEY } from './system-qa/system-qa.constants';
import { FOOD_PERMISSION, FOOD_REMOTE_EXPOSED_MODULE, FOOD_REMOTE_NAME } from './food-admin/food-admin.constants';
import { foodScreenGuard } from './food-admin/food-screen-guard';
import { foodPartnerScreenGuard } from './food-admin/food-partner-screen-guard';
import { INVENTORY_PERMISSION, INVENTORY_REMOTE_EXPOSED_MODULE, INVENTORY_REMOTE_NAME } from './inventory/inventory.constants';
import { inventoryScreenGuard } from './inventory/inventory-screen-guard';
import { WORKSPACE_PERMISSION, WORKSPACE_SCREEN_KEY } from './workspace/workspace.constants';
import { PUBLIC_TRACK_ROUTE_PATH, PUBLIC_TRACK_SCREEN_KEY } from './public-track/public-track.constants';
import { RouterModule, Routes } from '@angular/router';
import { loadRemoteModule } from '@angular-architects/native-federation-v4';
import { Layout } from './layout/layout';
import { authGuard } from './auth/auth-guard';
import { permissionGuard } from './auth/permission-guard';
import { DEVELOPER_DOCS_PERMISSION, SERVICE_REGISTRY_PERMISSION } from './shared/developer-tools.constants';
import { privilegesReadyGuard } from './auth/privileges-ready-guard';

const routes: Routes = [
  {
    path: 'driver-onboarding',
    loadChildren: () => loadRemoteModule('railcab-admin', './PublicDriverOnboarding')
      .then(m => m.PublicDriverOnboardingModule),
  },
  { path: '', redirectTo: 'today', pathMatch: 'full' },
  {
    path: 'auth',
    loadChildren: () => import('./auth/auth-module').then(m => m.AuthModule),
  },
  {
    path: 'vendor',
    loadChildren: () => import('./vendor-portal/vendor-portal-module').then(m => m.VendorPortalModule),
  },
  {
    // Public live-trip page opened from a link the rider shared (/track#<token>): no auth guard, like 'vendor'. It uses
    // no session (its reads bypass the auth interceptor), is not reported to the audit trail and the idle sign-out
    // never interrupts it (see shared/public-routes.constants.ts).
    path: PUBLIC_TRACK_ROUTE_PATH,
    data: { screenKey: PUBLIC_TRACK_SCREEN_KEY },
    loadComponent: () => import('./public-track/public-track').then(m => m.PublicTrack),
  },
  {
    path: '',
    component: Layout,
    // Every screen under the layout needs a signed-in user: a visitor without a session is sent to /auth/login.
    // canActivateChild re-checks on each in-app navigation, so a session cleared in another tab or by the idle
    // timeout cannot keep browsing.
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    children: [
      { path: 'chart-gallery', canActivate: [permissionGuard], data: { permission: DEVELOPER_DOCS_PERMISSION }, loadComponent: () => import('./chart-gallery/hq-chart-gallery').then(m => m.ChartGalleryPage) },
      { path: 'today', loadChildren: () => import('./today/today-module').then(m => m.TodayModule) },
      { path: 'home', loadChildren: () => import('./home/home-module').then(m => m.HomeModule) },
      {
        path: 'change-password',
        loadComponent: () => import('./auth/change-password/change-password').then(m => m.ChangePassword),
        canActivate: [authGuard],
      },
      { path: 'notifications', loadChildren: () => import('./notifications/notifications-module').then(m => m.NotificationsModule) },
      {
        path: 'admin',
        canActivate: [privilegesReadyGuard],
        loadChildren: () =>
          loadRemoteModule('railcab-admin', './AdminRoutes').then(m => m.AdminRemoteEntryModule),
      },
      {
        // Matrimony operations portal (matrimonial-web-ops), a native-federation remote like 'admin' above.
        // Exposes only content routes; this Layout is its chrome and Rider_Web's session is its session.
        path: 'matrimony',
        canActivate: [privilegesReadyGuard, permissionGuard],
        data: { permission: 'view_matrimony' },
        loadChildren: () =>
          loadRemoteModule('railcab-matrimony', './MatrimonyRoutes').then(m => m.MatrimonyRemoteEntryModule),
      },
      {
        // Wallet portal (realboom-wallet-web), a native-federation remote like 'matrimony' above. Each screen carries its
        // own permission (agent or admin wallet), so the mount itself only waits for the privileges.
        path: 'wallet',
        canActivate: [privilegesReadyGuard],
        loadChildren: () =>
          loadRemoteModule('railcab-wallet', './WalletRoutes').then(m => m.WalletRemoteEntryModule),
      },
      {
        // Accounting portal (realboom-accounting-web, Rider_AccountingService), a native-federation remote like 'wallet' above.
        // Each screen carries its own permission (chart of accounts, trial balance), so the mount itself only waits for the privileges.
        path: 'accounting',
        canActivate: [privilegesReadyGuard],
        loadChildren: () =>
          loadRemoteModule('railcab-accounting', './AccountingRoutes').then(m => m.AccountingRemoteEntryModule),
      },
      {
        // Reports portal (realboom-reports-web), a native-federation remote like 'matrimony' above. The mount needs any one
        // reports privilege; each screen then checks its own permission key.
        path: 'reports',
        canActivate: [privilegesReadyGuard, permissionGuard],
        data: { permission: 'view_reports' },
        loadChildren: () =>
          loadRemoteModule('railcab-reports', './ReportsRoutes').then(m => m.ReportsRemoteEntryModule),
      },
      {
        // Food delivery admin (food-delivery-platform/admin-web), a native-federation remote like 'inventory' below. Hosted, it
        // shows only its screens (this Layout and sidebar are its chrome) and uses the portal's token. The mount needs any one Food
        // privilege; its routes carry no permission keys, so foodScreenGuard checks each screen's key. No screenKey here: each
        // screen reports its own (food.<screen>, from its URL).
        path: 'food',
        canActivate: [privilegesReadyGuard, permissionGuard],
        canActivateChild: [foodScreenGuard],
        data: { permission: FOOD_PERMISSION },
        loadChildren: () =>
          loadRemoteModule(FOOD_REMOTE_NAME, FOOD_REMOTE_EXPOSED_MODULE).then(m => m.FoodRemoteEntryModule),
      },
      {
        // The restaurant-partner portal of the same remote. No mount permission: its join screen (applying to become a partner)
        // is open to any signed-in user; foodPartnerScreenGuard checks the partner key on every other screen.
        path: 'food-partner',
        canActivate: [privilegesReadyGuard],
        canActivateChild: [foodPartnerScreenGuard],
        loadChildren: () =>
          loadRemoteModule(FOOD_REMOTE_NAME, FOOD_REMOTE_EXPOSED_MODULE).then(m => m.FoodPartnerRemoteEntryModule),
      },
      {
        // Inventory portal (Rider_IMSWeb, Rider_IMS), a native-federation remote like 'reports' above. The mount needs any one
        // inventory privilege; its routes carry no permission keys of their own, so inventoryScreenGuard checks each screen's key.
        path: 'inventory',
        canActivate: [privilegesReadyGuard, permissionGuard],
        canActivateChild: [inventoryScreenGuard],
        data: { permission: INVENTORY_PERMISSION },
        loadChildren: () =>
          loadRemoteModule(INVENTORY_REMOTE_NAME, INVENTORY_REMOTE_EXPOSED_MODULE).then(m => m.InventoryRemoteEntryModule),
      },
      { path: 'onboarding-forms', canActivate: [permissionGuard], data: { permission: 'manage_onboarding' }, loadChildren: () => import('./onboarding-forms/onboarding-forms-module').then(m => m.OnboardingFormsModule) },
      { path: 'onboarding-reviews', canActivate: [permissionGuard], data: { permission: 'manage_onboarding' }, loadChildren: () => import('./onboarding-reviews/onboarding-reviews-module').then(m => m.OnboardingReviewsModule) },
      { path: 'workspace', canActivate: [permissionGuard], data: { permission: WORKSPACE_PERMISSION, screenKey: WORKSPACE_SCREEN_KEY }, loadChildren: () => import('./workspace/workspace-module').then(m => m.WorkspaceModule) },
      { path: 'workflow', loadChildren: () => import('./workflow/workflow-module').then(m => m.WorkflowModule) },
      { path: 'system/coverage', canActivate: [permissionGuard], data: { permission: SYSTEM_COVERAGE_PERMISSION, screenKey: SYSTEM_COVERAGE_SCREEN_KEY }, loadChildren: () => import('./system-coverage/system-coverage-module').then(m => m.SystemCoverageModule) },
      { path: 'system/qa', canActivate: [permissionGuard], data: { permission: SYSTEM_QA_PERMISSION, screenKey: SYSTEM_QA_SCREEN_KEY }, loadChildren: () => import('./system-qa/system-qa-module').then(m => m.SystemQaModule) },
      // QA Automation: its own menu section; every screen below checks its own permission and carries its own screen key.
      { path: 'qa', loadChildren: () => import('./qa-dashboard/qa-dashboard-module').then(m => m.QaDashboardModule) },
      { path: 'service-registry', canActivate: [permissionGuard], data: { permission: SERVICE_REGISTRY_PERMISSION }, loadChildren: () => import('./service-registry/service-registry-module').then(m => m.ServiceRegistryModule) },
      { path: 'onboarded-users', canActivate: [permissionGuard], data: { permission: 'manage_onboarding' }, loadChildren: () => import('./onboarded-users/onboarded-users-module').then(m => m.OnboardedUsersModule) },
      { path: 'org-structure', loadChildren: () => import('./organization-structure/organization-structure-module').then(m => m.OrganizationStructureModule) },
      { path: 'access-management', loadChildren: () => import('./access-management/access-management-module').then(m => m.AccessManagementModule) },
      { path: 'document-management', loadChildren: () => import('./document-management/document-management-module').then(m => m.DocumentManagementModule) },
      { path: 'nms-templates', loadChildren: () => import('./nms-templates/nms-templates-module').then(m => m.NmsTemplatesModule) },
      { path: 'profile', loadChildren: () => import('./profile/profile-module').then(m => m.ProfileModule) },
      { path: 'api-docs', canActivate: [permissionGuard], data: { permission: DEVELOPER_DOCS_PERMISSION }, loadChildren: () => import('./api-docs/api-docs-module').then(m => m.ApiDocsModule) },
      { path: 'payments', loadChildren: () => import('./payments/payments-module').then(m => m.PaymentsModule) },
      // Placeholder sidebar/header nav entry routes to Home until a real booking screen exists.
      { path: 'my-bookings', loadChildren: () => import('./home/home-module').then(m => m.HomeModule) },
    ],
  },
  { path: '**', redirectTo: 'today' },
];

@NgModule({
  imports: [RouterModule.forRoot(routes, { onSameUrlNavigation: 'reload' })],
  exports: [RouterModule],
})
export class AppRoutingModule {}
