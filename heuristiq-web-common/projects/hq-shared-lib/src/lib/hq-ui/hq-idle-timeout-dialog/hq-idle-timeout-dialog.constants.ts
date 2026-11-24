import { Observable } from 'rxjs';

/** Which face of the dialog: the countdown warning or the "session ended" notice. */
export type HqIdleTimeoutDialogMode = 'warning' | 'expired';

/** Data passed to the dialog (same shape as the apps' old IdleTimeoutDialogData). */
export interface HqIdleTimeoutDialogData {
  mode: HqIdleTimeoutDialogMode;
  /** Seconds left before the session ends; only used in 'warning' mode. */
  secondsLeft$?: Observable<number>;
}

/** What the user chose in the warning dialog (closing the expired notice returns undefined). */
export type HqIdleTimeoutDialogResult = 'stay' | 'logout';

/** Suggested panel class when opening the dialog (lets an app restyle the overlay pane). */
export const HQ_IDLE_TIMEOUT_PANEL_CLASS = 'hq-idle-timeout-dialog-panel';
