import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared-module';
import { WorkflowHistoryDialog } from './workflow-history-dialog';
import { WorkflowTimeline } from './workflow-timeline';

/** A request's history (timeline and the history dialog), shared by the Workflow screens and the onboarding screens. */
@NgModule({
  declarations: [WorkflowTimeline, WorkflowHistoryDialog],
  imports: [SharedModule],
  exports: [WorkflowTimeline, WorkflowHistoryDialog],
})
export class WorkflowHistoryModule {}
