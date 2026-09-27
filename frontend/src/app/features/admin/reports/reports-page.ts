import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { AdminReportsApi } from '../../../core/api/admin.api';
import { SalesSummary } from '../../../core/api/models';
import { ToastService } from '../../../core/ui/toast.service';
import { formatInr } from '../../../core/util/money';
import { orderTypeLabel, staffPaymentMethodLabel } from '../../../core/util/order-labels';
import { saveBlob } from '../shared/browser';
import { errorMessage } from '../../../shared/util/form-errors';
import { formatIsoDate, istDate } from '../shared/ist-date';
import { BarDatum } from './bar-chart';
import {
  DateRange,
  detectPreset,
  eachDay,
  presetRange,
  RANGE_PRESETS,
  RangePreset,
  rangeProblem,
} from './date-range';

interface RangeForm {
  from: FormControl<string>;
  to: FormControl<string>;
}

/**
 * OWNER sales report: presets/custom range, totals, GST split, payment channels (cash highlighted: it is what the
 * drawer should hold), order types, manual refunds, payment methods, top items, daily chart, CSV.
 */
@Component({
  selector: 'app-admin-reports-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './reports-page.html',
})
export class ReportsPage {
  private readonly api = inject(AdminReportsApi);
  private readonly toasts = inject(ToastService);

  protected readonly presets = RANGE_PRESETS;
  protected readonly today = istDate();
  protected readonly range = signal<DateRange>(presetRange('today'));
  protected readonly preset = computed<RangePreset>(() => detectPreset(this.range()));

  protected readonly form = new FormGroup<RangeForm>({
    from: new FormControl(this.range().from, { nonNullable: true, validators: [Validators.required] }),
    to: new FormControl(this.range().to, { nonNullable: true, validators: [Validators.required] }),
  });
  protected readonly rangeError = signal('');

  protected readonly summary = signal<SalesSummary | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<unknown>(null);
  protected readonly exporting = signal(false);

  protected readonly rangeLabel = computed(() => {
    const { from, to } = this.range();
    return from === to ? formatIsoDate(from) : `${formatIsoDate(from)} – ${formatIsoDate(to)}`;
  });

  protected readonly methodRows = computed(() => {
    const s = this.summary();
    if (!s) return [];
    const total = s.paymentMethods.reduce((sum, m) => sum + m.amount, 0);
    return s.paymentMethods.map((m) => ({
      method: m.method ? m.method.toUpperCase() : 'Other',
      count: m.count,
      amount: m.amount,
      share: total > 0 ? Math.round((m.amount / total) * 100) : 0,
    }));
  });

  /** Paid orders per channel: Online, Cash, UPI at counter, Card at counter. */
  protected readonly channelRows = computed(() => {
    const channels = this.summary()?.paymentChannels ?? [];
    const total = channels.reduce((sum, c) => sum + c.amount, 0);
    return channels.map((c) => ({
      channel: c.channel,
      label: c.channel === 'ONLINE' ? 'Online (gateway)' : staffPaymentMethodLabel(c.channel),
      count: c.count,
      amount: c.amount,
      share: total > 0 ? Math.round((c.amount / total) * 100) : 0,
      cash: c.channel === 'CASH',
    }));
  });

  /** Cash taken in the range (what should be in the drawer, before manual refunds). */
  protected readonly cashAmount = computed(
    () => this.summary()?.paymentChannels?.find((c) => c.channel === 'CASH')?.amount ?? 0,
  );

  protected readonly typeRows = computed(() => {
    const types = this.summary()?.orderTypes ?? [];
    const total = types.reduce((sum, t) => sum + t.amount, 0);
    return types.map((t) => ({
      type: t.orderType,
      label: orderTypeLabel(t.orderType === 'TAKEAWAY' ? 'TAKEAWAY' : 'DINE_IN'),
      count: t.count,
      amount: t.amount,
      share: total > 0 ? Math.round((t.amount / total) * 100) : 0,
    }));
  });

  protected readonly daily = computed<BarDatum[]>(() => {
    const s = this.summary();
    if (!s) return [];
    const byDate = new Map(s.daily.map((d) => [d.date, d]));
    return eachDay({ from: s.from, to: s.to }).map((date) => {
      const point = byDate.get(date);
      const gross = point?.gross ?? 0;
      const orders = point?.orders ?? 0;
      return {
        key: date,
        label: formatIsoDate(date, true),
        value: gross,
        description: `${formatIsoDate(date, true)}: ${formatInr(gross)} · ${orders} ${orders === 1 ? 'order' : 'orders'}`,
      };
    });
  });

  protected readonly formatMoney = (value: number) => formatInr(value, { whole: true });

  constructor() {
    void this.load();
  }

  protected choosePreset(id: Exclude<RangePreset, 'custom'>): void {
    const range = presetRange(id);
    this.form.setValue(range);
    this.rangeError.set('');
    this.range.set(range);
    void this.load();
  }

  protected applyCustom(): void {
    const range = this.form.getRawValue();
    const problem = rangeProblem(range);
    this.rangeError.set(problem ?? '');
    if (problem) return;
    this.range.set(range);
    void this.load();
  }

  protected async load(): Promise<void> {
    const { from, to } = this.range();
    this.loading.set(true);
    this.error.set(null);
    try {
      this.summary.set(await firstValueFrom(this.api.summary(from, to)));
    } catch (error) {
      this.error.set(error);
    } finally {
      this.loading.set(false);
    }
  }

  protected async exportCsv(): Promise<void> {
    const { from, to } = this.range();
    this.exporting.set(true);
    try {
      const blob = await firstValueFrom(this.api.ordersCsv(from, to));
      saveBlob(blob, `orders-${from}-to-${to}.csv`);
    } catch (error) {
      this.toasts.error(errorMessage(error, 'Could not export the CSV.'));
    } finally {
      this.exporting.set(false);
    }
  }
}
