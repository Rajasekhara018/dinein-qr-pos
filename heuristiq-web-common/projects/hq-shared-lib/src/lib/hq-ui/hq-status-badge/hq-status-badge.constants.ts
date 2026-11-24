/** Colour role of a status badge; each maps to the theme's --hq-tone-<tone>-* tokens. */
export type HqStatusTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

/** The tones a badge accepts, in display order. */
export const HQ_STATUS_TONES: readonly HqStatusTone[] = ['success', 'warning', 'danger', 'info', 'neutral'];

/** Tone of a badge whose tone is not given. */
export const HQ_STATUS_DEFAULT_TONE: HqStatusTone = 'neutral';

/** Badge sizes. */
export type HqStatusBadgeSize = 'sm' | 'md';
