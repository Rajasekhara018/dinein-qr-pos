import ar from '../../assets/i18n/ar.json';
import en from '../../assets/i18n/en.json';
import hi from '../../assets/i18n/hi.json';
import kn from '../../assets/i18n/kn.json';
import ml from '../../assets/i18n/ml.json';
import ta from '../../assets/i18n/ta.json';
import te from '../../assets/i18n/te.json';
import {
  COVERAGE_AGE_KEYS,
  COVERAGE_ERROR_KEYS,
  COVERAGE_KINDS,
  COVERAGE_KIND_KEYS,
  COVERAGE_LEVELS,
  COVERAGE_LEVEL_KEYS,
  COVERAGE_MESSAGE_KEYS,
  COVERAGE_SORT_KEYS,
  COVERAGE_SORT_MODES,
} from './system-coverage.constants';

const LANGUAGES: Record<string, Record<string, unknown>> = { en, hi, ta, te, kn, ml, ar };

/** Flattens the strings under `root` (and, optionally, extra top-level paths) to `dotted.key -> text`. */
function flatten(node: unknown, prefix: string, out: Record<string, string> = {}): Record<string, string> {
  if (typeof node === 'string') {
    out[prefix] = node;
  } else if (node && typeof node === 'object') {
    Object.entries(node).forEach(([k, v]) => flatten(v, prefix ? `${prefix}.${k}` : k, out));
  }
  return out;
}

function screenTexts(lang: string): Record<string, string> {
  const root = LANGUAGES[lang];
  const nav = (root['nav'] as { items: Record<string, unknown> }).items['codeCoverage'];
  return { ...flatten(root['systemCoverage'], 'systemCoverage'), ...flatten(nav, 'nav.items.codeCoverage') };
}

const params = (text: string): string[] => (text.match(/{{\s*\w+\s*}}/g) ?? []).map((p) => p.replace(/\s/g, '')).sort();

describe('systemCoverage i18n', () => {
  const english = screenTexts('en');
  const englishKeys = Object.keys(english).sort();

  it('has strings for the screen', () => {
    expect(englishKeys.length).toBeGreaterThan(60);
  });

  it.each(Object.keys(LANGUAGES))('%s has exactly the same keys as English', (lang) => {
    expect(Object.keys(screenTexts(lang)).sort()).toEqual(englishKeys);
  });

  it.each(Object.keys(LANGUAGES))('%s has no empty text and the same {{params}} as English', (lang) => {
    const texts = screenTexts(lang);
    englishKeys.forEach((key) => {
      expect(texts[key].trim(), `${lang}:${key}`).not.toBe('');
      expect(params(texts[key]), `${lang}:${key}`).toEqual(params(english[key]));
    });
  });

  it.each(Object.keys(LANGUAGES).filter((l) => l !== 'en'))('%s is translated, not English copy-pasted', (lang) => {
    const texts = screenTexts(lang);
    const same = englishKeys.filter((key) => texts[key] === english[key]);
    expect(same, `${lang} keeps English text`).toEqual([]);
  });

  it('defines every translation key the screen refers to from its constants', () => {
    const keys = [
      ...Object.values(COVERAGE_KIND_KEYS),
      ...Object.values(COVERAGE_LEVEL_KEYS),
      ...Object.values(COVERAGE_SORT_KEYS),
      ...Object.values(COVERAGE_AGE_KEYS),
      ...Object.values(COVERAGE_ERROR_KEYS),
      ...Object.values(COVERAGE_MESSAGE_KEYS),
    ];
    keys.forEach((key) => expect(english[key], key).toBeTruthy());
  });

  it('has a label key for every kind, level and sort mode', () => {
    COVERAGE_KINDS.forEach((k) => expect(COVERAGE_KIND_KEYS[k]).toBeTruthy());
    COVERAGE_LEVELS.forEach((l) => expect(COVERAGE_LEVEL_KEYS[l]).toBeTruthy());
    COVERAGE_SORT_MODES.forEach((m) => expect(COVERAGE_SORT_KEYS[m]).toBeTruthy());
  });
});
