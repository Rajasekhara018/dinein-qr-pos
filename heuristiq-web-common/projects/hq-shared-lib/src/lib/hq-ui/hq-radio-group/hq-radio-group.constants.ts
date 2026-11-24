/** How the options of a hq-radio-group are laid out. */
export const HQ_RADIO_DIRECTIONS = ['row', 'column'] as const;

export type HqRadioDirection = (typeof HQ_RADIO_DIRECTIONS)[number];

/** Prefix of the generated `name` shared by a group's native radios (when `name` is not given). */
export const HQ_RADIO_GROUP_NAME_PREFIX = 'hq-radio-group-';
