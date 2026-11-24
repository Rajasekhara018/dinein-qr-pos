import { SchemaStep } from './onboarding-forms.models';
import { cloneSteps, errorMessage, formatDateTime } from './onboarding-forms.utils';

describe('onboarding-forms utils', () => {
  describe('errorMessage', () => {
    it('prefers the server message, then the server detail, then the error message, then the fallback', () => {
      expect(errorMessage({ error: { detail: 'D', message: 'M' }, message: 'E' }, 'F')).toBe('M');
      expect(errorMessage({ error: { detail: 'D' }, message: 'E' }, 'F')).toBe('D');
      expect(errorMessage({ error: { message: 'M' }, message: 'E' }, 'F')).toBe('M');
      expect(errorMessage({ message: 'E' }, 'F')).toBe('E');
      expect(errorMessage({}, 'F')).toBe('F');
      expect(errorMessage(null, 'F')).toBe('F');
      expect(errorMessage(undefined, 'F')).toBe('F');
    });
  });

  describe('cloneSteps', () => {
    it('copies steps, fields and options so edits never reach the original', () => {
      const original: SchemaStep[] = [{ key: 's', title: 'S', fields: [{ key: 'a', label: 'A', type: 'SELECT', required: true, options: ['x'] }, { key: 'b', label: 'B', type: 'TEXT', required: false }] }];
      const copy = cloneSteps(original);
      expect(copy).toEqual(original);
      copy[0].fields[0].options!.push('y');
      copy[0].fields[1].label = 'changed';
      copy[0].title = 'changed';
      expect(original[0].fields[0].options).toEqual(['x']);
      expect(original[0].fields[1].label).toBe('B');
      expect(original[0].title).toBe('S');
      expect(copy[0].fields[1].options).toBeUndefined();
    });
  });

  describe('formatDateTime', () => {
    it('shows a dash for a missing date', () => {
      expect(formatDateTime(undefined, 'en')).toBe('-');
    });

    it('formats in the given time zone', () => {
      const text = formatDateTime('2026-03-05T20:00:00Z', 'en', 'Asia/Kolkata');
      expect(text).toContain('2026');
      expect(text).toContain('06'); // 01:30 IST on the 6th
      expect(formatDateTime('2026-03-05T20:00:00Z', 'en', 'UTC')).not.toBe(text);
    });
  });
});
