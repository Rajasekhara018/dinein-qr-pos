import { HQ_PERMISSION_PRIVILEGES } from 'hq-shared-lib';

import { NAV_AREAS } from '../layout/nav-areas.constants';
import { NAV_ITEMS } from '../layout/nav-items.constants';
import { SCREEN_KEY_PATTERN } from '../shared/services/screen-view.constants';
import { QA_DASHBOARD_PRIVILEGES, QA_PERMISSIONS, QA_ROUTES, QA_SCREEN_KEYS, QA_TABLE_CONFIGS } from './qa-dashboard.constants';
import { QA_DASHBOARD_ROUTES } from './qa-dashboard.routes';
import { QA_API_DOC_PRIVILEGES } from './qa-api-documents/qa-api-documents.constants';
import { QA_STUDIO_PRIVILEGES } from './qa-test-case-studio/qa-studio.constants';

const screenRoutes = QA_DASHBOARD_ROUTES.filter((r) => r.component);

describe('QA dashboard routes, menu and privileges', () => {
  it('gives every screen its own permission and a unique, well-formed screen key', () => {
    const keys = screenRoutes.map((r) => r.data?.['screenKey'] as string);
    // The studio list and its two editor routes are one screen and share its key.
    expect(keys).toHaveLength(14);
    expect(keys.filter((k) => k === QA_SCREEN_KEYS.testCaseStudio)).toHaveLength(3);
    keys.forEach((key) => expect(key, key).toMatch(SCREEN_KEY_PATTERN));
    expect([...new Set(keys)].sort()).toEqual(Object.values(QA_SCREEN_KEYS).sort());
    screenRoutes.forEach((r) => expect(r.canActivate?.length, String(r.path)).toBe(1));
  });

  it('asks before the studio editor is left with unsaved changes, and reads "new" before an id', () => {
    const editors = QA_DASHBOARD_ROUTES.filter((r) => String(r.path).startsWith('test-case-studio/'));
    expect(editors.map((r) => r.path)).toEqual(['test-case-studio/new', 'test-case-studio/:id']);
    editors.forEach((r) => expect(r.canDeactivate?.length, String(r.path)).toBe(1));
  });

  it('opens the section on the overview', () => {
    const root = QA_DASHBOARD_ROUTES.find((r) => r.path === '');
    expect(root?.redirectTo).toBe('overview');
  });

  it('tells the shared table which kind of table each route shows', () => {
    const kinds = screenRoutes.filter((r) => r.data?.['kind']).map((r) => r.data?.['kind'] as string);
    expect(kinds.sort()).toEqual(['executions', 'inventory', 'regression', 'smoke']);
    kinds.forEach((kind) => expect(Object.keys(QA_TABLE_CONFIGS)).toContain(kind));
  });

  it('maps every screen permission to privileges the dashboard defines', () => {
    const known = new Set<string>([...Object.values(QA_DASHBOARD_PRIVILEGES), ...Object.values(QA_STUDIO_PRIVILEGES), ...Object.values(QA_API_DOC_PRIVILEGES)]);
    Object.values(QA_PERMISSIONS).forEach((permission) => {
      const codes = HQ_PERMISSION_PRIVILEGES[permission];
      expect(codes, permission).toBeDefined();
      expect(codes.length, permission).toBeGreaterThan(0);
      codes.forEach((code) => expect(known.has(code), `${permission} -> ${code}`).toBe(true));
    });
  });

  it('puts every screen in its own menu section with the permission its route checks', () => {
    const qaSections = NAV_AREAS.find((a) => a.key === 'qa')!.sections;
    const items = NAV_ITEMS.filter((n) => qaSections.includes(n.section));
    const qaPaths = Object.values(QA_ROUTES);
    expect(items.map((n) => n.path).sort()).toEqual([...qaPaths].sort());
    screenRoutes.filter((r) => !String(r.path).includes(':') && !String(r.path).endsWith('/new')).forEach((route) => {
      const item = items.find((n) => n.path === `/qa/${route.path}`);
      expect(item, String(route.path)).toBeDefined();
      expect(item?.permission, String(route.path)).toBe(route.data?.['permission']);
    });
    expect(items.find((n) => n.path === '/system/qa')?.label).toBe('nav.items.qaRuns');
  });

  it('shows no change action on the suite tables and a delete only on the inventory', () => {
    expect(QA_TABLE_CONFIGS.regression.allowDelete).toBe(false);
    expect(QA_TABLE_CONFIGS.smoke.allowDelete).toBe(false);
    expect(QA_TABLE_CONFIGS.executions.allowDelete).toBe(false);
    expect(QA_TABLE_CONFIGS.inventory.allowDelete).toBe(true);
    expect(QA_TABLE_CONFIGS.regression.suite).toBe('REGRESSION');
    expect(QA_TABLE_CONFIGS.smoke.suite).toBe('SMOKE');
  });
});
