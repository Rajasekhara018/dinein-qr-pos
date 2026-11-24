import { ChangeDetectorRef, NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { UamService } from '../../shared/services/uam-service';
import { AdminFormPage } from './admin-form-page';
import { ADMIN_FORM_PAGE_DATA_KEY } from './admin-form-page.constants';

describe('AdminFormPage', () => {
  const router = { navigate: vi.fn() };
  const uam = { list: vi.fn(), get: vi.fn(), getById: vi.fn(), create: vi.fn(), update: vi.fn() };
  const dialogs = { confirm: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };

  /** Re-renders the page after the test changed its state directly (no ExpressionChanged check, as the page does itself). */
  const refresh = (fixture: ComponentFixture<AdminFormPage>) => fixture.debugElement.injector.get(ChangeDetectorRef).detectChanges();

  function create(path: string, id: string | null = null): ComponentFixture<AdminFormPage> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [AdminFormPage],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: Router, useValue: router },
        { provide: UamService, useValue: uam },
        { provide: AppDialogService, useValue: dialogs },
        { provide: NotificationService, useValue: notify },
        { provide: HqPrivilegeAccessService, useValue: access },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { data: { [ADMIN_FORM_PAGE_DATA_KEY]: path }, paramMap: convertToParamMap(id ? { id } : {}) } },
        },
      ],
    });
    const fixture = TestBed.createComponent(AdminFormPage);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    dialogs.confirm.mockReturnValue(of(true));
    uam.list.mockReturnValue(of({ content: [{ code: 'ADMIN', name: 'Admin' }, { code: 'OPS', name: '' }] }));
    uam.get.mockReturnValue(of([]));
    uam.create.mockReturnValue(of({ id: 'new1' }));
    uam.update.mockReturnValue(of({}));
    uam.getById.mockReturnValue(of({}));
  });

  const fillOrganization = (page: AdminFormPage, extra: Record<string, unknown> = {}) => {
    page.values['code'] = 'ORG_1';
    page.values['name'] = 'Org One';
    Object.assign(page.values, extra);
  };

  describe('creating', () => {
    it('opens an empty form with the entity title and breadcrumb', () => {
      const fixture = create('organizations');
      const page = fixture.componentInstance;

      expect(page.isEdit).toBe(false);
      expect(page.isLoading).toBe(false);
      expect(page.title).toBe('shared.adminCrud.newTitle');
      expect(page.canSubmit).toBe(false);
      expect(fixture.nativeElement.querySelector('h1').textContent).toContain('shared.adminCrud.newTitle');
      expect(fixture.nativeElement.querySelector('#f-code')).not.toBeNull();
    });

    it('needs a phone and a password to create a user, and marks them required, but not when editing one', () => {
      const fixture = create('users');
      const page = fixture.componentInstance;
      const password = page.fields.find((f) => f.key === 'password')!;
      const phone = page.fields.find((f) => f.key === 'phoneNumber')!;

      expect(page.isRequired(password)).toBe(true);
      expect(page.isRequired(phone)).toBe(true);
      page.values['username'] = 'qa.user';
      expect(page.canSubmit).toBe(false);

      const edit = create('users', 'u1').componentInstance;
      expect(edit.isRequired(edit.fields.find((f) => f.key === 'password')!)).toBe(false);
      expect(edit.isRequired(edit.fields.find((f) => f.key === 'phoneNumber')!)).toBe(false);
    });

    it('falls back to the first page definition for an unknown route key', () => {
      const page = create('nope').componentInstance;

      expect(page.page.path).toBe('users');
    });

    it('enables saving once the required fields are valid and sends the payload, then returns to the list', () => {
      const fixture = create('organizations');
      const page = fixture.componentInstance;
      fillOrganization(page, { maxUsers: '5', status: 'ACTIVE' });

      expect(page.canSubmit).toBe(true);
      expect(page.hasPendingChanges()).toBe(true);
      page.submit();

      expect(uam.create).toHaveBeenCalledWith('/organizations', expect.objectContaining({ code: 'ORG_1', name: 'Org One', maxUsers: 5, status: 'ACTIVE' }));
      expect(notify.success).toHaveBeenCalledWith('shared.adminCrud.messages.created');
      expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'organizations']);
      expect(page.hasPendingChanges()).toBe(false);
      expect(page.isSaving).toBe(false);
    });

    it('keeps the hidden application settings off the page but still holds their values to save', () => {
      const fixture = create('applications');
      const page = fixture.componentInstance;
      refresh(fixture);
      const hidden = ['ssoEnabled', 'mfaRequired', 'active', 'displayOrder'];

      for (const key of hidden) {
        expect(page.visibleFields.some((f) => f.key === key)).toBe(false);
        expect(page.fields.some((f) => f.key === key)).toBe(true);
        expect((fixture.nativeElement as HTMLElement).querySelector('#f-' + key)).toBeNull();
      }
      expect((fixture.nativeElement as HTMLElement).querySelector('#f-name')).not.toBeNull();
      // a new application still starts active
      expect(page.values['active']).toBe(true);
    });

    it('does not submit while a field is invalid and explains why', () => {
      const page = create('organizations').componentInstance;
      fillOrganization(page, { code: 'a b' });

      expect(page.errorOf(page.fields[0])).toBe('accessManagement.organizations.errors.code');
      expect(page.canSubmit).toBe(false);
      page.submit();
      expect(uam.create).not.toHaveBeenCalled();
    });

    it('refuses a malformed phone number with a toast', () => {
      const page = create('organizations').componentInstance;
      fillOrganization(page, { supportPhone: '12' });

      page.submit();

      expect(notify.error).toHaveBeenCalledWith('shared.phoneNumberInput.invalid');
      expect(uam.create).not.toHaveBeenCalled();
    });

    it('stays on the page when saving fails', () => {
      uam.create.mockReturnValue(throwError(() => ({ error: { detail: 'Duplicate' } })));
      const page = create('organizations').componentInstance;
      fillOrganization(page);

      page.submit();

      expect(notify.error).toHaveBeenCalledWith('Duplicate');
      expect(router.navigate).not.toHaveBeenCalled();
      expect(page.isSaving).toBe(false);
    });

    it('cancel returns to the list', () => {
      create('applications').componentInstance.cancel();

      expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'applications']);
    });
  });

  describe('first administrator of a new organization', () => {
    it('is only offered when creating an organization', () => {
      expect(create('organizations').componentInstance.offersAdmin).toBe(true);
      expect(create('organizations', 'o1').componentInstance.offersAdmin).toBe(false);
      expect(create('applications').componentInstance.offersAdmin).toBe(false);
    });

    it('needs all three details or none', () => {
      const page = create('organizations').componentInstance;
      fillOrganization(page);

      page.admin.name = 'Asha';
      expect(page.canSubmit).toBe(false);
      page.admin.email = 'a@x.io';
      page.admin.mobile = '+919876543210';
      expect(page.canSubmit).toBe(true);
    });

    it('creates the administrator and shows the one-time password once', () => {
      uam.create.mockImplementation((path: string) => of(path === '/organizations'
        ? { id: 'org9' }
        : { userId: 'u', email: 'a@x.io', roleCode: 'ORG_ADMIN', accountCreated: true, temporaryPassword: 'Tmp#1' }));
      const page = create('organizations').componentInstance;
      fillOrganization(page);
      Object.assign(page.admin, { name: ' Asha ', email: ' a@x.io ', mobile: '+919876543210' });

      page.submit();

      expect(uam.create).toHaveBeenCalledWith('/organizations/org9/admin', { name: 'Asha', mobile: '+919876543210', email: 'a@x.io' });
      expect(notify.success).toHaveBeenCalledWith('shared.adminCrud.orgAdmin.created');
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ title: 'shared.adminCrud.orgAdmin.credentialsTitle' }));
      expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'organizations']);
    });

    it('shows no password dialog for an existing account', () => {
      uam.create.mockImplementation((path: string) => of(path === '/organizations'
        ? { id: 'org9' }
        : { userId: 'u', email: 'a@x.io', roleCode: 'ORG_ADMIN', accountCreated: false, temporaryPassword: null }));
      const page = create('organizations').componentInstance;
      fillOrganization(page);
      Object.assign(page.admin, { name: 'Asha', email: 'a@x.io', mobile: '+919876543210' });

      page.submit();

      expect(dialogs.confirm).not.toHaveBeenCalled();
      expect(router.navigate).toHaveBeenCalled();
    });

    it('reports an administrator failure but the organization is already saved', () => {
      uam.create.mockImplementation((path: string) => (path === '/organizations'
        ? of({ id: 'org9' })
        : throwError(() => ({ error: { detail: 'Email in use' } }))));
      const page = create('organizations').componentInstance;
      fillOrganization(page);
      Object.assign(page.admin, { name: 'Asha', email: 'a@x.io', mobile: '+919876543210' });

      page.submit();

      expect(notify.error).toHaveBeenCalledWith('Email in use');
      expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'organizations']);
    });

    it('skips the administrator when the new organization has no id', () => {
      uam.create.mockReturnValue(of({}));
      const page = create('organizations').componentInstance;
      fillOrganization(page);
      Object.assign(page.admin, { name: 'Asha', email: 'a@x.io', mobile: '+919876543210' });

      page.submit();

      expect(uam.create).toHaveBeenCalledTimes(1);
    });

    it('falls back to a default message when the administrator failure has no detail', () => {
      uam.create.mockImplementation((path: string) => (path === '/organizations' ? of({ id: 'o' }) : throwError(() => new Error('x'))));
      const page = create('organizations').componentInstance;
      fillOrganization(page);
      Object.assign(page.admin, { name: 'Asha', email: 'a@x.io', mobile: '+919876543210' });

      page.submit();

      expect(notify.error).toHaveBeenCalledWith('shared.adminCrud.orgAdmin.failed');
    });
  });

  describe('organization self-signup switch', () => {
    it('is off by default on a new organization and is sent as false', () => {
      const page = create('organizations').componentInstance;
      expect(page.values['openForSelfSignup']).toBe(false);
      fillOrganization(page);

      page.submit();

      expect(uam.create).toHaveBeenCalledWith('/organizations', expect.objectContaining({ openForSelfSignup: false }));
    });

    it('sends true once switched on', () => {
      const page = create('organizations').componentInstance;
      fillOrganization(page, { openForSelfSignup: true });

      page.submit();

      expect(uam.create).toHaveBeenCalledWith('/organizations', expect.objectContaining({ openForSelfSignup: true }));
    });

    it('reads the saved flag from the organization settings when editing', () => {
      uam.getById.mockReturnValue(of({ code: 'ORG_1', name: 'Org One', settings: { openForSelfSignup: true } }));
      const page = create('organizations', 'org1').componentInstance;

      expect(page.values['openForSelfSignup']).toBe(true);
    });

    it('is shown with its label and hint for a user who may update organizations', () => {
      granted = new Set(['ORGANIZATION_UPDATE']);
      const fixture = create('organizations');

      expect(fixture.nativeElement.querySelector('#f-openForSelfSignup')).not.toBeNull();
      expect(fixture.nativeElement.textContent).toContain('accessManagement.organizations.openForSelfSignupHint');
    });

    it('is hidden and not sent without the organization update privilege', () => {
      granted = new Set(['ORGANIZATION_CREATE']);
      const fixture = create('organizations');
      const page = fixture.componentInstance;
      fillOrganization(page);

      page.submit();

      expect(fixture.nativeElement.querySelector('#f-openForSelfSignup')).toBeNull();
      expect(page.fields.some((f) => f.key === 'openForSelfSignup')).toBe(false);
      expect(uam.create.mock.calls[0][1]).not.toHaveProperty('openForSelfSignup');
    });
  });

  describe('editing', () => {
    it('loads the entity and fills the form from it', () => {
      uam.getById.mockReturnValue(of({ code: 'ORG_1', name: 'Org One', status: 'ACTIVE', settings: { dataScope: 'DEPARTMENT' } }));
      const fixture = create('organizations', 'org1');
      const page = fixture.componentInstance;

      expect(uam.getById).toHaveBeenCalledWith('/organizations', 'org1');
      expect(page.isEdit).toBe(true);
      expect(page.title).toBe('shared.adminCrud.editTitle');
      expect(page.values['name']).toBe('Org One');
      expect(page.values['dataScope']).toBe('DEPARTMENT');
      expect(page.hasPendingChanges()).toBe(false);
      expect(fixture.nativeElement.querySelector('.afp__dirty')).toBeNull();
    });

    it('updates through UAM and leaves without asking to discard', () => {
      uam.getById.mockReturnValue(of({ code: 'ORG_1', name: 'Org One' }));
      const page = create('organizations', 'org1').componentInstance;
      page.values['name'] = 'Renamed';
      expect(page.hasPendingChanges()).toBe(true);

      page.submit();

      expect(uam.update).toHaveBeenCalledWith('/organizations', 'org1', expect.objectContaining({ name: 'Renamed' }));
      expect(notify.success).toHaveBeenCalledWith('shared.adminCrud.messages.updated');
      expect(page.hasPendingChanges()).toBe(false);
    });

    it('goes back with a message when the entity cannot be loaded', () => {
      uam.getById.mockReturnValue(throwError(() => ({ error: { detail: 'Not yours' } })));
      const page = create('organizations', 'org1').componentInstance;

      expect(notify.error).toHaveBeenCalledWith('Not yours');
      expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'organizations']);
      expect(page.hasPendingChanges()).toBe(false);
    });

    it('uses a default load-failure message without a server detail', () => {
      uam.getById.mockReturnValue(throwError(() => new Error('x')));
      create('applications', 'a1');

      expect(notify.error).toHaveBeenCalledWith('shared.adminCrud.messages.loadFailed');
    });

    it('nests the organization applications and members only when editing an organization', () => {
      uam.getById.mockReturnValue(of({}));
      const html = (create('organizations', 'org1').nativeElement as HTMLElement).innerHTML;
      expect(html).toContain('app-organization-applications');
      expect(html).toContain('app-organization-members');

      uam.getById.mockReturnValue(of({}));
      expect((create('applications', 'a1').nativeElement as HTMLElement).innerHTML).not.toContain('app-organization-members');
    });
  });

  describe('multi-select fields', () => {
    it('loads the live role choices and filters them as typed', () => {
      const page = create('users').componentInstance;
      const roles = page.fields.find((f) => f.key === 'roles')!;

      expect(uam.list).toHaveBeenCalledWith('/roles', 0, 200);
      expect(page.optionsOf(roles).map((o) => o.label)).toEqual(['Admin', 'OPS']);
      page.filters['roles'] = ' adm ';
      expect(page.optionsOf(roles).map((o) => o.value)).toEqual(['ADMIN']);
    });

    it('toggles a choice on and off', () => {
      const page = create('users').componentInstance;
      const roles = page.fields.find((f) => f.key === 'roles')!;

      page.toggle(roles, 'ADMIN');
      page.toggle(roles, 'OPS');
      expect(page.isChecked(roles, 'ADMIN')).toBe(true);
      page.toggle(roles, 'ADMIN');
      expect(page.isChecked(roles, 'ADMIN')).toBe(false);
      expect(page.values['roles']).toEqual(['OPS']);
    });

    it('treats a field with no live options as having the static ones, and a failed loader as none', () => {
      uam.list.mockReturnValue(throwError(() => new Error('x')));
      const page = create('users').componentInstance;
      const roles = page.fields.find((f) => f.key === 'roles')!;
      const status = page.fields.find((f) => f.key === 'status')!;

      expect(page.optionsOf(roles)).toEqual([]);
      expect(page.optionsOf(status).length).toBeGreaterThan(0);
    });

    it('renders the choices as checkboxes', () => {
      const fixture = create('users');
      const boxes = (fixture.nativeElement as HTMLElement).querySelectorAll('.afp__multi-item input[type="checkbox"]');

      expect(boxes.length).toBe(2);
    });
  });

  describe('field helpers', () => {
    it('hasValue and lengthOf read the typed value', () => {
      const page = create('organizations').componentInstance;
      const [code] = page.fields;

      expect(page.hasValue(code)).toBe(false);
      page.values['code'] = '  ';
      expect(page.hasValue(code)).toBe(false);
      page.values['code'] = 'AB';
      expect(page.hasValue(code)).toBe(true);
      expect(page.lengthOf(code)).toBe(2);
      page.values['code'] = true;
      expect(page.hasValue(code)).toBe(true);
      page.values['code'] = [];
      expect(page.hasValue(code)).toBe(false);
      page.values['code'] = ['x'];
      expect(page.hasValue(code)).toBe(true);
    });
  });

  describe('leaving with unsaved changes', () => {
    it('allows leaving a pristine form without asking, even after the inputs have settled', async () => {
      let allowed: boolean | undefined;
      const fixture = create('organizations');
      await fixture.whenStable();
      refresh(fixture);
      fixture.componentInstance.confirmDiscardChanges().subscribe((r) => (allowed = r));

      expect(allowed).toBe(true);
      expect(dialogs.confirm).not.toHaveBeenCalled();
    });

    it('asks before discarding typed values and returns the answer', () => {
      const page = create('organizations').componentInstance;
      page.values['name'] = 'typed';
      dialogs.confirm.mockReturnValue(of(false));
      let allowed: boolean | undefined;

      page.confirmDiscardChanges().subscribe((r) => (allowed = r));

      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ variant: 'danger', title: 'shared.adminCrud.discard.title' }));
      expect(allowed).toBe(false);
    });

    it('counts a started administrator as a pending change and shows the badge', async () => {
      const fixture = create('organizations');
      await fixture.whenStable();
      refresh(fixture);
      fixture.componentInstance.admin.name = 'A';
      refresh(fixture);

      expect(fixture.componentInstance.hasPendingChanges()).toBe(true);
      expect(fixture.nativeElement.querySelector('.afp__dirty')).not.toBeNull();
    });
  });

  describe('privilege-gated actions', () => {
    it('shows Save only to someone who may create this entity', () => {
      granted = new Set(['ORGANIZATION_CREATE']);
      expect(create('organizations').nativeElement.querySelector('.btn-accent')).not.toBeNull();

      granted = new Set(['ORGANIZATION_UPDATE']);
      expect(create('organizations').nativeElement.querySelector('.btn-accent')).toBeNull();
      expect(create('organizations').nativeElement.querySelector('.btn-secondary')).not.toBeNull();
    });

    it('shows Update to someone who may update, and not to someone who may only create', () => {
      uam.getById.mockReturnValue(of({}));
      granted = new Set(['ORGANIZATION_UPDATE']);
      expect(create('organizations', 'o1').nativeElement.querySelector('.btn-accent')).not.toBeNull();

      granted = new Set(['ORGANIZATION_CREATE']);
      expect(create('organizations', 'o1').nativeElement.querySelector('.btn-accent')).toBeNull();
    });

    it('accepts the users manage privilege for either action', () => {
      granted = new Set(['UAM_USERS_MANAGE']);
      expect(create('users').nativeElement.querySelector('.btn-accent')).not.toBeNull();
    });

    it('shows the administrator section only with the organization-admin privilege', () => {
      granted = new Set(['ORGANIZATION_CREATE', 'ORGANIZATION_ADMIN_CREATE']);
      expect(create('organizations').nativeElement.querySelector('#admin-name')).not.toBeNull();

      granted = new Set(['ORGANIZATION_CREATE']);
      expect(create('organizations').nativeElement.querySelector('#admin-name')).toBeNull();
    });

    it('clicking Save submits the form', async () => {
      const fixture = create('organizations');
      await fixture.whenStable();
      refresh(fixture);
      fillOrganization(fixture.componentInstance);
      refresh(fixture);

      (fixture.nativeElement.querySelector('.btn-accent') as HTMLButtonElement).click();

      expect(uam.create).toHaveBeenCalled();
    });
  });

  describe('the application form', () => {
    const catalogue = [
      { code: 'PASUPU_KUMKUMA_MATRIMONY_APP', name: 'Pasupu Kumkuma Matrimony', description: 'Matrimony', type: 'WEB', displayOrder: 10, iconHint: 'favorite', created: true },
      { code: 'BESTRIDE_RIDER_APP', name: 'BestRide Rider', description: 'Rides', type: 'MOBILE', displayOrder: 20, iconHint: 'local_taxi', created: false },
    ];
    const field = (page: AdminFormPage, key: string) => page.fields.find((f) => f.key === key)!;

    beforeEach(() => {
      uam.get.mockImplementation((path: string) => of(path === '/applications/catalogue' ? catalogue : []));
    });

    it('offers the catalogue codes as "Name (CODE)" with the used ones disabled and marked', () => {
      const fixture = create('applications');
      const options = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('#f-code option')) as HTMLOptionElement[];

      expect(uam.get).toHaveBeenCalledWith('/applications/catalogue');
      expect(options.map((o) => o.value)).toEqual(['', 'PASUPU_KUMKUMA_MATRIMONY_APP', 'BESTRIDE_RIDER_APP']);
      expect(options[1].disabled).toBe(true);
      expect(options[1].textContent).toContain('Pasupu Kumkuma Matrimony (PASUPU_KUMKUMA_MATRIMONY_APP)');
      expect(options[1].textContent).toContain('accessManagement.applications.alreadyCreated');
      expect(options[2].disabled).toBe(false);
      expect(options[2].textContent).not.toContain('accessManagement.applications.alreadyCreated');
    });

    it('picking a code fills in the name, description and type, and the type is read-only', () => {
      const fixture = create('applications');
      const page = fixture.componentInstance;

      page.values['code'] = 'BESTRIDE_RIDER_APP';
      page.onSelect(field(page, 'code'), 'BESTRIDE_RIDER_APP');
      refresh(fixture);

      expect(page.values['name']).toBe('BestRide Rider');
      expect(page.values['description']).toBe('Rides');
      expect(page.values['type']).toBe('MOBILE');
      expect(page.isReadOnly(field(page, 'type'))).toBe(true);
      expect(page.isReadOnly(field(page, 'code'))).toBe(false);
      const typeInput = fixture.nativeElement.querySelector('#f-type') as HTMLInputElement;
      expect(typeInput.readOnly).toBe(true);
      expect(typeInput.value).toBe('MOBILE');
      expect(page.canSubmit).toBe(true);
      page.submit();
      expect(uam.create).toHaveBeenCalledWith('/applications', expect.objectContaining({ code: 'BESTRIDE_RIDER_APP', name: 'BestRide Rider', type: 'MOBILE' }));
    });

    it('choosing in the select itself also fills the defaults', async () => {
      const fixture = create('applications');
      await fixture.whenStable();
      const select = fixture.nativeElement.querySelector('#f-code') as HTMLSelectElement;

      select.value = 'BESTRIDE_RIDER_APP';
      select.dispatchEvent(new Event('change'));

      expect(fixture.componentInstance.values['code']).toBe('BESTRIDE_RIDER_APP');
      expect(fixture.componentInstance.values['type']).toBe('MOBILE');
    });

    it('an option without defaults, or the placeholder, leaves the other fields alone', () => {
      const page = create('applications').componentInstance;
      page.values['name'] = 'Typed';

      page.onSelect(field(page, 'code'), '');
      page.onSelect(field(page, 'type'), 'WEB');

      expect(page.values['name']).toBe('Typed');
    });

    it('shows the code read-only with its label when editing and still sends it', () => {
      uam.getById.mockReturnValue(of({ id: 'a1', code: 'PASUPU_KUMKUMA_MATRIMONY_APP', name: 'PK', type: 'WEB' }));
      const fixture = create('applications', 'a1');
      const page = fixture.componentInstance;
      const code = fixture.nativeElement.querySelector('#f-code') as HTMLInputElement;

      expect(page.isReadOnly(field(page, 'code'))).toBe(true);
      expect(code.tagName).toBe('INPUT');
      expect(code.readOnly).toBe(true);
      expect(code.value).toBe('Pasupu Kumkuma Matrimony (PASUPU_KUMKUMA_MATRIMONY_APP)');
      expect(page.noOptionsMessage(field(page, 'code'))).toBeNull();
      page.submit();
      expect(uam.update).toHaveBeenCalledWith('/applications', 'a1', expect.objectContaining({ code: 'PASUPU_KUMKUMA_MATRIMONY_APP', type: 'WEB' }));
    });

    it('a read-only code outside the catalogue shows its raw value', () => {
      uam.getById.mockReturnValue(of({ id: 'a1', code: 'OLD_CODE' }));
      const page = create('applications', 'a1').componentInstance;

      expect(page.displayOf(field(page, 'code'))).toBe('OLD_CODE');
      delete page.values['code'];
      expect(page.displayOf(field(page, 'code'))).toBe('');
    });

    it('explains when the catalogue cannot be loaded', () => {
      uam.get.mockImplementation(() => throwError(() => new Error('down')));
      const fixture = create('applications');
      const page = fixture.componentInstance;

      expect(page.noOptionsMessage(field(page, 'code'))).toBe('accessManagement.applications.catalogueUnavailable');
      expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('accessManagement.applications.catalogueUnavailable');
      expect(page.canSubmit).toBe(false);
      expect(page.allDisabledMessage(field(page, 'code'))).toBeNull();
    });

    it('says so when every catalogue application already exists', () => {
      uam.get.mockImplementation(() => of(catalogue.map((c) => ({ ...c, created: true }))));
      const fixture = create('applications');
      const page = fixture.componentInstance;

      expect(page.allDisabledMessage(field(page, 'code'))).toBe('accessManagement.applications.allCreated');
      expect(fixture.nativeElement.textContent).toContain('accessManagement.applications.allCreated');
      expect(page.noOptionsMessage(field(page, 'code'))).toBeNull();
    });

    describe('code panel and code box', () => {
      const stubClipboard = (writeText: ((text: string) => Promise<void>) | undefined) =>
        Object.defineProperty(navigator, 'clipboard', { value: writeText ? { writeText } : undefined, configurable: true });
      afterEach(() => stubClipboard(undefined));

      it('lists every catalogue code with its name, purpose and type, and marks the created ones', () => {
        const fixture = create('applications');
        const items = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.afp__panel-item')) as HTMLElement[];

        expect(fixture.nativeElement.querySelector('.afp__panel-title').textContent).toContain('accessManagement.applications.codePanelTitle');
        expect(fixture.nativeElement.querySelector('.afp__panel').textContent).toContain('accessManagement.applications.codePanelNote');
        expect(items.map((i) => i.querySelector('.afp__panel-code')?.textContent)).toEqual(['PASUPU_KUMKUMA_MATRIMONY_APP', 'BESTRIDE_RIDER_APP']);
        expect(items[0].textContent).toContain('Pasupu Kumkuma Matrimony');
        expect(items[0].textContent).toContain('Matrimony');
        expect(items[0].textContent).toContain('WEB');
        expect(items[0].querySelector('.afp__panel-badge')?.textContent).toContain('accessManagement.applications.codePanelCreated');
        expect(items[1].querySelector('.afp__panel-badge')).toBeNull();
        expect(uam.get).toHaveBeenCalledTimes(1);
      });

      it('shows a message instead of the list when the catalogue is empty or could not be loaded', () => {
        uam.get.mockImplementation(() => throwError(() => new Error('down')));
        const fixture = create('applications');
        const panel = fixture.nativeElement.querySelector('.afp__panel') as HTMLElement;

        expect(panel.querySelector('.afp__panel-list')).toBeNull();
        expect(panel.querySelector('[role="alert"]')?.textContent).toContain('accessManagement.applications.catalogueUnavailable');
      });

      it('offers the panel on the code field only', () => {
        const page = create('applications').componentInstance;

        expect(page.isLoading).toBe(false);
        expect(page.panelFields.map((f) => f.key)).toEqual(['code']);
      });

      it('hides the panel when editing, where the code is read-only', () => {
        uam.getById.mockReturnValue(of({ id: 'a1', code: 'BESTRIDE_RIDER_APP' }));
        const fixture = create('applications', 'a1');

        expect(fixture.componentInstance.panelFields).toEqual([]);
        expect(fixture.nativeElement.querySelector('.afp__panel')).toBeNull();
      });

      it('shows the picked code in the read-only box only once one is picked, on create and on edit', () => {
        const fixture = create('applications');
        const page = fixture.componentInstance;
        expect(fixture.nativeElement.querySelector('.afp__codebox')).toBeNull();

        page.values['code'] = 'BESTRIDE_RIDER_APP';
        refresh(fixture);
        expect(fixture.nativeElement.querySelector('.afp__codebox-value').textContent).toContain('BESTRIDE_RIDER_APP');
        expect(fixture.nativeElement.querySelector('.afp__codebox-label').textContent).toContain('accessManagement.applications.codeBoxLabel');
        expect(fixture.nativeElement.querySelector('.afp__codebox-copy').getAttribute('aria-label')).toBe('accessManagement.applications.codeBoxCopy');

        uam.getById.mockReturnValue(of({ id: 'a1', code: 'PASUPU_KUMKUMA_MATRIMONY_APP' }));
        const edit = create('applications', 'a1');
        expect(edit.nativeElement.querySelector('.afp__codebox-value').textContent).toContain('PASUPU_KUMKUMA_MATRIMONY_APP');
      });

      it('copies the code with the Clipboard API and confirms with a toast', async () => {
        const writeText = vi.fn().mockResolvedValue(undefined);
        stubClipboard(writeText);
        const fixture = create('applications');
        const page = fixture.componentInstance;
        page.values['code'] = 'BESTRIDE_RIDER_APP';
        refresh(fixture);

        (fixture.nativeElement.querySelector('.afp__codebox-copy') as HTMLButtonElement).click();
        await fixture.whenStable();

        expect(writeText).toHaveBeenCalledWith('BESTRIDE_RIDER_APP');
        expect(notify.success).toHaveBeenCalledWith('accessManagement.applications.codeBoxCopied');
      });

      it('tells the user when copying fails and does nothing without a picked code', async () => {
        stubClipboard(vi.fn().mockRejectedValue(new Error('denied')));
        Object.defineProperty(document, 'execCommand', { value: vi.fn(() => false), configurable: true });
        const page = create('applications').componentInstance;
        const code = page.fields.find((f) => f.key === 'code')!;

        page.copyCode(code);
        expect(notify.error).not.toHaveBeenCalled();

        page.values['code'] = 'BESTRIDE_RIDER_APP';
        page.copyCode(code);
        await new Promise((resolve) => setTimeout(resolve));

        expect(notify.error).toHaveBeenCalledWith('accessManagement.applications.codeBoxCopyFailed');
        expect(notify.success).not.toHaveBeenCalled();
      });
    });

    it('leaves the other forms as they were', () => {
      const page = create('organizations').componentInstance;

      expect(page.fields.some((f) => page.isReadOnly(f))).toBe(false);
      expect(page.noOptionsMessage(page.fields[0])).toBeNull();
      expect(page.allDisabledMessage(page.fields[0])).toBeNull();
      expect(page.panelFields).toEqual([]);
    });
  });

  describe('embedded in the organization setup', () => {
    /** The form of organization `org-1` as the stepper hosts it: from inputs, not from the route. */
    function embed(): { fixture: ComponentFixture<AdminFormPage>; loaded: unknown[]; saved: unknown[] } {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        declarations: [AdminFormPage],
        imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
        schemas: [NO_ERRORS_SCHEMA],
        providers: [
          { provide: Router, useValue: router },
          { provide: UamService, useValue: uam },
          { provide: AppDialogService, useValue: dialogs },
          { provide: NotificationService, useValue: notify },
          { provide: HqPrivilegeAccessService, useValue: access },
          { provide: ActivatedRoute, useValue: { snapshot: { data: {}, paramMap: convertToParamMap({}) } } },
        ],
      });
      const fixture = TestBed.createComponent(AdminFormPage);
      const loaded: unknown[] = [];
      const saved: unknown[] = [];
      fixture.componentInstance.embedded = true;
      fixture.componentInstance.embeddedPath = 'organizations';
      fixture.componentInstance.embeddedId = 'org-1';
      fixture.componentInstance.entityLoaded.subscribe((v) => loaded.push(v));
      fixture.componentInstance.entitySaved.subscribe((v) => saved.push(v));
      fixture.detectChanges();
      return { fixture, loaded, saved };
    }

    beforeEach(() => {
      uam.getById.mockReturnValue(of({ code: 'ORG_1', name: 'Org One' }));
    });

    it('reads the organization it is given, not the one of the route, and reports what it read', () => {
      const { fixture, loaded } = embed();
      expect(uam.getById).toHaveBeenCalledWith('/organizations', 'org-1');
      expect(fixture.componentInstance.isEdit).toBe(true);
      expect(loaded).toEqual([expect.objectContaining({ name: 'Org One' })]);
    });

    it('draws no heading, no Cancel and none of the sections that are steps of their own', () => {
      const html = (embed().fixture.nativeElement as HTMLElement).innerHTML;
      expect(html).not.toContain('afp__header');
      expect(html).not.toContain('shared.adminCrud.cancel');
      expect(html).not.toContain('app-organization-applications');
      expect(html).not.toContain('app-organization-members');
      expect(html).not.toContain('app-organization-operating-model');
    });

    it('saves through UAM and stays where it is: no navigation, the saved values become the starting point', () => {
      const { fixture, saved } = embed();
      const page = fixture.componentInstance;
      page.values['name'] = 'Renamed';
      expect(page.hasPendingChanges()).toBe(true);
      page.submit();
      expect(uam.update).toHaveBeenCalledWith('/organizations', 'org-1', expect.objectContaining({ name: 'Renamed' }));
      expect(router.navigate).not.toHaveBeenCalled();
      expect(saved).toEqual([expect.objectContaining({ name: 'Renamed' })]);
      expect(page.hasPendingChanges()).toBe(false);
    });

    it('keeps the edits when the save fails', () => {
      uam.update.mockReturnValue(throwError(() => ({ error: { detail: 'Name taken' } })));
      const { fixture, saved } = embed();
      const page = fixture.componentInstance;
      page.values['name'] = 'Renamed';
      page.submit();
      expect(saved).toEqual([]);
      expect(router.navigate).not.toHaveBeenCalled();
      expect(page.values['name']).toBe('Renamed');
      expect(page.hasPendingChanges()).toBe(true);
    });

    it('shows a retry instead of an empty form, and leaves the page alone, when the organization cannot be read', () => {
      uam.getById.mockReturnValue(throwError(() => ({ error: { detail: 'Not yours' } })));
      const { fixture } = embed();
      const page = fixture.componentInstance;
      expect(page.loadFailed).toBe(true);
      expect(router.navigate).not.toHaveBeenCalled();
      expect(notify.error).toHaveBeenCalledWith('Not yours');
      expect((fixture.nativeElement as HTMLElement).querySelector('form')).toBeNull();
      expect((fixture.nativeElement as HTMLElement).querySelector('.afp__error')).not.toBeNull();

      uam.getById.mockReturnValue(of({ code: 'ORG_1', name: 'Org One' }));
      page.retryLoad();
      expect(page.loadFailed).toBe(false);
      expect(page.values['name']).toBe('Org One');
    });
  });
});
