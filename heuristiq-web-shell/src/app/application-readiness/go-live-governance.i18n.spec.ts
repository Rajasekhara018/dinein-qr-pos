import ar from '../../assets/i18n/ar.json';
import en from '../../assets/i18n/en.json';
import hi from '../../assets/i18n/hi.json';
import kn from '../../assets/i18n/kn.json';
import ml from '../../assets/i18n/ml.json';
import ta from '../../assets/i18n/ta.json';
import te from '../../assets/i18n/te.json';
import { GOVERNANCE_TEXT_KEYS } from './go-live-governance.constants';

const LANGUAGES: Record<string, unknown> = { en, hi, ta, te, kn, ml, ar };

function lookup(root: unknown, key: string): string | undefined {
  const value = key.split('.').reduce<unknown>((node, part) => (node && typeof node === 'object' ? (node as Record<string, unknown>)[part] : undefined), root);
  return typeof value === 'string' ? value : undefined;
}

function flatten(node: unknown, prefix: string, out: Record<string, string> = {}): Record<string, string> {
  if (typeof node === 'string') out[prefix] = node;
  else if (node && typeof node === 'object') Object.entries(node).forEach(([k, v]) => flatten(v, prefix ? `${prefix}.${k}` : k, out));
  return out;
}

const params = (text: string): string[] => [...text.matchAll(/{{\s*(\w+)\s*}}/g)].map((m) => m[1]).sort();

describe('go-live governance translations', () => {
  const keys = Object.values(GOVERNANCE_TEXT_KEYS);

  it.each(Object.keys(LANGUAGES))('%s has every governance key the screens use, with text', (lang) => {
    const missing = keys.filter((key) => !lookup(LANGUAGES[lang], key)?.trim());
    expect(missing).toEqual([]);
  });

  it.each(Object.keys(LANGUAGES).filter((l) => l !== 'en'))('%s uses the same {{params}} as English for every governance key', (lang) => {
    const mismatched = keys.filter((key) => params(lookup(LANGUAGES[lang], key) ?? '').join() !== params(lookup(en, key) ?? '').join());
    expect(mismatched).toEqual([]);
  });

  it('has the same governance key set in all seven files, and no key the screens do not use', () => {
    const sets = Object.entries(LANGUAGES).map(([lang, root]) => [lang, Object.keys(flatten((root as { readiness: { governance: unknown } }).readiness.governance, 'readiness.governance')).sort()] as const);
    sets.forEach(([, set]) => expect(set).toEqual(sets[0][1]));
    expect(sets[0][1]).toEqual([...keys].sort());
  });

  it('translates the non-English texts instead of copying English', () => {
    ['hi', 'ta', 'te', 'kn', 'ml', 'ar'].forEach((lang) => {
      expect(lookup(LANGUAGES[lang], GOVERNANCE_TEXT_KEYS.goLiveRefused)).not.toBe(lookup(en, GOVERNANCE_TEXT_KEYS.goLiveRefused));
      expect(lookup(LANGUAGES[lang], GOVERNANCE_TEXT_KEYS.overrideReasonInvalid)).toContain('{{min}}');
    });
  });
});
