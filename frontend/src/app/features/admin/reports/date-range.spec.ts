import { describe, expect, it } from 'vitest';
import { detectPreset, eachDay, presetRange, rangeProblem } from './date-range';

// 27 Sep 2026, 01:00 IST (still 26 Sep in UTC) — presets must use the IST business date.
const lateNightUtc = new Date('2026-09-26T19:30:00Z');

describe('report date-range presets (IST)', () => {
  it('uses the IST date, not the UTC date', () => {
    expect(presetRange('today', lateNightUtc)).toEqual({ from: '2026-09-27', to: '2026-09-27' });
    expect(presetRange('yesterday', lateNightUtc)).toEqual({ from: '2026-09-26', to: '2026-09-26' });
  });

  it('computes inclusive rolling windows and this month', () => {
    expect(presetRange('last7', lateNightUtc)).toEqual({ from: '2026-09-21', to: '2026-09-27' });
    expect(presetRange('last30', lateNightUtc)).toEqual({ from: '2026-08-29', to: '2026-09-27' });
    expect(presetRange('thisMonth', lateNightUtc)).toEqual({ from: '2026-09-01', to: '2026-09-27' });
  });

  it('crosses month and year boundaries', () => {
    const newYear = new Date('2027-01-01T06:00:00Z');
    expect(presetRange('yesterday', newYear)).toEqual({ from: '2026-12-31', to: '2026-12-31' });
    expect(presetRange('last7', new Date('2024-03-02T06:00:00Z'))).toEqual({ from: '2024-02-25', to: '2024-03-02' });
  });

  it('detects which preset a range matches', () => {
    expect(detectPreset({ from: '2026-09-21', to: '2026-09-27' }, lateNightUtc)).toBe('last7');
    expect(detectPreset({ from: '2026-09-02', to: '2026-09-05' }, lateNightUtc)).toBe('custom');
  });

  it('validates custom ranges like the backend', () => {
    expect(rangeProblem({ from: '2026-09-27', to: '2026-09-01' })).toContain('start date');
    expect(rangeProblem({ from: '2024-01-01', to: '2026-01-01' })).toContain('one year');
    expect(rangeProblem({ from: '2026-09-01', to: '2026-09-27' })).toBeNull();
  });

  it('enumerates every day of a range', () => {
    expect(eachDay({ from: '2026-02-27', to: '2026-03-02' })).toEqual([
      '2026-02-27',
      '2026-02-28',
      '2026-03-01',
      '2026-03-02',
    ]);
  });
});
