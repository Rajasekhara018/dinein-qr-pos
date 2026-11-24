import { describe, expect, it } from 'vitest';
import { hqReadSetting } from './hq-app-setting';

describe('hqReadSetting', () => {
  it('returns a fixed value, trimmed', () => {
    expect(hqReadSetting(' AED ', 'INR')).toBe('AED');
  });

  it('calls a function each time, so a value known later is picked up', () => {
    let current: string | null = null;
    const setting = () => current;
    expect(hqReadSetting(setting, 'INR')).toBe('INR');
    current = 'USD';
    expect(hqReadSetting(setting, 'INR')).toBe('USD');
  });

  it('falls back when the setting is missing or blank', () => {
    expect(hqReadSetting(null, 'IN')).toBe('IN');
    expect(hqReadSetting(undefined, 'IN')).toBe('IN');
    expect(hqReadSetting('  ', 'IN')).toBe('IN');
    expect(hqReadSetting(() => undefined, 'IN')).toBe('IN');
  });
});
