import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { debounceTime, firstValueFrom, merge } from 'rxjs';
import { ApiError } from '../../../core/api/api-error';
import { TOPICS, WaiterTableView } from '../../../core/api/models';
import { WaiterApi } from '../../../core/api/waiter.api';
import { silentErrors } from '../../../core/http/http-context';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { WaiterBoardStore } from '../data/waiter-board.store';

/**
 * `/waiter/tables`: every active table with its open / cooking / ready counts (live). Tapping a table starts a new
 * order for it; the Takeaway tile starts a takeaway order.
 */
@Component({
  selector: 'app-waiter-tables-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './tables-page.html',
})
export class TablesPage {
  private readonly api = inject(WaiterApi);
  private readonly router = inject(Router);
  private readonly realtime = inject(RealtimeService);
  protected readonly board = inject(WaiterBoardStore);

  protected readonly tables = signal<readonly WaiterTableView[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<ApiError | null>(null);
  private seq = 0;

  constructor() {
    void this.load();
    // Counts change with every kitchen event; refetch (debounced) and on every reconnect.
    merge(this.realtime.watch(TOPICS.kitchenOrders), this.realtime.connected$)
      .pipe(debounceTime(500), takeUntilDestroyed())
      .subscribe(() => void this.load(true));
  }

  protected async load(silent = false): Promise<void> {
    const seq = ++this.seq;
    if (!silent) this.loading.set(true);
    try {
      const tables = await firstValueFrom(this.api.tables(silentErrors()));
      if (seq !== this.seq) return;
      this.tables.set(tables);
      this.error.set(null);
    } catch (e) {
      if (seq === this.seq && !this.tables().length) this.error.set(ApiError.from(e));
    } finally {
      if (seq === this.seq) this.loading.set(false);
    }
  }

  protected openTable(table: WaiterTableView): void {
    void this.router.navigate(['/waiter/new'], { queryParams: { table: table.id } });
  }

  protected openTakeaway(): void {
    void this.router.navigate(['/waiter/new'], { queryParams: { type: 'TAKEAWAY' } });
  }
}
