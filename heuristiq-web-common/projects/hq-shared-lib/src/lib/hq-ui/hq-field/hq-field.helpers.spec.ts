import { describe, expect, it } from 'vitest';
import { HQ_FIELD_DEFAULT_ROWS, HQ_FIELD_TYPES } from './hq-field.constants';
import {
  hqFieldAutocomplete,
  hqFieldCounterShown,
  hqFieldDateLimit,
  hqFieldDateMode,
  hqFieldIsNewPastDate,
  hqFieldLaterDate,
  hqFieldToday,
  hqFieldNumberLimit,
  hqFieldDescribedBy,
  hqFieldInputMode,
  hqFieldKnownType,
  hqFieldParse,
  hqFieldResolveType,
  hqFieldRows,
  hqFieldText,
} from './hq-field.helpers';

describe('hq-field helpers', () => {
  describe('field types', () => {
    it('knows every type the component draws and nothing else', () => {
      for (const type of HQ_FIELD_TYPES) expect(hqFieldKnownType(type)).toBe(true);
      expect(hqFieldKnownType('checkbox')).toBe(false);
      expect(hqFieldKnownType('')).toBe(false);
    });

    it('draws an unknown or missing type as plain text', () => {
      expect(hqFieldResolveType('email')).toBe('email');
      expect(hqFieldResolveType('checkbox')).toBe('text');
      expect(hqFieldResolveType(null)).toBe('text');
      expect(hqFieldResolveType(undefined)).toBe('text');
    });
  });

  describe('keyboard and autofill hints', () => {
    it('asks for the right on-screen keyboard only where the type needs one', () => {
      expect(hqFieldInputMode('email')).toBe('email');
      expect(hqFieldInputMode('tel')).toBe('tel');
      expect(hqFieldInputMode('url')).toBe('url');
      expect(hqFieldInputMode('search')).toBe('search');
      expect(hqFieldInputMode('number')).toBe('decimal');
      expect(hqFieldInputMode('text')).toBeNull();
      expect(hqFieldInputMode('password')).toBeNull();
    });

    it('passes on only the autofill hint the screen asked for, and guesses nothing', () => {
      expect(hqFieldAutocomplete('')).toBeNull();
      expect(hqFieldAutocomplete('new-password')).toBe('new-password');
      expect(hqFieldAutocomplete('current-password')).toBe('current-password');
      expect(hqFieldAutocomplete('email')).toBe('email');
    });
  });

  describe('value', () => {
    it('reports text as typed, and a number for a number field', () => {
      expect(hqFieldParse('text', ' a b ')).toBe(' a b ');
      expect(hqFieldParse('number', '12.5')).toBe(12.5);
      expect(hqFieldParse('number', '-3')).toBe(-3);
      expect(hqFieldParse('number', '0')).toBe(0);
    });

    it('reports nothing for an empty or unusable number', () => {
      expect(hqFieldParse('number', '')).toBeNull();
      expect(hqFieldParse('number', '   ')).toBeNull();
      expect(hqFieldParse('number', 'abc')).toBeNull();
      expect(hqFieldParse('number', 'Infinity')).toBeNull();
    });

    it('shows a value as text, and nothing for none', () => {
      expect(hqFieldText('abc')).toBe('abc');
      expect(hqFieldText(0)).toBe('0');
      expect(hqFieldText(12.5)).toBe('12.5');
      expect(hqFieldText(null)).toBe('');
      expect(hqFieldText(undefined)).toBe('');
    });
  });

  describe('counter', () => {
    it('is shown for a text or textarea field with a limit, unless switched off', () => {
      expect(hqFieldCounterShown('text', 254, null)).toBe(true);
      expect(hqFieldCounterShown('textarea', 500, null)).toBe(true);
      expect(hqFieldCounterShown('text', 254, false)).toBe(false);
    });

    it('is not shown for other types by default, but can be asked for', () => {
      expect(hqFieldCounterShown('email', 254, null)).toBe(false);
      expect(hqFieldCounterShown('password', 64, null)).toBe(false);
      expect(hqFieldCounterShown('email', 254, true)).toBe(true);
    });

    it('is never shown without a positive limit', () => {
      expect(hqFieldCounterShown('text', null, true)).toBe(false);
      expect(hqFieldCounterShown('text', 0, true)).toBe(false);
      expect(hqFieldCounterShown('text', -5, true)).toBe(false);
    });
  });

  describe('textarea rows', () => {
    it('uses the requested rows, or the default for a missing or invalid number', () => {
      expect(hqFieldRows(6)).toBe(6);
      expect(hqFieldRows(null)).toBe(HQ_FIELD_DEFAULT_ROWS);
      expect(hqFieldRows(0)).toBe(HQ_FIELD_DEFAULT_ROWS);
      expect(hqFieldRows(2.5)).toBe(HQ_FIELD_DEFAULT_ROWS);
      expect(hqFieldRows(-1)).toBe(HQ_FIELD_DEFAULT_ROWS);
    });
  });

  describe('aria-describedby', () => {
    it('lists the error first, then the hint, and only those that are shown', () => {
      expect(hqFieldDescribedBy('f', true, true)).toBe('f-error f-hint');
      expect(hqFieldDescribedBy('f', true, false)).toBe('f-hint');
      expect(hqFieldDescribedBy('f', false, true)).toBe('f-error');
      expect(hqFieldDescribedBy('f', false, false)).toBeNull();
    });
  });

  describe('date-time fields', () => {
    it('draws date, time and datetime-local as the common date-time field, and nothing else', () => {
      expect(hqFieldDateMode('date')).toBe('date');
      expect(hqFieldDateMode('time')).toBe('time');
      expect(hqFieldDateMode('datetime-local')).toBe('datetime');
      expect(hqFieldDateMode('text')).toBeNull();
      expect(hqFieldDateMode('number')).toBeNull();
    });

    it('takes only text as a date limit', () => {
      expect(hqFieldDateLimit('2026-11-14')).toBe('2026-11-14');
      expect(hqFieldDateLimit('')).toBeNull();
      expect(hqFieldDateLimit(5)).toBeNull();
      expect(hqFieldDateLimit(null)).toBeNull();
      expect(hqFieldDateLimit(undefined)).toBeNull();
    });
  });

  describe('number limits', () => {
    it('reads a limit given as a number or as text, and nothing from anything else', () => {
      expect(hqFieldNumberLimit(5)).toBe(5);
      expect(hqFieldNumberLimit('18')).toBe(18);
      expect(hqFieldNumberLimit('-2.5')).toBe(-2.5);
      expect(hqFieldNumberLimit(0)).toBe(0);
      expect(hqFieldNumberLimit('')).toBeNull();
      expect(hqFieldNumberLimit('abc')).toBeNull();
      expect(hqFieldNumberLimit(null)).toBeNull();
      expect(hqFieldNumberLimit(undefined)).toBeNull();
    });
  });

  describe('past dates (noPastDates)', () => {
    it('gives today in the browser time zone as YYYY-MM-DD', () => {
      expect(hqFieldToday(new Date(2026, 10, 19, 23, 59))).toBe('2026-11-19');
      expect(hqFieldToday(new Date(2026, 0, 5))).toBe('2026-01-05');
    });

    it('takes the later of two date limits and ignores a missing one', () => {
      expect(hqFieldLaterDate('2026-11-01', '2026-11-19')).toBe('2026-11-19');
      expect(hqFieldLaterDate('2026-12-01', '2026-11-19')).toBe('2026-12-01');
      expect(hqFieldLaterDate(null, '2026-11-19')).toBe('2026-11-19');
      expect(hqFieldLaterDate('2026-11-19', null)).toBe('2026-11-19');
    });

    it('refuses a newly chosen past date but keeps today, the future and the date a record was loaded with', () => {
      const today = '2026-11-19';
      expect(hqFieldIsNewPastDate('2026-11-18', null, today)).toBe(true);
      expect(hqFieldIsNewPastDate('2026-11-18T10:00', null, today)).toBe(true);
      expect(hqFieldIsNewPastDate('2026-11-19', null, today)).toBe(false);
      expect(hqFieldIsNewPastDate('2027-01-01', null, today)).toBe(false);
      expect(hqFieldIsNewPastDate('', null, today)).toBe(false);
      expect(hqFieldIsNewPastDate('2026-10-01', '2026-10-01', today)).toBe(false);
      expect(hqFieldIsNewPastDate('2026-10-02', '2026-10-01', today)).toBe(true);
    });
  });
});
