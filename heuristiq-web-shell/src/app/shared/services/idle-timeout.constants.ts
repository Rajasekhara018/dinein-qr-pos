/** Idle-session timeout: how long a signed-in user may be inactive before being warned, then signed out. */
export const IDLE_TIMEOUT_CONFIG = {
  /** Seconds of inactivity (mouse, keyboard, touch, scroll) before the "still there?" warning opens: 9.5 minutes. */
  idleSeconds: 570,
  /** Seconds the warning stays up before the session is ended: the countdown shown to the user. */
  warningSeconds: 30,
} as const;

export type IdleTimeoutDialogMode = 'warning' | 'expired';

/** Translation keys used by the idle-timeout dialog. */
export const IDLE_TIMEOUT_I18N = {
  warningTitle: 'idleTimeout.warningTitle',
  warningMessage: 'idleTimeout.warningMessage',
  stayActive: 'idleTimeout.stayActive',
  logout: 'idleTimeout.logout',
  expiredTitle: 'idleTimeout.expiredTitle',
  expiredMessage: 'idleTimeout.expiredMessage',
  loginAgain: 'idleTimeout.loginAgain',
} as const;

/** CSS class on the dialog overlay panel. */
export const IDLE_TIMEOUT_PANEL_CLASS = 'idle-timeout-dialog-panel';
