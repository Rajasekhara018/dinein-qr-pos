import { HqChartsModule } from 'hq-shared-lib';
import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared-module';
import { AccessManagementRoutingModule } from './access-management-routing-module';
import { UsersList } from './users-list/users-list';
import { UserDetail } from './user-detail/user-detail';
import { UserApplicationAccess } from './user-detail/user-application-access';
import { RolesList } from './roles-list/roles-list';
import { RoleFormDialog } from './roles-list/role-form-dialog/role-form-dialog';
import { OrganizationsList } from './organizations-list/organizations-list';
import { OrganizationSetupPage } from './organization-setup/organization-setup';
import { ApplicationsList } from './applications-list/applications-list';
import { ClientsList } from './clients-list/clients-list';
import { SecurityPoliciesList } from './security-policies-list/security-policies-list';
import { SecurityPolicyPreview } from './security-policies-list/security-policy-preview';
import { OrganizationApplications } from './organization-applications/organization-applications';
import { OrganizationMembers } from './organization-members/organization-members';
import { OrganizationOperatingModel } from './organization-operating-model/organization-operating-model';
import { RoleAccessPicker } from './role-access/role-access-picker';
import { AdminFormPage } from './admin-form-page/admin-form-page';
import { FormGuide } from './form-guide/form-guide';
import { NotificationTemplatesList } from './notification-templates-list/notification-templates-list';
import { GoLiveGovernanceModule } from '../application-readiness/go-live-governance-module';
import { FranchisesList } from './franchises/franchises-list';
import { MyFranchises } from './franchises/my-franchises';
import { MyEarnings } from './franchises/my-earnings';
import { MyPayouts } from './franchises/my-payouts';
import { WalletStatementScreen } from './wallet-statement/wallet-statement';
import { FranchiseMembersScreen } from './franchises/franchise-members';
import { FranchiseDriversScreen } from './franchises/franchise-drivers';
import { FranchiseHierarchyPage } from './franchises/franchise-hierarchy';
import { AgentKycPage } from './franchise-onboarding/agent-kyc-page';
import { FranchiseAddAgentDialog } from './franchises/franchise-add-agent-dialog';
import { DriverSubscriptionDialog } from './franchises/driver-subscription-dialog';
import { FranchisePlansList } from './franchises/franchise-plans-list';
import { FranchiseForm } from './franchises/franchise-form';
import { LegalProfilePage } from './legal-profile/legal-profile-page';
import { FranchiseOnboardingPage } from './franchise-onboarding/franchise-onboarding-page';
import { FranchiseSetupPage } from './franchise-setup/franchise-setup';
import { FranchisePlanPreview } from './franchises/franchise-plan-preview';
import { FranchiseMemberTree } from './franchises/franchise-member-tree';
import { FranchisePlanForm } from './franchises/franchise-plan-form';
import { FranchiseTypeFees } from './franchises/franchise-type-fees';
import { FranchisePackagesList } from './franchises/franchise-packages-list';
import { FranchisePackageForm } from './franchises/franchise-package-form';
import { FranchiseLinesEditor } from './franchises/franchise-lines-editor';
import { FranchiseReferralEditor } from './franchises/franchise-referral-editor';
import { MyFloat } from './wallet-float/my-float';
import { PartnerFloats } from './wallet-float/partner-floats';
import { FranchiseLocations } from './franchises/franchise-locations';
import { FranchiseDemand } from './franchises/franchise-demand';
import { SetupChecklistScreen } from './setup-checklist/setup-checklist';
import { FranchiseLocationForm } from './franchises/franchise-location-form';
import { LocationPicker } from './franchises/location-picker';
import { LocationImport } from './franchises/location-import';

@NgModule({
  declarations: [
    UsersList,
    UserDetail,
    UserApplicationAccess,
    RolesList,
    RoleFormDialog,
    FormGuide,
    OrganizationsList,
    ApplicationsList,
    ClientsList,
    SecurityPoliciesList,
    NotificationTemplatesList,
    AdminFormPage,
    OrganizationApplications,
    OrganizationMembers,
    OrganizationOperatingModel,
    OrganizationSetupPage,
    RoleAccessPicker,
    FranchisesList,
    MyFranchises,
    MyEarnings,
    MyPayouts,
    WalletStatementScreen,
    FranchiseMembersScreen,
    FranchiseDriversScreen,
    FranchiseHierarchyPage,
    AgentKycPage,
    FranchiseAddAgentDialog,
    DriverSubscriptionDialog,
    FranchisePlansList,
    FranchiseForm,
    LegalProfilePage,
    FranchiseOnboardingPage,
    FranchiseSetupPage,
    FranchisePlanPreview,
    FranchiseMemberTree,
    FranchisePlanForm,
    FranchiseTypeFees,
    FranchisePackagesList,
    FranchisePackageForm,
    FranchiseLinesEditor,
    FranchiseReferralEditor,
    MyFloat,
    PartnerFloats,
    FranchiseLocations,
    FranchiseDemand,
    SetupChecklistScreen,
    FranchiseLocationForm,
    LocationPicker,
    LocationImport,
  ],
  imports: [SharedModule, HqChartsModule, GoLiveGovernanceModule, AccessManagementRoutingModule, SecurityPolicyPreview],
})
export class AccessManagementModule {}
