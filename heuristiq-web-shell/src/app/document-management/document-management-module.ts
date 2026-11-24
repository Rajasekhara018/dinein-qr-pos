import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

import { DocumentManagementRoutingModule } from './document-management-routing-module';
import { MaterialModule } from '../shared/material-module';
import { SharedModule } from '../shared/shared-module';

import { DocumentTypeList } from './document-type-list/document-type-list';
import { DocumentTypeFormComponent } from './document-type-form/document-type-form';
import { LifecycleList } from './lifecycle/lifecycle-list/lifecycle-list';
import { LifecycleDetail } from './lifecycle/lifecycle-detail/lifecycle-detail';
import { LifecyclePreviewDialog } from './lifecycle/lifecycle-preview-dialog/lifecycle-preview-dialog';

@NgModule({
  declarations: [DocumentTypeList, DocumentTypeFormComponent, LifecycleList, LifecycleDetail, LifecyclePreviewDialog],
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    MaterialModule,
    SharedModule,
    DocumentManagementRoutingModule,
  ],
})
export class DocumentManagementModule {}
