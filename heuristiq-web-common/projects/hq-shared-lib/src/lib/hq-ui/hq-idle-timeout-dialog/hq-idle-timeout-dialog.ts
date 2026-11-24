import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { HqIdleTimeoutDialogData, HqIdleTimeoutDialogResult } from './hq-idle-timeout-dialog.constants';
import { HQ_IDLE_TIMEOUT_DEFAULT_TEXTS, HQ_IDLE_TIMEOUT_TEXTS, HqIdleTimeoutTexts } from './hq-idle-timeout-dialog.texts';

let nextIdleDialogId = 0;

/**
 * The idle-timeout dialog shared by every app (the apps keep their own IdleTimeoutService and open this component):
 * a "still there?" warning with a live countdown (stay signed in / sign out) and, once the session has ended, a
 * single-button "session timed out" notice. Works when opened through Angular Material's MatDialog or the CDK Dialog;
 * it draws its own card surface. Closes with 'stay' or 'logout' (warning) or undefined (expired notice).
 *
 *   this.dialog.open<HqIdleTimeoutDialog, HqIdleTimeoutDialogData, HqIdleTimeoutDialogResult>(HqIdleTimeoutDialog, {
 *     data: { mode: 'warning', secondsLeft$ }, disableClose: true, panelClass: HQ_IDLE_TIMEOUT_PANEL_CLASS,
 *   });
 *
 * Words come from HQ_IDLE_TIMEOUT_TEXTS (English defaults).
 */
@Component({
  selector: 'hq-idle-timeout-dialog',
  standalone: false,
  template: `
    <div class="hq-idle" role="alertdialog" [attr.aria-labelledby]="titleId" [attr.aria-describedby]="messageId">
      <div class="hq-idle__header">
        <div class="hq-idle__icon" aria-hidden="true">
          <i class="bi" [class.bi-hourglass-split]="isWarning" [class.bi-lock]="!isWarning"></i>
        </div>
        <div class="hq-idle__text">
          <h2 class="hq-idle__title" [id]="titleId">{{ isWarning ? texts.warningTitle() : texts.expiredTitle() }}</h2>
          <p class="hq-idle__message" [id]="messageId">
            @if (isWarning) {
              {{ texts.warningMessage((data.secondsLeft$ | async) ?? null) }}
            } @else {
              {{ texts.expiredMessage() }}
            }
          </p>
        </div>
      </div>
      <hq-form-actions class="hq-idle__actions">
        @if (isWarning) {
          <button hqButton="secondary" type="button" (click)="logout()">{{ texts.logout() }}</button>
          <button hqButton="primary" type="button" (click)="stay()" cdkFocusInitial>{{ texts.stayActive() }}</button>
        } @else {
          <button hqButton="primary" type="button" (click)="dismiss()" cdkFocusInitial>{{ texts.loginAgain() }}</button>
        }
      </hq-form-actions>
    </div>
  `,
  styleUrl: './hq-idle-timeout-dialog.scss',
})
export class HqIdleTimeoutDialog {
  private readonly matRef = inject<MatDialogRef<HqIdleTimeoutDialog, HqIdleTimeoutDialogResult>>(MatDialogRef, { optional: true });
  private readonly cdkRef = inject<DialogRef<HqIdleTimeoutDialogResult, HqIdleTimeoutDialog>>(DialogRef, { optional: true });
  readonly data: HqIdleTimeoutDialogData =
    inject<HqIdleTimeoutDialogData>(MAT_DIALOG_DATA, { optional: true })
    ?? inject<HqIdleTimeoutDialogData>(DIALOG_DATA, { optional: true })
    ?? { mode: 'expired' };
  readonly texts: HqIdleTimeoutTexts = inject(HQ_IDLE_TIMEOUT_TEXTS, { optional: true }) ?? HQ_IDLE_TIMEOUT_DEFAULT_TEXTS;

  private readonly uid = nextIdleDialogId++;
  readonly titleId = `hq-idle-title-${this.uid}`;
  readonly messageId = `hq-idle-message-${this.uid}`;

  get isWarning(): boolean {
    return this.data.mode === 'warning';
  }

  stay(): void {
    this.close('stay');
  }

  logout(): void {
    this.close('logout');
  }

  /** Closes the "session timed out" notice. */
  dismiss(): void {
    this.close(undefined);
  }

  private close(result: HqIdleTimeoutDialogResult | undefined): void {
    if (this.matRef) this.matRef.close(result);
    else this.cdkRef?.close(result);
  }
}
