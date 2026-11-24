import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { WORKFLOW_HISTORY_DIALOG_WIDTH } from './workflow-history.constants';
import { WorkflowHistoryDialog, WorkflowHistoryDialogData } from './workflow-history-dialog';

/** Opens a request's history in the history dialog (the screens that show requests use it instead of leaving for Workflow). */
@Injectable({ providedIn: 'root' })
export class WorkflowHistoryService {
  private readonly dialog = inject(MatDialog);

  open(data: WorkflowHistoryDialogData): void {
    if (!data.instanceId) return;
    this.dialog.open<WorkflowHistoryDialog, WorkflowHistoryDialogData>(WorkflowHistoryDialog, {
      data,
      width: WORKFLOW_HISTORY_DIALOG_WIDTH,
      maxWidth: '96vw',
      autoFocus: false,
      restoreFocus: true,
      panelClass: 'app-dialog-panel',
    });
  }
}
