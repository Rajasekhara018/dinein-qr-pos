/** Sizes a hq-checkbox comes in: `compact` for table cells and dense lists, `regular` for forms. */
export const HQ_CHECKBOX_SIZES = ['compact', 'regular'] as const;

export type HqCheckboxSize = (typeof HQ_CHECKBOX_SIZES)[number];

/** Prefix of the generated id of a checkbox's native input (when `inputId` is not given). */
export const HQ_CHECKBOX_ID_PREFIX = 'hq-checkbox-';
