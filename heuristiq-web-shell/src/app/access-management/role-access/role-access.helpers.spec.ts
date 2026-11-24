import { describe, expect, it } from 'vitest';
import { Permission } from '../../shared/models/uam.models';
import {
  applicationOf, areaKeyOf, areaProgress, groupByApplication, areasInUse, groupLevelOf, isSensitive, reviewLines, summarize, availableLevels, buildAccessCatalog, featureKeyOf, levelOf, matchingFeatures, withAreaLevel, withLevel,
} from './role-access.helpers';

const permission = (code: string, action: Permission['action'], apipath: string): Permission => ({
  id: code, code, applicationCode: 'UAM', module: null, resource: null, action, displayName: null,
  description: code === 'USER_READ' ? 'Looks at users' : code === 'USER_CREATE' ? 'Adds users' : null,
  active: true, systemPermission: true, attributes: { apipath }, audit: null,
});

const CATALOG: Permission[] = [
  permission('USER_LIST', 'LIST', '/api/v1/uam/users'),
  permission('USER_READ', 'READ', '/api/v1/uam/users/{id}'),
  permission('USER_CREATE', 'CREATE', '/api/v1/uam/users'),
  permission('USER_DELETE', 'DELETE', '/api/v1/uam/users/{id}'),
  permission('AUDIT_LOG_LIST', 'LIST', '/api/v1/uam/audit-logs'),
  permission('TRAIN_ADMIN_BOOKING_LIST_READ', 'READ', '/api/v1/train/admin/bookings'),
  permission('TRAIN_ADMIN_REFUND_CREATE', 'CREATE', '/api/v1/train/admin/refunds'),
  permission('MYSTERY_THING_READ', 'READ', '/api/v1/something-new/x'),
];

describe('role access grouping', () => {
  it('keeps food, reporting and QA features out of the unclassified group', () => {
    expect(areaKeyOf(permission('FOOD_ORDER_READ', 'READ', '/api/v1/food/orders'))).toBe('food');
    expect(areaKeyOf(permission('REPORT_RUN_READ', 'READ', '/api/v1/reports/runs'))).toBe('reports');
    expect(areaKeyOf(permission('QA_RUN_READ', 'READ', '/api/v1/qa/runs'))).toBe('quality');
    expect(applicationOf('food')).toBe('food');
    expect(applicationOf('reports')).toBe('reports');
    expect(applicationOf('quality')).toBe('platform');
  });

  it('puts the Best Purchase shop privileges in their own area and application', () => {
    expect(areaKeyOf(permission('COMMERCE_PRODUCT_LIST', 'LIST', '/api/v1/commerce/admin/products'))).toBe('shop');
    expect(areaKeyOf(permission('COMMERCE_CHECKOUT_CREATE', 'CREATE', '/api/v1/commerce/store/orders'))).toBe('shop');
    expect(applicationOf('shop')).toBe('shop');
    const apps = groupByApplication(buildAccessCatalog([permission('COMMERCE_ORDER_READ', 'READ', '/api/v1/commerce/admin/orders/{orderId}')]));
    expect(apps.map(app => app.key)).toContain('shop');
  });

  it('groups codes under areas by the first segment of their API path, with a catch-all', () => {
    expect(areaKeyOf(CATALOG[0])).toBe('access');
    expect(areaKeyOf(CATALOG[5])).toBe('train');
    expect(areaKeyOf(CATALOG[7])).toBe('other');
  });

  it('names a feature without its trailing action words', () => {
    expect(featureKeyOf('USER_CREATE')).toBe('USER');
    expect(featureKeyOf('TRAIN_ADMIN_BOOKING_LIST_READ')).toBe('TRAIN_ADMIN_BOOKING');
    expect(featureKeyOf('LIST')).toBe('LIST');
  });

  it('builds areas in a fixed order with their features sorted', () => {
    const catalog = buildAccessCatalog(CATALOG);
    expect(catalog.map((area) => area.key)).toEqual(['access', 'train', 'other']);
    const user = catalog[0].features.find((feature) => feature.key === 'USER')!;
    expect(user.viewCodes.sort()).toEqual(['USER_LIST', 'USER_READ']);
    expect(user.changeCodes.sort()).toEqual(['USER_CREATE', 'USER_DELETE']);
  });
});

describe('role access levels', () => {
  const catalog = buildAccessCatalog(CATALOG);
  const user = catalog[0].features.find((feature) => feature.key === 'USER')!;
  const refunds = catalog[1].features.find((feature) => feature.key === 'TRAIN_ADMIN_REFUND')!;

  it('reads the level from what is selected', () => {
    expect(levelOf(user, new Set())).toBe('none');
    expect(levelOf(user, new Set(['USER_LIST', 'USER_READ']))).toBe('view');
    expect(levelOf(user, new Set(['USER_LIST', 'USER_READ', 'USER_CREATE', 'USER_DELETE']))).toBe('manage');
    expect(levelOf(user, new Set(['USER_LIST', 'USER_CREATE']))).toBe('custom');
    expect(levelOf(refunds, new Set(['TRAIN_ADMIN_REFUND_CREATE']))).toBe('manage');
  });

  it('only offers levels a feature can actually have', () => {
    expect(availableLevels(user)).toEqual(['none', 'view', 'manage']);
    expect(availableLevels(refunds)).toEqual(['none', 'manage']);
  });

  it('sets a level without touching other features or unknown codes', () => {
    const start = new Set(['AUDIT_LOG_LIST', 'RETIRED_CODE', 'USER_CREATE']);
    const viewOnly = withLevel(start, user, 'view');
    expect([...viewOnly].sort()).toEqual(['AUDIT_LOG_LIST', 'RETIRED_CODE', 'USER_LIST', 'USER_READ']);
    const manage = withLevel(viewOnly, user, 'manage');
    expect(levelOf(user, manage)).toBe('manage');
    expect(levelOf(user, withLevel(manage, user, 'none'))).toBe('none');
    expect(withLevel(manage, user, 'custom')).toEqual(manage);
    expect(start.has('USER_LIST')).toBe(false);
  });

  it('sets a whole area at once and counts what a role has', () => {
    const all = withAreaLevel(new Set(), catalog, 'view');
    expect(all.has('USER_READ')).toBe(true);
    expect(all.has('USER_CREATE')).toBe(false);
    expect(areaProgress(catalog[0], all)).toEqual({ on: 2, total: 2 });
    expect(areasInUse(catalog, all)).toBe(3);
    expect(areasInUse(catalog, new Set())).toBe(0);
  });

  it('finds features by name or description, optionally only those that are on', () => {
    expect(matchingFeatures(catalog[1], 'refund').map((feature) => feature.key)).toEqual(['TRAIN_ADMIN_REFUND']);
    expect(matchingFeatures(catalog[1], '  ').length).toBe(2);
    expect(matchingFeatures(catalog[0], 'looks at').map((feature) => feature.key)).toEqual(['USER']);
    const on = new Set(['USER_LIST']);
    expect(matchingFeatures(catalog[0], '', on, true).map((feature) => feature.key)).toEqual(['USER']);
    expect(matchingFeatures(catalog[0], '', new Set(), true)).toEqual([]);
  });

  it('describes a feature in the words of its look-only permission', () => {
    expect(catalog[0].features.find((feature) => feature.key === 'USER')!.description).toBe('Looks at users');
  });
});

describe('role access summary and review', () => {
  const catalog = buildAccessCatalog(CATALOG);
  const user = catalog[0].features.find((feature) => feature.key === 'USER')!;

  it('counts features by what the role has of them', () => {
    const selected = new Set(['USER_LIST', 'USER_READ', 'USER_CREATE', 'USER_DELETE', 'AUDIT_LOG_LIST']);
    expect(summarize(catalog, selected)).toEqual({ manage: 1, view: 1, custom: 0, none: 3, total: 5 });
    expect(summarize(catalog, new Set(['USER_LIST']))).toMatchObject({ view: 0, custom: 1 });
  });

  it('flags features that can delete or move money', () => {
    expect(isSensitive(user)).toBe(true);
    const audit = catalog[0].features.find((feature) => feature.key === 'AUDIT_LOG')!;
    expect(isSensitive(audit)).toBe(false);
  });

  it('lists what the role manages and looks at per area, leaving out areas it has nothing in', () => {
    const selected = new Set(['USER_LIST', 'USER_READ', 'USER_CREATE', 'USER_DELETE', 'AUDIT_LOG_LIST']);
    expect(reviewLines(catalog, selected)).toEqual([{ area: 'access', manage: ['User'], view: ['Audit log'] }]);
    expect(reviewLines(catalog, new Set())).toEqual([]);
  });
});

describe('role access group level', () => {
  const catalog = buildAccessCatalog(CATALOG);
  const access = catalog[0];

  it('reads the level an area or everything is currently set to, or null for a mix', () => {
    expect(groupLevelOf([access], new Set())).toBe('none');
    expect(groupLevelOf([access], withAreaLevel(new Set(), [access], 'view'))).toBe('view');
    expect(groupLevelOf([access], withAreaLevel(new Set(), [access], 'manage'))).toBe('manage');
    expect(groupLevelOf([access], new Set(['USER_LIST']))).toBeNull();
    expect(groupLevelOf(catalog, withAreaLevel(new Set(), catalog, 'manage'))).toBe('manage');
  });
});


describe('role access applications', () => {
  it('puts each area under its application and sends an unlisted area to the platform', () => {
    expect(applicationOf('rides')).toBe('rider');
    expect(applicationOf('admin')).toBe('rider');
    expect(applicationOf('wallet')).toBe('money');
    expect(applicationOf('payments')).toBe('money');
    expect(applicationOf('access')).toBe('platform');
    expect(applicationOf('never-heard-of-it')).toBe('platform');
  });

  it('groups the areas by application in display order, leaving out an application with no area', () => {
    const areas = buildAccessCatalog(CATALOG);
    const groups = groupByApplication(areas);

    expect(groups.map((g) => g.key)).toEqual(['platform', 'train']);
    expect(groups.find((g) => g.key === 'train')!.areas.map((a) => a.key)).toEqual(['train']);
    expect(groups.find((g) => g.key === 'platform')!.areas.map((a) => a.key)).toEqual(['access', 'other']);
    expect(groupByApplication([])).toEqual([]);
  });
});
