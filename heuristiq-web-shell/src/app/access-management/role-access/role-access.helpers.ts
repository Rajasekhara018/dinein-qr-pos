import { Permission } from '../../shared/models/uam.models';
import {
  ROLE_ACCESS_ACTION_WORDS,
  ROLE_ACCESS_APPLICATION_BY_AREA,
  ROLE_ACCESS_APPLICATION_ORDER,
  ROLE_ACCESS_PLATFORM_APPLICATION,
  ROLE_ACCESS_SENSITIVE_WORDS,
  ROLE_ACCESS_AREA_BY_SEGMENT,
  ROLE_ACCESS_AREA_ORDER,
  ROLE_ACCESS_OTHER_AREA,
  ROLE_ACCESS_VIEW_ACTIONS,
} from './role-access.constants';

/** How much of a feature a role has: none, only looking, looking and changing, or a mix that needs the advanced view. */
export type AccessLevel = 'none' | 'view' | 'manage' | 'custom';

/** One thing people work with (users, refunds, banners ...) and the permission codes behind it. */
export interface AccessFeature {
  key: string;
  label: string;
  /** What the feature lets someone do, in the catalogue's own words (empty when it has none). */
  description: string;
  viewCodes: string[];
  changeCodes: string[];
}

export interface AccessArea {
  key: string;
  features: AccessFeature[];
}

/** An application and the areas (sections) under it. */
export interface AccessApplication {
  key: string;
  areas: AccessArea[];
}

/** The application an area belongs to; an area nobody lists belongs to the platform. */
export function applicationOf(areaKey: string): string {
  return ROLE_ACCESS_APPLICATION_BY_AREA[areaKey] ?? ROLE_ACCESS_PLATFORM_APPLICATION;
}

/** The areas grouped by application, applications in display order, areas keeping their order; an application with no area is left out. */
export function groupByApplication(areas: readonly AccessArea[]): AccessApplication[] {
  const byKey = new Map<string, AccessArea[]>();
  for (const area of areas) {
    const key = applicationOf(area.key);
    byKey.set(key, [...(byKey.get(key) ?? []), area]);
  }
  const known = ROLE_ACCESS_APPLICATION_ORDER.filter((key) => byKey.has(key));
  const unknown = [...byKey.keys()].filter((key) => !ROLE_ACCESS_APPLICATION_ORDER.includes(key));
  return [...known, ...unknown].map((key) => ({ key, areas: byKey.get(key) ?? [] }));
}

const API_PATH_AREA = /^\/api\/v\d+\/([^/]+)/;

function segmentOf(permission: Permission): string {
  const apiPath = permission.attributes?.['apipath'];
  const match = typeof apiPath === 'string' ? API_PATH_AREA.exec(apiPath) : null;
  return (match ? match[1] : permission.code.split('_')[0]).toLowerCase();
}

/** The area a permission is grouped under. */
export function areaKeyOf(permission: Permission): string {
  return ROLE_ACCESS_AREA_BY_SEGMENT[segmentOf(permission)] ?? ROLE_ACCESS_OTHER_AREA;
}

/** The permission code without its trailing action words: USER_CREATE and USER_READ are both the feature USER. */
export function featureKeyOf(code: string): string {
  const words = code.split('_');
  while (words.length > 1 && ROLE_ACCESS_ACTION_WORDS.has(words[words.length - 1])) {
    words.pop();
  }
  return words.join('_');
}

/** USER_SESSION -> "User session". */
export function featureLabelOf(featureKey: string): string {
  const text = featureKey.toLowerCase().replace(/_/g, ' ');
  return text.charAt(0).toUpperCase() + text.slice(1);
}

function isViewPermission(permission: Permission): boolean {
  const action = permission.action ?? permission.code.split('_').pop() ?? '';
  return ROLE_ACCESS_VIEW_ACTIONS.has(action.toUpperCase());
}

/** Groups the permission catalogue into areas of features; an area or feature with nothing in it is left out. */
export function buildAccessCatalog(permissions: readonly Permission[]): AccessArea[] {
  const byArea = new Map<string, Map<string, AccessFeature>>();
  for (const permission of permissions) {
    const area = areaKeyOf(permission);
    const key = featureKeyOf(permission.code);
    const features = byArea.get(area) ?? new Map<string, AccessFeature>();
    const feature = features.get(key) ?? { key, label: featureLabelOf(key), description: '', viewCodes: [], changeCodes: [] };
    const isView = isViewPermission(permission);
    (isView ? feature.viewCodes : feature.changeCodes).push(permission.code);
    // Prefer the wording of a look-only permission ("Allows viewing a user's details"), else the first one with any.
    if (permission.description && (!feature.description || (isView && feature.viewCodes.length === 1))) {
      feature.description = permission.description;
    }
    features.set(key, feature);
    byArea.set(area, features);
  }
  return ROLE_ACCESS_AREA_ORDER.filter((area) => byArea.has(area)).map((area) => ({
    key: area,
    features: [...byArea.get(area)!.values()].sort((a, b) => a.label.localeCompare(b.label)),
  }));
}

function countSelected(codes: readonly string[], selected: ReadonlySet<string>): number {
  return codes.reduce((total, code) => total + (selected.has(code) ? 1 : 0), 0);
}

/** What the role currently has of this feature. */
export function levelOf(feature: AccessFeature, selected: ReadonlySet<string>): AccessLevel {
  const view = countSelected(feature.viewCodes, selected);
  const change = countSelected(feature.changeCodes, selected);
  if (view + change === 0) return 'none';
  const allView = view === feature.viewCodes.length;
  const allChange = change === feature.changeCodes.length;
  if (allView && allChange) return feature.changeCodes.length === 0 ? 'view' : 'manage';
  if (allView && change === 0 && feature.viewCodes.length > 0) return 'view';
  return 'custom';
}

/** The levels a feature can be set to: no looking-only choice when it has nothing to look at, and the reverse. */
export function availableLevels(feature: AccessFeature): AccessLevel[] {
  const levels: AccessLevel[] = ['none'];
  if (feature.viewCodes.length > 0) levels.push('view');
  if (feature.changeCodes.length > 0) levels.push('manage');
  return levels;
}

/** A copy of the selection with this feature set to the level; 'custom' changes nothing. Codes of other features are untouched. */
export function withLevel(selected: ReadonlySet<string>, feature: AccessFeature, level: AccessLevel): Set<string> {
  const next = new Set(selected);
  if (level === 'custom') return next;
  [...feature.viewCodes, ...feature.changeCodes].forEach((code) => next.delete(code));
  if (level === 'view' || level === 'manage') feature.viewCodes.forEach((code) => next.add(code));
  if (level === 'manage') feature.changeCodes.forEach((code) => next.add(code));
  return next;
}

/** A copy of the selection with every feature of the areas set to the level. */
export function withAreaLevel(selected: ReadonlySet<string>, areas: readonly AccessArea[], level: AccessLevel): Set<string> {
  return areas.reduce(
    (current, area) => area.features.reduce((acc, feature) => withLevel(acc, feature, level), current),
    new Set(selected),
  );
}

/** How many of the area's features the role has at all, and how many there are. */
export function areaProgress(area: AccessArea, selected: ReadonlySet<string>): { on: number; total: number } {
  return { on: area.features.filter((feature) => levelOf(feature, selected) !== 'none').length, total: area.features.length };
}

/**
 * Features whose name or description contains the text (an empty text keeps every feature); with onlyOn, only the ones the role
 * has anything of.
 */
export function matchingFeatures(area: AccessArea, text: string, selected?: ReadonlySet<string>, onlyOn = false): AccessFeature[] {
  const wanted = text.trim().toLowerCase();
  return area.features.filter((feature) =>
    (!wanted || feature.label.toLowerCase().includes(wanted) || feature.description.toLowerCase().includes(wanted)) &&
    (!onlyOn || !selected || levelOf(feature, selected) !== 'none'));
}

/** Number of areas in which the role has anything. */
export function areasInUse(areas: readonly AccessArea[], selected: ReadonlySet<string>): number {
  return areas.filter((area) => areaProgress(area, selected).on > 0).length;
}

/** How the role's features split: managed, look-only, mixed or not at all. */
export interface AccessSummary {
  manage: number;
  view: number;
  custom: number;
  none: number;
  total: number;
}

export function summarize(areas: readonly AccessArea[], selected: ReadonlySet<string>): AccessSummary {
  const summary: AccessSummary = { manage: 0, view: 0, custom: 0, none: 0, total: 0 };
  for (const area of areas) {
    for (const feature of area.features) {
      summary[levelOf(feature, selected)] += 1;
      summary.total += 1;
    }
  }
  return summary;
}

/** True when switching the feature to Manage can delete data or move money, so it deserves a second look. */
export function isSensitive(feature: AccessFeature): boolean {
  return feature.changeCodes.some((code) => code.split('_').some((word) => ROLE_ACCESS_SENSITIVE_WORDS.has(word)));
}

/** One line of the review: an area and how many of its features the role manages and looks at. */
export interface AccessReviewLine {
  area: string;
  manage: string[];
  view: string[];
}

/** What the role can do, area by area; an area it has nothing in is left out. */
export function reviewLines(areas: readonly AccessArea[], selected: ReadonlySet<string>): AccessReviewLine[] {
  return areas
    .map((area) => ({
      area: area.key,
      manage: area.features.filter((feature) => levelOf(feature, selected) === 'manage').map((feature) => feature.label),
      view: area.features.filter((feature) => levelOf(feature, selected) === 'view').map((feature) => feature.label),
    }))
    .filter((line) => line.manage.length + line.view.length > 0);
}

/**
 * The level a group of areas is currently set to as a whole: the one whose result matches what the role has now, or null for a
 * mix. "None" is checked first, then "View", then "Manage", so an area whose features are all look-only reads as View.
 */
export function groupLevelOf(areas: readonly AccessArea[], selected: ReadonlySet<string>): AccessLevel | null {
  const codes = areas.flatMap((area) => area.features.flatMap((feature) => [...feature.viewCodes, ...feature.changeCodes]));
  for (const level of ['none', 'view', 'manage'] as const) {
    const expected = withAreaLevel(selected, areas, level);
    if (codes.every((code) => expected.has(code) === selected.has(code))) {
      return level;
    }
  }
  return null;
}
