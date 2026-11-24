import { HqStatusTone } from '../hq-status-badge/hq-status-badge.constants';

/** Tone of an alert whose tone is not given. */
export const HQ_ALERT_DEFAULT_TONE: HqStatusTone = 'info';

/** The bootstrap-icons class shown for each tone when `icon` is not given. */
export const HQ_ALERT_TONE_ICONS: Readonly<Record<HqStatusTone, string>> = {
  info: 'bi-info-circle',
  success: 'bi-check-circle',
  warning: 'bi-exclamation-triangle',
  danger: 'bi-x-octagon',
  neutral: 'bi-chat-left-text',
};

/** `icon` value that hides the icon. */
export const HQ_ALERT_NO_ICON = 'none';

/** Tones announced at once (role="alert"); the others are polite (role="status"). */
export const HQ_ALERT_URGENT_TONES: readonly HqStatusTone[] = ['danger', 'warning'];
