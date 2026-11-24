import ar from '../../../assets/i18n/ar.json';
import en from '../../../assets/i18n/en.json';
import hi from '../../../assets/i18n/hi.json';
import kn from '../../../assets/i18n/kn.json';
import ml from '../../../assets/i18n/ml.json';
import ta from '../../../assets/i18n/ta.json';
import te from '../../../assets/i18n/te.json';
import { describe, expect, it } from 'vitest';
import { ORGANIZATION_SETUP_I18N, ORGANIZATION_SETUP_STEPS } from './organization-setup.constants';

type Tree = Record<string, unknown>;
const LANGUAGES: Record<string, Tree> = { en: en as Tree, hi: hi as Tree, ta: ta as Tree, te: te as Tree, kn: kn as Tree, ml: ml as Tree, ar: ar as Tree };

const at = (root: Tree, path: string): unknown => path.split('.').reduce<unknown>((node, key) => (node as Tree | undefined)?.[key], root);

function flatten(node: unknown, prefix: string, out: Record<string, string> = {}): Record<string, string> {
  if (typeof node === 'string') out[prefix] = node;
  else if (node && typeof node === 'object') Object.entries(node).forEach(([k, v]) => flatten(v, prefix ? `${prefix}.${k}` : k, out));
  return out;
}

const params = (text: string): string => [...text.matchAll(/{{\s*(\w+)\s*}}/g)].map((m) => m[1]).sort().join();

/** Every key the setup screen, its retry and the legal profile's district picker use. */
const KEYS = [ORGANIZATION_SETUP_I18N, 'shared.adminCrud.retry', 'legalProfile.districtLabel', 'legalProfile.chooseStateFirst'];

describe('organization setup translations', () => {
  it.each(KEYS)('%s has the same keys and {{params}} in all seven languages', (key) => {
    const english = flatten(at(LANGUAGES['en'], key), key);
    expect(Object.keys(english).length).toBeGreaterThan(0);
    for (const [lang, root] of Object.entries(LANGUAGES)) {
      const flat = flatten(at(root, key), key);
      expect(Object.keys(flat).sort(), lang).toEqual(Object.keys(english).sort());
      expect(Object.keys(flat).filter((k) => params(flat[k]) !== params(english[k])), lang).toEqual([]);
      expect(Object.values(flat).filter((text) => !text.trim()), lang).toEqual([]);
    }
  });

  it('does not leave the other six languages as a copy of the English words', () => {
    const english = flatten(at(LANGUAGES['en'], ORGANIZATION_SETUP_I18N), ORGANIZATION_SETUP_I18N);
    for (const [lang, root] of Object.entries(LANGUAGES).filter(([l]) => l !== 'en')) {
      const flat = flatten(at(root, ORGANIZATION_SETUP_I18N), ORGANIZATION_SETUP_I18N);
      expect(Object.keys(flat).filter((k) => flat[k] === english[k]), lang).toEqual([]);
    }
  });

  it('has a label in every language for each step of the stepper', () => {
    for (const root of Object.values(LANGUAGES)) {
      for (const step of ORGANIZATION_SETUP_STEPS) {
        expect(typeof at(root, step.labelKey)).toBe('string');
        expect(typeof at(root, step.summaryKey)).toBe('string');
      }
      for (const key of ['stepOf', 'progress', 'jumpTo', 'railNote', 'fillIn', 'saveFirst']) expect(typeof at(root, `${ORGANIZATION_SETUP_I18N}.${key}`)).toBe('string');
      for (const status of ['todo', 'done', 'unsaved']) expect(typeof at(root, `${ORGANIZATION_SETUP_I18N}.status.${status}`)).toBe('string');
    }
  });
});
