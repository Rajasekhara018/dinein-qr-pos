/** Colour roles of a `hqButton`; every one follows the active theme (tokens in theme/styles.scss). */
export const HQ_BUTTON_COLORS = ['primary', 'accent', 'warn', 'success', 'secondary'] as const;
export type HqButtonColor = (typeof HQ_BUTTON_COLORS)[number];

/** How a `hqButton` is drawn: solid fill, outline only, or text only. */
export const HQ_BUTTON_APPEARANCES = ['filled', 'outline', 'text'] as const;
export type HqButtonAppearance = (typeof HQ_BUTTON_APPEARANCES)[number];

/** Button sizes (sm / lg match the old `.btn-sm` / `.btn-lg`). */
export const HQ_BUTTON_SIZES = ['sm', 'md', 'lg'] as const;
export type HqButtonSize = (typeof HQ_BUTTON_SIZES)[number];

/** Used for a bare `hqButton` attribute (or an unknown value). */
export const HQ_BUTTON_DEFAULT_COLOR: HqButtonColor = 'primary';
export const HQ_BUTTON_DEFAULT_APPEARANCE: HqButtonAppearance = 'filled';
export const HQ_BUTTON_DEFAULT_SIZE: HqButtonSize = 'md';
