import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared-module';
import { OrganizationStructureRoutingModule } from './organization-structure-routing-module';
import { OrgBuilder } from './org-builder/org-builder';
import { OrgChartView } from './org-chart-view/org-chart-view';
import { OrgPersonEditor } from './org-person-editor/org-person-editor';
import { OrgPicker } from './org-picker/org-picker';
import { OrgDeptRolesDialog } from './org-dept-roles-dialog/org-dept-roles-dialog';
import { OrgDeptTree } from './org-dept-tree/org-dept-tree';
import { OrgChart } from './org-chart/org-chart';
import { OrgImport } from './org-import/org-import';
import { OrgImportLoaded } from './org-import/org-import-loaded';
import { OrgLogins } from './org-logins/org-logins';
import { OrgSelector } from './org-selector/org-selector';

@NgModule({
  declarations: [OrgChart, OrgBuilder, OrgImport, OrgLogins, OrgPicker, OrgDeptTree, OrgPersonEditor, OrgChartView, OrgSelector, OrgImportLoaded, OrgDeptRolesDialog],
  imports: [SharedModule, OrganizationStructureRoutingModule],
})
export class OrganizationStructureModule {}
