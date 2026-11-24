import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { ChangeRequestAck, DocumentTypeResponse, FileCheckResult } from '../document-type.models';
import { DocumentTypesService } from '../document-types.service';
import { BYTES_PER_MB } from '../document-types.constants';
import { DocumentTypeFormComponent } from './document-type-form';

const stored = (over: Partial<DocumentTypeResponse> = {}): DocumentTypeResponse =>
  ({ id: 't1', code: 'PAN', category: 'KYC', flow: null, names: { en: 'PAN card' }, allowedFormats: ['PDF', 'JPG'], maxFileSizeMb: 5, required: true, expiryYears: null, retentionDays: null, active: true, version: 3, ...over } as DocumentTypeResponse);

describe('DocumentTypeFormComponent', () => {
  let params: Record<string, string>;
  const router = { navigate: vi.fn() };
  const svc = {
    getById: vi.fn((_id: string): Observable<DocumentTypeResponse> => of(stored())),
    create: vi.fn((_r: unknown): Observable<ChangeRequestAck> => of({ changeRequestId: 'c1', status: 'PENDING' })),
    update: vi.fn((_id: string, _r: unknown): Observable<ChangeRequestAck> => of({ changeRequestId: 'c2', status: 'PENDING' })),
    checkFile: vi.fn((_f: File, _c: string, _cat?: string): Observable<FileCheckResult> => of({ accepted: true, reasons: [] })),
  };
  const notify = { error: vi.fn(), success: vi.fn() };
  const dialogs = { confirm: vi.fn((_: unknown): Observable<boolean> => of(true)) };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };

  function create(): ComponentFixture<DocumentTypeFormComponent> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [DocumentTypeFormComponent],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(params) } } },
        { provide: Router, useValue: router },
        { provide: DocumentTypesService, useValue: svc },
        { provide: NotificationService, useValue: notify },
        { provide: AppDialogService, useValue: dialogs },
        { provide: OrganizationContextService, useValue: { currentName$: of('Acme') } },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    const fixture = TestBed.createComponent(DocumentTypeFormComponent);
    fixture.detectChanges();
    return fixture;
  }
  const make = () => create().componentInstance;
  function fillValid(c: DocumentTypeFormComponent): void {
    c.form.code = 'pan';
    c.form.category = 'KYC';
    c.form.names.en = 'PAN card';
  }

  beforeEach(() => {
    vi.clearAllMocks();
    params = {};
    granted = null;
    svc.getById.mockImplementation(() => of(stored()));
  });

  describe('create mode', () => {
    it('starts empty and clean', () => {
      const c = make();
      expect(c.isEdit).toBe(false);
      expect(c.form.allowedFormats).toEqual(['PDF']);
      expect(c.hasChanges).toBe(false);
      expect(c.hasPendingChanges()).toBe(false);
    });

    it('shows validation messages for an empty form and sends nothing', () => {
      const c = make();
      c.save();
      expect(c.submitted).toBe(true);
      expect(Object.keys(c.errors).sort()).toEqual(['category', 'code', 'nameEn']);
      expect(svc.create).not.toHaveBeenCalled();
    });

    it('revalidates as the user corrects the form, but only after a first attempt', () => {
      const c = make();
      c.onFieldChange();
      expect(c.errors).toEqual({});
      c.save();
      fillValid(c);
      c.onFieldChange();
      expect(c.errors).toEqual({});
      c.toggleFormat('PDF', false);
      expect(c.errors.formats).toBe('documentManagement.editor.errors.formats');
      c.toggleFormat('PNG', true);
      expect(c.errors.formats).toBeUndefined();
      expect(c.isFormatSelected('PNG')).toBe(true);
    });

    it('sends a normalised request and then waits for approval', () => {
      const c = make();
      fillValid(c);
      c.form.flow = '  ';
      c.save();
      expect(svc.create).toHaveBeenCalledWith(expect.objectContaining({ code: 'PAN', category: 'KYC', flow: null, names: { en: 'PAN card' }, allowedFormats: ['PDF'], maxFileSizeMb: 5 }));
      expect(c.sentForApproval).toBe(true);
      expect(c.hasChanges).toBe(false);
      expect(notify.success).toHaveBeenCalledWith('documentManagement.editor.messages.sentForApproval');
      expect(c.saving).toBe(false);
    });

    it('shows a message chosen by the failure status and stays editable', () => {
      const c = make();
      fillValid(c);
      svc.create.mockReturnValueOnce(throwError(() => ({ status: 409 })));
      c.save();
      expect(c.saveErrorMessage).toMatch(/^documentManagement\./);
      expect(notify.error).toHaveBeenCalledWith(c.saveErrorMessage);
      expect(c.sentForApproval).toBe(false);
      expect(c.saving).toBe(false);
      svc.create.mockReturnValueOnce(throwError(() => undefined));
      c.save();
      expect(c.saveErrorMessage).not.toBe('');
    });

    it('explains the summary of the rule as the form changes', () => {
      const c = make();
      const first = c.summary;
      c.form.maxFileSizeMb = 20;
      c.form.required = true;
      expect(c.summary).not.toBe(first);
    });

    it('asks for a verification method when verification is turned on, and clears it when turned off', () => {
      const c = make();
      c.onVerificationToggle(true);
      expect(c.form.verificationMethod).toBe('MANUAL_REVIEW');
      c.form.verificationMethod = 'BANK_PENNY_DROP';
      c.onVerificationToggle(true);
      expect(c.form.verificationMethod).toBe('BANK_PENNY_DROP');
      c.onVerificationToggle(false);
      expect(c.form.verificationMethod).toBe('NONE');
    });

    it('offers several compatible provider options while keeping one selected value', () => {
      const c = make();
      c.form.verificationRequired = true;
      c.form.verificationMethod = 'BANK_PENNY_DROP';
      expect(c.availableProviders.map(provider => provider.key)).toEqual(['KARZA', 'CASHFREE', 'RAZORPAY']);
      c.form.verificationProvider = 'CASHFREE';
      c.form.verificationMethod = 'DRIVING_LICENCE_SARATHI';
      c.onVerificationMethodChange();
      expect(c.form.verificationProvider).toBe('CASHFREE');
      c.form.verificationProvider = 'RAZORPAY';
      c.onVerificationMethodChange();
      expect(c.form.verificationProvider).toBeNull();
      c.onVerificationToggle(false);
      expect(c.form.verificationProvider).toBeNull();
    });

    it('goes back to the list', () => {
      make().cancel();
      expect(router.navigate).toHaveBeenCalledWith(['/document-management/document-types']);
    });
  });

  describe('edit mode', () => {
    beforeEach(() => { params = { id: 't1' }; });

    it('loads the type into the form, starting clean', () => {
      const c = make();
      expect(svc.getById).toHaveBeenCalledWith('t1');
      expect(c.isEdit).toBe(true);
      expect(c.form).toMatchObject({ code: 'PAN', category: 'KYC', allowedFormats: ['PDF', 'JPG'], version: 3 });
      expect(c.hasChanges).toBe(false);
      expect(c.codeForTest).toBe('PAN');
    });

    it('reports a failed load and can retry it', () => {
      svc.getById.mockReturnValueOnce(throwError(() => new Error('x')));
      const c = make();
      expect(c.loadFailed).toBe(true);
      expect(notify.error).toHaveBeenCalledWith('documentManagement.editor.messages.loadFailed');
      c.reload();
      expect(c.loadFailed).toBe(false);
      expect(c.form.code).toBe('PAN');
    });

    it('does not check the code when editing, and updates by id', () => {
      const c = make();
      c.form.code = '';
      c.form.maxFileSizeMb = 10;
      expect(c.hasChanges).toBe(true);
      c.save();
      expect(svc.update).toHaveBeenCalledWith('t1', expect.objectContaining({ maxFileSizeMb: 10, version: 3 }));
      expect(svc.create).not.toHaveBeenCalled();
    });

    it('asks before discarding and blocks browser unload only with edits', () => {
      const c = make();
      const clean = { preventDefault: vi.fn(), returnValue: 'x' } as unknown as BeforeUnloadEvent;
      c.beforeUnload(clean);
      expect(clean.preventDefault).not.toHaveBeenCalled();
      c.form.category = 'Other';
      const dirty = { preventDefault: vi.fn(), returnValue: 'x' } as unknown as BeforeUnloadEvent;
      c.beforeUnload(dirty);
      expect(dirty.preventDefault).toHaveBeenCalled();
      c.confirmDiscardChanges().subscribe();
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ variant: 'warning' }));
    });

    it('reload does nothing in create mode', () => {
      params = {};
      make().reload();
      expect(svc.getById).not.toHaveBeenCalled();
    });
  });

  describe('test a file', () => {
    beforeEach(() => { params = { id: 't1' }; });
    const file = (name: string, size = 10) => { const f = new File(['x'], name); Object.defineProperty(f, 'size', { value: size }); return f; };
    const picked = (f: File | null): File[] => (f ? [f] : []);

    it('picks a file and shows its size', () => {
      const c = make();
      c.onFilesPicked(picked(file('a.pdf', 2 * BYTES_PER_MB)));
      expect(c.testFile?.name).toBe('a.pdf');
      expect(c.formatSize(c.testFile!)).toContain('2');
    });

    it('rejects a file over the platform limit with a message', () => {
      const c = make();
      c.onFilesPicked(picked(file('big.pdf', 60 * BYTES_PER_MB)));
      expect(c.testFile).toBeNull();
      expect(c.testError).toBe('documentManagement.test.tooLarge');
    });

    it('shows the shared drop area, and a dropped file is taken for the check', () => {
      granted = null;
      const fixture = create();
      const zone = (fixture.nativeElement as HTMLElement).querySelector('hq-file-drop .hq-drop') as HTMLElement;
      expect(zone).not.toBeNull();
      const event = new Event('drop', { cancelable: true });
      Object.defineProperty(event, 'dataTransfer', { value: { files: [file('dropped.pdf')] } });
      zone.dispatchEvent(event);
      expect(fixture.componentInstance.testFile?.name).toBe('dropped.pdf');
    });

    it('an empty pick clears the chosen file', () => {
      const c = make();
      c.onFilesPicked(picked(file('d.pdf')));
      expect(c.testFile?.name).toBe('d.pdf');
      c.onFilesPicked(picked(null));
      expect(c.testFile).toBeNull();
    });

    it('checks the file against the type and shows the verdict', () => {
      const c = make();
      c.onFilesPicked(picked(file('a.pdf')));
      svc.checkFile.mockReturnValueOnce(of({ accepted: false, reasons: [{ code: 'FORMAT', message: 'bad' }] }));
      c.runTest();
      expect(svc.checkFile).toHaveBeenCalledWith(c.testFile, 'PAN', 'KYC');
      expect(c.testResult?.accepted).toBe(false);
      expect(c.testing).toBe(false);
      c.clearTest();
      expect([c.testFile, c.testResult, c.testError]).toEqual([null, null, '']);
    });

    it('omits a blank category, reports a failed check, and needs a file and a saved type', () => {
      const c = make();
      c.runTest();
      expect(svc.checkFile).not.toHaveBeenCalled();
      c.form.category = '  ';
      c.onFilesPicked(picked(file('a.pdf')));
      svc.checkFile.mockReturnValueOnce(throwError(() => new Error('x')));
      c.runTest();
      expect(svc.checkFile).toHaveBeenCalledWith(expect.anything(), 'PAN', undefined);
      expect(c.testError).toBe('documentManagement.test.failed');
      params = {};
      const created = make();
      created.onFilesPicked(picked(file('a.pdf')));
      created.runTest();
      expect(svc.checkFile).toHaveBeenCalledTimes(1);
    });
  });

  describe('privileges', () => {
    const save = (el: HTMLElement) => el.querySelector('button[hqButton="primary"].px-4');

    it('create mode needs the create privilege to show the submit button', () => {
      expect(save(create().nativeElement)).not.toBeNull();
      granted = new Set(['DMS_DOCUMENT_TYPE_UPDATE']);
      expect(save(create().nativeElement)).toBeNull();
      granted = new Set(['DMS_DOCUMENT_TYPE_CREATE']);
      expect(save(create().nativeElement)).not.toBeNull();
    });

    it('edit mode needs the update privilege, and the file test its own', () => {
      params = { id: 't1' };
      granted = new Set(['DMS_DOCUMENT_TYPE_CREATE']);
      let el: HTMLElement = create().nativeElement;
      expect(save(el)).toBeNull();
      expect(el.querySelector('.dms-test')).toBeNull();
      granted = new Set(['DMS_DOCUMENT_TYPE_UPDATE', 'DMS_DOCUMENT_TYPE_CHECK_CREATE']);
      el = create().nativeElement;
      expect(save(el)).not.toBeNull();
      expect(el.querySelector('.dms-test')).not.toBeNull();
    });

    it('create mode never shows the file test', () => {
      expect((create().nativeElement as HTMLElement).querySelector('.dms-test')).toBeNull();
    });
  });
});
