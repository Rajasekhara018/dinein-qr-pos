import { describe, expect, it } from 'vitest';
import {
  hqAddDays,
  hqAddMonths,
  hqClampDate,
  hqCompareDate,
  hqDaysInMonth,
  hqFormatDisplayDate,
  hqFormatIsoDate,
  hqFormatIsoDateTime,
  hqFormatIsoTime,
  hqIsDayDisabled,
  hqIsRealDate,
  hqMinuteOptions,
  hqModeOfTag,
  hqMonthGrid,
  hqNormalStep,
  hqNowTime,
  hqParseIsoDate,
  hqParseIsoDateTime,
  hqParseIsoTime,
  hqParseTypedDate,
  hqParseTypedTime,
  hqToday,
  hqWeekdayLabels,
} from './hq-date-time.helpers';

const d = (year: number, month: number, day: number) => ({ year, month, day });

describe('hq-date-time helpers', () => {
  describe('real days', () => {
    it('knows the length of every month, leap years included', () => {
      expect(hqDaysInMonth(2026, 1)).toBe(31);
      expect(hqDaysInMonth(2026, 2)).toBe(28);
      expect(hqDaysInMonth(2028, 2)).toBe(29);
      expect(hqDaysInMonth(1900, 2)).toBe(28);
      expect(hqDaysInMonth(2000, 2)).toBe(29);
      expect(hqDaysInMonth(2026, 4)).toBe(30);
      expect(hqDaysInMonth(2026, 12)).toBe(31);
    });

    it('accepts only days that exist', () => {
      expect(hqIsRealDate(d(2026, 11, 14))).toBe(true);
      expect(hqIsRealDate(d(2028, 2, 29))).toBe(true);
      expect(hqIsRealDate(d(2026, 2, 29))).toBe(false);
      expect(hqIsRealDate(d(2026, 13, 1))).toBe(false);
      expect(hqIsRealDate(d(2026, 0, 1))).toBe(false);
      expect(hqIsRealDate(d(2026, 4, 31))).toBe(false);
      expect(hqIsRealDate(d(0, 1, 1))).toBe(false);
      expect(hqIsRealDate(d(2026, 1.5, 1))).toBe(false);
    });
  });

  describe('the text a native input holds', () => {
    it('writes and reads a day', () => {
      expect(hqFormatIsoDate(d(2026, 3, 7))).toBe('2026-03-07');
      expect(hqParseIsoDate('2026-03-07')).toEqual(d(2026, 3, 7));
      expect(hqParseIsoDate(' 2026-03-07 ')).toEqual(d(2026, 3, 7));
    });

    it('reads nothing from an empty, malformed or impossible day', () => {
      expect(hqParseIsoDate('')).toBeNull();
      expect(hqParseIsoDate(null)).toBeNull();
      expect(hqParseIsoDate(undefined)).toBeNull();
      expect(hqParseIsoDate('07-03-2026')).toBeNull();
      expect(hqParseIsoDate('2026-02-30')).toBeNull();
      expect(hqParseIsoDate('2026-3-7')).toBeNull();
    });

    it('writes and reads a time, with or without seconds', () => {
      expect(hqFormatIsoTime({ hour: 9, minute: 5 })).toBe('09:05');
      expect(hqParseIsoTime('09:05')).toEqual({ hour: 9, minute: 5 });
      expect(hqParseIsoTime('23:59:59')).toEqual({ hour: 23, minute: 59 });
      expect(hqParseIsoTime('23:59:59.123')).toEqual({ hour: 23, minute: 59 });
    });

    it('reads nothing from an empty, malformed or impossible time', () => {
      expect(hqParseIsoTime('')).toBeNull();
      expect(hqParseIsoTime(null)).toBeNull();
      expect(hqParseIsoTime('24:00')).toBeNull();
      expect(hqParseIsoTime('12:60')).toBeNull();
      expect(hqParseIsoTime('9:05')).toBeNull();
    });

    it('writes and reads a day and a time together', () => {
      expect(hqFormatIsoDateTime(d(2026, 11, 14), { hour: 12, minute: 30 })).toBe('2026-11-14T12:30');
      expect(hqParseIsoDateTime('2026-11-14T12:30')).toEqual({ date: d(2026, 11, 14), time: { hour: 12, minute: 30 } });
      expect(hqParseIsoDateTime('2026-11-14 12:30')).toEqual({ date: d(2026, 11, 14), time: { hour: 12, minute: 30 } });
      expect(hqParseIsoDateTime('2026-11-14T12:30:15')).toEqual({ date: d(2026, 11, 14), time: { hour: 12, minute: 30 } });
    });

    it('reads nothing from half a value', () => {
      expect(hqParseIsoDateTime('2026-11-14')).toBeNull();
      expect(hqParseIsoDateTime('2026-11-14T')).toBeNull();
      expect(hqParseIsoDateTime('2026-02-30T10:00')).toBeNull();
      expect(hqParseIsoDateTime('2026-11-14T25:00')).toBeNull();
      expect(hqParseIsoDateTime(null)).toBeNull();
    });
  });

  describe('what the user types', () => {
    it('shows a day as dd-MM-yyyy', () => {
      expect(hqFormatDisplayDate(d(2026, 3, 7))).toBe('07-03-2026');
    });

    it('reads a day typed day first with any usual separator, or in the ISO order', () => {
      expect(hqParseTypedDate('07-03-2026')).toEqual(d(2026, 3, 7));
      expect(hqParseTypedDate('7/3/2026')).toEqual(d(2026, 3, 7));
      expect(hqParseTypedDate('07.03.2026')).toEqual(d(2026, 3, 7));
      expect(hqParseTypedDate('07 03 2026')).toEqual(d(2026, 3, 7));
      expect(hqParseTypedDate('2026-03-07')).toEqual(d(2026, 3, 7));
      expect(hqParseTypedDate('2026/3/7')).toEqual(d(2026, 3, 7));
      expect(hqParseTypedDate('  14-11-2026 ')).toEqual(d(2026, 11, 14));
    });

    it('does not guess a short year or accept an impossible day', () => {
      expect(hqParseTypedDate('07-03-26')).toBeNull();
      expect(hqParseTypedDate('30-02-2026')).toBeNull();
      expect(hqParseTypedDate('13-13-2026')).toBeNull();
      expect(hqParseTypedDate('07-03')).toBeNull();
      expect(hqParseTypedDate('')).toBeNull();
      expect(hqParseTypedDate(null)).toBeNull();
      expect(hqParseTypedDate('abc')).toBeNull();
    });

    it('reads a time typed as HH:mm, H:mm or HHmm', () => {
      expect(hqParseTypedTime('09:05')).toEqual({ hour: 9, minute: 5 });
      expect(hqParseTypedTime('9:05')).toEqual({ hour: 9, minute: 5 });
      expect(hqParseTypedTime('0905')).toEqual({ hour: 9, minute: 5 });
      expect(hqParseTypedTime('23:59')).toEqual({ hour: 23, minute: 59 });
    });

    it('rejects a time that is not on the clock', () => {
      expect(hqParseTypedTime('24:00')).toBeNull();
      expect(hqParseTypedTime('12:61')).toBeNull();
      expect(hqParseTypedTime('9')).toBeNull();
      expect(hqParseTypedTime('')).toBeNull();
      expect(hqParseTypedTime(null)).toBeNull();
    });
  });

  describe('ranges', () => {
    it('orders days', () => {
      expect(hqCompareDate(d(2026, 1, 1), d(2026, 1, 2))).toBeLessThan(0);
      expect(hqCompareDate(d(2027, 1, 1), d(2026, 12, 31))).toBeGreaterThan(0);
      expect(hqCompareDate(d(2026, 5, 5), d(2026, 5, 5))).toBe(0);
      expect(hqCompareDate(d(2026, 2, 1), d(2026, 1, 31))).toBeGreaterThan(0);
    });

    it('disables a day outside the range, ends included', () => {
      const min = d(2026, 11, 10);
      const max = d(2026, 11, 20);
      expect(hqIsDayDisabled(d(2026, 11, 9), min, max)).toBe(true);
      expect(hqIsDayDisabled(d(2026, 11, 10), min, max)).toBe(false);
      expect(hqIsDayDisabled(d(2026, 11, 20), min, max)).toBe(false);
      expect(hqIsDayDisabled(d(2026, 11, 21), min, max)).toBe(true);
      expect(hqIsDayDisabled(d(1999, 1, 1), null, null)).toBe(false);
      expect(hqIsDayDisabled(d(1999, 1, 1), null, max)).toBe(false);
      expect(hqIsDayDisabled(d(2999, 1, 1), min, null)).toBe(false);
    });

    it('moves a day to the nearest allowed one', () => {
      const min = d(2026, 11, 10);
      const max = d(2026, 11, 20);
      expect(hqClampDate(d(2026, 1, 1), min, max)).toEqual(min);
      expect(hqClampDate(d(2027, 1, 1), min, max)).toEqual(max);
      expect(hqClampDate(d(2026, 11, 15), min, max)).toEqual(d(2026, 11, 15));
      expect(hqClampDate(d(2026, 11, 15), null, null)).toEqual(d(2026, 11, 15));
    });
  });

  describe('moving through the calendar', () => {
    it('moves by months across the end of a year', () => {
      expect(hqAddMonths(2026, 11, 1)).toEqual({ year: 2026, month: 12 });
      expect(hqAddMonths(2026, 12, 1)).toEqual({ year: 2027, month: 1 });
      expect(hqAddMonths(2026, 1, -1)).toEqual({ year: 2025, month: 12 });
      expect(hqAddMonths(2026, 3, 12)).toEqual({ year: 2027, month: 3 });
      expect(hqAddMonths(2026, 3, -15)).toEqual({ year: 2024, month: 12 });
    });

    it('moves by days across month ends and leap days', () => {
      expect(hqAddDays(d(2026, 1, 31), 1)).toEqual(d(2026, 2, 1));
      expect(hqAddDays(d(2026, 3, 1), -1)).toEqual(d(2026, 2, 28));
      expect(hqAddDays(d(2028, 3, 1), -1)).toEqual(d(2028, 2, 29));
      expect(hqAddDays(d(2026, 12, 31), 1)).toEqual(d(2027, 1, 1));
      expect(hqAddDays(d(2026, 11, 14), 7)).toEqual(d(2026, 11, 21));
    });

    it('draws six full weeks that start on the chosen weekday and contain the whole month', () => {
      // November 2026 starts on a Sunday.
      const sundayFirst = hqMonthGrid(2026, 11, 0);
      expect(sundayFirst).toHaveLength(42);
      expect(sundayFirst[0].iso).toBe('2026-11-01');
      expect(sundayFirst[0].inMonth).toBe(true);
      expect(sundayFirst.filter((cell) => cell.inMonth)).toHaveLength(30);
      expect(sundayFirst[41].iso).toBe('2026-12-12');

      const mondayFirst = hqMonthGrid(2026, 11, 1);
      expect(mondayFirst[0].iso).toBe('2026-10-26');
      expect(mondayFirst[0].inMonth).toBe(false);
      expect(mondayFirst[6].iso).toBe('2026-11-01');
      expect(mondayFirst.filter((cell) => cell.inMonth)).toHaveLength(30);
    });

    it('starts a month that begins on the first weekday with that day, not a blank week', () => {
      expect(hqMonthGrid(2026, 2, 0)[0].iso).toBe('2026-02-01');
      expect(hqMonthGrid(2026, 2, 0).filter((cell) => cell.inMonth)).toHaveLength(28);
    });

    it('marks a leap February correctly', () => {
      expect(hqMonthGrid(2028, 2, 0).filter((cell) => cell.inMonth)).toHaveLength(29);
    });
  });

  describe('now', () => {
    it('reads today and the time of day from a clock', () => {
      const now = new Date(2026, 10, 14, 13, 47, 5);
      expect(hqToday(now)).toEqual(d(2026, 11, 14));
      expect(hqNowTime(now)).toEqual({ hour: 13, minute: 47 });
    });

    it('rounds the minute down to the step', () => {
      const now = new Date(2026, 10, 14, 13, 47);
      expect(hqNowTime(now, 15)).toEqual({ hour: 13, minute: 45 });
      expect(hqNowTime(now, 30)).toEqual({ hour: 13, minute: 30 });
      expect(hqNowTime(now, 5)).toEqual({ hour: 13, minute: 45 });
    });
  });

  describe('the clock', () => {
    it('accepts only steps that divide the hour', () => {
      for (const step of [1, 2, 3, 4, 5, 6, 10, 12, 15, 20, 30, 60]) expect(hqNormalStep(step)).toBe(step);
      for (const step of [0, 7, 8, 25, 45, 61, -5, 2.5]) expect(hqNormalStep(step)).toBe(1);
      expect(hqNormalStep(null)).toBe(1);
      expect(hqNormalStep(undefined)).toBe(1);
    });

    it('offers the minutes of a step', () => {
      expect(hqMinuteOptions(15)).toEqual([0, 15, 30, 45]);
      expect(hqMinuteOptions(1)).toHaveLength(60);
      expect(hqMinuteOptions(60)).toEqual([0]);
      expect(hqMinuteOptions(7)).toHaveLength(60);
    });
  });

  describe('names', () => {
    it('lists seven weekday names starting on the chosen day', () => {
      const sunday = hqWeekdayLabels('en', 0);
      expect(sunday).toHaveLength(7);
      expect(sunday[0]).toMatch(/^Sun/);
      expect(sunday[6]).toMatch(/^Sat/);
      const monday = hqWeekdayLabels('en', 1);
      expect(monday[0]).toMatch(/^Mon/);
      expect(monday[6]).toMatch(/^Sun/);
    });
  });

  describe('which kind of field a tag is', () => {
    it('reads the mode from the tag', () => {
      expect(hqModeOfTag('hq-date-input')).toBe('date');
      expect(hqModeOfTag('HQ-TIME-INPUT')).toBe('time');
      expect(hqModeOfTag('hq-datetime-input')).toBe('datetime');
      expect(hqModeOfTag('div')).toBe('date');
    });
  });
});
