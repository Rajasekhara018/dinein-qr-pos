import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { UsersList } from './users-list/users-list';
import { UserDetail } from './user-detail/user-detail';
import { RolesList } from './roles-list/roles-list';
import { RoleFormDialog } from './roles-list/role-form-dialog/role-form-dialog';
import { OrganizationsList } from './organizations-list/organizations-list';
import { ApplicationsList } from './applications-list/applications-list';
import { ClientsList } from './clients-list/clients-list';
import { SecurityPoliciesList } from './security-policies-list/security-policies-list';
import { NotificationTemplatesList } from './notification-templates-list/notification-templates-list';
import { pendingChangesGuard } from '../shared/guards/pending-changes.guard';
import { permissionGuard } from '../auth/permission-guard';
import { ACCESS_MANAGEMENT_PERMISSIONS } from './access-management.permissions';
import { AdminFormPage } from './admin-form-page/admin-form-page';
import { GoLiveGovernancePage } from '../application-readiness/go-live-governance-page/go-live-governance-page';
import { FranchisesList } from './franchises/franchises-list';
import { MyFranchises } from './franchises/my-franchises';
import { MyEarnings } from './franchises/my-earnings';
import { MyPayouts } from './franchises/my-payouts';
import { WalletStatementScreen } from './wallet-statement/wallet-statement';
import {
  ACCOUNT_STATEMENT_PERMISSION, ACCOUNT_STATEMENT_SCREEN_KEY, MY_STATEMENT_PERMISSION, MY_STATEMENT_SCREEN_KEY,
} from './wallet-statement/wallet-statement.constants';
import {
  MY_EARNINGS_PERMISSION, MY_EARNINGS_SCREEN_KEY, MY_PAYOUTS_PERMISSION, MY_PAYOUTS_SCREEN_KEY,
} from './franchises/franchise-earnings.constants';
import { FranchiseMembersScreen } from './franchises/franchise-members';
import { FranchiseDriversScreen } from './franchises/franchise-drivers';
import { FRANCHISE_DRIVERS_PERMISSION, FRANCHISE_DRIVERS_ROUTE, FRANCHISE_DRIVERS_SCREEN_KEY, MY_DRIVERS_ROUTE } from './franchises/franchise-drivers.constants';
import { myDriversRedirectGuard } from './franchises/my-drivers-redirect.guard';
import { FRANCHISE_MEMBERS_PERMISSION, FRANCHISE_MEMBERS_SCREEN_KEY } from './franchises/franchise-members.constants';
import { FranchisePlansList } from './franchises/franchise-plans-list';
import { OrganizationSetupPage } from './organization-setup/organization-setup';
import {
  ORGANIZATION_SETUP_DEFAULT_STEP, ORGANIZATION_SETUP_PERMISSION, ORGANIZATION_SETUP_ROUTE, ORGANIZATION_SETUP_SCREEN_KEY,
} from './organization-setup/organization-setup.constants';
import { FranchiseSetupPage } from './franchise-setup/franchise-setup';
import {
  FRANCHISE_SETUP_DEFAULT_STEP, FRANCHISE_SETUP_PERMISSION, FRANCHISE_SETUP_ROUTE, FRANCHISE_SETUP_SCREEN_KEY, MY_FRANCHISE_SETUP_PERMISSION,
  MY_FRANCHISE_SETUP_SCREEN_KEY,
} from './franchise-setup/franchise-setup.constants';
import { FranchiseHierarchyPage } from './franchises/franchise-hierarchy';
import { AgentKycPage } from './franchise-onboarding/agent-kyc-page';
import { AGENT_KYC_ROUTE, AGENT_KYC_SCREEN_KEY, AGENT_ROUTE, MY_AGENT_KYC_SCREEN_KEY } from './franchise-onboarding/agent-kyc.constants';
import { FRANCHISE_HIERARCHY_SCREEN_KEY, HIERARCHY_ROUTE } from './franchises/franchise-hierarchy.constants';
import { ONBOARDING_ROUTE } from './franchise-onboarding/franchise-onboarding.constants';
import { LEGAL_PROFILE_ROUTE } from './legal-profile/legal-profile.constants';
import { FranchisePlanForm } from './franchises/franchise-plan-form';
import { FranchisePackagesList } from './franchises/franchise-packages-list';
import { FranchisePackageForm } from './franchises/franchise-package-form';
import { FRANCHISE_PACKAGES_PERMISSION, FRANCHISE_PACKAGES_SCREEN_KEY, FRANCHISE_PACKAGE_FORM_SCREEN_KEY } from './franchises/franchise-packages.constants';
import { MyFloat } from './wallet-float/my-float';
import { PartnerFloats } from './wallet-float/partner-floats';
import { MY_FLOAT_PERMISSION, MY_FLOAT_SCREEN_KEY, PARTNER_FLOATS_PERMISSION, PARTNER_FLOATS_SCREEN_KEY } from './wallet-float/wallet-float.constants';
import { FranchiseLocations } from './franchises/franchise-locations';
import { FranchiseDemand } from './franchises/franchise-demand';
import { FRANCHISE_DEMAND_PERMISSION, FRANCHISE_DEMAND_ROUTE, FRANCHISE_DEMAND_SCREEN_KEY } from './franchises/franchise-demand.constants';
import { SetupChecklistScreen } from './setup-checklist/setup-checklist';
import { SETUP_CHECKLIST_PERMISSION, SETUP_CHECKLIST_ROUTE, SETUP_CHECKLIST_SCREEN_KEY } from './setup-checklist/setup-checklist.constants';
import { FranchiseLocationForm } from './franchises/franchise-location-form';
import { FRANCHISE_LOCATIONS_PERMISSION, FRANCHISE_LOCATIONS_SCREEN_KEY, FRANCHISE_LOCATION_FORM_SCREEN_KEY } from './franchises/franchise-locations.constants';
import {
  FRANCHISES_PERMISSION, FRANCHISES_SCREEN_KEY, FRANCHISE_FORM_SCREEN_KEY, FRANCHISE_PLANS_PERMISSION, FRANCHISE_PLANS_SCREEN_KEY, FRANCHISE_PLAN_FORM_SCREEN_KEY, MY_FRANCHISES_PERMISSION,
  MY_FRANCHISES_SCREEN_KEY, FRANCHISE_ADMIN_MEMBERS_SCREEN_KEY,
} from './franchises/franchises.constants';
import { GO_LIVE_GOVERNANCE_SCREEN_KEY } from '../application-readiness/go-live-governance.constants';
import {
  ADMIN_FORM_EDIT_SEGMENT, ADMIN_FORM_NEW_SEGMENT, ADMIN_FORM_PAGES, ADMIN_FORM_PAGE_DATA_KEY,
} from './admin-form-page/admin-form-page.constants';

// Every config-driven entity is created and edited on its own full page (AdminFormPage), not in a pop-up.
// `<entity>/new` must stay ABOVE any `:id` detail route -- the router matches top-to-bottom, so otherwise
// `new` would be swallowed as an `:id`. The same goes for `roles/new` and `roles/:id/edit` below.
const ORGANIZATIONS_PATH = 'organizations';
const formRoutes: Routes = ADMIN_FORM_PAGES.flatMap((page) => [
  { path: `${page.path}/${ADMIN_FORM_NEW_SEGMENT}`, component: AdminFormPage, canDeactivate: [pendingChangesGuard], data: { [ADMIN_FORM_PAGE_DATA_KEY]: page.path } },
  // An organization is edited in its setup (below), not on a page of its own: its old edit URL redirects there.
  ...(page.path === ORGANIZATIONS_PATH ? [] : [
    { path: `${page.path}/:id/${ADMIN_FORM_EDIT_SEGMENT}`, component: AdminFormPage, canDeactivate: [pendingChangesGuard], data: { [ADMIN_FORM_PAGE_DATA_KEY]: page.path } },
  ]),
]);

const routes: Routes = [
  { path: '', redirectTo: 'users', pathMatch: 'full' },
  ...formRoutes,
  { path: 'users', component: UsersList },
  { path: 'users/:id', component: UserDetail },
  { path: 'roles', component: RolesList },
  { path: 'roles/new', component: RoleFormDialog, canDeactivate: [pendingChangesGuard] },
  { path: 'roles/:id/edit', component: RoleFormDialog, canDeactivate: [pendingChangesGuard] },
  { path: 'organizations', component: OrganizationsList },
  // An organization's guided setup: details, legal and tax, operating model, applications and members as steps, opened from the
  // organizations list. The pages that used to hold them (edit, legal-profile) redirect to their step so bookmarks keep working.
  { path: `organizations/:id/${ORGANIZATION_SETUP_ROUTE}`, redirectTo: `organizations/:id/${ORGANIZATION_SETUP_ROUTE}/${ORGANIZATION_SETUP_DEFAULT_STEP}`, pathMatch: 'full' },
  { path: `organizations/:id/${ORGANIZATION_SETUP_ROUTE}/:step`, component: OrganizationSetupPage, canDeactivate: [pendingChangesGuard],
    data: { permission: ORGANIZATION_SETUP_PERMISSION, screenKey: ORGANIZATION_SETUP_SCREEN_KEY, subject: 'organization' } },
  { path: `organizations/:id/${ADMIN_FORM_EDIT_SEGMENT}`, redirectTo: `organizations/:id/${ORGANIZATION_SETUP_ROUTE}/details`, pathMatch: 'full' },
  { path: `organizations/:id/${LEGAL_PROFILE_ROUTE}`, redirectTo: `organizations/:id/${ORGANIZATION_SETUP_ROUTE}/legal`, pathMatch: 'full' },
  { path: 'applications', component: ApplicationsList },
  // Go-live governance of one application (last override, alerts); opened from the applications list, same screen permission as the list.
  { path: 'applications/:code/access', component: GoLiveGovernancePage,
    data: { permission: 'view_applications', screenKey: GO_LIVE_GOVERNANCE_SCREEN_KEY } },
  // Franchises: administrators open and price them; owners run theirs and onboard the agents. Each has its own permission.
  { path: 'franchises', component: FranchisesList, data: { permission: FRANCHISES_PERMISSION, screenKey: FRANCHISES_SCREEN_KEY } },
  // Opening a franchise is the first step of its setup screen; the other steps open once it exists.
  { path: 'franchises/new', component: FranchiseSetupPage, canDeactivate: [pendingChangesGuard],
    data: { permission: FRANCHISES_PERMISSION, screenKey: FRANCHISE_FORM_SCREEN_KEY, subject: 'franchise' } },
  // A franchise's guided setup: applications and terms, legal and tax details, KYC, payout bank account, agreement and activation as
  // steps, for any franchise (administrators) or one's own (owners). The pages that used to hold them (edit, legal-profile,
  // onboarding) redirect to their step so bookmarks keep working.
  { path: `franchises/:id/${FRANCHISE_SETUP_ROUTE}`, redirectTo: `franchises/:id/${FRANCHISE_SETUP_ROUTE}/${FRANCHISE_SETUP_DEFAULT_STEP}`, pathMatch: 'full' },
  { path: `franchises/:id/${FRANCHISE_SETUP_ROUTE}/:step`, component: FranchiseSetupPage, canDeactivate: [pendingChangesGuard],
    data: { permission: FRANCHISE_SETUP_PERMISSION, screenKey: FRANCHISE_SETUP_SCREEN_KEY, subject: 'franchise' } },
  { path: `my-franchises/:id/${FRANCHISE_SETUP_ROUTE}`, redirectTo: `my-franchises/:id/${FRANCHISE_SETUP_ROUTE}/legal`, pathMatch: 'full' },
  { path: `my-franchises/:id/${FRANCHISE_SETUP_ROUTE}/:step`, component: FranchiseSetupPage, canDeactivate: [pendingChangesGuard],
    data: { permission: MY_FRANCHISE_SETUP_PERMISSION, screenKey: MY_FRANCHISE_SETUP_SCREEN_KEY, subject: 'myFranchise' } },
  { path: 'franchises/:id/edit', redirectTo: `franchises/:id/${FRANCHISE_SETUP_ROUTE}/details`, pathMatch: 'full' },
  { path: `franchises/:id/${LEGAL_PROFILE_ROUTE}`, redirectTo: `franchises/:id/${FRANCHISE_SETUP_ROUTE}/legal`, pathMatch: 'full' },
  { path: `my-franchises/:id/${LEGAL_PROFILE_ROUTE}`, redirectTo: `my-franchises/:id/${FRANCHISE_SETUP_ROUTE}/legal`, pathMatch: 'full' },
  { path: `franchises/:id/${ONBOARDING_ROUTE}`, redirectTo: `franchises/:id/${FRANCHISE_SETUP_ROUTE}/kyc`, pathMatch: 'full' },
  { path: `my-franchises/:id/${ONBOARDING_ROUTE}`, redirectTo: `my-franchises/:id/${FRANCHISE_SETUP_ROUTE}/kyc`, pathMatch: 'full' },
  // An administrator reading any franchise's members (read only); the screen is the owner's members screen in admin mode.
  { path: `franchises/:id/${HIERARCHY_ROUTE}`, component: FranchiseHierarchyPage,
    data: { permission: FRANCHISES_PERMISSION, screenKey: FRANCHISE_HIERARCHY_SCREEN_KEY } },
  { path: `franchises/:id/${AGENT_ROUTE}/:memberId/${AGENT_KYC_ROUTE}`, component: AgentKycPage,
    data: { permission: FRANCHISES_PERMISSION, screenKey: AGENT_KYC_SCREEN_KEY, subject: 'franchise' } },
  { path: `my-franchises/:id/${AGENT_ROUTE}/:memberId/${AGENT_KYC_ROUTE}`, component: AgentKycPage,
    data: { permission: MY_FRANCHISES_PERMISSION, screenKey: MY_AGENT_KYC_SCREEN_KEY, subject: 'myFranchise' } },
  { path: 'franchises/:id/members', component: FranchiseMembersScreen, data: { permission: FRANCHISES_PERMISSION, screenKey: FRANCHISE_ADMIN_MEMBERS_SCREEN_KEY, admin: true } },
  // The owner's drivers of one of their franchises ("My drivers"), and the way into onboarding one.
  { path: `franchises/:franchiseId/${FRANCHISE_DRIVERS_ROUTE}`, component: FranchiseDriversScreen,
    data: { permission: FRANCHISE_DRIVERS_PERMISSION, screenKey: FRANCHISE_DRIVERS_SCREEN_KEY } },
  // The "My drivers" menu entry: never activates, it redirects to the only franchise's drivers or to My franchises.
  { path: MY_DRIVERS_ROUTE, children: [], canActivate: [myDriversRedirectGuard], data: { permission: FRANCHISE_DRIVERS_PERMISSION } },
  { path: 'franchise-packages', component: FranchisePackagesList, data: { permission: FRANCHISE_PACKAGES_PERMISSION, screenKey: FRANCHISE_PACKAGES_SCREEN_KEY } },
  { path: 'franchise-packages/new', component: FranchisePackageForm, canDeactivate: [pendingChangesGuard], data: { permission: FRANCHISE_PACKAGES_PERMISSION, screenKey: FRANCHISE_PACKAGE_FORM_SCREEN_KEY } },
  { path: 'franchise-packages/:id/edit', component: FranchisePackageForm, canDeactivate: [pendingChangesGuard], data: { permission: FRANCHISE_PACKAGES_PERMISSION, screenKey: FRANCHISE_PACKAGE_FORM_SCREEN_KEY } },
  { path: 'my-float', component: MyFloat, data: { permission: MY_FLOAT_PERMISSION, screenKey: MY_FLOAT_SCREEN_KEY } },
  { path: 'partner-floats', component: PartnerFloats, data: { permission: PARTNER_FLOATS_PERMISSION, screenKey: PARTNER_FLOATS_SCREEN_KEY } },
  { path: 'franchise-plans', component: FranchisePlansList, data: { permission: FRANCHISE_PLANS_PERMISSION, screenKey: FRANCHISE_PLANS_SCREEN_KEY } },
  { path: 'franchise-plans/:id/edit', component: FranchisePlanForm, canDeactivate: [pendingChangesGuard], data: { permission: FRANCHISE_PLANS_PERMISSION, screenKey: FRANCHISE_PLAN_FORM_SCREEN_KEY } },
  { path: 'franchise-locations', component: FranchiseLocations, data: { permission: FRANCHISE_LOCATIONS_PERMISSION, screenKey: FRANCHISE_LOCATIONS_SCREEN_KEY } },
  { path: FRANCHISE_DEMAND_ROUTE, component: FranchiseDemand, data: { permission: FRANCHISE_DEMAND_PERMISSION, screenKey: FRANCHISE_DEMAND_SCREEN_KEY } },
  { path: SETUP_CHECKLIST_ROUTE, component: SetupChecklistScreen, data: { permission: SETUP_CHECKLIST_PERMISSION, screenKey: SETUP_CHECKLIST_SCREEN_KEY } },
  { path: 'franchise-locations/new', component: FranchiseLocationForm, canDeactivate: [pendingChangesGuard], data: { permission: FRANCHISE_LOCATIONS_PERMISSION, screenKey: FRANCHISE_LOCATION_FORM_SCREEN_KEY } },
  { path: 'franchise-locations/:id/edit', component: FranchiseLocationForm, canDeactivate: [pendingChangesGuard], data: { permission: FRANCHISE_LOCATIONS_PERMISSION, screenKey: FRANCHISE_LOCATION_FORM_SCREEN_KEY } },
  { path: 'my-franchises', component: MyFranchises, data: { permission: MY_FRANCHISES_PERMISSION, screenKey: MY_FRANCHISES_SCREEN_KEY } },
  { path: 'franchise-members', component: FranchiseMembersScreen, data: { permission: FRANCHISE_MEMBERS_PERMISSION, screenKey: FRANCHISE_MEMBERS_SCREEN_KEY } },
  { path: 'my-earnings', component: MyEarnings, data: { permission: MY_EARNINGS_PERMISSION, screenKey: MY_EARNINGS_SCREEN_KEY } },
  { path: 'my-payouts', component: MyPayouts, data: { permission: MY_PAYOUTS_PERMISSION, screenKey: MY_PAYOUTS_SCREEN_KEY } },
  // Wallet statements: the signed-in person's own, and an administrator's view of any account of the organization.
  { path: 'my-statement', component: WalletStatementScreen,
    data: { permission: MY_STATEMENT_PERMISSION, screenKey: MY_STATEMENT_SCREEN_KEY, scope: 'self' } },
  { path: 'account-statement', component: WalletStatementScreen,
    data: { permission: ACCOUNT_STATEMENT_PERMISSION, screenKey: ACCOUNT_STATEMENT_SCREEN_KEY, scope: 'admin' } },
  { path: 'clients', component: ClientsList },
  { path: 'security-policies', component: SecurityPoliciesList },
  { path: 'notification-templates', component: NotificationTemplatesList },
];

/**
 * Each area opens only for users holding a privilege behind its screen permission (see the privilege map in hq-shared-lib).
 * A route that names its own permission keeps it instead of its area's. A redirect gets no guard: the router refuses
 * redirectTo with canActivate, and the route it lands on is guarded anyway. A route's own guards (the My drivers redirect) run
 * after the permission check.
 */
const guardedRoutes: Routes = routes.map((route) => {
  if (route.redirectTo) return route;
  const own = route.data?.['permission'] as string | undefined;
  const permission = own ?? ACCESS_MANAGEMENT_PERMISSIONS[(route.path ?? '').split('/')[0]];
  return permission
    ? { ...route, canActivate: [permissionGuard, ...(route.canActivate ?? [])], data: { ...route.data, permission } }
    : route;
});

@NgModule({
  imports: [RouterModule.forChild(guardedRoutes)],
  exports: [RouterModule],
})
export class AccessManagementRoutingModule {}
