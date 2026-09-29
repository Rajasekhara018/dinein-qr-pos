import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { TableResponse } from '../../../core/api/models';

export interface MoveTableDialogData {
  table: TableResponse;
  otherTables: TableResponse[];
}

/**
 * Picks where this table's open order goes. An empty, unreserved destination is a plain move; an already-occupied
 * one merges the two bills together onto it instead — the dialog just picks the table, the caller decides which
 * API call that implies (see TablesPage.moveOrMerge).
 */
@Component({
  selector: 'app-move-table-dialog',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './move-table-dialog.html',
})
export class MoveTableDialog {
  protected readonly ref = inject<DialogRef<TableResponse | undefined, MoveTableDialog>>(DialogRef);
  protected readonly data = inject<MoveTableDialogData>(DIALOG_DATA);

  protected readonly candidates = this.data.otherTables.filter((t) => t.active);
  protected readonly selectedId = signal<number | null>(null);
  protected readonly selected = computed(() => this.candidates.find((t) => t.id === this.selectedId()) ?? null);

  protected select(id: number): void {
    this.selectedId.set(id);
  }

  confirm(): void {
    const table = this.selected();
    if (table) this.ref.close(table);
  }
}
