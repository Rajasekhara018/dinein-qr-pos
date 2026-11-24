import { describe, expect, it } from 'vitest';
import { EMPTY_VALUE } from './onboarded-users.constants';
import {
  answerText,
  displayValue,
  formatBytes,
  formatDateTime,
  franchiseLabel,
  organizationLabel,
  placeLabel,
} from './onboarded-users.utils';

describe('placement labels', () => {
  it('shows the organization name with its code, else the code or stored id, else a dash', () => {
    expect(organizationLabel({ tenantId: 'org-a', organizationCode: 'CITYCABS', organizationName: 'City Cabs' })).toBe('City Cabs (CITYCABS)');
    expect(organizationLabel({ tenantId: 'org-a', organizationName: 'City Cabs' })).toBe('City Cabs');
    expect(organizationLabel({ tenantId: 'org-a', organizationCode: 'CITYCABS' })).toBe('CITYCABS');
    expect(organizationLabel({ tenantId: 'org-a' })).toBe('org-a');
    expect(organizationLabel({})).toBe(EMPTY_VALUE);
  });

  it('shows the franchise code and name, else the stored id when Franchise could not answer, else a dash', () => {
    expect(franchiseLabel({ franchiseId: 'f-1', franchiseCode: 'HYD1', franchiseName: 'Hyd One' })).toBe('HYD1 · Hyd One');
    expect(franchiseLabel({ franchiseId: 'f-1', franchiseName: 'Hyd One' })).toBe('Hyd One');
    expect(franchiseLabel({ franchiseId: 'f-1', franchiseCode: 'HYD1', franchiseName: 'HYD1' })).toBe('HYD1');
    expect(franchiseLabel({ franchiseId: 'f-1', franchiseCode: ' ' })).toBe('f-1');
    expect(franchiseLabel({})).toBe(EMPTY_VALUE);
  });

  it('shows the place name, else its id, else a dash', () => {
    expect(placeLabel({ placeId: 'vja', placeName: 'Vijayawada' })).toBe('Vijayawada');
    expect(placeLabel({ placeId: 'vja' })).toBe('vja');
    expect(placeLabel({})).toBe(EMPTY_VALUE);
  });
});

describe('displayValue', () => {
  it('shows a dash for empty values', () => {
    for (const empty of [null, undefined, '']) expect(displayValue(empty)).toBe(EMPTY_VALUE);
  });
  it('keeps falsy but meaningful values, stringifies primitives and serialises objects', () => {
    expect(displayValue(0)).toBe('0');
    expect(displayValue(false)).toBe('false');
    expect(displayValue('abc')).toBe('abc');
    expect(displayValue({ a: 1 })).toBe('{"a":1}');
    expect(displayValue([1, 2])).toBe('[1,2]');
  });
});

describe('answerText', () => {
  it('reads one answer, with a dash when the answer or the whole map is missing', () => {
    expect(answerText({ answers: { name: 'Asha' } }, 'name')).toBe('Asha');
    expect(answerText({ answers: { name: 'Asha' } }, 'other')).toBe(EMPTY_VALUE);
    expect(answerText({ answers: null }, 'name')).toBe(EMPTY_VALUE);
    expect(answerText({}, 'name')).toBe(EMPTY_VALUE);
  });
});

describe('formatBytes', () => {
  it('formats bytes, kilobytes and megabytes, and a dash for nothing', () => {
    expect(formatBytes(null)).toBe(EMPTY_VALUE);
    expect(formatBytes(undefined)).toBe(EMPTY_VALUE);
    expect(formatBytes(0)).toBe(EMPTY_VALUE);
    expect(formatBytes(500)).toBe('500 B');
    expect(formatBytes(1536)).toBe('1.5 KB');
    expect(formatBytes(1024 * 1024)).toBe('1.0 MB');
    expect(formatBytes(5 * 1024 * 1024 + 512 * 1024)).toBe('5.5 MB');
  });
});

describe('formatDateTime', () => {
  it('shows a dash without a date', () => {
    expect(formatDateTime(null)).toBe(EMPTY_VALUE);
    expect(formatDateTime(undefined)).toBe(EMPTY_VALUE);
    expect(formatDateTime('')).toBe(EMPTY_VALUE);
  });

  it('renders the instant in the requested zone', () => {
    const iso = '2026-03-10T20:30:00Z';
    const kolkata = formatDateTime(iso, 'Asia/Kolkata');
    const utc = formatDateTime(iso, 'UTC');
    expect(kolkata).toContain('11');
    expect(kolkata).toContain('2026');
    expect(kolkata).not.toBe(utc);
    expect(utc).toMatch(/10/);
  });
});
