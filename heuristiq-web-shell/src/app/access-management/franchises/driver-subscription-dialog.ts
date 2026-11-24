import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { Observable } from 'rxjs';
import { NotificationService } from '../../shared/services/notification-service';
import { DRIVER_SUBSCRIPTION_DIALOG_TITLE_ID, DRIVER_SUBSCRIPTION_PERIOD_KEYS } from './driver-subscription.constants';
import { DriverSubscriptionPlanOption, DriverSubscriptionResult } from './driver-subscription.models';
import { FranchiseApi } from './franchise-api';

/** What the dialog is opened with: the driver, and the franchise when the franchise owner (not an administrator) subscribes them. */
export interface DriverSubscriptionDialogData {
  driverUserId: string;
  driverName: string;
  /** Set for the franchise owner's calls; empty for an administrator's. */
  franchiseId: string;
  asAdmin: boolean;
}

/**
 * Subscribes a driver to one of the plans open to them, for an administrator (franchise members screen) or the driver's franchise
 * owner (My drivers): the plans are read when it opens, one is chosen (preselected when there is only one) and confirmed. The same
 * rules and first charge apply as when the driver subscribes in the Driver app. Closes with true once subscribed.
 */
@Component({
  selector: 'app-driver-subscription-dialog',
  standalone: false,
  templateUrl: './driver-subscription-dialog.html',
  styleUrl: './driver-subscription-dialog.scss',
})
export class DriverSubscriptionDialog implements OnInit {
  readonly data = inject<DriverSubscriptionDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<DriverSubscriptionDialog, boolean>>(MatDialogRef);
  private readonly api = inject(FranchiseApi);
  private readonly notify = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);

  readonly titleId = DRIVER_SUBSCRIPTION_DIALOG_TITLE_ID;
  plans: DriverSubscriptionPlanOption[] = [];
  planId = '';
  loading = true;
  loadFailed = false;
  saving = false;

  ngOnInit(): void {
    const read = this.data.asAdmin
      ? this.api.adminDriverPlans(this.data.driverUserId)
      : this.api.ownerDriverPlans(this.data.franchiseId, this.data.driverUserId);
    read.subscribe({
      next: (plans) => {
        this.plans = plans ?? [];
        this.planId = this.plans.length === 1 ? this.plans[0].id : '';
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.loadFailed = true;
        this.cdr.detectChanges();
      },
    });
  }

  periodLabel(code: string): string {
    const key = DRIVER_SUBSCRIPTION_PERIOD_KEYS[code];
    return key ? this.translate.instant(key) : code;
  }

  select(plan: DriverSubscriptionPlanOption): void {
    if (!this.saving) {
      this.planId = plan.id;
    }
  }

  submit(): void {
    if (this.saving || !this.planId) {
      return;
    }
    this.saving = true;
    const call: Observable<DriverSubscriptionResult> = this.data.asAdmin
      ? this.api.adminSubscribeDriver(this.data.driverUserId, this.planId)
      : this.api.ownerSubscribeDriver(this.data.franchiseId, this.data.driverUserId, this.planId);
    call.subscribe({
      next: (result) => {
        this.saving = false;
        this.report(result);
        this.ref.close(true);
      },
      error: (err: { error?: { message?: string } }) => {
        this.saving = false;
        this.notify.error(err?.error?.message || this.translate.instant('accessManagement.driverSubscription.error'));
        this.cdr.detectChanges();
      },
    });
  }

  cancel(): void {
    if (!this.saving) {
      this.ref.close(false);
    }
  }

  private report(result: DriverSubscriptionResult | null): void {
    const t = (key: string) => this.translate.instant(`accessManagement.driverSubscription.${key}`, { name: this.data.driverName });
    const subscription = result?.subscription;
    if (subscription?.status === 'BLOCKED_DUES') {
      this.notify.warning(t('blocked'));
    } else if (subscription && subscription.planId !== this.planId && subscription.nextPlanId === this.planId) {
      this.notify.success(t('nextRenewal'));
    } else {
      this.notify.success(t('done'));
    }
  }
}
