import { ChangeDetectorRef, Component, OnDestroy, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { ReadinessFixLink } from '../application-readiness.models';
import { ReadinessNavigationService } from '../readiness-navigation.service';
import {
  GOVERNANCE_PRIVILEGES,
  GOVERNANCE_TEXT_KEYS,
  OVERRIDE_REASON_MAX,
  OVERRIDE_REASON_MIN,
} from '../go-live-governance.constants';
import { governanceFailure, isOverrideReasonValid, toBlockerView } from '../go-live-governance.helpers';
import { GoLiveBlockedInfo, GoLiveBlockerView, GoLiveDialogResult } from '../go-live-governance.models';
import { GoLiveGovernanceService } from '../go-live-governance.service';

/** Input of the blocked dialog. */
export interface GoLiveBlockedDialogData {
  info: GoLiveBlockedInfo;
}

/**
 * Shown when Rider_UAM refuses to switch an application on (HTTP 409 GO_LIVE_BLOCKED): lists every required step that fails,
 * each with its translated text and a Fix button to its screen. A SUPER_ADMIN (privilege APPLICATION_GO_LIVE_OVERRIDE_CREATE)
 * can instead override the guard with a written reason; the server decides whether the caller really is one.
 */
@Component({
  selector: 'app-go-live-blocked-dialog',
  standalone: false,
  templateUrl: './go-live-blocked-dialog.html',
  styleUrl: './go-live-blocked-dialog.scss',
})
export class GoLiveBlockedDialog implements OnDestroy {
  private readonly data = inject<GoLiveBlockedDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<GoLiveBlockedDialog, GoLiveDialogResult>>(MatDialogRef);
  private readonly translate = inject(TranslateService);
  private readonly governance = inject(GoLiveGovernanceService);
  private readonly navigation = inject(ReadinessNavigationService);
  private readonly dialogs = inject(AppDialogService);
  private readonly notify = inject(NotificationService);
  private readonly cdr = inject(ChangeDetectorRef);
  private overrideSub: Subscription | null = null;

  readonly texts = GOVERNANCE_TEXT_KEYS;
  readonly overridePrivilege = GOVERNANCE_PRIVILEGES.override;
  readonly reasonMin = OVERRIDE_REASON_MIN;
  readonly reasonMax = OVERRIDE_REASON_MAX;

  readonly code = this.data.info.applicationCode;
  readonly blockers: GoLiveBlockerView[];

  /** False shows the list of blockers, true the override form. */
  overriding = false;
  reason = '';
  busy = false;
  /** Translated sentence of the last failed override, or null. */
  errorText: string | null = null;

  constructor() {
    const has = (key: string): boolean => this.translate.instant(key) !== key;
    this.blockers = this.data.info.blockers.map((b) => toBlockerView(b, has, (path) => this.navigation.canOpen(path)));
  }

  ngOnDestroy(): void {
    this.overrideSub?.unsubscribe();
  }

  get reasonLength(): number {
    return this.reason.trim().length;
  }

  get reasonValid(): boolean {
    return isOverrideReasonValid(this.reason, this.reasonMin, this.reasonMax);
  }

  /** Opens the screen that fixes a step and closes the dialog (nothing changed). */
  fix(link: ReadinessFixLink): void {
    this.ref.close('closed');
    void this.navigation.open(link);
  }

  close(): void {
    this.ref.close('closed');
  }

  startOverride(): void {
    this.overriding = true;
    this.errorText = null;
  }

  backToList(): void {
    if (this.busy) return;
    this.overriding = false;
    this.errorText = null;
  }

  /** Asks to confirm, then sends the override with the reason. */
  confirmOverride(): void {
    if (!this.reasonValid || this.busy) return;
    this.dialogs.confirm({
      title: this.translate.instant(GOVERNANCE_TEXT_KEYS.overrideConfirmTitle),
      message: this.translate.instant(GOVERNANCE_TEXT_KEYS.overrideConfirmMessage, { code: this.code }),
      variant: 'danger',
      confirmText: this.translate.instant(GOVERNANCE_TEXT_KEYS.overrideConfirm),
    }).subscribe((confirmed) => {
      if (confirmed) this.sendOverride();
    });
  }

  private sendOverride(): void {
    this.busy = true;
    this.errorText = null;
    this.overrideSub?.unsubscribe();
    this.overrideSub = this.governance.override(this.code, this.reason).subscribe({
      next: () => {
        this.busy = false;
        this.notify.success(this.translate.instant(GOVERNANCE_TEXT_KEYS.overrideDone, { code: this.code }));
        this.ref.close('overridden');
      },
      error: (err: unknown) => {
        const failure = governanceFailure(err);
        this.busy = false;
        this.errorText = this.translate.instant(failure.key, failure.params);
        this.cdr.markForCheck();
      },
    });
  }

  trackBlocker(_: number, blocker: GoLiveBlockerView): string {
    return blocker.stepId;
  }
}
