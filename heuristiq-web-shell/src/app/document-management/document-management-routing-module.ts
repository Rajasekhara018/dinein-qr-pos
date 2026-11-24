import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { permissionGuard } from '../auth/permission-guard';
import { pendingChangesGuard } from '../shared/guards/pending-changes.guard';
import { DocumentTypeFormComponent } from './document-type-form/document-type-form';
import { DocumentTypeList } from './document-type-list/document-type-list';
import { DOCUMENT_TYPES_PERMISSION } from './document-types.constants';
import { LIFECYCLE_PERMISSION } from './lifecycle/lifecycle.constants';
import { LifecycleDetail } from './lifecycle/lifecycle-detail/lifecycle-detail';
import { LifecycleList } from './lifecycle/lifecycle-list/lifecycle-list';

const guarded = { canActivate: [permissionGuard], data: { permission: DOCUMENT_TYPES_PERMISSION } };

const lifecycleGuarded = { canActivate: [permissionGuard], data: { permission: LIFECYCLE_PERMISSION } };

// New Document Management screens (reject reasons, review queue ...) are added here as sibling routes.
const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'document-types' },
  { path: 'document-types', component: DocumentTypeList, ...guarded },
  { path: 'integration-directory', loadComponent: () => import('./integration-directory/integration-directory').then(m => m.IntegrationDirectoryPage), ...guarded },
  { path: 'document-types/new', component: DocumentTypeFormComponent, ...guarded, canDeactivate: [pendingChangesGuard] },
  { path: 'document-types/:id/edit', component: DocumentTypeFormComponent, ...guarded, canDeactivate: [pendingChangesGuard] },
  // Read-only troubleshooting screens.
  { path: 'lifecycle', component: LifecycleList, ...lifecycleGuarded },
  { path: 'lifecycle/:id', component: LifecycleDetail, ...lifecycleGuarded },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class DocumentManagementRoutingModule {}
