import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiError } from '../../../core/api/api-error';
import { AdminTablesApi } from '../../../core/api/admin.api';
import { ReserveTableRequest, TableResponse } from '../../../core/api/models';
import { SheetService } from '../../../core/ui/sheet.service';
import { ToastService } from '../../../core/ui/toast.service';
import { copyText, saveBlob } from '../shared/browser';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { errorMessage } from '../../../shared/util/form-errors';
import { MoveTableDialog, MoveTableDialogData } from './move-table-dialog';
import { QrPreviewData, QrPreviewDialog } from './qr-preview-dialog';
import { ReserveTableDialog, ReserveTableDialogData } from './reserve-table-dialog';
import { TableDialog, TableDialogData } from './table-dialog';

const PAGE_SIZE_OPTIONS = [5, 10, 20, 50] as const;

/** Tables & QR codes: add, rename, (de)activate, regenerate, preview, copy link, printable PDF. */
@Component({
  selector: 'app-admin-tables-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './tables-page.html',
})
export class TablesPage {
  private readonly api = inject(AdminTablesApi);
  private readonly sheets = inject(SheetService);
  private readonly toasts = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);

  protected readonly tables = signal<TableResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<unknown>(null);
  protected readonly selected = signal<ReadonlySet<number>>(new Set());
  protected readonly busyIds = signal<ReadonlySet<number>>(new Set());
  protected readonly downloading = signal(false);

  /** Client-side paging: the full active table list already has to be fetched at once for "select all" /
   *  "download PDF (all)" to work, so only the on-screen slice changes per page, not the request. */
  protected readonly page = signal(1);
  protected readonly pageSize = signal(10);
  protected readonly pageSizeOptions = PAGE_SIZE_OPTIONS;

  protected readonly allSelected = computed(
    () => this.tables().length > 0 && this.tables().every((t) => this.selected().has(t.id)),
  );
  protected readonly someSelected = computed(() => this.selected().size > 0 && !this.allSelected());
  protected readonly activeCount = computed(() => this.tables().filter((t) => t.active).length);
  protected readonly pageCount = computed(() => Math.max(1, Math.ceil(this.tables().length / this.pageSize())));
  protected readonly pagedTables = computed(() => {
    const size = this.pageSize();
    const start = (this.page() - 1) * size;
    return this.tables().slice(start, start + size);
  });

  constructor() {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    try {
      const tables = await firstValueFrom(this.api.list());
      // Newest-created table first, so a table you just added is on page 1 instead of wherever it sorts to.
      this.tables.set([...tables].sort((a, b) => b.id - a.id));
      this.error.set(null);
      const ids = new Set(tables.map((t) => t.id));
      this.selected.update((set) => new Set([...set].filter((id) => ids.has(id))));
      this.page.set(1);
    } catch (error) {
      this.error.set(error);
    } finally {
      this.loading.set(false);
    }
  }

  protected toggleSelected(id: number, checked: boolean): void {
    this.selected.update((set) => {
      const next = new Set(set);
      if (checked) next.add(id);
      else next.delete(id);
      return next;
    });
  }

  protected toggleAll(checked: boolean): void {
    this.selected.set(checked ? new Set(this.tables().map((t) => t.id)) : new Set());
  }

  protected goToPage(page: number): void {
    this.page.set(Math.min(Math.max(1, page), this.pageCount()));
  }

  protected setPageSize(size: number): void {
    this.pageSize.set(size);
    this.page.set(1);
  }

  protected open(table: TableResponse | null): void {
    const ref = this.sheets.open<TableResponse, TableDialogData, TableDialog>(TableDialog, {
      data: { table },
      maxWidth: '30rem',
    });
    ref.closed.subscribe((saved) => {
      if (!saved) return;
      this.toasts.success(table ? `Table ${saved.label} saved.` : `Table ${saved.label} added.`);
      void this.load();
    });
  }

  protected preview(table: TableResponse): void {
    this.sheets.open<void, QrPreviewData, QrPreviewDialog>(QrPreviewDialog, {
      data: { table },
      maxWidth: '26rem',
    });
  }

  protected async setActive(table: TableResponse, active: boolean): Promise<void> {
    this.patch({ ...table, active });
    this.markBusy(table.id, true);
    try {
      this.patch(await firstValueFrom(this.api.update(table.id, { label: table.label, active })));
      this.toasts.success(active ? `Table ${table.label} is active.` : `Table ${table.label} is deactivated.`, {
        key: `table-${table.id}`,
      });
    } catch (error) {
      this.patch(table);
      this.toasts.error(errorMessage(error, 'Could not update the table.'));
    } finally {
      this.markBusy(table.id, false);
    }
  }

  protected async regenerate(table: TableResponse): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: `New QR code for ${table.label}?`,
      message: 'The printed QR code on this table stops working immediately.',
      details: ['Guests scanning the old code will be asked to scan again.', 'Print and place the new card right away.'],
      confirmLabel: 'Regenerate QR',
    });
    if (!confirmed) return;
    this.markBusy(table.id, true);
    try {
      this.patch(await firstValueFrom(this.api.regenerateQr(table.id)));
      this.toasts.success(`New QR code created for ${table.label}. Remember to reprint it.`, {
        action: { label: 'Download PDF', run: () => void this.downloadPdf([table.id]) },
      });
    } catch (error) {
      this.toasts.error(errorMessage(error, 'Could not regenerate the QR code.'));
    } finally {
      this.markBusy(table.id, false);
    }
  }

  protected async copyLink(table: TableResponse): Promise<void> {
    const ok = await copyText(table.qrUrl);
    if (ok) this.toasts.success(`Menu link for ${table.label} copied.`, { key: 'copy-link' });
    else this.toasts.error('Could not copy the link.');
  }

  /** Printable A4 PDF for the given ids (all tables when empty). */
  async downloadPdf(ids: number[] = []): Promise<void> {
    this.downloading.set(true);
    try {
      const blob = await firstValueFrom(this.api.qrPdf(ids));
      const suffix = ids.length === 1 ? (this.tables().find((t) => t.id === ids[0])?.label ?? 'table') : ids.length ? 'selected' : 'all';
      saveBlob(blob, `table-qr-codes-${suffix.replace(/[^A-Za-z0-9_-]+/g, '-')}.pdf`);
    } catch (error) {
      this.toasts.error(errorMessage(error, 'Could not create the PDF.'));
    } finally {
      this.downloading.set(false);
    }
  }

  protected downloadSelected(): void {
    void this.downloadPdf([...this.selected()]);
  }

  protected async reserve(table: TableResponse): Promise<void> {
    const ref = this.sheets.open<ReserveTableRequest | undefined, ReserveTableDialogData, ReserveTableDialog>(
      ReserveTableDialog,
      { data: { table }, maxWidth: '28rem' },
    );
    const request = await firstValueFrom(ref.closed);
    if (!request) return;
    this.markBusy(table.id, true);
    try {
      this.patch(await firstValueFrom(this.api.reserve(table.id, request)));
      this.toasts.success(`Table ${table.label} reserved.`, { key: `table-${table.id}` });
    } catch (error) {
      this.toasts.error(errorMessage(error, 'Could not reserve the table.'));
    } finally {
      this.markBusy(table.id, false);
    }
  }

  protected async clearReservation(table: TableResponse): Promise<void> {
    this.markBusy(table.id, true);
    try {
      this.patch(await firstValueFrom(this.api.clearReservation(table.id)));
      this.toasts.success(`Reservation cleared for table ${table.label}.`, { key: `table-${table.id}` });
    } catch (error) {
      this.toasts.error(errorMessage(error, 'Could not clear the reservation.'));
    } finally {
      this.markBusy(table.id, false);
    }
  }

  /** Move (destination free) or merge (destination occupied) — see MoveTableDialog. */
  protected async moveOrMerge(table: TableResponse): Promise<void> {
    const otherTables = this.tables().filter((t) => t.id !== table.id);
    const ref = this.sheets.open<TableResponse | undefined, MoveTableDialogData, MoveTableDialog>(MoveTableDialog, {
      data: { table, otherTables },
      maxWidth: '28rem',
    });
    const target = await firstValueFrom(ref.closed);
    if (!target) return;
    this.markBusy(table.id, true);
    try {
      if (target.occupied) {
        await firstValueFrom(this.api.merge({ fromTableId: table.id, toTableId: target.id }));
        this.toasts.success(`Merged ${table.label} into ${target.label}.`);
      } else {
        await firstValueFrom(this.api.moveOrders(table.id, { tableId: target.id }));
        this.toasts.success(`Moved ${table.label}'s order to ${target.label}.`);
      }
      void this.load();
    } catch (error) {
      const code = ApiError.from(error).code;
      if (code === 'TABLE_OCCUPIED' || code === 'TABLE_RESERVED') {
        this.toasts.warning(errorMessage(error, 'That table is no longer available.'));
        void this.load();
      } else {
        this.toasts.error(errorMessage(error, 'Could not move the order.'));
      }
    } finally {
      this.markBusy(table.id, false);
    }
  }

  private patch(table: TableResponse): void {
    this.tables.update((list) => list.map((t) => (t.id === table.id ? table : t)));
  }

  private markBusy(id: number, busy: boolean): void {
    this.busyIds.update((set) => {
      const next = new Set(set);
      if (busy) next.add(id);
      else next.delete(id);
      return next;
    });
  }
}
