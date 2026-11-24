import { describe, expect, it } from 'vitest';
import { UserPreference } from '../models/uam.models';
import { languageCodeFromEnum, languageEnumFromCode, preferredLanguageCode } from './language-preference.helpers';

describe('language preference helpers', () => {
  it('maps every language the account holds and the app shows, both ways (Kannada is KA on the account, kn in the app)', () => {
    expect(['EN', 'HI', 'TA', 'TE', 'KA', 'ML', 'AR'].map((value) => languageCodeFromEnum(value)))
      .toEqual(['en', 'hi', 'ta', 'te', 'kn', 'ml', 'ar']);
    expect(['en', 'hi', 'ta', 'te', 'kn', 'ml', 'ar'].map((code) => languageEnumFromCode(code)))
      .toEqual(['EN', 'HI', 'TA', 'TE', 'KA', 'ML', 'AR']);
  });

  it('ignores a language the app has no text for, an unknown one and an empty one', () => {
    expect(languageCodeFromEnum('OD')).toBeNull();
    expect(languageCodeFromEnum('XX')).toBeNull();
    expect(languageCodeFromEnum(null)).toBeNull();
    expect(languageCodeFromEnum('')).toBeNull();
    expect(languageEnumFromCode('od')).toBeNull();
    expect(languageEnumFromCode(undefined)).toBeNull();
  });

  it('accepts either case', () => {
    expect(languageCodeFromEnum('hi')).toBe('hi');
    expect(languageEnumFromCode('HI')).toBe('HI');
  });

  it('reads the language from Preferences > Regional', () => {
    expect(preferredLanguageCode({ userId: 'u1', regional: { language: 'TA' } } as UserPreference)).toBe('ta');
    expect(preferredLanguageCode({ userId: 'u1', regional: { language: null } } as UserPreference)).toBeNull();
    expect(preferredLanguageCode({ userId: 'u1' } as UserPreference)).toBeNull();
    expect(preferredLanguageCode(null)).toBeNull();
  });
});
