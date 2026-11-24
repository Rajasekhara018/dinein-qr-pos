import { ReadinessStep } from './application-readiness.models';
import { isExcludedStep, markOf, toGoLiveSteps } from './go-live-steps.helpers';

const step = (over: Partial<ReadinessStep>): ReadinessStep => ({
  id: 'WALLET_APP_REGISTERED', phase: 'BUSINESS', order: 10, service: 'wallet', severity: 'BLOCK', status: 'PASS',
  messageKey: 'readiness.wallet.appRegistered.pass', fixPath: null, locked: false, ...over,
});
const all = (): boolean => true;
const none = (): boolean => false;

describe('go-live steps helpers', () => {
  it('marks passed and not applicable steps done, unknown ones as not checked and everything else not done', () => {
    expect(markOf('PASS')).toBe('done');
    expect(markOf('NOT_APPLICABLE')).toBe('done');
    expect(markOf('UNKNOWN')).toBe('unknown');
    expect(markOf('FAIL')).toBe('notDone');
    expect(markOf('WARN')).toBe('notDone');
  });

  it('leaves out the summary step, every user related step and every reports step', () => {
    const excluded = ['GO_LIVE_REVIEW', 'UAM_OWNER_TEAM_SET', 'UAM_ROLE_HAS_ACCESS', 'UAM_USER_HAS_ACCESS', 'REPORTS_TEMPLATE_EXISTS', 'REPORTS_REACHABLE'];
    excluded.forEach((id) => expect(isExcludedStep({ id, service: 'uam' }), id).toBe(true));
    expect(isExcludedStep({ id: 'ANY_STEP', service: 'reports' })).toBe(true);
    ['UAM_APPLICATION_ACTIVE', 'UAM_APPLICATION_IN_ORGANIZATION', 'WALLET_REACHABLE', 'NMS_TEMPLATES_EXIST'].forEach((id) =>
      expect(isExcludedStep({ id, service: id.split('_')[0].toLowerCase() }), id).toBe(false));
  });

  it('keeps the order UAM sent and builds the mark, the sentence, the required label and the Fix of each step', () => {
    const views = toGoLiveSteps([
      step({ id: 'UAM_APPLICATION_ACTIVE', service: 'uam', messageKey: 'readiness.uam.applicationActive.pass' }),
      step({ id: 'WALLET_APP_REGISTERED', status: 'FAIL', messageKey: 'readiness.wallet.appRegistered.fail', fixPath: '/wallet/admin/apps?application=APP' }),
      step({ id: 'DMS_DOCUMENT_TYPES_CONFIGURED', service: 'dms', severity: 'WARN', status: 'UNKNOWN', messageKey: 'readiness.dms.documentTypesConfigured.unknown' }),
      step({ id: 'GO_LIVE_REVIEW', service: 'uam', status: 'FAIL', messageKey: 'readiness.uam.goLiveReview.fail' }),
      step({ id: 'REPORTS_TEMPLATE_EXISTS', service: 'reports', status: 'FAIL', messageKey: 'readiness.reports.templateExists.fail' }),
    ], all, all);

    expect(views.map((v) => v.id)).toEqual(['UAM_APPLICATION_ACTIVE', 'WALLET_APP_REGISTERED', 'DMS_DOCUMENT_TYPES_CONFIGURED']);
    expect(views.map((v) => v.mark)).toEqual(['done', 'notDone', 'unknown']);
    expect(views.map((v) => v.required)).toEqual([true, true, false]);
    expect(views[1].textKey).toBe('readiness.wallet.appRegistered.fail');
    expect(views[1].fix).toEqual({ path: '/wallet/admin/apps', queryParams: { application: 'APP' } });
    expect(views[0].fix).toBeNull();
  });

  it('gives no Fix to a done step, a locked step or a screen the user may not open, and says a locked step is waiting', () => {
    const failing = step({ status: 'FAIL', fixPath: '/wallet/admin/apps?application=APP', messageKey: 'readiness.wallet.appRegistered.fail' });

    expect(toGoLiveSteps([step({ fixPath: '/wallet/admin/apps' })], all, all)[0].fix).toBeNull();
    expect(toGoLiveSteps([{ ...failing, locked: true }], all, all)[0]).toEqual(expect.objectContaining({ fix: null, waiting: true }));
    expect(toGoLiveSteps([failing], all, none)[0].fix).toBeNull();
    expect(toGoLiveSteps([failing], all, all)[0].waiting).toBe(false);
  });

  it('falls back to the generic sentence of the status when the step has no text of its own, and copes with no steps', () => {
    expect(toGoLiveSteps([step({ status: 'FAIL', messageKey: 'readiness.wallet.appRegistered.fail' })], none, all)[0].textKey).toBe('readiness.status.fail');
    expect(toGoLiveSteps(null, all, all)).toEqual([]);
    expect(toGoLiveSteps(undefined, all, all)).toEqual([]);
  });
});
