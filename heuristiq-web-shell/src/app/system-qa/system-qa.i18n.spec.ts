import ar from '../../assets/i18n/ar.json';
import en from '../../assets/i18n/en.json';
import hi from '../../assets/i18n/hi.json';
import kn from '../../assets/i18n/kn.json';
import ml from '../../assets/i18n/ml.json';
import ta from '../../assets/i18n/ta.json';
import te from '../../assets/i18n/te.json';
import {
  QA_DURATION_KEYS,
  QA_ENVIRONMENTS,
  QA_ENVIRONMENT_KEYS,
  QA_MESSAGE_KEYS,
  QA_SCENARIO_STATUSES,
  QA_SIZE_KEYS,
  QA_STATUS_KEYS,
} from './system-qa.constants';

const LANGUAGES: Record<string, Record<string, unknown>> = { en, hi, ta, te, kn, ml, ar };

/** Texts that are acronyms or unit symbols and may legitimately read the same in every language. */
const SAME_AS_ENGLISH_ALLOWED = new Set([
  'systemQa.environments.qa',
  'systemQa.environments.uat',
  'systemQa.size.B',
  'systemQa.size.KB',
  'systemQa.size.MB',
  'systemQa.runs.jenkins',
]);

/** Flattens the strings under a node to `dotted.key -> text`. */
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
  const nav = (root['nav'] as { items: Record<string, unknown> }).items['qaAutomation'];
  return { ...flatten(root['systemQa'], 'systemQa'), ...flatten(nav, 'nav.items.qaAutomation') };
}

const params = (text: string): string[] => (text.match(/{{\s*\w+\s*}}/g) ?? []).map((p) => p.replace(/\s/g, '')).sort();

describe('systemQa i18n', () => {
  const english = screenTexts('en');
  const englishKeys = Object.keys(english).sort();

  it('has strings for the screen and its menu item', () => {
    expect(englishKeys.length).toBeGreaterThan(60);
    expect(english['nav.items.qaAutomation']).toBeTruthy();
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
    const same = englishKeys.filter((key) => texts[key] === english[key] && !SAME_AS_ENGLISH_ALLOWED.has(key));
    expect(same, `${lang} keeps English text`).toEqual([]);
  });

  it('defines every translation key the screen refers to from its constants', () => {
    const keys = [
      ...Object.values(QA_STATUS_KEYS),
      ...Object.values(QA_ENVIRONMENT_KEYS),
      ...Object.values(QA_DURATION_KEYS),
      ...Object.values(QA_SIZE_KEYS),
      ...Object.values(QA_MESSAGE_KEYS),
    ];
    keys.forEach((key) => expect(english[key], key).toBeTruthy());
  });

  it('has a label key for every environment and scenario status', () => {
    QA_ENVIRONMENTS.forEach((e) => expect(QA_ENVIRONMENT_KEYS[e]).toBeTruthy());
    QA_SCENARIO_STATUSES.forEach((s) => expect(QA_STATUS_KEYS[s]).toBeTruthy());
    ['PASSED', 'FAILED', 'UNSTABLE'].forEach((s) => expect(QA_STATUS_KEYS[s]).toBeTruthy());
  });
});
