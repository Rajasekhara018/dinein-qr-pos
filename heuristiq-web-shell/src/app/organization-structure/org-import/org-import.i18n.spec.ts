import ar from '../../../assets/i18n/ar.json';
import en from '../../../assets/i18n/en.json';
import hi from '../../../assets/i18n/hi.json';
import kn from '../../../assets/i18n/kn.json';
import ml from '../../../assets/i18n/ml.json';
import ta from '../../../assets/i18n/ta.json';
import te from '../../../assets/i18n/te.json';
import { DEPARTMENT_IMPORT_ERROR_CODES, IMPORT_ERROR_CODES, IMPORT_MODES, LADDER_IMPORT_ERROR_CODES, UPLOAD_PROBLEM_KEYS } from '../org-structure.constants';

const LANGUAGES: Record<string, Record<string, unknown>> = { en, hi, ta, te, kn, ml, ar };

function flatten(node: unknown, prefix: string, out: Record<string, string> = {}): Record<string, string> {
  if (typeof node === 'string') {
    out[prefix] = node;
  } else if (node && typeof node === 'object') {
    Object.entries(node).forEach(([k, v]) => flatten(v, prefix ? `${prefix}.${k}` : k, out));
  }
  return out;
}

const importTexts = (lang: string): Record<string, string> =>
  flatten((LANGUAGES[lang]['orgStructure'] as { import: unknown }).import, 'orgStructure.import');
const params = (text: string): string[] => (text.match(/{{\s*\w+\s*}}/g) ?? []).map((p) => p.replace(/\s/g, '')).sort();

describe('org import i18n (employees and departments)', () => {
  const english = importTexts('en');
  const englishKeys = Object.keys(english).sort();

  it('has texts for the tabs, the hint and the whole department tab', () => {
    ['orgStructure.import.steps.label', 'orgStructure.import.tabs.employees', 'orgStructure.import.tabs.departments',
      'orgStructure.import.unknownDepartmentHint.text', 'orgStructure.import.unknownDepartmentHint.action',
      'orgStructure.import.departments.title', 'orgStructure.import.departments.noErrorAccess',
      'orgStructure.import.departments.columns.parentCode', 'orgStructure.import.departments.columns.ladder',
      'orgStructure.import.departments.withoutLadder', 'orgStructure.import.departments.withoutLadderOne',
      'orgStructure.import.ladderHint.text', 'orgStructure.import.ladderHint.action'].forEach((key) => expect(english[key], key).toBeTruthy());
  });

  it('knows exactly the row problems of the backend ImportErrorCode enum (employees)', () => {
    const backend = ['EMPLOYEE_CODE_REQUIRED', 'EMPLOYEE_CODE_TOO_LONG', 'EMPLOYEE_CODE_DUPLICATE', 'FULL_NAME_REQUIRED', 'FULL_NAME_TOO_LONG',
      'EMAIL_INVALID', 'JOINED_ON_INVALID', 'DEPARTMENT_REQUIRED', 'DEPARTMENT_UNKNOWN', 'DEPARTMENT_HAS_NO_LADDER', 'POSITION_REQUIRED',
      'POSITION_NOT_IN_LADDER', 'MANAGER_UNKNOWN', 'MANAGER_IS_SELF', 'MANAGER_CYCLE', 'IS_HEAD_INVALID', 'HEAD_DUPLICATE'];
    expect([...IMPORT_ERROR_CODES].sort()).toEqual([...backend].sort());
    Object.keys(LANGUAGES).forEach((lang) => {
      const prefix = 'orgStructure.import.errors.';
      const codes = Object.keys(importTexts(lang)).filter((k) => k.startsWith(prefix)).map((k) => k.slice(prefix.length)).sort();
      expect(codes, lang).toEqual([...backend].sort());
    });
    expect(params(english['orgStructure.import.errors.HEAD_DUPLICATE'])).toEqual(['{{code}}', '{{row}}']);
  });

  it('knows exactly the row problems of the backend ladder import enum', () => {
    const backend = ['LADDER_REQUIRED', 'LADDER_TOO_LONG', 'POSITION_REQUIRED', 'POSITION_TOO_LONG', 'RANK_INVALID', 'MANAGERIAL_INVALID', 'DUPLICATE_ROW',
      'RANK_DUPLICATE', 'RANK_TAKEN'];
    expect([...LADDER_IMPORT_ERROR_CODES].sort()).toEqual([...backend].sort());
    Object.keys(LANGUAGES).forEach((lang) => {
      const prefix = 'orgStructure.import.ladders.errors.';
      const codes = Object.keys(importTexts(lang)).filter((k) => k.startsWith(prefix)).map((k) => k.slice(prefix.length)).sort();
      expect(codes, lang).toEqual([...backend].sort());
    });
    expect(params(english['orgStructure.import.ladders.errors.DUPLICATE_ROW'])).toEqual(['{{detail}}']);
    expect(params(english['orgStructure.import.ladders.errors.RANK_DUPLICATE'])).toEqual(['{{detail}}']);
    expect(params(english['orgStructure.import.ladders.errors.RANK_TAKEN'])).toEqual(['{{detail}}']);
  });

  it('has the texts of the "Loaded now" sections and the latest import card, with the numbers they show', () => {
    const keys = ['title', 'loading', 'error', 'retry', 'openChart', 'showMore', 'yes', 'no', 'noLadder', 'expand', 'collapse', 'childrenError',
      'empty.ladders', 'empty.departments', 'empty.employees', 'tiles.ladders', 'tiles.positions', 'tiles.departments', 'tiles.withoutLadder',
      'tiles.employees', 'tiles.departmentsWithPeople', 'tiles.heads', 'atLeast', 'atLeastValue',
      'columns.type', 'columns.head', 'columns.headcount', 'columns.department'];
    keys.forEach((key) => expect(english['orgStructure.import.loaded.' + key], key).toBeTruthy());
    ['title', 'by', 'headsSet', 'waiting', 'showRejected', 'cancelPreview'].forEach((key) => expect(english['orgStructure.import.latest.' + key], key).toBeTruthy());
    expect(params(english['orgStructure.import.loaded.atLeastValue'])).toEqual(['{{count}}']);
    expect(params(english['orgStructure.import.loaded.expand'])).toEqual(['{{name}}']);
    expect(params(english['orgStructure.import.latest.by'])).toEqual(['{{file}}', '{{when}}', '{{who}}']);
    Object.keys(LANGUAGES).filter((l) => l !== 'en').forEach((lang) => {
      const texts = importTexts(lang);
      ['title', 'noLadder', 'empty.ladders'].forEach((key) => expect(texts['orgStructure.import.loaded.' + key], lang + ' ' + key).not.toBe(english['orgStructure.import.loaded.' + key]));
    });
  });

  it('has a text for every state of a step, one hint for the preview state and no plural to get wrong', () => {
    ['NOT_STARTED', 'IN_PROGRESS', 'PREVIEW', 'DONE'].forEach((state) => expect(english['orgStructure.import.steps.state.' + state], state).toBeTruthy());
    expect(english['orgStructure.import.steps.previewHint']).toBeTruthy();
    expect(params(english['orgStructure.import.steps.state.PREVIEW'])).toEqual([]);
    expect(params(english['orgStructure.import.steps.previewHint'])).toEqual([]);
    Object.keys(LANGUAGES).filter((l) => l !== 'en').forEach((lang) => {
      const texts = importTexts(lang);
      expect(texts['orgStructure.import.steps.state.PREVIEW'], lang).not.toBe(english['orgStructure.import.steps.state.PREVIEW']);
      expect(texts['orgStructure.import.steps.previewHint'], lang).not.toBe(english['orgStructure.import.steps.previewHint']);
    });
  });

  it('has a tab text for every tab, a plural-safe heads note and the hint to load the ladders first', () => {
    IMPORT_MODES.forEach((mode) => expect(english['orgStructure.import.tabs.' + mode.toLowerCase()], mode).toBeTruthy());
    expect(params(english['orgStructure.import.headsSet'])).toEqual(['{{count}}']);
    expect(params(english['orgStructure.import.headsSetOne'])).toEqual([]);
    ['orgStructure.import.laddersHint.text', 'orgStructure.import.laddersHint.action', 'orgStructure.import.summary.laddersCreated',
      'orgStructure.import.ladders.title', 'orgStructure.import.ladders.columns.rank',
      'orgStructure.import.ladders.noErrorAccess'].forEach((key) => expect(english[key], key).toBeTruthy());
  });

  it('knows exactly the row problems of the backend DepartmentErrorCode enum', () => {
    const backend = ['CODE_REQUIRED', 'CODE_TOO_LONG', 'CODE_INVALID', 'CODE_DUPLICATE', 'CODE_ARCHIVED', 'NAME_REQUIRED', 'NAME_TOO_LONG',
      'UNIT_TYPE_TOO_LONG', 'PARENT_UNKNOWN', 'PARENT_REJECTED', 'PARENT_CYCLE', 'TOO_DEEP', 'LADDER_UNKNOWN'];
    expect([...DEPARTMENT_IMPORT_ERROR_CODES].sort()).toEqual([...backend].sort());
    Object.keys(LANGUAGES).forEach((lang) => {
      const prefix = 'orgStructure.import.departments.errors.';
      const codes = Object.keys(importTexts(lang)).filter((k) => k.startsWith(prefix)).map((k) => k.slice(prefix.length)).sort();
      expect(codes, lang).toEqual([...backend].sort());
    });
  });

  it('shows what a rejected parent, a loop, an archived code and a deep structure are about', () => {
    expect(params(english['orgStructure.import.departments.errors.PARENT_REJECTED'])).toEqual(['{{code}}', '{{row}}']);
    expect(params(english['orgStructure.import.departments.errors.TOO_DEEP'])).toEqual(['{{detail}}']);
    expect(params(english['orgStructure.import.departments.errors.CODE_ARCHIVED'])).toEqual(['{{detail}}']);
    expect(params(english['orgStructure.import.departments.errors.PARENT_CYCLE'])).toEqual([]);
  });

  it('has a text for every department row problem the server can report and for every kind of refused file', () => {
    DEPARTMENT_IMPORT_ERROR_CODES.forEach((code) => expect(english['orgStructure.import.departments.errors.' + code], code).toBeTruthy());
    Object.values(UPLOAD_PROBLEM_KEYS).forEach((kind) => expect(english['orgStructure.import.departments.upload.' + kind], kind).toBeTruthy());
  });

  it('gives the plural-safe note a form for several departments with the count and a form for one without it', () => {
    expect(params(english['orgStructure.import.departments.withoutLadder'])).toEqual(['{{count}}']);
    expect(params(english['orgStructure.import.departments.withoutLadderOne'])).toEqual([]);
  });

  it.each(['hi', 'ta', 'te', 'kn', 'ml', 'ar'])('%s has exactly the keys and {{params}} of English, with no empty text', (lang) => {
    const texts = importTexts(lang);
    expect(Object.keys(texts).sort()).toEqual(englishKeys);
    englishKeys.forEach((key) => {
      expect(texts[key].trim(), `${lang} ${key}`).not.toBe('');
      expect(params(texts[key]), `${lang} ${key}`).toEqual(params(english[key]));
    });
  });

  it.each(['hi', 'ta', 'te', 'kn', 'ml', 'ar'])('%s does not leave the new department texts in English', (lang) => {
    const texts = importTexts(lang);
    const departmentKeys = englishKeys.filter((k) => k.startsWith('orgStructure.import.departments.') || k.startsWith('orgStructure.import.tabs.') || k.startsWith('orgStructure.import.ladderHint.')
      || k.startsWith('orgStructure.import.ladders.') || k.startsWith('orgStructure.import.laddersHint.') || k.startsWith('orgStructure.import.headsSet')
      || k.startsWith('orgStructure.import.loaded.') || k.startsWith('orgStructure.import.latest.'));
    const neutral = [
      'orgStructure.import.loaded.atLeastValue', // a symbol and a number, the same in every language
      // The upload hint is only the format name; the shared drop area adds the translated size limit itself.
      'orgStructure.import.departments.uploadHints',
      'orgStructure.import.ladders.uploadHints',
    ];
    const unchanged = departmentKeys.filter((k) => !neutral.includes(k) && texts[k] === english[k]);
    expect(unchanged).toEqual([]);
  });
});
