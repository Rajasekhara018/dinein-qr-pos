import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared-module';
import { WorkflowHistoryModule } from '../workflow/history/workflow-history.module';
import { OnboardedUsersRoutingModule } from './onboarded-users-routing-module';
import { OnboardedUsersList } from './onboarded-users-list/onboarded-users-list';
import { OnboardedUserDetail } from './onboarded-user-detail/onboarded-user-detail';
import { RecordNotes } from '../workspace/record-notes/record-notes';
import { DocumentPreviewDialog } from './document-preview-dialog/document-preview-dialog';

@NgModule({
  declarations: [OnboardedUsersList, OnboardedUserDetail, DocumentPreviewDialog],
  imports: [SharedModule, WorkflowHistoryModule, OnboardedUsersRoutingModule, RecordNotes],
})
export class OnboardedUsersModule {}
