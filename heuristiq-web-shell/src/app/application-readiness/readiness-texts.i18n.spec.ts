import ar from '../../assets/i18n/ar.json';
import en from '../../assets/i18n/en.json';
import hi from '../../assets/i18n/hi.json';
import kn from '../../assets/i18n/kn.json';
import ml from '../../assets/i18n/ml.json';
import ta from '../../assets/i18n/ta.json';
import te from '../../assets/i18n/te.json';

const LANGUAGES: Record<string, { readiness: Record<string, unknown>; applicationAccess: Record<string, unknown> }> = {
  en: en as never, hi: hi as never, ta: ta as never, te: te as never, kn: kn as never, ml: ml as never, ar: ar as never,
};

function flatten(node: unknown, prefix: string, out: Record<string, string> = {}): Record<string, string> {
  if (typeof node === 'string') out[prefix] = node;
  else if (node && typeof node === 'object') Object.entries(node).forEach(([k, v]) => flatten(v, prefix ? `${prefix}.${k}` : k, out));
  return out;
}

const params = (text: string): string => [...text.matchAll(/{{\s*(\w+)\s*}}/g)].map((m) => m[1]).sort().join();

describe('go-live and application access translations', () => {
  it.each(['readiness', 'applicationAccess'] as const)('%s has the same keys and {{params}} in all seven files', (namespace) => {
    const english = flatten(LANGUAGES['en'][namespace], namespace);
    for (const [lang, root] of Object.entries(LANGUAGES)) {
      const flat = flatten(root[namespace], namespace);
      expect(Object.keys(flat).sort(), lang).toEqual(Object.keys(english).sort());
      expect(Object.keys(flat).filter((key) => params(flat[key]) !== params(english[key])), lang).toEqual([]);
    }
  });

  it('keeps no text of the removed owner team, role access and user access checks or of the setup checklist', () => {
    for (const root of Object.values(LANGUAGES)) {
      const uam = root.readiness['uam'] as Record<string, unknown>;
      expect(Object.keys(uam).sort()).toEqual(['applicationActive', 'applicationInOrganization', 'goLiveReview', 'reachable']);
      ['overall', 'severity', 'severityEdit', 'phases', 'progress', 'nextStep', 'openChecklist', 'reports'].forEach((key) => expect(root.readiness[key]).toBeUndefined());
    }
  });
});
