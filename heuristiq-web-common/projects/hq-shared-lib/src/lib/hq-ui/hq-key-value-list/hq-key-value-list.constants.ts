import type { HqStatusTone } from '../hq-status-badge/hq-status-badge.constants';

/**
 * One row of {@link HqKeyValueList}: the same shape as the apps' old `KeyValueItem`. Give either `label` (already
 * localised) or `labelKey` (translated through HQ_UI_STATE_TEXTS.translate).
 */
export interface HqKeyValueItem {
  labelKey?: string;
  label?: string;
  /** Rendered text; null/undefined/'' render the "none" placeholder. API data is never translated. */
  value: string | number | null | undefined;
  /** Monospace (ids, transaction numbers). */
  mono?: boolean;
  /** Show a copy-to-clipboard button. */
  copyable?: boolean;
  /** Renders the value as a status badge in this tone. */
  tone?: HqStatusTone;
}

/** Width of the label column (CSS grid track). */
export const HQ_KEY_VALUE_DEFAULT_LABEL_WIDTH = 'minmax(7rem, 38%)';
