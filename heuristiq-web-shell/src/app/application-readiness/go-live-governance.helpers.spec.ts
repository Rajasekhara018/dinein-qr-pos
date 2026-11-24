import { describe, expect, it } from 'vitest';
import { GOVERNANCE_TEXT_KEYS } from './go-live-governance.constants';
import {
  governanceFailure,
  httpStatusOf,
  isOverrideReasonValid,
  parseGoLiveBlocked,
  parseTimestamp,
  toBlockerView,
} from './go-live-governance.helpers';

const refusal = (over: Record<string, unknown> = {}) => ({
  status: 409,
  error: {
    success: false,
    errorCode: 'GO_LIVE_BLOCKED',
    responseObject: {
      applicationCode: 'CENTRALISED_WALLET_APP',
      messageKey: 'readiness.governance.goLiveRefused',
      blockers: [
        { stepId: 'WALLET_APP_REGISTERED', status: 'FAIL', messageKey: 'readiness.wallet.appRegistered.fail', params: { count: 2 }, fixPath: '/wallet/admin/apps?application=X' },
        { stepId: 'BROKEN' },
        'junk',
      ],
    },
    ...over,
  },
});

describe('go-live governance helpers', () => {
  describe('parseGoLiveBlocked', () => {
    it('reads the blockers of a 409 GO_LIVE_BLOCKED answer and drops malformed entries', () => {
      const info = parseGoLiveBlocked(refusal());
      expect(info?.applicationCode).toBe('CENTRALISED_WALLET_APP');
      expect(info?.messageKey).toBe(GOVERNANCE_TEXT_KEYS.goLiveRefused);
      expect(info?.blockers.map((b) => b.stepId)).toEqual(['WALLET_APP_REGISTERED', 'BROKEN']);
      expect(info?.blockers[0].params).toEqual({ count: '2' });
      expect(info?.blockers[1]).toMatchObject({ status: 'UNKNOWN', messageKey: '', fixPath: null, params: null });
    });

    it('is null for any other failure', () => {
      expect(parseGoLiveBlocked({ status: 500, error: refusal().error })).toBeNull();
      expect(parseGoLiveBlocked(refusal({ errorCode: 'OTHER' }))).toBeNull();
      expect(parseGoLiveBlocked(refusal({ responseObject: null }))).toBeNull();
      expect(parseGoLiveBlocked(new Error('x'))).toBeNull();
      expect(parseGoLiveBlocked(null)).toBeNull();
    });

    it('falls back to the refused key and an empty list when the body is thin', () => {
      const info = parseGoLiveBlocked(refusal({ responseObject: {} }));
      expect(info).toEqual({ applicationCode: '', messageKey: GOVERNANCE_TEXT_KEYS.goLiveRefused, blockers: [] });
    });
  });

  describe('toBlockerView', () => {
    const blocker = parseGoLiveBlocked(refusal())!.blockers[0];

    it('resolves the step text through the readiness resolver and keeps a safe fix link', () => {
      const view = toBlockerView(blocker, (key) => key === 'readiness.wallet.appRegistered.fail', () => true);
      expect(view.textKey).toBe('readiness.wallet.appRegistered.fail');
      expect(view.statusKey).toBe('readiness.stepStatus.fail');
      expect(view.params).toEqual({ count: '2' });
      expect(view.fix).toEqual({ path: '/wallet/admin/apps', queryParams: { application: 'X' } });
    });

    it('falls back to the generic status sentence and hides a fix the user may not open', () => {
      const view = toBlockerView(blocker, () => false, () => false);
      expect(view.textKey).toBe('readiness.status.fail');
      expect(view.fix).toBeNull();
    });

    it('never links an external or missing fix path', () => {
      expect(toBlockerView({ ...blocker, fixPath: 'https://evil.example/x' }, () => true, () => true).fix).toBeNull();
      expect(toBlockerView({ ...blocker, fixPath: null }, () => true, () => true).fix).toBeNull();
    });
  });

  describe('governanceFailure', () => {
    const failure = (status: number, message: string) => ({ status, error: { message } });

    it('maps the override failures to their translated keys', () => {
      expect(governanceFailure(failure(403, 'Only a platform administrator')).key).toBe(GOVERNANCE_TEXT_KEYS.overrideForbidden);
      expect(governanceFailure(failure(400, 'The override reason must be between 10 and 500 characters')))
        .toEqual({ key: GOVERNANCE_TEXT_KEYS.overrideReasonInvalid, params: { min: 10, max: 500 } });
      expect(governanceFailure(failure(400, 'No required step is blocking this application; switch it on normally')).key)
        .toBe(GOVERNANCE_TEXT_KEYS.nothingToOverride);
      expect(governanceFailure(failure(500, 'boom')).key).toBe(GOVERNANCE_TEXT_KEYS.requestFailed);
      expect(governanceFailure(failure(400, 'something else')).key).toBe(GOVERNANCE_TEXT_KEYS.requestFailed);
    });
  });

  it('checks the override reason on its trimmed length, 10 to 500 by default', () => {
    expect(isOverrideReasonValid('         short')).toBe(false);
    expect(isOverrideReasonValid('ten chars!')).toBe(true);
    expect(isOverrideReasonValid('x'.repeat(500))).toBe(true);
    expect(isOverrideReasonValid('x'.repeat(501))).toBe(false);
    expect(isOverrideReasonValid(null)).toBe(false);
    expect(isOverrideReasonValid('abc', 3, 5)).toBe(true);
  });

  it('parses timestamps and rejects invalid ones', () => {
    expect(parseTimestamp('2026-10-25T10:00:00')?.getFullYear()).toBe(2026);
    expect(parseTimestamp('nope')).toBeNull();
    expect(parseTimestamp(null)).toBeNull();
  });

  it('reads the HTTP status of a failure, 0 when there is none', () => {
    expect(httpStatusOf({ status: 409 })).toBe(409);
    expect(httpStatusOf('x')).toBe(0);
  });
});
