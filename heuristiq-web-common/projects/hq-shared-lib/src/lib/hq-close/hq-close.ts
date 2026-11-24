import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { HqCloseDialogData } from '../hq-common.models';

/** Yes/No confirmation or single "Close" acknowledgement (tou-close). Closes with true on Yes. */
@Component({
  selector: 'hq-close',
  standalone: false,
  templateUrl: './hq-close.html',
  styleUrl: './hq-close.scss',
})
export class HqClose {
  constructor(
    public dialogRef: MatDialogRef<HqClose, boolean>,
    @Inject(MAT_DIALOG_DATA) public data: HqCloseDialogData,
  ) {}

  answer(yes: boolean): void {
    this.dialogRef.close(yes);
  }
}
