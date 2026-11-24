import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { HqLookupService, HqPrivilegeAccessService, HqSharedLibModule, HqTableFooter } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { NotificationService } from '../../shared/services/notification-service';
import { SearchApiService, UAM_SEARCH_MODELS } from '../../shared/services/search-api-service';
import { UamService } from '../../shared/services/uam-service';
import { OrganizationMembers } from './organization-members';

interface LookupConfig {
  idOf: (u: unknown) => string;
  chipLabel: (u: unknown) => string;
  columns: { key: string; render?: (u: unknown) => string }[];
  fetch: (query: string, page: number, size: number) => { subscribe: (cb: (v: unknown) => void) => void };
}

describe('OrganizationMembers', () => {
  const uam = { list: vi.fn(), create: vi.fn(), update: vi.fn() };
  const search = { searchRows: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  const lookup = { open: vi.fn() };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };

  const members = [
    { displayName: 'Asha', userName: 'asha', email: 'a@x.io', userType: 'DRIVER', membership: { id: 'm1', userId: 'u1', status: 'ACTIVE', joinedAt: '2026-01-02T00:00:00Z' } },
    { displayName: '', userName: 'ravi', email: '', userType: 'PARTNER_X', membership: { id: 'm2', userId: 'u2', status: 'SUSPENDED', joinedAt: '2026-01-03T00:00:00Z' } },
    { displayName: '', userName: '', email: '', membership: { id: 'm3', userId: 'u3', status: 'REMOVED', joinedAt: '2026-01-04T00:00:00Z' } },
  ];

  function create(): ComponentFixture<OrganizationMembers> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [OrganizationMembers],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        { provide: UamService, useValue: uam },
        { provide: SearchApiService, useValue: search },
        { provide: NotificationService, useValue: notify },
        { provide: HqLookupService, useValue: lookup },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    const fixture = TestBed.createComponent(OrganizationMembers);
    fixture.componentRef.setInput('tenantId', 'org1');
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    uam.list.mockReturnValue(of({ content: members, totalElements: 25, totalPages: 3 }));
    uam.create.mockReturnValue(of({}));
    uam.update.mockReturnValue(of({}));
    lookup.open.mockReturnValue(of([]));
  });

  it('loads the first page of members', () => {
    const component = create().componentInstance;

    expect(uam.list).toHaveBeenCalledWith('/organizations/org1/members', 0, 10);
    expect(component.rows.length).toBe(3);
    expect([component.totalElements, component.totalPages]).toEqual([25, 3]);
  });

  it('renders names with a fallback to user name and id, and the membership status', () => {
    const text: string = create().nativeElement.textContent;

    expect(text).toContain('Asha');
    expect(text).toContain('ravi');
    expect(text).toContain('u3');
    expect(text).toContain('accessManagement.organizations.members.statuses.SUSPENDED');
  });

  it('defaults totals when the response has none, and shows the empty message', () => {
    uam.list.mockReturnValue(of({ content: undefined }));
    const fixture = create();

    expect(fixture.componentInstance.totalPages).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('accessManagement.organizations.members.empty');
  });

  it('reports a failed load', () => {
    uam.list.mockReturnValue(throwError(() => ({ error: { detail: 'No access' } })));
    create();
    expect(notify.error).toHaveBeenLastCalledWith('No access');

    uam.list.mockReturnValue(throwError(() => new Error('x')));
    create();
    expect(notify.error).toHaveBeenLastCalledWith('accessManagement.organizations.members.messages.loadFailed');
  });

  it('pages within bounds only', () => {
    const component = create().componentInstance;
    uam.list.mockClear();

    component.goTo(-1);
    component.goTo(3);
    expect(uam.list).not.toHaveBeenCalled();
    component.goTo(1);
    expect(uam.list).toHaveBeenCalledWith('/organizations/org1/members', 1, 10);
  });

  it('pages through the shared table footer, with the totals and page sizes', () => {
    const fixture = create();
    const footer = fixture.debugElement.query(By.directive(HqTableFooter));
    const instance = footer.componentInstance as HqTableFooter;

    expect([instance.page, instance.size, instance.itemCount, instance.totalItems, instance.hasMore]).toEqual([0, 10, 3, 25, true]);
    expect(instance.sizeOptions).toEqual([10, 25, 50]);
    expect(fixture.nativeElement.querySelector('.om__pager')).toBeNull();

    footer.triggerEventHandler('pageChange', 1);
    expect(fixture.componentInstance.page).toBe(1);
    expect(uam.list).toHaveBeenLastCalledWith('/organizations/org1/members', 1, 10);
  });

  it('changing the page size reloads from the first page, and the same size changes nothing', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    component.goTo(2);
    uam.list.mockClear();

    component.changePageSize(10);
    expect(uam.list).not.toHaveBeenCalled();

    fixture.debugElement.query(By.directive(HqTableFooter)).triggerEventHandler('sizeChange', 25);
    expect([component.page, component.pageSize]).toEqual([0, 25]);
    expect(uam.list).toHaveBeenCalledWith('/organizations/org1/members', 0, 25);
  });

  it('shows the user type of each member: a translated label, an unknown code as it is, a dash when there is none', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const text: string = fixture.nativeElement.textContent;

    expect(component.columns.map((c) => c.key)).toEqual(['member', 'userType', 'status', 'joined']);
    expect(component.userTypeKey(component.rows[0])).toBe('accessManagement.organizations.members.userTypes.DRIVER');
    expect(component.userTypeKey(component.rows[1])).toBeNull();
    expect(component.userTypeKey(component.rows[2])).toBeNull();
    expect(text).toContain('accessManagement.organizations.members.userTypes.DRIVER');
    expect(text).toContain('PARTNER_X');
    expect(text).toContain('—');
  });

  it('hides the footer when there is nothing to page', () => {
    uam.list.mockReturnValue(of({ content: [], totalElements: 0, totalPages: 0 }));

    expect(create().debugElement.query(By.directive(HqTableFooter))).toBeNull();
  });

  it('names a member and tracks rows by membership id', () => {
    const component = create().componentInstance;

    expect(component.nameOf(members[0] as never)).toBe('Asha');
    expect(component.nameOf(members[1] as never)).toBe('ravi');
    expect(component.nameOf(members[2] as never)).toBe('u3');
    expect(component.trackRow(0, members[1] as never)).toBe('m2');
  });

  it('knows who is already an active member', () => {
    const component = create().componentInstance;

    expect(component.isMember({ id: 'u1' } as never)).toBe(true);
    expect(component.isMember({ id: 'u2' } as never)).toBe(false);
    expect(component.isMember({ id: 'zz' } as never)).toBe(false);
  });

  it('changes the status of a member, reloading afterwards', () => {
    const component = create().componentInstance;
    uam.list.mockClear();

    component.setStatus(members[0] as never, 'SUSPENDED');

    expect(uam.update).toHaveBeenCalledWith('/organizations/org1/members', 'u1', { status: 'SUSPENDED' });
    expect(notify.success).toHaveBeenCalledWith('accessManagement.organizations.members.messages.updated');
    expect(uam.list).toHaveBeenCalledTimes(1);
  });

  it('reports a failed status change', () => {
    uam.update.mockReturnValue(throwError(() => new Error('x')));
    const component = create().componentInstance;

    component.setStatus(members[0] as never, 'REMOVED');

    expect(notify.error).toHaveBeenCalledWith('accessManagement.organizations.members.messages.actionFailed');
    expect(component.busy).toBe(false);
  });

  describe('adding people', () => {
    it('adds everyone picked who is not already an active member', () => {
      lookup.open.mockReturnValue(of([{ id: 'u1' }, { id: 'u2' }, { id: 'u9' }]));
      const component = create().componentInstance;

      component.addPeople();

      expect(uam.create).toHaveBeenCalledTimes(2);
      expect(uam.create).toHaveBeenCalledWith('/organizations/org1/members', { userId: 'u2' });
      expect(uam.create).toHaveBeenCalledWith('/organizations/org1/members', { userId: 'u9' });
      expect(notify.success).toHaveBeenCalledWith('accessManagement.organizations.members.messages.added');
    });

    it('does nothing when the dialog is closed with nobody new', () => {
      lookup.open.mockReturnValue(of([{ id: 'u1' }]));
      create().componentInstance.addPeople();
      lookup.open.mockReturnValue(of(null));
      create().componentInstance.addPeople();

      expect(uam.create).not.toHaveBeenCalled();
    });

    it('describes the picker and searches users by name through the search index', () => {
      search.searchRows.mockReturnValue(of({ content: [{ id: 'u5', userName: 'sam', displayName: 'Sam' }], totalElements: 1 }));
      create().componentInstance.addPeople();
      const config = lookup.open.mock.calls[0][0] as LookupConfig;

      expect(config.idOf({ id: 'u5' })).toBe('u5');
      expect(config.chipLabel({ userName: 'sam', displayName: 'Sam' })).toBe('Sam');
      expect(config.chipLabel({ userName: 'sam' })).toBe('sam');
      expect(config.columns[1].render?.({ userName: 'sam' })).toBe('sam');
      let page: unknown;
      config.fetch('sa', 2, 20).subscribe((p) => (page = p));
      expect(search.searchRows).toHaveBeenCalledWith(UAM_SEARCH_MODELS.users, { userName: 'sa' }, 2, 20);
      expect(page).toEqual({ items: [{ id: 'u5', userName: 'sam', displayName: 'Sam' }], total: 1 });

      search.searchRows.mockReturnValue(of({ totalElements: 0 }));
      config.fetch('', 0, 20).subscribe((p) => (page = p));
      expect(page).toEqual({ items: [], total: 0 });
    });
  });

  describe('privileges', () => {
    it('renders nothing without the list privilege', () => {
      granted = new Set();

      expect(create().nativeElement.querySelector('section')).toBeNull();
    });

    it('a viewer sees members but neither Add people nor row actions', () => {
      granted = new Set(['ORGANIZATION_MEMBER_LIST']);
      const el: HTMLElement = create().nativeElement;

      expect(el.querySelector('tbody tr')).not.toBeNull();
      expect(el.querySelector('.om__add button')).toBeNull();
      expect(el.querySelectorAll('tbody button').length).toBe(0);
    });

    it('shows pause / resume / restore / remove per status with the update privilege', () => {
      granted = new Set(['ORGANIZATION_MEMBER_LIST', 'ORGANIZATION_MEMBER_UPDATE']);
      const rows = Array.from((create().nativeElement as HTMLElement).querySelectorAll('tbody tr'));
      const labels = rows.map((r) => Array.from(r.querySelectorAll('button')).map((b) => b.textContent?.trim()));

      expect(labels).toEqual([
        ['accessManagement.organizations.members.pause', 'accessManagement.organizations.members.remove'],
        ['accessManagement.organizations.members.resume', 'accessManagement.organizations.members.remove'],
        ['accessManagement.organizations.members.restore'],
      ]);
    });

    it('shows Add people with the add privilege and opens the picker', () => {
      granted = new Set(['ORGANIZATION_MEMBER_LIST', 'ORGANIZATION_MEMBER_ADD']);
      const el: HTMLElement = create().nativeElement;

      (el.querySelector('.om__add button') as HTMLElement).click();

      expect(lookup.open).toHaveBeenCalled();
    });
  });
});
