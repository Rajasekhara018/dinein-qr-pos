import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { permissionGuard } from '../auth/permission-guard';
import { OrgBuilder } from './org-builder/org-builder';
import { OrgChart } from './org-chart/org-chart';
import { OrgImport } from './org-import/org-import';
import { OrgLogins } from './org-logins/org-logins';
import { ORG_PERMISSIONS } from './org-structure.constants';

/** The chart opens for anyone who may list units; the builder only for those who may open a draft. */
const routes: Routes = [
  { path: '', redirectTo: 'chart', pathMatch: 'full' },
  { path: 'chart', component: OrgChart, canActivate: [permissionGuard], data: { permission: ORG_PERMISSIONS.view } },
  { path: 'logins', component: OrgLogins, canActivate: [permissionGuard], data: { permission: ORG_PERMISSIONS.logins } },
  { path: 'import', component: OrgImport, canActivate: [permissionGuard], data: { permission: ORG_PERMISSIONS.import } },
  { path: 'builder', component: OrgBuilder, canActivate: [permissionGuard], data: { permission: ORG_PERMISSIONS.manage } },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class OrganizationStructureRoutingModule {}
