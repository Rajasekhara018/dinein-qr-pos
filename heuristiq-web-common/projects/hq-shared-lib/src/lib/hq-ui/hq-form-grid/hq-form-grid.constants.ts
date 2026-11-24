/** Number of columns of a hq-form-grid on wide screens. */
export type HqFormGridCols = 1 | 2 | 3 | 4;
/** Space between the fields of a hq-form-grid. */
export type HqFormGridGap = 'sm' | 'md' | 'lg';

export const HQ_FORM_GRID_DEFAULT_COLS: HqFormGridCols = 2;
export const HQ_FORM_GRID_DEFAULT_GAP: HqFormGridGap = 'md';
export const HQ_FORM_GRID_MAX_COLS = 4;

/** Reads `cols` given as a number or an attribute string; anything out of range falls back to the default. */
export function hqFormGridCols(value: unknown): HqFormGridCols {
  const cols = typeof value === 'number' ? value : parseInt(String(value ?? ''), 10);
  return Number.isInteger(cols) && cols >= 1 && cols <= HQ_FORM_GRID_MAX_COLS ? (cols as HqFormGridCols) : HQ_FORM_GRID_DEFAULT_COLS;
}
