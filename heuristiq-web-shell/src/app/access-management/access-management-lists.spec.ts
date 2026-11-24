import { Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AppDialogService } from '../shared/services/app-dialog-service';
import { NotificationService } from '../shared/services/notification-service';
import { OrganizationContextService } from '../shared/services/organization-context-service';
import { SearchApiService, UAM_SEARCH_MODELS } from '../shared/services/search-api-service';
import { UamService } from '../shared/services/uam-service';
import { AdminFormService } from './admin-form.service';
import { ClientsList } from './clients-list/clients-list';
import { NotificationTemplatesList } from './notification-templates-list/notification-templates-list';
import { OrganizationsList } from './organizations-list/organizations-list';
import { RolesList } from './roles-list/roles-list';
import { SecurityPoliciesList } from './security-policies-list/security-policies-list';
import { UsersList } from './users-list/users-list';

interface ListUnderTest {
  rows: unknown[];
  stats?: { value: number }[];
  hasError: boolean;
  isLoading: boolean;
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  load(): void;
  applyFilters(): void;
  clearFilters(): void;
  goToPage(page: number): void;
  onPageSizeChange(size: number): void;
  openEdit(row: unknown): void;
  confirmDelete(row: unknown): void;
  filters: Record<string, string>;
}

const stubs = {
  search: { searchRows: vi.fn() },
  uam: { remove: vi.fn(), list: vi.fn(), get: vi.fn(), rotateClientSecret: vi.fn(), setUserPassword: vi.fn(), setTotpRequired: vi.fn(), deleteUser: vi.fn() },
  adminForm: { create: vi.fn(), edit: vi.fn() },
  dialogs: { confirm: vi.fn(), form: vi.fn() },
  notify: { success: vi.fn(), error: vi.fn() },
  router: { navigate: vi.fn() },
  orgs: { currentTenantId: vi.fn((): string | null => 't1') },
};
let granted: Set<string> | null = null;
const access = {
  changes$: EMPTY,
  can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
};

function configure(component: Type<unknown>): void {
  TestBed.configureTestingModule({
    declarations: [component],
    imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
    providers: [
      { provide: SearchApiService, useValue: stubs.search },
      { provide: UamService, useValue: stubs.uam },
      { provide: AdminFormService, useValue: stubs.adminForm },
      { provide: AppDialogService, useValue: stubs.dialogs },
      { provide: NotificationService, useValue: stubs.notify },
      { provide: Router, useValue: stubs.router },
      { provide: ActivatedRoute, useValue: {} },
      { provide: OrganizationContextService, useValue: stubs.orgs },
      { provide: HqPrivilegeAccessService, useValue: access },
    ],
  });
}

function create<T>(component: Type<T>): ComponentFixture<T> {
  const fixture = TestBed.createComponent(component);
  fixture.detectChanges();
  return fixture;
}

const page = (content: unknown[], extra: object = {}) => ({ content, totalElements: content.length, totalPages: 1, ...extra });

beforeEach(() => {
  TestBed.resetTestingModule();
  vi.clearAllMocks();
  granted = null;
  stubs.search.searchRows.mockReturnValue(of(page([])));
  stubs.uam.get.mockImplementation((path: string) => of(path === '/identity/data-scope' ? { restricted: false, unitIds: [] } : []));
  stubs.dialogs.confirm.mockReturnValue(of(true));
  stubs.uam.remove.mockReturnValue(of({}));
});

interface Case {
  name: string;
  component: Type<unknown>;
  model: keyof typeof UAM_SEARCH_MODELS;
  prefix: string;
  resourcePath: string;
  createCode: string;
  updateCode: string;
  deleteCode: string;
  rows: Record<string, unknown>[];
  firstCell: string;
  inactiveRow: Record<string, unknown>;
}

// ApplicationsList reads UAM's organization-scoped list, not the search index: see applications-list/applications-list.spec.ts.
const CASES: Case[] = [
  { name: 'OrganizationsList', component: OrganizationsList, model: 'organizations', prefix: 'accessManagement.organizations', resourcePath: '/organizations',
    createCode: 'ORGANIZATION_CREATE', updateCode: 'ORGANIZATION_UPDATE', deleteCode: 'ORGANIZATION_DELETE',
    rows: [{ id: 'o1', code: 'ORG1', name: 'Org One', status: 'ACTIVE' }], firstCell: 'ORG1', inactiveRow: { id: 'o2', code: 'ORG2', status: 'SUSPENDED' } },
  { name: 'ClientsList', component: ClientsList, model: 'clients', prefix: 'accessManagement.clients', resourcePath: '/clients',
    createCode: 'CLIENT_CREATE', updateCode: 'CLIENT_UPDATE', deleteCode: 'CLIENT_DELETE',
    rows: [{ id: 'c1', clientId: 'CLI1', name: 'Client One' }], firstCell: 'CLI1', inactiveRow: { id: 'c2', clientId: 'CLI2', active: false } },
  { name: 'SecurityPoliciesList', component: SecurityPoliciesList, model: 'securityPolicies', prefix: 'accessManagement.securityPolicies', resourcePath: '/security-policies',
    createCode: 'SECURITY_POLICY_CREATE', updateCode: 'SECURITY_POLICY_UPDATE', deleteCode: 'SECURITY_POLICY_DELETE',
    rows: [{ id: 'p1', name: 'Policy One' }], firstCell: 'Policy One', inactiveRow: { id: 'p2', name: 'Off', active: false } },
];

describe.each(CASES)('$name', (c) => {
  beforeEach(() => configure(c.component));

  it('loads the first page through the search index and renders the rows', () => {
    stubs.search.searchRows.mockReturnValue(of(page(c.rows, { totalElements: 25, totalPages: 3 })));
    const fixture = create(c.component);
    const list = fixture.componentInstance as unknown as ListUnderTest;

    expect(stubs.search.searchRows).toHaveBeenCalledWith(UAM_SEARCH_MODELS[c.model], expect.any(Object), 0, 10);
    expect(list.rows).toEqual(c.rows);
    expect(list.totalElements).toBe(25);
    expect(list.totalPages).toBe(3);
    expect(list.isLoading).toBe(false);
    expect(list.stats?.map((s) => s.value)).toEqual([25, 1, 0]);
    expect(fixture.nativeElement.textContent).toContain(c.firstCell);
  });

  it('counts inactive rows in the stat cards', () => {
    stubs.search.searchRows.mockReturnValue(of(page([...c.rows, c.inactiveRow])));
    const list = create(c.component).componentInstance as unknown as ListUnderTest;

    expect(list.stats?.map((s) => s.value)).toEqual([2, 1, 1]);
  });

  it('falls back to the row count and one page when the response has no totals', () => {
    stubs.search.searchRows.mockReturnValue(of({ content: c.rows }));
    const list = create(c.component).componentInstance as unknown as ListUnderTest;

    expect(list.totalElements).toBe(1);
    expect(list.totalPages).toBe(1);
  });

  it('shows the error state and a toast when the search fails', () => {
    stubs.search.searchRows.mockReturnValue(throwError(() => new Error('down')));
    const fixture = create(c.component);
    const list = fixture.componentInstance as unknown as ListUnderTest;

    expect(list.hasError).toBe(true);
    expect(list.isLoading).toBe(false);
    expect(stubs.notify.error).toHaveBeenCalledWith(`${c.prefix}.errorMessage`);
    expect(fixture.nativeElement.querySelector('.app-state--error')).not.toBeNull();
  });

  it('applyFilters goes back to page 0 and clearFilters empties every filter', () => {
    const list = create(c.component).componentInstance as unknown as ListUnderTest;
    list.page = 4;
    const firstKey = Object.keys(list.filters)[0];
    list.filters[firstKey] = 'abc';
    stubs.search.searchRows.mockClear();

    list.applyFilters();
    expect(stubs.search.searchRows).toHaveBeenLastCalledWith(UAM_SEARCH_MODELS[c.model], expect.objectContaining({ [firstKey]: 'abc' }), 0, 10);

    list.clearFilters();
    expect(Object.values(list.filters).every((v) => v === '')).toBe(true);
    expect(list.page).toBe(0);
  });

  it('pages and resizes through the search index', () => {
    const list = create(c.component).componentInstance as unknown as ListUnderTest;

    list.goToPage(2);
    expect(stubs.search.searchRows).toHaveBeenLastCalledWith(UAM_SEARCH_MODELS[c.model], expect.any(Object), 2, 10);
    list.onPageSizeChange(50);
    expect(list.page).toBe(0);
    expect(stubs.search.searchRows).toHaveBeenLastCalledWith(UAM_SEARCH_MODELS[c.model], expect.any(Object), 0, 50);
  });

  it('hands New and Edit to the form service with the entity config', () => {
    const list = create(c.component).componentInstance as unknown as ListUnderTest & { openCreate(): void; config: unknown };

    list.openCreate();
    expect(stubs.adminForm.create).toHaveBeenCalledWith(list.config);
    list.openEdit({ id: 'x9' });
    expect(stubs.adminForm.edit).toHaveBeenCalledWith(list.config, 'x9');
  });

  it('deletes after confirmation, toasts and reloads', () => {
    const list = create(c.component).componentInstance as unknown as ListUnderTest;
    stubs.search.searchRows.mockClear();

    list.confirmDelete({ id: 'd1', code: 'CODE', name: 'Name' });

    expect(stubs.dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ variant: 'danger', message: `${c.prefix}.deleteDialog.message` }));
    expect(stubs.uam.remove).toHaveBeenCalledWith(c.resourcePath, 'd1');
    expect(stubs.notify.success).toHaveBeenCalledWith(`${c.prefix}.messages.deleted`);
    expect(stubs.search.searchRows).toHaveBeenCalledTimes(1);
  });

  it('does nothing when the delete is declined', () => {
    stubs.dialogs.confirm.mockReturnValue(of(false));
    const list = create(c.component).componentInstance as unknown as ListUnderTest;

    list.confirmDelete({ id: 'd1', code: 'CODE' });

    expect(stubs.uam.remove).not.toHaveBeenCalled();
    expect(stubs.notify.success).not.toHaveBeenCalled();
  });

  it('shows the server detail, or a default message, when the delete fails', () => {
    const list = create(c.component).componentInstance as unknown as ListUnderTest;

    stubs.uam.remove.mockReturnValueOnce(throwError(() => ({ error: { detail: 'In use' } })));
    list.confirmDelete({ id: 'd1', code: 'CODE' });
    expect(stubs.notify.error).toHaveBeenLastCalledWith('In use');

    stubs.uam.remove.mockReturnValueOnce(throwError(() => new Error('x')));
    list.confirmDelete({ id: 'd1', code: 'CODE' });
    expect(stubs.notify.error).toHaveBeenLastCalledWith(`${c.prefix}.messages.deleteFailed`);
  });

  it('shows New, Edit and Delete only to a user who holds those privileges', () => {
    stubs.search.searchRows.mockReturnValue(of(page(c.rows)));
    granted = new Set([c.createCode, c.updateCode, c.deleteCode]);
    let el: HTMLElement = create(c.component).nativeElement;
    expect(el.querySelector('.table-create-btn')).not.toBeNull();
    expect(el.querySelector('button[title="shared.listPage.rowActions.edit"]')).not.toBeNull();
    expect(el.querySelector('button.danger')).not.toBeNull();

    TestBed.resetTestingModule();
    configure(c.component);
    granted = new Set([c.updateCode]);
    el = create(c.component).nativeElement;
    expect(el.querySelector('.table-create-btn')).toBeNull();
    expect(el.querySelector('button[title="shared.listPage.rowActions.edit"]')).not.toBeNull();
    expect(el.querySelector('button.danger')).toBeNull();

    TestBed.resetTestingModule();
    configure(c.component);
    granted = new Set();
    el = create(c.component).nativeElement;
    expect(el.querySelector('.table-create-btn')).toBeNull();
    expect(el.querySelector('button[title="shared.listPage.rowActions.edit"]')).toBeNull();
    expect(el.querySelector('button.danger')).toBeNull();
  });

  it('clicking the visible row actions calls the handlers', () => {
    stubs.search.searchRows.mockReturnValue(of(page(c.rows)));
    const fixture = create(c.component);
    const el: HTMLElement = fixture.nativeElement;

    (el.querySelector('.table-create-btn') as HTMLElement).click();
    expect(stubs.adminForm.create).toHaveBeenCalled();
    (el.querySelector('button[title="shared.listPage.rowActions.edit"]') as HTMLElement).click();
    expect(stubs.adminForm.edit).toHaveBeenCalled();
    (el.querySelector('button.danger') as HTMLElement).click();
    expect(stubs.dialogs.confirm).toHaveBeenCalled();
  });
});

describe('ClientsList rotate secret', () => {
  beforeEach(() => configure(ClientsList));

  it('reveals the new secret once and offers it only with the rotate privilege', () => {
    stubs.search.searchRows.mockReturnValue(of(page([{ id: 'c1', clientId: 'CLI1' }])));
    stubs.uam.rotateClientSecret.mockReturnValue(of('s3cret'));
    granted = new Set(['CLIENT_ROTATE_SECRET']);
    const fixture = create(ClientsList);

    fixture.componentInstance.rotateSecret({ id: 'c1' } as never);

    expect(stubs.uam.rotateClientSecret).toHaveBeenCalledWith('c1');
    expect(stubs.dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ title: 'shared.adminCrud.clientSecret.title' }));

    TestBed.resetTestingModule();
    configure(ClientsList);
    granted = new Set(['CLIENT_UPDATE']);
    const plain: HTMLElement = create(ClientsList).nativeElement;
    expect(plain.querySelectorAll('.row-action-btn').length).toBe(1);
  });
});

describe('RolesList', () => {
  beforeEach(() => configure(RolesList));

  it('counts system and custom roles and renders the Yes / No column', () => {
    stubs.search.searchRows.mockReturnValue(of(page([{ id: 'r1', code: 'ADMIN', systemRole: true }, { id: 'r2', code: 'CUSTOM', systemRole: false }], { totalElements: 9 })));
    const fixture = create(RolesList);
    const list = fixture.componentInstance;

    expect(stubs.search.searchRows).toHaveBeenCalledWith(UAM_SEARCH_MODELS.roles, { code: '', name: '' }, 0, 10);
    expect([list.totalRoles, list.systemRoles, list.customRoles]).toEqual([9, 1, 1]);
    expect(fixture.nativeElement.textContent).toContain('shared.commonTable.yes');
    expect(fixture.nativeElement.textContent).toContain('shared.commonTable.no');
  });

  it('offers no Edit on the platform administrator role', () => {
    granted = new Set(['ROLE_UPDATE']);
    stubs.search.searchRows.mockReturnValue(of(page([{ id: 'r1', code: 'SUPER_ADMIN', systemRole: true }, { id: 'r2', code: 'CUSTOM', systemRole: false }])));
    const fixture = create(RolesList);

    expect(fixture.componentInstance.isPlatformRole({ id: 'r1', code: 'super_admin' } as never)).toBe(true);
    // the Edit button is the row action that is not the danger (delete) one
    const edits = fixture.nativeElement.querySelectorAll('.row-action-btn:not(.danger)');
    expect(edits.length).toBe(1);
  });

  it('offers Delete on a custom role only, never on a built-in (system) role', () => {
    granted = new Set(['ROLE_DELETE']);
    stubs.search.searchRows.mockReturnValue(of(page([{ id: 'r1', code: 'ADMIN', systemRole: true }, { id: 'r2', code: 'CUSTOM', systemRole: false }])));
    const fixture = create(RolesList);

    const deletes = fixture.nativeElement.querySelectorAll('.row-action-btn.danger');
    expect(deletes.length).toBe(1);
  });

  it('shows the error state when loading fails', () => {
    stubs.search.searchRows.mockReturnValue(throwError(() => new Error('x')));
    const list = create(RolesList).componentInstance;

    expect(list.hasError).toBe(true);
    expect(stubs.notify.error).toHaveBeenCalledWith('accessManagement.roles.errorMessage');
  });

  it('filters, pages and navigates to the role form', () => {
    const list = create(RolesList).componentInstance;
    list.filters.code = 'X';
    list.applyFilters();
    expect(stubs.search.searchRows).toHaveBeenLastCalledWith(UAM_SEARCH_MODELS.roles, { code: 'X', name: '' }, 0, 10);
    list.clearFilters();
    expect(list.filters).toEqual({ code: '', name: '' });
    list.goToPage(3);
    expect(list.page).toBe(3);
    list.onPageSizeChange(25);
    expect([list.page, list.pageSize]).toEqual([0, 25]);

    list.openCreate();
    expect(stubs.router.navigate).toHaveBeenCalledWith(['new'], { relativeTo: expect.anything() });
    list.openEdit({ id: 'r7' } as never);
    expect(stubs.router.navigate).toHaveBeenCalledWith(['r7', 'edit'], { relativeTo: expect.anything() });
  });

  it('deletes a role after confirmation and reports failures', () => {
    const list = create(RolesList).componentInstance;

    list.confirmDelete({ id: 'r1', code: 'C' } as never);
    expect(stubs.uam.remove).toHaveBeenCalledWith('/roles', 'r1');
    expect(stubs.notify.success).toHaveBeenCalledWith('accessManagement.roles.messages.deleted');

    stubs.uam.remove.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Assigned' } })));
    list.confirmDelete({ id: 'r1', code: 'C' } as never);
    expect(stubs.notify.error).toHaveBeenLastCalledWith('Assigned');

    stubs.uam.remove.mockClear();
    stubs.dialogs.confirm.mockReturnValue(of(false));
    list.confirmDelete({ id: 'r1', code: 'C' } as never);
    expect(stubs.uam.remove).not.toHaveBeenCalled();
  });

  it('hides Create / Edit / Delete without the matching privileges', () => {
    stubs.search.searchRows.mockReturnValue(of(page([{ id: 'r1', code: 'ADMIN' }])));
    granted = new Set(['ROLE_CREATE', 'ROLE_DELETE']);
    let el: HTMLElement = create(RolesList).nativeElement;
    expect(el.querySelector('.table-create-btn')).not.toBeNull();
    expect(el.querySelector('button.danger')).not.toBeNull();
    expect(el.querySelector('button[title="shared.listPage.rowActions.edit"]')).toBeNull();

    TestBed.resetTestingModule();
    configure(RolesList);
    granted = new Set();
    el = create(RolesList).nativeElement;
    expect(el.querySelector('.table-create-btn')).toBeNull();
    expect(el.querySelectorAll('.row-action-btn').length).toBe(0);

    TestBed.resetTestingModule();
    configure(RolesList);
    granted = new Set(['UAM_ROLES_MANAGE']);
    el = create(RolesList).nativeElement;
    expect(el.querySelector('.table-create-btn')).not.toBeNull();
    expect(el.querySelectorAll('.row-action-btn').length).toBe(2);
  });
});

describe('NotificationTemplatesList', () => {
  beforeEach(() => configure(NotificationTemplatesList));
  const templates = [{ id: 't1', code: 'WELCOME', name: 'Welcome mail' }, { id: 't2', code: 'OTP', name: 'One time code' }];

  it('loads a page from UAM and renders it', () => {
    stubs.uam.list.mockReturnValue(of(page(templates, { totalElements: 12, totalPages: 2 })));
    const fixture = create(NotificationTemplatesList);

    expect(stubs.uam.list).toHaveBeenCalledWith('/notification-templates', 0, 10);
    expect(fixture.componentInstance.totalElements).toBe(12);
    expect(fixture.nativeElement.textContent).toContain('WELCOME');
  });

  it('filters the loaded page by code or name and can clear the search', () => {
    stubs.uam.list.mockReturnValue(of(page(templates)));
    const list = create(NotificationTemplatesList).componentInstance;

    list.searchText = '  otp ';
    list.applySearch();
    expect(list.rows.map((r) => r.code)).toEqual(['OTP']);
    list.searchText = 'welcome';
    list.applySearch();
    expect(list.rows.map((r) => r.code)).toEqual(['WELCOME']);
    list.clearSearch();
    expect(list.searchText).toBe('');
    expect(list.rows.length).toBe(2);
  });

  it('debounces typing in the search box', () => {
    vi.useFakeTimers();
    try {
      stubs.uam.list.mockReturnValue(of(page(templates)));
      const list = create(NotificationTemplatesList).componentInstance;
      list.searchText = 'otp';
      list.onSearchInput();
      expect(list.rows.length).toBe(2);
      vi.advanceTimersByTime(300);
      expect(list.rows.map((r) => r.code)).toEqual(['OTP']);
    } finally {
      vi.useRealTimers();
    }
  });

  it('reports a load failure', () => {
    stubs.uam.list.mockReturnValue(throwError(() => new Error('x')));
    const list = create(NotificationTemplatesList).componentInstance;

    expect(list.hasError).toBe(true);
    expect(stubs.notify.error).toHaveBeenCalledWith('accessManagement.notificationTemplates.errorMessage');
  });

  it('pages, edits, creates and deletes', () => {
    stubs.uam.list.mockReturnValue(of(page(templates)));
    const list = create(NotificationTemplatesList).componentInstance;

    list.goToPage(1);
    expect(stubs.uam.list).toHaveBeenLastCalledWith('/notification-templates', 1, 10);
    list.onPageSizeChange(20);
    expect(stubs.uam.list).toHaveBeenLastCalledWith('/notification-templates', 0, 20);
    list.openCreate();
    expect(stubs.adminForm.create).toHaveBeenCalledWith(list.config);
    list.openEdit(templates[0] as never);
    expect(stubs.adminForm.edit).toHaveBeenCalledWith(list.config, 't1');

    list.confirmDelete(templates[0] as never);
    expect(stubs.uam.remove).toHaveBeenCalledWith('/notification-templates', 't1');
    expect(stubs.notify.success).toHaveBeenCalledWith('accessManagement.notificationTemplates.messages.deleted');

    stubs.uam.remove.mockReturnValueOnce(throwError(() => new Error('x')));
    list.confirmDelete(templates[0] as never);
    expect(stubs.notify.error).toHaveBeenLastCalledWith('accessManagement.notificationTemplates.messages.deleteFailed');

    stubs.dialogs.confirm.mockReturnValue(of(false));
    stubs.uam.remove.mockClear();
    list.confirmDelete(templates[0] as never);
    expect(stubs.uam.remove).not.toHaveBeenCalled();
  });

  it('hides New / Edit / Delete without privileges and shows them with', () => {
    stubs.uam.list.mockReturnValue(of(page(templates)));
    granted = new Set();
    let el: HTMLElement = create(NotificationTemplatesList).nativeElement;
    expect(el.querySelector('.table-create-btn')).toBeNull();
    expect(el.querySelectorAll('.row-action-btn').length).toBe(0);

    TestBed.resetTestingModule();
    configure(NotificationTemplatesList);
    granted = new Set(['NOTIFICATION_TEMPLATE_CREATE', 'NOTIFICATION_TEMPLATE_UPDATE', 'NOTIFICATION_TEMPLATE_DELETE']);
    el = create(NotificationTemplatesList).nativeElement;
    expect(el.querySelector('.table-create-btn')).not.toBeNull();
    expect(el.querySelectorAll('.row-action-btn').length).toBe(templates.length * 2);
  });
});

describe('UsersList', () => {
  beforeEach(() => configure(UsersList));
  /** The searches that load a page of users; the one-row query that counts the active users is left out. */
  const userPageQueries = () => stubs.search.searchRows.mock.calls.filter((call: unknown[]) => call[3] !== 1);
  const user = { id: 'u1', userName: 'sita', email: 's@x.io', status: 'ACTIVE', roles: ['A'], applications: ['APP'], phoneNumber: { number: '99' }, fullName: { firstName: 'Sita', lastName: 'R' } };

  it('reads the data scope, then loads users and renders the formatted columns', () => {
    // The page holds one active user, but the search says 3 of the 4 matches are active: the card must show 3, not the page's 1.
    stubs.search.searchRows.mockImplementation((_model: unknown, filters: Record<string, string>) =>
      of(filters['status'] === 'ACTIVE' ? page([], { totalElements: 3 }) : page([user], { totalElements: 4 })));
    const fixture = create(UsersList);
    const list = fixture.componentInstance;

    expect(stubs.uam.get).toHaveBeenCalledWith('/identity/data-scope');
    expect(stubs.search.searchRows).toHaveBeenCalledWith(UAM_SEARCH_MODELS.users, expect.not.objectContaining({ department: expect.anything() }), 0, 10);
    expect(stubs.search.searchRows).toHaveBeenCalledWith(UAM_SEARCH_MODELS.users, expect.objectContaining({ status: 'ACTIVE' }), 0, 1);
    expect(list.stats.map((s) => s.value)).toEqual([4, 3, 1]);
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('Sita R');
    expect(text).toContain('99');
    expect(text).toContain('APP');
  });

  it('shows a dash for missing phone, name and applications', () => {
    stubs.search.searchRows.mockImplementation((_model: unknown, filters: Record<string, string>) =>
      of(filters['status'] === 'ACTIVE' ? page([], { totalElements: 0 }) : page([{ id: 'u2', userName: 'bare', status: 'INACTIVE' }])));
    const fixture = create(UsersList);

    expect(fixture.nativeElement.querySelectorAll('td')[2].textContent).toContain('—');
    expect(fixture.componentInstance.stats.map((s) => s.value)).toEqual([1, 0, 1]);
  });

  it('limits the list to the departments the person is scoped to', () => {
    stubs.uam.get.mockImplementation((path: string) => of(path === '/identity/data-scope' ? { restricted: true, unitIds: ['d1', 'd2'] } : []));
    create(UsersList);

    expect(stubs.search.searchRows).toHaveBeenCalledWith(UAM_SEARCH_MODELS.users, expect.objectContaining({ department: 'd1,d2' }), 0, 10);
  });

  it('shows nobody, without searching, when scoped to no department', () => {
    stubs.uam.get.mockImplementation((path: string) => of(path === '/identity/data-scope' ? { restricted: true, unitIds: [] } : []));
    const list = create(UsersList).componentInstance;

    expect(stubs.search.searchRows).not.toHaveBeenCalled();
    expect(list.rows).toEqual([]);
    expect(list.totalPages).toBe(1);
    expect(list.stats.map((s) => s.value)).toEqual([0, 0, 0]);
  });

  it('keeps the whole organization when the data scope cannot be read', () => {
    stubs.uam.get.mockImplementation((path: string) => (path === '/identity/data-scope' ? throwError(() => new Error('x')) : of([])));
    create(UsersList);

    expect(userPageQueries()).toHaveLength(1);
  });

  it('offers only the active applications of the current organization as a filter', () => {
    stubs.uam.get.mockImplementation((path: string) => (path === '/identity/data-scope'
      ? of({ restricted: false, unitIds: [] })
      : of([
        { applicationName: 'Rides', subscription: { applicationCode: 'RIDES', status: 'ACTIVE' } },
        { subscription: { applicationCode: 'OLD', status: 'SUSPENDED' } },
        { subscription: { applicationCode: 'NONAME', status: 'ACTIVE' } },
      ])));
    const list = create(UsersList).componentInstance;

    expect(list.applicationChoices).toEqual([{ code: 'RIDES', name: 'Rides' }, { code: 'NONAME', name: 'NONAME' }]);
  });

  it('tolerates a failing application lookup and a missing organization', () => {
    stubs.uam.get.mockImplementation((path: string) => (path === '/identity/data-scope' ? of({ restricted: false, unitIds: [] }) : throwError(() => new Error('x'))));
    expect(create(UsersList).componentInstance.applicationChoices).toEqual([]);

    TestBed.resetTestingModule();
    stubs.orgs.currentTenantId.mockReturnValue(null);
    configure(UsersList);
    expect(create(UsersList).componentInstance.applicationChoices).toEqual([]);
    stubs.orgs.currentTenantId.mockReturnValue('t1');
  });

  it('reports a failed load', () => {
    stubs.search.searchRows.mockReturnValue(throwError(() => new Error('x')));
    const list = create(UsersList).componentInstance;

    expect(list.hasError).toBe(true);
    expect(stubs.notify.error).toHaveBeenCalledWith('accessManagement.users.errorMessage');
  });

  it('filters, pages, navigates and opens the forms', () => {
    const list = create(UsersList).componentInstance;
    list.filters.userName = 'sita';
    list.applyFilters();
    expect(stubs.search.searchRows).toHaveBeenCalledWith(UAM_SEARCH_MODELS.users, expect.objectContaining({ userName: 'sita' }), 0, 10);
    list.clearFilters();
    expect(list.filters.userName).toBe('');
    list.goToPage(2);
    expect(list.page).toBe(2);
    list.onPageSizeChange(5);
    expect([list.page, list.pageSize]).toEqual([0, 5]);

    list.viewDetail(user as never);
    expect(stubs.router.navigate).toHaveBeenCalledWith(['/access-management/users', 'u1']);
    list.openCreate();
    expect(stubs.adminForm.create).toHaveBeenCalled();
    list.openEdit(user as never);
    expect(stubs.adminForm.edit).toHaveBeenCalledWith(expect.anything(), 'u1');
  });

  it('sets a password from the form dialog, and ignores a cancelled dialog', () => {
    const list = create(UsersList).componentInstance;
    stubs.dialogs.form.mockReturnValue(of({ password: 'Pw1!' }));
    stubs.uam.setUserPassword.mockReturnValue(of({}));
    list.setPassword(user as never);
    expect(stubs.uam.setUserPassword).toHaveBeenCalledWith('u1', { password: 'Pw1!', temporary: false });
    expect(stubs.notify.success).toHaveBeenCalledWith('accessManagement.users.messages.passwordUpdated');

    stubs.uam.setUserPassword.mockReturnValue(throwError(() => new Error('x')));
    list.setPassword(user as never);
    expect(stubs.notify.error).toHaveBeenCalledWith('accessManagement.users.messages.passwordSetFailed');

    stubs.uam.setUserPassword.mockClear();
    stubs.dialogs.form.mockReturnValue(of(null));
    list.setPassword(user as never);
    expect(stubs.uam.setUserPassword).not.toHaveBeenCalled();
  });

  it('requires and disables two-factor sign-in, reporting failures', () => {
    const list = create(UsersList).componentInstance;
    stubs.uam.setTotpRequired.mockReturnValue(of({}));
    list.requireTotp(user as never);
    expect(stubs.uam.setTotpRequired).toHaveBeenLastCalledWith('u1', true);
    expect(stubs.notify.success).toHaveBeenLastCalledWith('accessManagement.users.messages.totpRequired');
    list.disableTotp(user as never);
    expect(stubs.uam.setTotpRequired).toHaveBeenLastCalledWith('u1', false);
    expect(stubs.notify.success).toHaveBeenLastCalledWith('accessManagement.users.messages.totpDisabled');

    stubs.uam.setTotpRequired.mockReturnValue(throwError(() => new Error('x')));
    list.requireTotp(user as never);
    list.disableTotp(user as never);
    expect(stubs.notify.error).toHaveBeenCalledTimes(2);
    expect(stubs.notify.error).toHaveBeenLastCalledWith('accessManagement.users.messages.totpUpdateFailed');
  });

  it('deletes a user after confirmation and reloads, or reports why it failed', () => {
    const list = create(UsersList).componentInstance;
    stubs.uam.deleteUser.mockReturnValue(of({}));
    stubs.search.searchRows.mockClear();
    list.confirmDelete(user as never);
    expect(stubs.uam.deleteUser).toHaveBeenCalledWith('u1');
    expect(userPageQueries()).toHaveLength(1);

    stubs.uam.deleteUser.mockReturnValue(throwError(() => ({ error: { detail: 'Owner' } })));
    list.confirmDelete(user as never);
    expect(stubs.notify.error).toHaveBeenLastCalledWith('Owner');
    stubs.uam.deleteUser.mockReturnValue(throwError(() => new Error('x')));
    list.confirmDelete(user as never);
    expect(stubs.notify.error).toHaveBeenLastCalledWith('accessManagement.users.messages.deleteFailed');

    stubs.dialogs.confirm.mockReturnValue(of(false));
    stubs.uam.deleteUser.mockClear();
    list.confirmDelete(user as never);
    expect(stubs.uam.deleteUser).not.toHaveBeenCalled();
  });

  it('shows row actions according to the privileges held', () => {
    stubs.search.searchRows.mockReturnValue(of(page([user])));
    granted = new Set();
    let el: HTMLElement = create(UsersList).nativeElement;
    expect(el.querySelector('.table-create-btn')).toBeNull();
    // Only "view detail" remains.
    expect(el.querySelectorAll('.row-action-btn').length).toBe(1);

    TestBed.resetTestingModule();
    configure(UsersList);
    granted = new Set(['USER_UPDATE', 'USER_DELETE']);
    el = create(UsersList).nativeElement;
    expect(el.querySelector('button[title="shared.listPage.rowActions.edit"]')).not.toBeNull();
    expect(el.querySelector('button.danger')).not.toBeNull();
    expect(el.querySelector('button[title="accessManagement.users.actions.setPassword"]')).toBeNull();

    TestBed.resetTestingModule();
    configure(UsersList);
    granted = new Set(['UAM_USERS_MANAGE']);
    el = create(UsersList).nativeElement;
    expect(el.querySelector('.table-create-btn')).not.toBeNull();
    expect(el.querySelectorAll('.row-action-btn').length).toBe(6);
    (el.querySelector('button[title="accessManagement.users.actions.requireTotp"]') as HTMLElement).click();
    expect(stubs.uam.setTotpRequired).toHaveBeenCalled();
  });
});

// A privilege change after the screen is open re-renders the actions (the directive listens to changes$).
describe('privilege changes while a list is open', () => {
  it('adds the New button once the privilege is granted', () => {
    const changes$ = new Subject<void>();
    TestBed.configureTestingModule({
      declarations: [RolesList],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        { provide: SearchApiService, useValue: stubs.search },
        { provide: UamService, useValue: stubs.uam },
        { provide: AppDialogService, useValue: stubs.dialogs },
        { provide: NotificationService, useValue: stubs.notify },
        { provide: Router, useValue: stubs.router },
        { provide: ActivatedRoute, useValue: {} },
        { provide: HqPrivilegeAccessService, useValue: { changes$, can: () => granted?.has('UAM_ROLES_MANAGE') ?? true } },
      ],
    });
    granted = new Set();
    const fixture = create(RolesList);
    expect(fixture.nativeElement.querySelector('.table-create-btn')).toBeNull();

    granted = new Set(['UAM_ROLES_MANAGE']);
    changes$.next();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.table-create-btn')).not.toBeNull();
  });
});
