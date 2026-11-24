import { describe, expect, it } from 'vitest';
import { ISO_COUNTRY_CODES, ORGANIZATION_LANGUAGES } from './regional-names.constants';
import { countryOptions, currencyOptions, languageLabel, languageOptions, timeZoneOptions } from './regional-names.helpers';

describe('regional names', () => {
  it('shows a language by its own name and its English name instead of its code', () => {
    expect(languageLabel('TE')).toBe('తెలుగు (Telugu)');
    expect(languageLabel('KA')).toBe('ಕನ್ನಡ (Kannada)');
    expect(languageLabel('HI')).toBe('हिन्दी (Hindi)');
    expect(languageLabel('EN')).toBe('English');
    expect(languageLabel('te')).toBe('తెలుగు (Telugu)');
    expect(languageLabel('')).toBe('');
    expect(languageLabel(null)).toBe('');
  });

  it('lists the organization languages in order with their names', () => {
    const options = languageOptions(ORGANIZATION_LANGUAGES);
    expect(options.map((o) => o.value)).toEqual(['EN', 'HI', 'TE', 'TA', 'KA', 'ML', 'AR']);
    expect(options.every((o) => o.label !== o.value)).toBe(true);
  });

  it('names every ISO country in the screen language, with its code, sorted by name', () => {
    const options = countryOptions('en');
    expect(options).toHaveLength(ISO_COUNTRY_CODES.length);
    expect(options).toContainEqual({ value: 'IN', label: 'India (IN)' });
    const labels = options.map((o) => o.label);
    expect([...labels].sort((a, b) => a.localeCompare(b, 'en'))).toEqual(labels);
    expect(countryOptions('hi').find((o) => o.value === 'IN')?.label).toBe('भारत (IN)');
  });

  it('names currencies with their code and lists IANA time zones', () => {
    expect(currencyOptions('en')).toContainEqual({ value: 'INR', label: 'Indian Rupee (INR)' });
    expect(timeZoneOptions()).toContainEqual({ value: 'Asia/Kolkata', label: 'Asia/Kolkata' });
    expect(timeZoneOptions().find((o) => o.value === 'America/New_York')?.label).toBe('America/New York');
  });
});
