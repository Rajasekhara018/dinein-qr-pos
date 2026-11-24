import ar from '../../assets/i18n/ar.json';
import en from '../../assets/i18n/en.json';
import hi from '../../assets/i18n/hi.json';
import kn from '../../assets/i18n/kn.json';
import ml from '../../assets/i18n/ml.json';
import ta from '../../assets/i18n/ta.json';
import te from '../../assets/i18n/te.json';
import {
  QA_COLUMN_KEYS,
  QA_DURATION_KEYS,
  QA_ENVIRONMENT_KEYS,
  QA_FAILURE_CATEGORY_KEYS,
  QA_HEADLINE_KEYS,
  QA_MESSAGE_KEYS,
  QA_OVERVIEW_MODE_KEYS,
  QA_STATUS_KEYS,
  QA_SUITE_KEYS,
  QA_WORKSPACE_KEYS,
} from './qa-dashboard.constants';

const LANGUAGES: Record<string, Record<string, unknown>> = { en, hi, ta, te, kn, ml, ar };

/** Acronyms and product words that read the same in every language. */
const SAME_AS_ENGLISH_ALLOWED = new Set([
  'qaDashboard.workspace.UI',
  'qaDashboard.workspace.API',
  'qaDashboard.environments.qa',
  'qaDashboard.environments.uat',
  // A bare counter ("+3") has nothing to translate.
  'qaDashboard.endpointInventory.moreScenarios',
  // Product names and a title made of a code and a name.
  'qaDashboard.apiDocuments.swagger',
  'qaDashboard.apiDocuments.javadoc',
  'qaDashboard.apiDocuments.defaultTitle',
]);

/** QA automation is its own area (nav.areas.qa); nav.sections.qaAutomation stays the kicker of the QA screens. */
const NAV_PATHS = [
  ['nav', 'sections', 'qaAutomation'],
  ['nav', 'items', 'qaOverview'],
  ['nav', 'items', 'qaRegression'],
  ['nav', 'items', 'qaSmoke'],
  ['nav', 'items', 'qaFailures'],
  ['nav', 'items', 'qaExecutionsTestCases'],
  ['nav', 'items', 'qaExecutionsTestSets'],
  ['nav', 'items', 'qaExecutiveSummary'],
  ['nav', 'items', 'qaTestCases'],
  ['nav', 'items', 'qaTestSets'],
  ['nav', 'items', 'qaEndpointInventory'],
  ['nav', 'items', 'qaTestCaseStudio'],
  ['nav', 'items', 'qaApiDocuments'],
  ['nav', 'items', 'qaRuns'],
];

/** Flattens the strings under a node to `dotted.key -> text`. */
function flatten(node: unknown, prefix: string, out: Record<string, string> = {}): Record<string, string> {
  if (typeof node === 'string') {
    out[prefix] = node;
  } else if (node && typeof node === 'object') {
    Object.entries(node).forEach(([k, v]) => flatten(v, prefix ? `${prefix}.${k}` : k, out));
  }
  return out;
}

function at(root: unknown, path: string[]): unknown {
  return path.reduce<unknown>((node, key) => (node && typeof node === 'object' ? (node as Record<string, unknown>)[key] : undefined), root);
}

function screenTexts(lang: string): Record<string, string> {
  const root = LANGUAGES[lang];
  const texts = flatten(root['qaDashboard'], 'qaDashboard');
  NAV_PATHS.forEach((path) => {
    const value = at(root, path);
    if (typeof value === 'string') texts[path.join('.')] = value;
  });
  return texts;
}

const params = (text: string): string[] => (text.match(/{{\s*\w+\s*}}/g) ?? []).map((p) => p.replace(/\s/g, '')).sort();

describe('qaDashboard i18n', () => {
  const english = screenTexts('en');
  const englishKeys = Object.keys(english).sort();

  it('has strings for the screens and every menu entry', () => {
    expect(englishKeys.length).toBeGreaterThan(150);
    NAV_PATHS.forEach((path) => expect(english[path.join('.')], path.join('.')).toBeTruthy());
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

  it('defines every translation key the screens take from their constants', () => {
    const keys = [
      ...Object.values(QA_STATUS_KEYS),
      ...Object.values(QA_SUITE_KEYS),
      ...Object.values(QA_FAILURE_CATEGORY_KEYS),
      ...Object.values(QA_ENVIRONMENT_KEYS),
      ...Object.values(QA_HEADLINE_KEYS),
      ...Object.values(QA_DURATION_KEYS),
      ...Object.values(QA_COLUMN_KEYS),
      ...Object.values(QA_WORKSPACE_KEYS),
      ...Object.values(QA_OVERVIEW_MODE_KEYS),
      ...Object.values(QA_MESSAGE_KEYS),
    ];
    keys.forEach((key) => expect(english[key], key).toBeTruthy());
  });

  it('shows the test case number column as "Test case ID"', () => {
    expect(english['qaDashboard.columns.tcId']).toBe('Test case ID');
  });
});
