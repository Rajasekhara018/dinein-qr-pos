import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { OrderStatus } from '../../core/api/models';
import { ALL_ORDER_STATUSES, StatusAudience } from '../../shared/order-status';
import { DataTableColumn } from '../../shared/components/data-table';

interface DemoRow extends Record<string, unknown> {
  id: number;
  table: string;
  status: string;
  total: number;
}

/** Literal Tailwind class strings (not built by string concatenation, so the JIT scanner can see them). */
const TONE_SWATCHES = [
  { name: 'success', box: 'border-success-border bg-success-bg', text: 'text-success-fg' },
  { name: 'warning', box: 'border-warning-border bg-warning-bg', text: 'text-warning-fg' },
  { name: 'danger', box: 'border-danger-border bg-danger-bg', text: 'text-danger-fg' },
  { name: 'info', box: 'border-info-border bg-info-bg', text: 'text-info-fg' },
] as const;

/** Dev-only page: every token, type style and shared component, light and dark, in every state we can show statically. */
@Component({
  selector: 'app-design-system-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block min-h-dvh' },
  templateUrl: './design-system-page.html',
})
export class DesignSystemPage {
  protected readonly dark = signal(false);
  protected readonly audience = signal<StatusAudience>('staff');
  protected readonly statuses: readonly OrderStatus[] = ALL_ORDER_STATUSES;
  protected readonly tones = TONE_SWATCHES;

  protected readonly qty = signal(1);
  protected readonly chipSelected = signal(true);
  protected readonly sort = signal<{ key: string; direction: 'asc' | 'desc' } | null>(null);
  protected readonly selectedRows = signal<Set<unknown>>(new Set());
  protected readonly page = signal(1);

  protected readonly columns: DataTableColumn<DemoRow>[] = [
    { key: 'table', header: 'Table', sortable: true },
    { key: 'status', header: 'Status', sortable: true },
    {
      key: 'total',
      header: 'Total',
      sortable: true,
      align: 'end',
      value: (row) => `₹${row['total']}`,
    },
  ];

  protected readonly rows = computed<DemoRow[]>(() =>
    Array.from({ length: 24 }, (_, i) => ({
      id: i + 1,
      table: `T${(i % 9) + 1}`,
      status: ['New', 'Cooking', 'Ready', 'Served'][i % 4],
      total: 120 + i * 35,
    })),
  );

  protected toggleTheme(): void {
    this.dark.update((v) => !v);
  }

  protected toggleAudience(): void {
    this.audience.update((a) => (a === 'staff' ? 'guest' : 'staff'));
  }
}
