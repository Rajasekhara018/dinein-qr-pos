import { InjectionToken } from '@angular/core';
import { HqDateTimeMode } from './hq-date-time.constants';

/**
 * The words the date, time and date-time fields show by themselves. The consuming app provides HQ_DATE_TIME_TEXTS with its own
 * translated text (each member is called when needed, so a language switch is picked up); without it the English defaults below
 * are used. Month and weekday names are not here: they come from the browser's own locale data for the field's `locale`.
 */
export interface HqDateTimeTexts {
  /** Hint inside an empty field for each kind of value, e.g. "dd-mm-yyyy". */
  placeholder(mode: HqDateTimeMode): string;
  /** Accessible name of the button that opens the calendar or clock. */
  open(mode: HqDateTimeMode): string;
  previousMonth(): string;
  nextMonth(): string;
  today(): string;
  now(): string;
  clear(): string;
  done(): string;
  hours(): string;
  minutes(): string;
  /** Accessible name of the open panel. */
  panel(mode: HqDateTimeMode): string;
}

export const HQ_DATE_TIME_TOKEN_NAME = 'HQ_DATE_TIME_TEXTS';

export const HQ_DATE_TIME_TEXTS = new InjectionToken<HqDateTimeTexts>(HQ_DATE_TIME_TOKEN_NAME);

export const HQ_DATE_TIME_DEFAULT_TEXTS: HqDateTimeTexts = {
  placeholder: (mode) => (mode === 'time' ? 'hh:mm' : mode === 'datetime' ? 'dd-mm-yyyy hh:mm' : 'dd-mm-yyyy'),
  open: (mode) => (mode === 'time' ? 'Choose a time' : mode === 'datetime' ? 'Choose a date and time' : 'Choose a date'),
  previousMonth: () => 'Previous month',
  nextMonth: () => 'Next month',
  today: () => 'Today',
  now: () => 'Now',
  clear: () => 'Clear',
  done: () => 'Done',
  hours: () => 'Hours',
  minutes: () => 'Minutes',
  panel: (mode) => (mode === 'time' ? 'Time' : mode === 'datetime' ? 'Date and time' : 'Calendar'),
};
