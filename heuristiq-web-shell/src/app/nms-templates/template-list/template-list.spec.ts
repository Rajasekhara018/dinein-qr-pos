import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule, HqTimezoneService } from 'hq-shared-lib';
import { BehaviorSubject, EMPTY, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { SearchApiService, UAM_SEARCH_MODELS } from '../../shared/services/search-api-service';
import { NmsTemplate } from '../nms-template.model';
import { NmsTemplatesService } from '../nms-templates.service';
import { TemplateList } from './template-list';

const tpl = (id: string, over: Partial<NmsTemplate> = {}): NmsTemplate =>
  ({ id, event: 'BOOKING_CONFIRMED', channel: 'EMAIL', name: 'Name ' + id, body: 'b', active: true, tenantId: null, updatedAt: '2026-02-03T00:00:00Z', ...over });

describe('TemplateList', () => {
  let query$ = new BehaviorSubject(convertToParamMap({ channel: 'SMS' }));
  const defaultPage = () => of({ content: [tpl('1'), tpl('2', { active: false, tenantId: 'org1', updatedAt: undefined })], totalElements: 25, totalPages: 3 });
  const router = { navigate: vi.fn() };
  const svc = { remove: vi.fn((_id: string): Observable<void> => of(undefined)) };
  const search = {
    searchRows: vi.fn((_m: unknown, _f: Record<string, unknown>, _p: number, _s: number): Observable<unknown> =>
      of({ content: [tpl('1'), tpl('2', { active: false, tenantId: 'org1', updatedAt: undefined })], totalElements: 25, totalPages: 3 })),
  };
  const notify = { error: vi.fn(), success: vi.fn() };
  const dialogs = { confirm: vi.fn((_: unknown): Observable<boolean> => of(true)) };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };

  function create(embeddedChannel?: string): ComponentFixture<TemplateList> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [TemplateList],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: ActivatedRoute, useValue: { queryParamMap: query$ } },
        { provide: Router, useValue: router },
        { provide: NmsTemplatesService, useValue: svc },
        { provide: SearchApiService, useValue: search },
        { provide: AppDialogService, useValue: dialogs },
        { provide: NotificationService, useValue: notify },
        { provide: HqTimezoneService, useValue: { formatLocale: (v: string) => 'd:' + v } },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    const fixture = TestBed.createComponent(TemplateList);
    if (embeddedChannel) {
      fixture.componentRef.setInput('embedded', true);
      fixture.componentRef.setInput('channel', embeddedChannel);
    }
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    query$ = new BehaviorSubject(convertToParamMap({ channel: 'SMS' }));
    search.searchRows.mockImplementation(defaultPage);
    dialogs.confirm.mockImplementation(() => of(true));
  });

  it('loads the first page for the channel in the URL, with stats for the channel', () => {
    const c = create().componentInstance;
    expect(c.channel).toBe('SMS');
    expect(search.searchRows).toHaveBeenCalledWith(UAM_SEARCH_MODELS.nmsTemplates, { name: '', event: '', status: '', channel: 'SMS' }, 0, 10);
    expect(c.rows).toHaveLength(2);
    expect([c.totalElements, c.totalPages]).toEqual([25, 3]);
    expect(search.searchRows).toHaveBeenCalledWith(UAM_SEARCH_MODELS.nmsTemplates, { channel: 'SMS', status: 'ACTIVE' }, 0, 1);
    expect(c.stats.map((s) => s.value)).toEqual([25, 25, 25]);
  });

  it('works without a channel, and defaults missing totals', () => {
    query$.next(convertToParamMap({}));
    search.searchRows.mockReturnValue(of({}));
    const c = create().componentInstance;
    expect(c.channel).toBeNull();
    expect(search.searchRows.mock.calls[0][1]).toEqual({ name: '', event: '', status: '' });
    expect(c.rows).toEqual([]);
    expect([c.totalElements, c.totalPages]).toEqual([0, 1]);
    expect(c.stats.map((s) => s.value)).toEqual([0, 0, 0]);
  });

  it('flags a failed load and keeps going when only the stats fail', () => {
    search.searchRows.mockReturnValue(throwError(() => new Error('x')));
    const c = create().componentInstance;
    expect(c.hasError).toBe(true);
    expect(c.isLoading).toBe(false);
    expect(notify.error).toHaveBeenCalledWith('nmsTemplates.list.messages.loadFailed');
    expect(c.stats).toEqual([]);
  });

  it('renders the columns: event label, scope, status and date', () => {
    const c = create().componentInstance;
    const [name, event, , scope, status, updated] = c.columns;
    expect(name.key).toBe('name');
    expect(event.render?.(c.rows[0])).toBe('Booking Confirmed');
    expect(scope.render?.(c.rows[0])).toBe('nmsTemplates.list.scope.shared');
    expect(scope.render?.(c.rows[1])).toBe('nmsTemplates.list.scope.organization');
    expect(status.render?.(c.rows[0])).toBe('nmsTemplates.list.stats.active');
    expect(status.render?.(c.rows[1])).toBe('nmsTemplates.list.stats.inactive');
    expect(updated.render?.(c.rows[0])).toBe('d:2026-02-03T00:00:00Z');
    expect(updated.render?.(c.rows[1])).toBe('—');
  });

  describe('filters and paging', () => {
    it('applies filters from page one, and clears them', () => {
      const c = create().componentInstance;
      c.page = 2;
      expect(c.hasActiveFilters).toBe(false);
      c.filters = { name: ' ops ', event: '', status: '' };
      c.applyFilters();
      expect(c.page).toBe(0);
      expect(search.searchRows).toHaveBeenCalledWith(UAM_SEARCH_MODELS.nmsTemplates, expect.objectContaining({ name: ' ops ' }), 0, 10);
      expect(c.hasActiveFilters).toBe(true);
      c.clearFilters();
      expect(c.filters).toEqual({ name: '', event: '', status: '' });
    });

    it('summarises the active filters for the empty message', () => {
      const c = create().componentInstance;
      c.filters = { name: 'a', event: 'b', status: '' };
      expect(c.filterSummary).toBe('a / b');
      c.filters = { name: '', event: '', status: 'ACTIVE' };
      expect(c.filterSummary).toBe('nmsTemplates.list.stats.active');
      c.filters = { name: '', event: '', status: 'INACTIVE' };
      expect(c.filterSummary).toBe('nmsTemplates.list.stats.inactive');
    });

    it('changes page and page size', () => {
      const c = create().componentInstance;
      c.goToPage(2);
      expect(search.searchRows).toHaveBeenCalledWith(expect.anything(), expect.anything(), 2, 10);
      c.onPageSizeChange(25);
      expect([c.page, c.pageSize]).toEqual([0, 25]);
      expect(search.searchRows).toHaveBeenCalledWith(expect.anything(), expect.anything(), 0, 25);
    });
  });

  it('titles the page by channel', () => {
    const c = create().componentInstance;
    c.channel = null;
    expect(c.pageTitle).toBe('nmsTemplates.list.pageTitle.all');
    for (const [ch, key] of [['EMAIL', 'email'], ['SMS', 'sms'], ['WHATSAPP', 'whatsapp'], ['FCM_PUSH', 'fcmPush']] as const) {
      c.channel = ch;
      expect(c.pageTitle).toBe('nmsTemplates.list.pageTitle.' + key);
    }
    c.channel = 'TELEX' as never;
    expect(c.pageTitle).toBe('nmsTemplates.list.pageTitle.generic');
  });

  describe('navigation', () => {
    it('creates keeping the channel, edits by id, and goes back to the channel list', () => {
      const c = create().componentInstance;
      c.create();
      expect(router.navigate).toHaveBeenLastCalledWith(['/nms-templates/create'], { queryParams: { channel: 'SMS' } });
      c.channel = null;
      c.create();
      expect(router.navigate).toHaveBeenLastCalledWith(['/nms-templates/create'], { queryParams: {} });
      c.edit(tpl('9'));
      expect(router.navigate).toHaveBeenLastCalledWith(['/nms-templates/edit', '9']);
      c.back();
      expect(router.navigate).toHaveBeenLastCalledWith(['/nms-templates']);
      c.channel = 'SMS';
      c.back();
      expect(router.navigate).toHaveBeenLastCalledWith(['/nms-templates'], { queryParams: { channel: 'SMS' } });
    });
  });

  describe('delete', () => {
    it('deletes after a danger confirmation and reloads', () => {
      const c = create().componentInstance;
      search.searchRows.mockClear();
      c.delete(tpl('1'));
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ variant: 'danger' }));
      expect(svc.remove).toHaveBeenCalledWith('1');
      expect(notify.success).toHaveBeenCalledWith('nmsTemplates.list.messages.deleted');
      expect(search.searchRows).toHaveBeenCalled();
    });

    it('does nothing when declined, and reports a failed delete', () => {
      const c = create().componentInstance;
      dialogs.confirm.mockImplementation(() => of(false));
      c.delete(tpl('1'));
      expect(svc.remove).not.toHaveBeenCalled();
      dialogs.confirm.mockImplementation(() => of(true));
      svc.remove.mockReturnValueOnce(throwError(() => new Error('x')));
      c.delete(tpl('1'));
      expect(notify.error).toHaveBeenCalledWith('nmsTemplates.list.messages.deleteFailed');
    });
  });

  describe('privileges', () => {
    const header = (el: HTMLElement) => el.querySelector('.table-create-btn');
    const rowButtons = (el: HTMLElement) => el.querySelectorAll('.row-action-btn');

    it('shows New, Edit and Delete to a full manager', () => {
      const el: HTMLElement = create().nativeElement;
      expect(header(el)).not.toBeNull();
      expect(rowButtons(el).length).toBe(4);
      (header(el) as HTMLElement).click();
      expect(router.navigate).toHaveBeenCalledWith(['/nms-templates/create'], { queryParams: { channel: 'SMS' } });
    });

    it('a viewer sees the rows but no New, Edit or Delete', () => {
      granted = new Set(['NMS_TEMPLATE_LIST']);
      const el: HTMLElement = create().nativeElement;
      expect(header(el)).toBeNull();
      expect(rowButtons(el).length).toBe(0);
      expect(el.querySelectorAll('tbody tr').length).toBe(2);
    });

    it('each privilege unlocks only its own action', () => {
      granted = new Set(['NMS_TEMPLATE_UPDATE']);
      let el: HTMLElement = create().nativeElement;
      expect(rowButtons(el).length).toBe(2);
      expect(el.querySelector('.row-action-btn.danger')).toBeNull();
      granted = new Set(['NMS_TEMPLATE_DELETE']);
      el = create().nativeElement;
      expect(el.querySelectorAll('.row-action-btn.danger').length).toBe(2);
      granted = new Set(['NMS_MANAGE']);
      el = create().nativeElement;
      expect(header(el)).not.toBeNull();
      expect(rowButtons(el).length).toBe(4);
    });
  });
  describe('embedded under the channel tabs', () => {
    it('lists the channel given by the tab, not the one in the address, and shows no header of its own', () => {
      query$.next(convertToParamMap({ channel: 'SMS' }));
      const fixture = create('EMAIL');

      expect(search.searchRows).toHaveBeenCalledWith(UAM_SEARCH_MODELS.nmsTemplates, expect.objectContaining({ channel: 'EMAIL' }), 0, 10);
      expect(fixture.componentInstance.channel).toBe('EMAIL');
      expect((fixture.nativeElement as HTMLElement).querySelector('hq-page-header')).toBeNull();
    });

    it('starts the new channel from its first page with the filters cleared when another tab is chosen', () => {
      const fixture = create('EMAIL');
      const list = fixture.componentInstance;
      list.filters = { name: 'welcome', event: 'X', status: 'ACTIVE' };
      list.page = 2;
      search.searchRows.mockClear();

      fixture.componentRef.setInput('channel', 'SMS');
      fixture.detectChanges();

      expect(list.page).toBe(0);
      expect(list.filters).toEqual({ name: '', event: '', status: '' });
      expect(search.searchRows).toHaveBeenCalledWith(UAM_SEARCH_MODELS.nmsTemplates, { name: '', event: '', status: '', channel: 'SMS' }, 0, 10);
    });

    it('sends the user to the new-template form for the channel of the open tab', () => {
      const fixture = create('WHATSAPP');
      fixture.componentInstance.create();

      expect(router.navigate).toHaveBeenCalledWith(['/nms-templates/create'], { queryParams: { channel: 'WHATSAPP' } });
    });
  });
});
