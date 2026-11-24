import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { AdminOrderView } from '../../../core/api/models';

export interface SplitOrderDialogData {
  order: AdminOrderView;
}

/**
 * Assigns each line to a group (1, 2, 3…); closes with the resulting item-id groups, or undefined if cancelled.
 * Only offered for PENDING_PAYMENT orders (see order-detail-page) — the backend enforces this too.
 */
@Component({
  selector: 'app-split-order-dialog',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './split-order-dialog.html',
})
export class SplitOrderDialog {
  protected readonly ref = inject<DialogRef<number[][] | undefined, SplitOrderDialog>>(DialogRef);
  protected readonly data = inject<SplitOrderDialogData>(DIALOG_DATA);

  protected readonly items = this.data.order.items.filter((line) => line.id != null);
  /** Line id -> group number (1-based). */
  protected readonly assignments = signal<Record<number, number>>(
    Object.fromEntries(this.items.map((line) => [line.id!, 1])),
  );
  protected readonly groupCount = signal(2);

  protected readonly groups = computed(() => {
    const assignments = this.assignments();
    return Array.from({ length: this.groupCount() }, (_, i) => i + 1).map((group) => ({
      group,
      items: this.items.filter((line) => assignments[line.id!] === group),
    }));
  });

  protected readonly nonEmptyGroupCount = computed(() => this.groups().filter((g) => g.items.length > 0).length);
  protected readonly canConfirm = computed(() => this.nonEmptyGroupCount() >= 2);

  protected setGroup(lineId: number, group: number): void {
    this.assignments.update((current) => ({ ...current, [lineId]: group }));
  }

  protected addGroup(): void {
    this.groupCount.update((n) => n + 1);
  }

  protected confirm(): void {
    if (!this.canConfirm()) return;
    const itemGroups = this.groups()
      .map((g) => g.items.map((line) => line.id!))
      .filter((ids) => ids.length > 0);
    this.ref.close(itemGroups);
  }
}
