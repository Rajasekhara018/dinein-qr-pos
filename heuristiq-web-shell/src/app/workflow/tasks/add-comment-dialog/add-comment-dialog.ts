import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { WorkflowTask } from '../../workflow.models';

export interface AddCommentDialogData {
  task: WorkflowTask;
}

/** Writes a note on an open task, kept in the request's history. Closes with the text, or null. */
@Component({
  selector: 'app-add-comment-dialog',
  standalone: false,
  templateUrl: './add-comment-dialog.html',
  styleUrl: './add-comment-dialog.scss',
})
export class AddCommentDialog {
  text = '';

  constructor(
    private ref: MatDialogRef<AddCommentDialog, string | null>,
    @Inject(MAT_DIALOG_DATA) public data: AddCommentDialogData,
  ) {}

  submit(): void {
    const value = this.text.trim();
    if (value) this.ref.close(value);
  }

  cancel(): void {
    this.ref.close(null);
  }
}
