import { offeredValues, searchableSelect } from '../../shared/testing/searchable-select.testing';
import { HqSharedLibModule } from 'hq-shared-lib';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { vi } from 'vitest';

import { QA_SEARCH_DEBOUNCE_MS } from '../qa-dashboard.constants';
import { facets } from '../qa-dashboard.fixtures';
import { emptyFilters } from '../qa-dashboard.helpers';
import { QaFilters } from '../qa-dashboard.models';
import { QaFilterBar } from './qa-filter-bar';

describe('QaFilterBar', () => {
  let fixture: ComponentFixture<QaFilterBar>;
  let bar: QaFilterBar;
  let emitted: QaFilters[];

  function create(inputs: Partial<QaFilterBar> = {}): void {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ declarations: [QaFilterBar], imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule] });
    fixture = TestBed.createComponent(QaFilterBar);
    bar = fixture.componentInstance;
    bar.facets = facets();
    Object.assign(bar, inputs);
    emitted = [];
    bar.filtersChange.subscribe((f) => emitted.push(f));
    fixture.detectChanges();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;

  it('shows only the controls the screen switches on', () => {
    create();
    expect(el().querySelector('.qa-environment')).not.toBeNull();
    expect(el().querySelector('.qa-release')).not.toBeNull();
    ['.qa-search', '.qa-module', '.qa-sub-module', '.qa-status', '.qa-suite', '.qa-from', '.qa-to'].forEach((s) => expect(el().querySelector(s), s).toBeNull());
    create({ showSearch: true, showModule: true, showStatus: true, showSuite: true, showRange: true, showEnvironment: false, showRelease: false });
    ['.qa-search', '.qa-module', '.qa-sub-module', '.qa-status', '.qa-suite', '.qa-from', '.qa-to'].forEach((s) => expect(el().querySelector(s), s).not.toBeNull());
    expect(el().querySelector('.qa-environment')).toBeNull();
    expect(el().querySelector('.qa-release')).toBeNull();
  });

  it('offers the environments and releases the server listed', () => {
    create();
    expect(offeredValues(fixture, '.qa-environment')).toEqual(['qa', 'uat']);
    expect(offeredValues(fixture, '.qa-release')).toEqual(['QA_REG_OCT26_R2', 'QA_REG_OCT25_R1']);
  });

  it('emits the whole filter set with the one change', () => {
    create({ filters: emptyFilters({ q: 'x' }) });
    bar.update({ environment: 'uat' });
    expect(emitted).toEqual([emptyFilters({ q: 'x', environment: 'uat' })]);
  });

  it('forgets the sub-module when the module changes, and lists the sub-modules of the chosen module', async () => {
    create({ showModule: true, filters: emptyFilters({ module: 'wallet', subModule: 'wallet.home' }) });
    expect(bar.subModules).toEqual(['wallet.home', 'wallet.agent']);
    bar.onModuleChange('uam');
    expect(emitted[0]).toEqual(emptyFilters({ module: 'uam', subModule: '' }));
    create({ showModule: true });
    await fixture.whenStable();
    expect(searchableSelect(fixture, '.qa-sub-module').disabled).toBe(true);
  });

  it('waits for a pause in typing before it emits the search text', () => {
    vi.useFakeTimers();
    try {
      create({ showSearch: true });
      bar.onSearchInput('wal');
      vi.advanceTimersByTime(QA_SEARCH_DEBOUNCE_MS - 100);
      bar.onSearchInput('wallet');
      vi.advanceTimersByTime(QA_SEARCH_DEBOUNCE_MS - 100);
      expect(emitted).toEqual([]);
      vi.advanceTimersByTime(200);
      expect(emitted).toEqual([emptyFilters({ q: 'wallet' })]);
      bar.ngOnDestroy();
    } finally {
      vi.useRealTimers();
    }
  });

  it('starts the search box with the text already in the filters', () => {
    create({ showSearch: true, filters: emptyFilters({ q: 'login' }) });
    expect(bar.searchText).toBe('login');
  });

  it('offers to clear filters only when one is set, and clears them all', () => {
    create({ showSearch: true });
    expect(el().querySelector('.qa-clear')).toBeNull();
    create({ showSearch: true, filters: emptyFilters({ status: 'FAILED' }) });
    expect(bar.isFiltered).toBe(true);
    (el().querySelector('.qa-clear') as HTMLButtonElement).click();
    expect(emitted).toEqual([emptyFilters()]);
    expect(bar.searchText).toBe('');
  });

  it('shows translated labels for status, suite and environment, and the raw code when it has none', () => {
    create({ showStatus: true, showSuite: true });
    expect(bar.statusLabel('FAILED')).toBe('FAILED');
    expect(bar.suiteLabel('SMOKE')).toBe('SMOKE');
    expect(bar.environmentLabel('qa')).toBe('qa');
    expect(bar.environmentLabel('other')).toBe('other');
  });

  it('works while the filter options are still loading', () => {
    create({ facets: null, showModule: true });
    expect(bar.subModules).toEqual([]);
    expect(offeredValues(fixture, '.qa-module')).toEqual([]);
  });
});
