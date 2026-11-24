import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AppDialogService } from '../shared/services/app-dialog-service';
import { NotificationService } from '../shared/services/notification-service';
import { GoLiveGovernanceService } from '../application-readiness/go-live-governance.service';
import { UamService } from '../shared/services/uam-service';
import { AdminFormService } from './admin-form.service';
import { APPLICATION_CRUD_CONFIG, ORGANIZATION_CRUD_CONFIG } from './access-management-configs';
import { APPLICATION_CODE_BOX, APPLICATION_CODE_PANEL } from './access-management.constants';
import { AdminFormConfig } from './admin-form.types';
import { activityStats } from './access-management-stats';
import { loadApplicationCatalogueOptions, loadDepartmentOptions, loadRoleOptions, loadUserOptions } from './uam-option-loaders';
import { TranslateService } from '@ngx-translate/core';

describe('AdminFormService', () => {
  const router = { navigate: vi.fn() };
  const uam = { create: vi.fn(), update: vi.fn() };
  const dialogs = { confirm: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  const goLive = { handleRefusal: vi.fn() };
  let service: AdminFormService;

  beforeEach(() => {
    TestBed.resetTestingModule();
    vi.clearAllMocks();
    dialogs.confirm.mockReturnValue(of(true));
    goLive.handleRefusal.mockReturnValue(of(null));
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [
        { provide: Router, useValue: router },
        { provide: UamService, useValue: uam },
        { provide: AppDialogService, useValue: dialogs },
        { provide: NotificationService, useValue: notify },
        { provide: GoLiveGovernanceService, useValue: goLive },
      ],
    });
    service = TestBed.inject(AdminFormService);
  });

  it('opens the new and edit pages under the entity route segment', () => {
    service.create(ORGANIZATION_CRUD_CONFIG);
    expect(router.navigate).toHaveBeenLastCalledWith(['/access-management', 'organizations', 'new']);

    service.edit(APPLICATION_CRUD_CONFIG, 'a7');
    expect(router.navigate).toHaveBeenLastCalledWith(['/access-management', 'applications', 'a7', 'edit']);
  });

  it('derives the route segment from the resource path for an unregistered config', () => {
    const unknown: AdminFormConfig = { resourcePath: '/widgets', entityName: 'x', columns: [], fields: [] };

    service.create(unknown);

    expect(router.navigate).toHaveBeenLastCalledWith(['/access-management', 'widgets', 'new']);
  });

  describe('loadOptions', () => {
    it('emits an empty map when no field loads its options', () => {
      let result: unknown;
      service.loadOptions([{ key: 'a', label: 'a', type: 'text' }]).subscribe((r) => (result = r));
      expect(result).toEqual({});
    });

    it('loads every loader, and a failing loader leaves just that field without choices', () => {
      const fields = [
        { key: 'good', label: 'g', type: 'select' as const, loadOptions: () => of([{ value: 'v', label: 'V' }]) },
        { key: 'bad', label: 'b', type: 'select' as const, loadOptions: () => throwError(() => new Error('down')) },
      ];
      let result: unknown;

      service.loadOptions(fields).subscribe((r) => (result = r));

      expect(result).toEqual({ good: [{ value: 'v', label: 'V' }], bad: [] });
    });
  });

  describe('save', () => {
    const payload = { code: 'X' };

    it('creates, toasts and emits true', () => {
      uam.create.mockReturnValue(of({ id: '1' }));
      let ok: boolean | undefined;

      service.save(ORGANIZATION_CRUD_CONFIG, null, payload).subscribe((r) => (ok = r));

      expect(uam.create).toHaveBeenCalledWith('/organizations', payload);
      expect(notify.success).toHaveBeenCalledWith('shared.adminCrud.messages.created');
      expect(ok).toBe(true);
    });

    it('updates an existing entity', () => {
      uam.update.mockReturnValue(of({}));
      let ok: boolean | undefined;

      service.save(ORGANIZATION_CRUD_CONFIG, '9', payload).subscribe((r) => (ok = r));

      expect(uam.update).toHaveBeenCalledWith('/organizations', '9', payload);
      expect(notify.success).toHaveBeenCalledWith('shared.adminCrud.messages.updated');
      expect(ok).toBe(true);
    });

    it('runs the follow-up after a create and still reports success when it fails', () => {
      uam.create.mockReturnValue(of({ id: 'org1' }));
      const followUp = vi.fn(() => throwError(() => new Error('admin failed')));
      let ok: boolean | undefined;

      service.save(ORGANIZATION_CRUD_CONFIG, null, payload, followUp).subscribe((r) => (ok = r));

      expect(followUp).toHaveBeenCalledWith({ id: 'org1' });
      expect(ok).toBe(true);
    });

    it('does not run the follow-up on an update', () => {
      uam.update.mockReturnValue(of({}));
      const followUp = vi.fn(() => of(null));

      service.save(ORGANIZATION_CRUD_CONFIG, '9', payload, followUp).subscribe();

      expect(followUp).not.toHaveBeenCalled();
    });

    it('shows the one-time client secret after a create, before it is lost', () => {
      uam.create.mockReturnValue(of({ clientSecret: 'shh' }));
      let ok: boolean | undefined;

      service.save(ORGANIZATION_CRUD_CONFIG, null, payload).subscribe((r) => (ok = r));

      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ title: 'shared.adminCrud.clientSecret.title' }));
      expect(ok).toBe(true);
    });

    it('does not show a secret dialog when none was returned or on update', () => {
      uam.create.mockReturnValue(of({ clientSecret: '' }));
      service.save(ORGANIZATION_CRUD_CONFIG, null, payload).subscribe();
      uam.update.mockReturnValue(of({ clientSecret: 'x' }));
      service.save(ORGANIZATION_CRUD_CONFIG, '1', payload).subscribe();
      uam.create.mockReturnValue(of(null));
      service.save(ORGANIZATION_CRUD_CONFIG, null, payload).subscribe();

      expect(dialogs.confirm).not.toHaveBeenCalled();
    });

    it('shows the server detail, or the default message, and emits false when saving fails', () => {
      const results: boolean[] = [];
      uam.create.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Code taken' } })));
      service.save(ORGANIZATION_CRUD_CONFIG, null, payload).subscribe((r) => results.push(r));
      expect(notify.error).toHaveBeenLastCalledWith('Code taken');

      uam.create.mockReturnValueOnce(throwError(() => new Error('x')));
      service.save(ORGANIZATION_CRUD_CONFIG, null, payload).subscribe((r) => results.push(r));
      expect(notify.error).toHaveBeenLastCalledWith('shared.adminCrud.messages.saveFailed');
      expect(results).toEqual([false, false]);
    });

    it('opens the go-live blocked dialog instead of a toast when the save is refused with GO_LIVE_BLOCKED', () => {
      const refusal = { status: 409, error: { errorCode: 'GO_LIVE_BLOCKED' } };
      goLive.handleRefusal.mockReturnValueOnce(of('closed'));
      uam.update.mockReturnValueOnce(throwError(() => refusal));
      let ok: boolean | undefined;

      service.save(APPLICATION_CRUD_CONFIG, '9', payload).subscribe((r) => (ok = r));

      expect(goLive.handleRefusal).toHaveBeenCalledWith(refusal);
      expect(notify.error).not.toHaveBeenCalled();
      expect(ok).toBe(false);
    });
  });
});

describe('option loaders and stats', () => {
  const translate = { instant: (key: string) => key } as unknown as TranslateService;

  it('loadRoleOptions labels by name and falls back to the code', () => {
    const uam = { list: vi.fn(() => of({ content: [{ code: 'A', name: 'Admin' }, { code: 'B', name: '' }] })) };
    let options: unknown;

    loadRoleOptions(uam as never).subscribe((o) => (options = o));

    expect(uam.list).toHaveBeenCalledWith('/roles', 0, 200);
    expect(options).toEqual([{ value: 'A', label: 'Admin' }, { value: 'B', label: 'B' }]);
  });

  it('loadDepartmentOptions uses the department name and tolerates an empty answer', () => {
    const uam = { get: vi.fn((_path: string): unknown => of([{ code: 'd1', name: 'Ops' }])) };
    let options: unknown;
    loadDepartmentOptions(uam as never).subscribe((o) => (options = o));
    expect(uam.get).toHaveBeenCalledWith('/org-structure/units/search?limit=200');
    expect(options).toEqual([{ value: 'Ops', label: 'Ops' }]);

    uam.get.mockReturnValue(of(null));
    loadDepartmentOptions(uam as never).subscribe((o) => (options = o));
    expect(options).toEqual([]);
  });

  it('loadApplicationCatalogueOptions labels each code with its name, disables created ones and carries the defaults', () => {
    const uam = { get: vi.fn((_path: string): unknown => of([
      { code: 'BEST_PURCHASE_APP', name: 'Best Purchase', description: 'Shop', type: 'WEB', displayOrder: 40, iconHint: 'shopping_cart', created: true },
      { code: 'FOOD_DELIVER_APP', name: 'Food Delivery', description: null, type: 'MOBILE', displayOrder: 30, iconHint: null, created: false },
    ])) };
    let options: unknown;

    loadApplicationCatalogueOptions(uam as never).subscribe((o) => (options = o));

    expect(uam.get).toHaveBeenCalledWith('/applications/catalogue');
    expect(options).toEqual([
      { value: 'BEST_PURCHASE_APP', label: 'Best Purchase (BEST_PURCHASE_APP)', disabled: true, fill: { name: 'Best Purchase', description: 'Shop', type: 'WEB' },
        detail: { name: 'Best Purchase', description: 'Shop', category: 'WEB', flagged: true } },
      { value: 'FOOD_DELIVER_APP', label: 'Food Delivery (FOOD_DELIVER_APP)', disabled: false, fill: { name: 'Food Delivery', description: '', type: 'MOBILE' },
        detail: { name: 'Food Delivery', description: undefined, category: 'MOBILE', flagged: false } },
    ]);

    uam.get.mockReturnValue(of(null));
    loadApplicationCatalogueOptions(uam as never).subscribe((o) => (options = o));
    expect(options).toEqual([]);
  });

  it('the application form takes its code from the catalogue: no free text, read-only on edit, type fixed', () => {
    const code = APPLICATION_CRUD_CONFIG.fields.find((f) => f.key === 'code');
    const type = APPLICATION_CRUD_CONFIG.fields.find((f) => f.key === 'type');

    expect(code).toEqual(expect.objectContaining({ type: 'select', required: true, readOnly: 'edit', loadOptions: loadApplicationCatalogueOptions }));
    expect(code?.maxLength).toBeUndefined();
    expect(code?.optionPanel).toBe(APPLICATION_CODE_PANEL);
    expect(code?.codeBox).toBe(APPLICATION_CODE_BOX);
    expect(code?.optionPanel).toEqual(expect.objectContaining({ empty: code?.emptyOptionsMessage }));
    expect(APPLICATION_CRUD_CONFIG.fields.filter((f) => f.optionPanel || f.codeBox).map((f) => f.key)).toEqual(['code']);
    expect(type?.readOnly).toBe('always');
  });

  it('loadUserOptions shows the email next to the user name when there is one', () => {
    const uam = { listUsers: vi.fn(() => of({ content: [{ userName: 'sita', email: 's@x.io' }, { userName: 'ram' }] })) };
    let options: unknown;

    loadUserOptions(uam as never).subscribe((o) => (options = o));

    expect(options).toEqual([{ value: 'sita', label: 'sita (s@x.io)' }, { value: 'ram', label: 'ram' }]);
  });

  it('activityStats never shows a total below the rows on screen', () => {
    const stats = activityStats(translate, 1, [{ on: true }, { on: false }, { on: true }], (r) => r.on, 'bi-x');

    expect(stats.map((s) => [s.label, s.value])).toEqual([
      ['accessManagement.stats.total', 3],
      ['accessManagement.stats.active', 2],
      ['accessManagement.stats.inactive', 1],
    ]);
    expect(stats[0].icon).toBe('bi-x');
  });
});
