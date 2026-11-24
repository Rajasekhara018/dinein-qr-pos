import {
  messageKeyCandidates,
  navPermissionFor,
  parseFixPath,
  parseMessageKey,
  readinessTextKey,
  resolveTranslationKey,
  statusFromSuffix,
} from './application-readiness.helpers';

const none = (): boolean => false;
const all = (): boolean => true;

describe('application readiness helpers', () => {
  describe('statusFromSuffix / parseMessageKey', () => {
    it('accepts every status spelling, any case, and rejects others', () => {
      expect(statusFromSuffix('pass')).toBe('PASS');
      expect(statusFromSuffix('FAIL')).toBe('FAIL');
      expect(statusFromSuffix('not_applicable')).toBe('NOT_APPLICABLE');
      expect(statusFromSuffix('notApplicable')).toBe('NOT_APPLICABLE');
      expect(statusFromSuffix('weird')).toBeNull();
      expect(statusFromSuffix(null)).toBeNull();
    });

    it('splits a well-formed messageKey and returns null for anything else', () => {
      expect(parseMessageKey('readiness.wallet.appRegistered.fail')).toEqual({ service: 'wallet', check: 'appRegistered', status: 'FAIL' });
      expect(parseMessageKey('readiness.goLive.review.warn')).toEqual({ service: 'goLive', check: 'review', status: 'WARN' });
      expect(parseMessageKey('other.wallet.x.fail')).toBeNull();
      expect(parseMessageKey('readiness.wallet.fail')).toBeNull();
      expect(parseMessageKey(undefined)).toBeNull();
    });
  });

  describe('translation key fallback', () => {
    it('tries the exact key (normalised), the neutral title, then the generic status text', () => {
      expect(messageKeyCandidates('readiness.dms.documentTypesConfigured.not_applicable')).toEqual([
        'readiness.dms.documentTypesConfigured.notApplicable',
        'readiness.dms.documentTypesConfigured.title',
        'readiness.status.notApplicable',
      ]);
    });

    it('uses the step status for the generic text, and only the generic text for an unknown shape', () => {
      expect(messageKeyCandidates('readiness.nms.templatesExist.pass', 'WARN').at(-1)).toBe('readiness.status.warn');
      expect(messageKeyCandidates('garbage', 'FAIL')).toEqual(['readiness.status.fail']);
      expect(messageKeyCandidates(null)).toEqual(['readiness.status.unknown']);
      expect(messageKeyCandidates('readiness.nms.templatesExist.odd')).toEqual(['readiness.nms.templatesExist.title', 'readiness.status.unknown']);
    });

    it('picks the first translated candidate and never the raw messageKey', () => {
      expect(resolveTranslationKey(['a', 'b', 'c'], (k) => k === 'b')).toBe('b');
      expect(resolveTranslationKey(['a', 'b', 'c'], none)).toBe('c');
      expect(readinessTextKey('readiness.wallet.appRegistered.fail', (k) => k.endsWith('.title'))).toBe('readiness.wallet.appRegistered.title');
      expect(readinessTextKey('readiness.wallet.appRegistered.fail', all)).toBe('readiness.wallet.appRegistered.fail');
      expect(readinessTextKey('readiness.wallet.appRegistered.fail', none)).toBe('readiness.status.fail');
    });
  });


  describe('parseFixPath', () => {
    it('splits an in-app path and its query parameters', () => {
      expect(parseFixPath('/wallet/admin/apps?application=MATRIMONY&x=1#top')).toEqual({ path: '/wallet/admin/apps', queryParams: { application: 'MATRIMONY', x: '1' } });
      expect(parseFixPath(' /access-management/applications ')).toEqual({ path: '/access-management/applications', queryParams: {} });
    });

    it('refuses anything that is not an in-app path', () => {
      expect(parseFixPath('https://evil.example/x')).toBeNull();
      expect(parseFixPath('//evil.example/x')).toBeNull();
      expect(parseFixPath('/\\evil')).toBeNull();
      expect(parseFixPath('javascript:alert(1)')).toBeNull();
      expect(parseFixPath('')).toBeNull();
      expect(parseFixPath(null)).toBeNull();
      expect(parseFixPath('/?a=b')).toEqual({ path: '/', queryParams: { a: 'b' } });
    });
  });

  describe('navPermissionFor', () => {
    const items = [
      { path: '/wallet/admin', permission: 'view_wallet_overview' },
      { path: '/wallet/admin/apps', permission: 'view_wallet_apps' },
      { path: '/today', permission: null },
    ];

    it('uses the longest owning entry, null for open entries and undefined when none owns the path', () => {
      expect(navPermissionFor('/wallet/admin/apps', items)).toBe('view_wallet_apps');
      expect(navPermissionFor('/wallet/admin/apps/new', items)).toBe('view_wallet_apps');
      expect(navPermissionFor('/wallet/admin/treasury', items)).toBe('view_wallet_overview');
      expect(navPermissionFor('/wallet/administrators', items)).toBeUndefined();
      expect(navPermissionFor('/today', items)).toBeNull();
      expect(navPermissionFor('/nowhere', items)).toBeUndefined();
    });
  });

});
