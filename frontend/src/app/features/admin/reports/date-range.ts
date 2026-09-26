import { IsoLocalDate } from '../../../core/api/models';
import { addDays, daysBetween, istDate } from '../shared/ist-date';

export type RangePreset = 'today' | 'yesterday' | 'last7' | 'last30' | 'thisMonth' | 'custom';

export interface DateRange {
  from: IsoLocalDate;
  to: IsoLocalDate;
}

export const RANGE_PRESETS: readonly { id: Exclude<RangePreset, 'custom'>; label: string }[] = [
  { id: 'today', label: 'Today' },
  { id: 'yesterday', label: 'Yesterday' },
  { id: 'last7', label: 'Last 7 days' },
  { id: 'last30', label: 'Last 30 days' },
  { id: 'thisMonth', label: 'This month' },
];

/** Backend limit (`ReportService.MAX_RANGE_DAYS`). */
export const MAX_RANGE_DAYS = 366;

/** Inclusive IST date range for a preset ("last 7 days" = today and the 6 days before). */
export function presetRange(preset: Exclude<RangePreset, 'custom'>, now: Date = new Date()): DateRange {
  const today = istDate(now);
  switch (preset) {
    case 'today':
      return { from: today, to: today };
    case 'yesterday': {
      const y = addDays(today, -1);
      return { from: y, to: y };
    }
    case 'last7':
      return { from: addDays(today, -6), to: today };
    case 'last30':
      return { from: addDays(today, -29), to: today };
    case 'thisMonth':
      return { from: `${today.slice(0, 7)}-01`, to: today };
  }
}

/** The preset matching a range, or 'custom'. */
export function detectPreset(range: DateRange, now: Date = new Date()): RangePreset {
  for (const { id } of RANGE_PRESETS) {
    const r = presetRange(id, now);
    if (r.from === range.from && r.to === range.to) return id;
  }
  return 'custom';
}

/** Validation message for a custom range, or null when valid. */
export function rangeProblem(range: DateRange): string | null {
  if (!range.from || !range.to) return 'Choose both dates.';
  if (range.from > range.to) return 'The start date must be on or before the end date.';
  if (daysBetween(range.from, range.to) > MAX_RANGE_DAYS) return 'Choose a range of one year or less.';
  return null;
}

/** Every date of the range (for filling gaps in the daily series). */
export function eachDay(range: DateRange): IsoLocalDate[] {
  const days: IsoLocalDate[] = [];
  const count = daysBetween(range.from, range.to);
  for (let i = 0; i <= count && i <= MAX_RANGE_DAYS; i++) days.push(addDays(range.from, i));
  return days;
}
