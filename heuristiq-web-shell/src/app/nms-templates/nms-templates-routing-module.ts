import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { TemplateLanding } from './template-landing/template-landing';
import { TemplateList } from './template-list/template-list';
import { TemplateFormComponent } from './template-form/template-form';
import { TemplateEvents } from './template-events/template-events';
import { pendingChangesGuard } from '../shared/guards/pending-changes.guard';
import { permissionGuard } from '../auth/permission-guard';

const routes: Routes = [
  { path: '',       component: TemplateLanding, canActivate: [permissionGuard], data: { permission: 'view_nms_templates' } },
  { path: 'events', component: TemplateEvents, canActivate: [permissionGuard], data: { permission: 'view_nms_events' }, canDeactivate: [pendingChangesGuard] },
  { path: 'list',   component: TemplateList, canActivate: [permissionGuard], data: { permission: 'view_nms_templates' } },
  { path: 'create', component: TemplateFormComponent, canActivate: [permissionGuard], data: { permission: 'view_nms_templates' }, canDeactivate: [pendingChangesGuard] },
  { path: 'edit/:id', component: TemplateFormComponent, canActivate: [permissionGuard], data: { permission: 'view_nms_templates' }, canDeactivate: [pendingChangesGuard] },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class NmsTemplatesRoutingModule {}
