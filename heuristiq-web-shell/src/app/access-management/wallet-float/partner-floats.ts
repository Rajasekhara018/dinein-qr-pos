import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqAmount, HqCommonTableColumn, HqPrivilegeAccessService, hqToAmount } from 'hq-shared-lib';
import { LIST_PAGE_SIZE_OPTIONS } from '../../shared/list-page.constants';
import { NotificationService } from '../../shared/services/notification-service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import { labelKey } from '../franchises/franchise-earnings.helpers';
import {
  DIRECTION_LABEL_KEYS, FLOAT_ALERTS_PAGE_SIZE, FLOAT_PRIVILEGES, FLOAT_STATUS_LABEL_KEYS, SETTLEMENT_DIRECTIONS,
} from './wallet-float.constants';
import {
  alertPageCount, amountToSend, floatCurrency, floatErrorKey, isNonNegativeAmount, isPositiveAmount, newIdempotencyKey, reasonError,
} from './wallet-float.helpers';
import { WalletFloatApi } from './wallet-float-api';
import { FloatSummary, SettlementDirection } from './wallet-float.models';

/** The alert-threshold form: a blank threshold clears it. Amounts are `{ code, value }` (hq-amount-input). */
export interface ThresholdForm {
  threshold: HqAmount | null;
  reason: string;
}

/** The settlement form; {@code key} is the Idempotency-Key of this attempt, kept for a retry and renewed after a success. */
export interface SettlementForm {
  appCode: string;
  amount: HqAmount | null;
  direction: SettlementDirection;
  reason: string;
  key: string;
}

/**
 * Administrators watch partner floats here: the floats below their alert threshold, a lookup of any holder by user id, and for
 * the holder found the alert threshold (with a reason) and a settlement (with a reason). Amounts are `{ code, value }`, only shown.
 */
@Component({
  selector: 'app-partner-floats',
  standalone: false,
  templateUrl: './partner-floats.html',
  styleUrl: '../franchises/franchises-list.scss',
})
export class PartnerFloats implements OnInit {
  readonly privileges = FLOAT_PRIVILEGES;
  readonly directions = SETTLEMENT_DIRECTIONS;
  readonly pageSizeOptions = LIST_PAGE_SIZE_OPTIONS;
  pageSize = FLOAT_ALERTS_PAGE_SIZE;
  /** The alerts grid's columns (headings translated when the screen opens). */
  alertColumns: HqCommonTableColumn<FloatSummary>[] = [];

  alerts: FloatSummary[] = [];
  page = 0;
  total = 0;
  isLoading = false;
  hasError = false;

  lookupUserId = '';
  detail: FloatSummary | null = null;
  isLookingUp = false;
  saving = false;

  thresholdForm: ThresholdForm = { threshold: null, reason: '' };
  settlementForm: SettlementForm = this.freshSettlement();

  constructor(
    private api: WalletFloatApi,
    private notify: NotificationService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
    private access: HqPrivilegeAccessService,
  ) {}

  ngOnInit(): void {
    this.alertColumns = [
      { key: 'userId', label: this.translate.instant('walletFloat.admin.userId') },
      { key: 'balance', label: this.translate.instant('walletFloat.balance') },
      { key: 'alertThreshold', label: this.translate.instant('walletFloat.admin.threshold') },
      { key: 'creditLimit', label: this.translate.instant('walletFloat.creditLimit') },
      { key: 'available', label: this.translate.instant('walletFloat.available') },
    ];
    if (this.access.can(this.privileges.alertList)) {
      this.loadAlerts();
    }
  }

  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.loadAlerts(0);
  }

  get pageCount(): number {
    return alertPageCount(this.total, this.pageSize);
  }

  get isLast(): boolean {
    return this.page + 1 >= this.pageCount;
  }

  loadAlerts(page = 0): void {
    this.page = Math.max(page, 0);
    this.isLoading = true;
    this.hasError = false;
    this.api.alerts(this.page, this.pageSize).subscribe({
      next: (result) => {
        this.alerts = result?.content ?? [];
        this.total = result?.totalElements ?? this.alerts.length;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: (err: unknown) => {
        this.hasError = true;
        this.isLoading = false;
        this.fail(err, 'walletFloat.admin.errorMessage');
      },
    });
  }

  lookup(userId = this.lookupUserId): void {
    const id = userId.trim();
    if (!id) { return; }
    this.lookupUserId = id;
    this.isLookingUp = true;
    this.api.byUser(id).subscribe({
      next: (summary) => { this.show(summary); this.isLookingUp = false; this.cdr.detectChanges(); },
      error: (err: unknown) => { this.isLookingUp = false; this.detail = null; this.fail(err, 'walletFloat.admin.lookupFailed'); },
    });
  }

  close(): void {
    this.detail = null;
  }

  private show(summary: FloatSummary): void {
    this.detail = summary;
    this.thresholdForm = { threshold: hqToAmount(summary.alertThreshold, floatCurrency(summary)), reason: '' };
    this.settlementForm = this.freshSettlement();
  }

  private freshSettlement(): SettlementForm {
    return { appCode: '', amount: null, direction: 'DEBIT', reason: '', key: newIdempotencyKey() };
  }

  /** The currency of the holder found (its balance's), for the amount fields; '' lets them use the app default. */
  get currency(): string {
    return floatCurrency(this.detail);
  }

  saveThreshold(): void {
    const detail = this.detail;
    if (!detail) { return; }
    const threshold = amountToSend(this.thresholdForm.threshold);
    const error = (threshold && !isNonNegativeAmount(threshold) ? 'walletFloat.errors.amount' : null) ?? reasonError(this.thresholdForm.reason);
    if (error) {
      this.notify.error(this.translate.instant(error));
      return;
    }
    this.saving = true;
    this.api.setAlertThreshold(detail.userId, {
      threshold, reason: this.thresholdForm.reason.trim(),
    }).subscribe({
      next: (summary) => {
        this.saving = false;
        this.show(summary);
        this.notify.success(this.translate.instant('walletFloat.admin.thresholdSaved'));
        this.refreshAlerts();
      },
      error: (err: unknown) => { this.saving = false; this.fail(err, 'walletFloat.admin.thresholdFailed'); },
    });
  }

  settle(): void {
    const detail = this.detail;
    const form = this.settlementForm;
    if (!detail) { return; }
    const amount = amountToSend(form.amount);
    const error = (!form.appCode.trim() ? 'walletFloat.errors.appCode' : null)
      ?? (!amount || !isPositiveAmount(amount) ? 'walletFloat.errors.amount' : null) ?? reasonError(form.reason);
    if (error || !amount) {
      this.notify.error(this.translate.instant(error ?? 'walletFloat.errors.amount'));
      return;
    }
    this.saving = true;
    this.api.settle(detail.userId, {
      appCode: form.appCode.trim().toUpperCase(), amount, direction: form.direction, reason: form.reason.trim(),
    }, form.key).subscribe({
      next: () => {
        this.saving = false;
        this.notify.success(this.translate.instant('walletFloat.admin.settled'));
        this.lookup(detail.userId);
        this.refreshAlerts();
      },
      // The key stays so that a retry of this attempt cannot post twice.
      error: (err: unknown) => { this.saving = false; this.fail(err, 'walletFloat.admin.settleFailed'); },
    });
  }

  private refreshAlerts(): void {
    if (this.access.can(this.privileges.alertList)) { this.loadAlerts(this.page); }
  }

  /** Known error codes show a translated label; anything else shows the service's own message. */
  private fail(err: unknown, fallbackKey: string): void {
    const key = floatErrorKey(err);
    this.notify.error(key ? this.translate.instant(key) : apiErrorMessage(err, this.translate.instant(fallbackKey)));
    this.cdr.detectChanges();
  }

  statusLabel(status: string): string {
    const key = labelKey(FLOAT_STATUS_LABEL_KEYS, status);
    return key ? this.translate.instant(key) : status;
  }

  directionLabel(direction: string): string {
    const key = labelKey(DIRECTION_LABEL_KEYS, direction);
    return key ? this.translate.instant(key) : direction;
  }
}
