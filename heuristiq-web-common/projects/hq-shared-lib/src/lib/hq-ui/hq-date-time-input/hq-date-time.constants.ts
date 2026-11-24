import { ConnectedPosition } from '@angular/cdk/overlay';

/** What a date-time field holds: a day, a time of day, or both. */
export type HqDateTimeMode = 'date' | 'time' | 'datetime';

/** The weeks a month grid shows: six, so the calendar keeps one height from month to month. */
export const HQ_DATE_TIME_GRID_ROWS = 6;
export const HQ_DATE_TIME_GRID_CELLS = HQ_DATE_TIME_GRID_ROWS * 7;

/** Where the calendar opens: under the field, or above it when there is no room below. */
export const HQ_DATE_TIME_POSITIONS: ConnectedPosition[] = [
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 6 },
  { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -6 },
  { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 6 },
  { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -6 },
];

/** First day of the week when a screen does not say: Sunday. */
export const HQ_DATE_TIME_DEFAULT_WEEK_START = 0;

/** Prefix of the generated ids of a field. */
export const HQ_DATE_TIME_ID_PREFIX = 'hq-dt';

/** The longest a typed day or time can be (a date and time with spaces), so the box never takes an essay. */
export const HQ_DATE_TIME_MAX_TYPED = 24;
