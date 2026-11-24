/** The kinds of input a `hq-field` draws: the native single-line types (month is the browser's own month picker, value YYYY-MM), plus a multi-line "textarea". */
export const HQ_FIELD_TYPES = [
  'text',
  'email',
  'password',
  'number',
  'tel',
  'url',
  'search',
  'date',
  'time',
  'datetime-local',
  'month',
  'textarea',
] as const;

export type HqFieldType = (typeof HQ_FIELD_TYPES)[number];

/** Visible rows of a textarea field when `rows` is not given. */
export const HQ_FIELD_DEFAULT_ROWS = 3;

/** Prefix of the generated ids of a field (`hq-field-0-input`, ...). */
export const HQ_FIELD_ID_PREFIX = 'hq-field';
