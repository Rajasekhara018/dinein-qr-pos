import { Component, NO_ERRORS_SCHEMA, forwardRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { NmsEventDef } from '../event-attributes.config';
import { NmsTemplate, NmsTemplateTestSendResult } from '../nms-template.model';
import { NmsTemplatesService } from '../nms-templates.service';
import { TemplateFormComponent } from './template-form';

const defs: NmsEventDef[] = [{ value: 'OTP', label: 'Otp', attributes: ['{{otp}}', '{{name}}'] }];
const stored = (over: Partial<NmsTemplate> = {}): NmsTemplate =>
  ({ id: 't1', event: 'OTP', channel: 'EMAIL', name: 'Login code', subject: 'Code {{otp}}', body: 'enc:<p>Your {{otp}}</p>', active: true, tenantId: null, ...over });

/** Stands in for ngx-quill's editor, which is a form control in the template. */
@Component({
  selector: 'quill-editor',
  template: '',
  standalone: false,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => QuillStub), multi: true }],
})
class QuillStub implements ControlValueAccessor {
  writeValue(): void { /* the editor content is not rendered in tests */ }
  registerOnChange(): void { /* not needed */ }
  registerOnTouched(): void { /* not needed */ }
}

describe('TemplateFormComponent', () => {
  let params: Record<string, string>;
  let query: Record<string, string>;
  const router = { navigate: vi.fn() };
  const svc = {
    getEventDefinitions: vi.fn((): Observable<NmsEventDef[]> => of(defs)),
    getAttributes: vi.fn((_e: string): Observable<string[]> => of(['{{live}}'])),
    getById: vi.fn((_id: string): Observable<NmsTemplate> => of(stored())),
    create: vi.fn((_t: unknown): Observable<unknown> => of({})),
    update: vi.fn((_id: string, _t: unknown): Observable<unknown> => of({})),
    testSend: vi.fn((_r: unknown): Observable<NmsTemplateTestSendResult> => of({ sent: true })),
    decode: vi.fn((b: string) => b.replace(/^enc:/, '')),
  };
  const snack = { open: vi.fn() };
  const dialogs = { confirm: vi.fn((_: unknown): Observable<boolean> => of(true)) };
  const orgs = { current$: of({ tenantId: 'org1', name: 'Acme' }) };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };

  function create(): ComponentFixture<TemplateFormComponent> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [TemplateFormComponent, QuillStub],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(params), queryParamMap: convertToParamMap(query) } } },
        { provide: Router, useValue: router },
        { provide: NmsTemplatesService, useValue: svc },
        { provide: MatSnackBar, useValue: snack },
        { provide: AppDialogService, useValue: dialogs },
        { provide: OrganizationContextService, useValue: orgs },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    const fixture = TestBed.createComponent(TemplateFormComponent);
    fixture.detectChanges();
    return fixture;
  }
  const make = () => create().componentInstance;
  function fillValid(c: TemplateFormComponent): void {
    c.form = { ...c.form, event: 'OTP', channel: 'EMAIL', name: 'Name', subject: 'Subj', body: '<p>Hi</p>' };
  }

  beforeEach(() => {
    vi.clearAllMocks();
    params = {};
    query = {};
    granted = null;
    svc.getEventDefinitions.mockReturnValue(of(defs));
    svc.getById.mockReturnValue(of(stored()));
    dialogs.confirm.mockImplementation(() => of(true));
  });

  describe('create mode', () => {
    it('starts empty, takes the channel from the URL and knows the organization', () => {
      query = { channel: 'SMS' };
      const c = make();
      expect(c.isEdit).toBe(false);
      expect(c.form.channel).toBe('SMS');
      expect([c.organizationId, c.organizationName]).toEqual(['org1', 'Acme']);
      expect(c.hasChanges).toBe(false);
      expect(c.subjectRequired).toBe(false);
      expect(c.showSubject).toBe(true);
    });

    it('lists the catalog events and loads attributes from the chosen event definition', () => {
      const c = make();
      c.onEventChange('OTP');
      expect(c.attributes).toEqual(['{{otp}}', '{{name}}']);
      expect(c.eventDefinitions).toEqual(defs);
    });

    it('asks the server for the attributes of an event outside the catalog, ignoring stale answers', () => {
      const c = make();
      c.onEventChange('OTHER');
      expect(svc.getAttributes).toHaveBeenCalledWith('OTHER');
      expect(c.attributes).toEqual(['{{live}}']);
      c.onEventChange('');
      expect(c.attributes).toEqual([]);
    });

    it('drops an answer that arrives after the event changed', () => {
      const c = make();
      let deliver: (a: string[]) => void = () => undefined;
      svc.getAttributes.mockReturnValueOnce(new Observable<string[]>((s) => { deliver = (a) => { s.next(a); s.complete(); }; }));
      c.onEventChange('SLOW');
      c.onEventChange('OTP');
      deliver(['{{late}}']);
      expect(c.attributes).toEqual(['{{otp}}', '{{name}}']);
    });
  });

  describe('edit mode', () => {
    beforeEach(() => { params = { id: 't1' }; });

    it('loads and decodes the template, starting clean, as visual for simple HTML', () => {
      const c = make();
      expect(c.isEdit).toBe(true);
      expect(c.form).toMatchObject({ event: 'OTP', channel: 'EMAIL', name: 'Login code', subject: 'Code {{otp}}', body: '<p>Your {{otp}}</p>', organizationOnly: false });
      expect(c.hasChanges).toBe(false);
      expect(c.bodyMode).toBe('visual');
      expect(c.attributes).toEqual(['{{otp}}', '{{name}}']);
    });

    it('opens full-page HTML in the HTML view and refuses to switch to the visual editor', () => {
      svc.getById.mockReturnValue(of(stored({ body: 'enc:<html><body><table></table></body></html>', subject: undefined, tenantId: 'org1' })));
      const c = make();
      expect(c.bodyMode).toBe('html');
      expect(c.advancedHtml).toBe(true);
      expect(c.form.organizationOnly).toBe(true);
      c.setBodyMode('visual');
      expect(c.bodyMode).toBe('html');
      c.setBodyMode('preview');
      expect(c.bodyMode).toBe('preview');
    });

    it('reports a template that cannot be loaded', () => {
      svc.getById.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      expect(snack.open).toHaveBeenCalledWith('nmsTemplates.form.messages.loadFailed', 'nmsTemplates.list.close', { duration: 3000 });
      expect(c.loading).toBe(false);
    });

    it('updates and goes back to the channel list', () => {
      const c = make();
      c.form.name = 'Renamed';
      c.save();
      expect(svc.update).toHaveBeenCalledWith('t1', expect.objectContaining({ name: 'Renamed', tenantId: null, body: '<p>Your {{otp}}</p>' }));
      expect(snack.open).toHaveBeenCalledWith('nmsTemplates.form.messages.updated', 'nmsTemplates.list.close', { duration: 3000 });
      expect(router.navigate).toHaveBeenCalledWith(['/nms-templates'], { queryParams: { channel: 'EMAIL' } });
      expect(c.hasChanges).toBe(false);
    });
  });

  describe('save', () => {
    it('shows validation state and does not call the API for incomplete forms', () => {
      const c = make();
      c.save();
      expect(c.submitted).toBe(true);
      expect(snack.open).toHaveBeenCalledWith('nmsTemplates.form.fields.body.required', 'nmsTemplates.list.close', { duration: 3000 });
      expect(svc.create).not.toHaveBeenCalled();
      fillValid(c);
      c.form.subject = '  ';
      c.save();
      expect(svc.create).not.toHaveBeenCalled();
      c.form.channel = 'SMS';
      c.form.name = ' ';
      c.save();
      expect(svc.create).not.toHaveBeenCalled();
    });

    it('creates a shared template by default', () => {
      const c = make();
      fillValid(c);
      c.form.externalTemplateId = 'ext1';
      c.save();
      expect(svc.create).toHaveBeenCalledWith({ event: 'OTP', channel: 'EMAIL', name: 'Name', subject: 'Subj', body: '<p>Hi</p>', externalTemplateId: 'ext1', active: true, tenantId: null });
      expect(snack.open).toHaveBeenCalledWith('nmsTemplates.form.messages.created', 'nmsTemplates.list.close', { duration: 3000 });
    });

    it('owns the template by the session organization when asked to, and omits blank optionals', () => {
      const c = make();
      fillValid(c);
      c.form.channel = 'SMS';
      c.form.subject = '';
      c.form.organizationOnly = true;
      c.save();
      expect(svc.create).toHaveBeenCalledWith(expect.objectContaining({ tenantId: 'org1', subject: undefined, externalTemplateId: undefined }));
    });

    it('needs a subject for push as well as email', () => {
      const c = make();
      fillValid(c);
      c.form.channel = 'FCM_PUSH';
      c.form.subject = '';
      expect(c.subjectRequired).toBe(true);
      c.save();
      expect(svc.create).not.toHaveBeenCalled();
    });

    it('reports a failed save and lets the user retry', () => {
      const c = make();
      fillValid(c);
      svc.create.mockReturnValueOnce(throwError(() => new Error('x')));
      c.save();
      expect(snack.open).toHaveBeenCalledWith('nmsTemplates.form.messages.saveFailed', 'nmsTemplates.list.close', { duration: 3000 });
      expect(c.saving).toBe(false);
    });
  });

  describe('leaving', () => {
    it('goes straight back when clean, asks when dirty', () => {
      const c = make();
      c.form.channel = 'SMS';
      c.cancel();
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ variant: 'warning' }));
      expect(router.navigate).toHaveBeenCalledWith(['/nms-templates'], { queryParams: { channel: 'SMS' } });
      router.navigate.mockClear();
      dialogs.confirm.mockImplementation(() => of(false));
      c.cancel();
      expect(router.navigate).not.toHaveBeenCalled();
      const clean = make();
      clean.cancel();
      expect(router.navigate).toHaveBeenCalledTimes(1);
      expect(clean.hasPendingChanges()).toBe(false);
    });

    it('blocks browser unload only with unsaved changes', () => {
      const c = make();
      const e1 = { preventDefault: vi.fn(), returnValue: 'x' } as unknown as BeforeUnloadEvent;
      c.beforeUnload(e1);
      expect(e1.preventDefault).not.toHaveBeenCalled();
      c.form.name = 'changed';
      const e2 = { preventDefault: vi.fn(), returnValue: 'x' } as unknown as BeforeUnloadEvent;
      c.beforeUnload(e2);
      expect(e2.preventDefault).toHaveBeenCalled();
    });
  });

  describe('preview', () => {
    it('collects placeholders from the event, subject and body, with editable samples', () => {
      const c = make();
      c.onEventChange('OTP');
      c.form.subject = 'Hi {{name}} {{extra}}';
      c.form.body = '{{otp}} and {{other}}';
      expect(c.placeholderNames).toEqual(['otp', 'name', 'extra', 'other']);
      expect(c.sampleFor('otp')).toBe('482913');
      expect(c.sampleFor('extra')).toBe('Sample extra');
      c.setSample('otp', '000');
      expect(c.sampleFor('otp')).toBe('000');
      expect(c.previewSubject).toContain('Sample extra');
      c.resetSamples();
      expect(c.sampleFor('otp')).toBe('482913');
    });

    it('renders text for non-HTML channels and a cached document for email', () => {
      const c = make();
      c.form.channel = 'SMS';
      c.form.body = '<b>Code</b> {{otp}}';
      expect(c.htmlPreview).toBe(false);
      expect(c.previewText).toContain('Code 482913');
      c.form.channel = 'EMAIL';
      expect(c.htmlPreview).toBe(true);
      const first = c.previewDocument;
      expect(c.previewDocument).toBe(first);
      c.form.body = 'other';
      expect(c.previewDocument).not.toBe(first);
    });

    it('sizes the preview frame by device', () => {
      const c = make();
      expect(c.previewWidth).toBe('100%');
      c.previewDevice = 'mobile';
      expect(c.previewWidth).toBe('375px');
    });

    it('copies an attribute and says so', () => {
      document.execCommand = vi.fn(() => true);
      make().copyAttribute('{{otp}}');
      expect(document.execCommand).toHaveBeenCalledWith('copy');
      expect(snack.open).toHaveBeenCalledWith('nmsTemplates.form.copiedAttr', '', { duration: 1500 });
    });
  });

  describe('test send', () => {
    it('is only available for email, SMS and WhatsApp with a body', () => {
      const c = make();
      expect(c.canSendTest).toBe(false);
      c.form.body = 'x';
      c.form.channel = 'FCM_PUSH';
      expect(c.canSendTest).toBe(false);
      c.form.channel = 'EMAIL';
      expect(c.canSendTest).toBe(true);
      expect(c.testRecipientLabelKey).toBe('nmsTemplates.form.test.recipientEmail');
      c.form.channel = 'SMS';
      expect(c.testRecipientLabelKey).toBe('nmsTemplates.form.test.recipientPhone');
    });

    it('needs a recipient', () => {
      const c = make();
      fillValid(c);
      c.sendTest();
      expect(snack.open).toHaveBeenCalledWith('nmsTemplates.form.test.recipientRequired', 'nmsTemplates.list.close', { duration: 3000 });
      expect(svc.testSend).not.toHaveBeenCalled();
    });

    it('sends the typed content with sample variables and confirms', () => {
      const c = make();
      fillValid(c);
      c.form.body = 'Code {{otp}}';
      c.testRecipient = ' a@x.io ';
      c.sendTest();
      expect(svc.testSend).toHaveBeenCalledWith({ channel: 'EMAIL', recipient: 'a@x.io', subject: 'Subj', body: 'Code {{otp}}', variables: { otp: '482913' } });
      expect(snack.open).toHaveBeenLastCalledWith('nmsTemplates.form.test.sent', 'nmsTemplates.list.close', { duration: 5000 });
      expect(c.testSending).toBe(false);
    });

    it('reports a provider refusal, a rate limit and a failure', () => {
      const c = make();
      fillValid(c);
      c.testRecipient = 'a@x.io';
      svc.testSend.mockReturnValueOnce(of({ sent: false, errorCode: 'BAD' }));
      c.sendTest();
      expect(snack.open).toHaveBeenLastCalledWith('nmsTemplates.form.test.failed', 'nmsTemplates.list.close', { duration: 5000 });
      svc.testSend.mockReturnValueOnce(throwError(() => ({ status: 429 })));
      c.sendTest();
      expect(snack.open).toHaveBeenLastCalledWith('nmsTemplates.form.test.tooMany', 'nmsTemplates.list.close', { duration: 5000 });
      svc.testSend.mockReturnValueOnce(throwError(() => ({ status: 500, error: { message: 'boom' } })));
      c.sendTest();
      expect(snack.open).toHaveBeenLastCalledWith('nmsTemplates.form.test.failed', 'nmsTemplates.list.close', { duration: 5000 });
      expect(c.testSending).toBe(false);
    });

    it('ignores the click when the channel cannot be tested or a send is in flight', () => {
      const c = make();
      fillValid(c);
      c.testRecipient = 'a@x.io';
      c.form.channel = 'FCM_PUSH';
      c.sendTest();
      c.form.channel = 'EMAIL';
      c.testSending = true;
      c.sendTest();
      expect(svc.testSend).not.toHaveBeenCalled();
    });
  });

  describe('privileges', () => {
    const saveButton = (el: HTMLElement) => el.querySelector('button.btn-primary');

    it('create mode needs the create privilege to show Save', () => {
      expect(saveButton(create().nativeElement)).not.toBeNull();
      granted = new Set(['NMS_TEMPLATE_UPDATE']);
      expect(saveButton(create().nativeElement)).toBeNull();
      granted = new Set(['NMS_TEMPLATE_CREATE']);
      expect(saveButton(create().nativeElement)).not.toBeNull();
    });

    it('edit mode needs the update privilege', () => {
      params = { id: 't1' };
      granted = new Set(['NMS_TEMPLATE_CREATE']);
      expect(saveButton(create().nativeElement)).toBeNull();
      granted = new Set(['NMS_TEMPLATE_UPDATE']);
      expect(saveButton(create().nativeElement)).not.toBeNull();
      granted = new Set(['NMS_MANAGE']);
      expect(saveButton(create().nativeElement)).not.toBeNull();
    });

    it('shows the test-send box only to holders of the test-send privilege', () => {
      params = { id: 't1' };
      granted = new Set(['NMS_TEMPLATE_UPDATE', 'NMS_TEMPLATE_TEST_SEND']);
      expect((create().nativeElement as HTMLElement).querySelector('.tf-test-box')).not.toBeNull();
      granted = new Set(['NMS_TEMPLATE_UPDATE']);
      expect((create().nativeElement as HTMLElement).querySelector('.tf-test-box')).toBeNull();
    });
  });
});
