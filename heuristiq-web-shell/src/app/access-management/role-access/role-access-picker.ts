import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Permission } from '../../shared/models/uam.models';
import { ROLE_ACCESS_APPLICATION_ICONS, ROLE_ACCESS_AREA_ICONS, ROLE_ACCESS_PLATFORM_APPLICATION, ROLE_ACCESS_SHARED_APPLICATIONS } from './role-access.constants';
import {
  AccessApplication, AccessArea, AccessFeature, AccessLevel, AccessReviewLine, AccessSummary, areaProgress, areasInUse, availableLevels,
  buildAccessCatalog, groupByApplication, groupLevelOf, isSensitive, levelOf, matchingFeatures, reviewLines, summarize, withAreaLevel, withLevel,
} from './role-access.helpers';

/** An application shown as a group: the areas that pass the filters (`areas`) and all of its areas (`all`, for the group's own None / View / Manage). */
export interface AccessApplicationView {
  key: string;
  areas: AccessArea[];
  all: AccessArea[];
}

/**
 * Picks what a role may do in plain terms: the 800-odd technical permission codes are grouped into areas (Access management,
 * Matrimony, Train ...) and features (Users, Refunds ...), and each feature is set to None, View or Manage. Whole areas, or
 * everything, can be set in one click. The areas sit under their application (Rider app, Matrimony, Train ...): chips above the cards
 * narrow the list to one application, and each application group has its own None / View / Manage. The selection is the same set of permission codes the role stores, so the advanced
 * table and this picker can be used on the same role.
 */
@Component({
  selector: 'app-role-access-picker',
  standalone: false,
  templateUrl: './role-access-picker.html',
  styleUrl: './role-access-picker.scss',
})
export class RoleAccessPicker implements OnChanges {
  private readonly translate = inject(TranslateService);

  @Input({ required: true }) permissions: Permission[] = [];
  @Input({ required: true }) selected: ReadonlySet<string> = new Set();
  @Output() selectedChange = new EventEmitter<Set<string>>();

  areas: AccessArea[] = [];
  /** The areas grouped by application, in display order. */
  applications: AccessApplication[] = [];
  /** The application the list is narrowed to; empty for all of them. */
  application = '';
  category: 'all' | 'products' | 'shared' = 'all';
  service = '';
  readonly categories = ['all', 'products', 'shared'] as const;

  get categoryApplications(): AccessApplication[] {
    return this.applications.filter(app => this.category === 'all'
      || (this.category === 'shared') === ROLE_ACCESS_SHARED_APPLICATIONS.includes(app.key));
  }

  get scopeAreas(): AccessArea[] {
    return this.categoryApplications.filter(app => !this.application || app.key === this.application)
      .flatMap(app => app.areas).filter(area => !this.service || area.key === this.service);
  }

  get serviceAreas(): AccessArea[] {
    return this.categoryApplications.filter(app => !this.application || app.key === this.application).flatMap(app => app.areas);
  }

  setCategory(category: 'all' | 'products' | 'shared'): void {
    this.category = category;
    this.application = '';
    this.service = '';
  }

  setService(key: string): void {
    this.service = key;
    if (key) this.open.add(key);
  }

  searchText = '';
  /** Show only the features this role already has something of. */
  onlyOn = false;
  private readonly open = new Set<string>();
  private levelCache: { selected: ReadonlySet<string>; areas: AccessArea[]; byArea: Map<string, AccessLevel | null>; all: AccessLevel | null } | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['permissions']) {
      this.areas = buildAccessCatalog(this.permissions);
      this.applications = groupByApplication(this.areas);
      if (this.service && !this.areas.some(area => area.key === this.service)) this.service = '';
      if (this.application && !this.applications.some((app) => app.key === this.application)) {
        this.application = '';
      }
    }
  }

  /** Which of None / View / Manage each area, and everything, is currently set to (null for a mix); recomputed only when the selection changes. */
  private levels(): NonNullable<RoleAccessPicker['levelCache']> {
    if (!this.levelCache || this.levelCache.selected !== this.selected || this.levelCache.areas !== this.areas) {
      this.levelCache = {
        selected: this.selected,
        areas: this.areas,
        byArea: new Map(this.areas.map((area) => [area.key, groupLevelOf([area], this.selected)])),
        all: groupLevelOf(this.areas, this.selected),
      };
    }
    return this.levelCache;
  }

  areaLevel(area: AccessArea): AccessLevel | null {
    return this.levels().byArea.get(area.key) ?? null;
  }

  get everythingLevel(): AccessLevel | null {
    return groupLevelOf(this.scopeAreas, this.selected);
  }

  get areasOn(): number {
    return areasInUse(this.areas, this.selected);
  }

  get permissionCount(): number {
    return this.selected.size;
  }

  get summary(): AccessSummary {
    return summarize(this.areas, this.selected);
  }

  /** Share of all features (0-100) for each part of the summary bar. */
  percentOf(count: number): number {
    const total = this.summary.total;
    return total === 0 ? 0 : (count / total) * 100;
  }

  get review(): AccessReviewLine[] {
    return reviewLines(this.areas, this.selected);
  }

  sensitive(feature: AccessFeature): boolean {
    return isSensitive(feature);
  }

  /** The area's name in the user's language, by area key (used by the review, which only has keys). */
  areaNameByKey(key: string): string {
    return this.areaName({ key, features: [] });
  }

  get totalFeatures(): number {
    return this.areas.reduce((total, area) => total + area.features.length, 0);
  }

  /** Features that pass the search and the "only what's on" filter, across every area. */
  get shownFeatures(): number {
    return this.visibleApplications.reduce((total, app) => total + app.areas.reduce((sum, area) => sum + this.featuresOf(area).length, 0), 0);
  }

  get filtering(): boolean {
    return !!this.searchText.trim() || this.onlyOn;
  }

  /** Areas that have at least one feature passing the filters; with no filter, every area. */
  get visibleAreas(): AccessArea[] {
    return this.filtering ? this.areas.filter((area) => this.featuresOf(area).length > 0) : this.areas;
  }

  /** The applications to show, each with the areas that pass the filters; one application only when a chip is chosen. */
  get visibleApplications(): AccessApplicationView[] {
    const visible = new Set(this.visibleAreas.map((area) => area.key));
    return this.categoryApplications
      .filter((app) => !this.application || app.key === this.application)
      .map((app) => ({ key: app.key, areas: app.areas.filter((area) => visible.has(area.key) && (!this.service || area.key === this.service)), all: app.areas.filter(area => !this.service || area.key === this.service) }))
      .filter((app) => app.areas.length > 0);
  }

  setApplication(key: string): void {
    this.application = this.application === key ? '' : key;
    this.service = '';
    if (this.application) this.serviceAreas.forEach(area => this.open.add(area.key));
  }

  /** How many of an application's features have something switched on, of how many it has. */
  applicationProgress(app: AccessApplication): { on: number; total: number } {
    return app.areas.reduce(
      (sum, area) => {
        const { on, total } = areaProgress(area, this.selected);
        return { on: sum.on + on, total: sum.total + total };
      },
      { on: 0, total: 0 },
    );
  }

  applicationLevel(app: AccessApplicationView): AccessLevel | null {
    return groupLevelOf(app.all, this.selected);
  }

  setApplicationLevel(app: AccessApplicationView, level: AccessLevel): void {
    this.selectedChange.emit(withAreaLevel(this.selected, app.all, level));
  }

  applicationIcon(key: string): string {
    return ROLE_ACCESS_APPLICATION_ICONS[key] ?? ROLE_ACCESS_APPLICATION_ICONS[ROLE_ACCESS_PLATFORM_APPLICATION];
  }

  /** The application's name in the user's language, or the key as a readable title when no translation exists. */
  applicationName(key: string): string {
    const translationKey = `accessManagement.roles.access.applications.${key}`;
    const text = this.translate.instant(translationKey);
    return text === translationKey ? key.charAt(0).toUpperCase() + key.slice(1) : text;
  }

  trackApplication(_: number, app: { key: string }): string {
    return app.key;
  }

  featuresOf(area: AccessArea): AccessFeature[] {
    return matchingFeatures(area, this.searchText, this.selected, this.onlyOn);
  }

  isOpen(area: AccessArea): boolean {
    return this.open.has(area.key) || this.filtering;
  }

  toggle(area: AccessArea): void {
    if (this.open.has(area.key)) {
      this.open.delete(area.key);
    } else {
      this.open.add(area.key);
    }
  }

  expandAll(): void {
    this.areas.forEach((area) => this.open.add(area.key));
  }

  collapseAll(): void {
    this.open.clear();
  }

  progress(area: AccessArea): { on: number; total: number; percent: number } {
    const { on, total } = areaProgress(area, this.selected);
    return { on, total, percent: total === 0 ? 0 : Math.round((on / total) * 100) };
  }

  icon(area: AccessArea): string {
    return ROLE_ACCESS_AREA_ICONS[area.key] ?? ROLE_ACCESS_AREA_ICONS['other'];
  }

  levelOf(feature: AccessFeature): AccessLevel {
    return levelOf(feature, this.selected);
  }

  can(feature: AccessFeature, level: AccessLevel): boolean {
    return availableLevels(feature).includes(level);
  }

  setLevel(feature: AccessFeature, level: AccessLevel): void {
    this.selectedChange.emit(withLevel(this.selected, feature, level));
  }

  setArea(area: AccessArea, level: AccessLevel): void {
    this.selectedChange.emit(withAreaLevel(this.selected, [area], level));
  }

  setEverything(level: AccessLevel): void {
    this.selectedChange.emit(withAreaLevel(this.selected, this.scopeAreas, level));
  }

  clearFilters(): void {
    this.searchText = '';
    this.onlyOn = false;
    this.application = '';
    this.category = 'all';
    this.service = '';
  }

  /** The area's name in the user's language, or the key as a readable title when no translation exists. */
  areaName(area: AccessArea): string {
    const key = `accessManagement.roles.access.areas.${area.key}`;
    const text = this.translate.instant(key);
    return text === key ? area.key.charAt(0).toUpperCase() + area.key.slice(1) : text;
  }

  trackArea(_: number, area: AccessArea): string {
    return area.key;
  }

  trackFeature(_: number, feature: AccessFeature): string {
    return feature.key;
  }
}
