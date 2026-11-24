import { Component, HostListener, OnInit, ViewChild } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { NgForm } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';
import { map, take, tap } from 'rxjs/operators';

import { NmsTemplatesService, NmsEventOption } from '../nms-templates.service';
import { NmsTemplate, NmsTemplateForm } from '../nms-template.model';
import { NmsChannel, NMS_CHANNELS, NmsEventDef } from '../event-attributes.config';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { PendingChangesAware } from '../../shared/guards/pending-changes.guard';
import { TranslateService } from '@ngx-translate/core';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import {
  BodyMode, HTML_PREVIEW_CHANNELS, PREVIEW_FRAME_WIDTH, PreviewDevice, TEST_SEND_CHANNELS,
} from '../preview/template-preview.constants';
import {
  attributeName, buildPreviewDocument, extractPlaceholders, fillPlaceholders,
  htmlToPlainText, isAdvancedHtml, sampleValueFor,
} from '../preview/template-preview.helpers';

@Component({
  selector: 'app-template-form',
  templateUrl: './template-form.html',
  styleUrls: ['./template-form.scss'],
  standalone: false,
})
export class TemplateFormComponent implements OnInit, PendingChangesAware {
  @ViewChild('templateForm') templateFormRef!: NgForm;

  isEdit = false;
  editId: string | null = null;
  saving = false;
  loading = false;
  submitted = false;

  channels = NMS_CHANNELS;
  events$!: Observable<NmsEventOption[]>;
  eventDefinitions: NmsEventDef[] = [];
  attributes: string[] = [];
  private attributeLoadId = 0;
  testRecipient = '';
  testSending = false;
  private previewCacheKey = '';
  private previewCacheValue: SafeHtml | null = null;
  private savedSnapshot = '';

  form: NmsTemplateForm = {
    event: '',
    channel: '',
    name: '',
    subject: '',
    body: '',
    externalTemplateId: '',
    active: true,
    organizationOnly: false,
  };

  /** Which view of the body is open: rich-text editor, raw HTML, or the rendered preview. */
  bodyMode: BodyMode = 'visual';
  previewDevice: PreviewDevice = 'desktop';
  /** Sample values for {{placeholders}}; used only to render the preview, never saved or sent. */
  sampleValues: Record<string, string> = {};

  /** The organization the user is working in; a template made "only for my organization" is owned by it. */
  organizationId: string | null = null;
  organizationName: string | null = null;

  readonly quillConfig = {
    toolbar: [
      [{ header: [1, 2, 3, false] }],
      ['bold', 'italic', 'underline', 'strike'],
      [{ color: [] }, { background: [] }],
      [{ list: 'ordered' }, { list: 'bullet' }],
      [{ align: [] }],
      ['link', 'image'],
      ['clean'],
    ],
  };

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private svc: NmsTemplatesService,
    private snack: MatSnackBar,
    private appDialog: AppDialogService,
    private translate: TranslateService,
    private organizations: OrganizationContextService,
    private sanitizer: DomSanitizer,
  ) {}

  ngOnInit(): void {
    // getEventDefinitions() can emit *synchronously* (its default-catalog fallback runs inside the
    // same call stack as subscribe()). Imperatively mutating a plain `events` array property from
    // that subscription — even deferred via setTimeout — kept landing inside the same change-detection
    // cycle as this component's own first template check, producing NG0100 on the event <select>'s
    // *ngFor. Driving that binding through the `async` pipe instead (`events$ | async`) is the
    // idiomatic fix: AsyncPipe schedules its own markForCheck() through Angular's proper change
    // detection hooks instead of a raw property write, so it never races the initial check.
    this.events$ = this.svc.getEventDefinitions().pipe(
      tap(definitions => {
        this.eventDefinitions = definitions;
        if (this.form.event) this.loadAttributesForEvent(this.form.event);
      }),
      map(definitions => definitions.map(event => ({ value: event.value, label: event.label }))),
    );

    this.organizations.current$.pipe(take(1)).subscribe((org) => {
      this.organizationId = org?.tenantId ?? null;
      this.organizationName = org?.name ?? null;
    });

    const id = this.route.snapshot.paramMap.get('id');
    const channelParam = this.route.snapshot.queryParamMap.get('channel') as NmsChannel | null;

    if (id) {
      this.isEdit = true;
      this.editId = id;
      this.loadTemplate(id);
    } else {
      if (channelParam) this.form.channel = channelParam;
      this.setSavedSnapshot();
    }
  }

  get hasChanges(): boolean {
    return this.currentSnapshot() !== this.savedSnapshot;
  }

  @HostListener('window:beforeunload', ['$event'])
  beforeUnload(event: BeforeUnloadEvent): void {
    if (!this.hasChanges) return;
    event.preventDefault();
    event.returnValue = '';
  }

  hasPendingChanges(): boolean {
    return this.hasChanges;
  }

  confirmDiscardChanges(): Observable<boolean> {
    return this.appDialog.confirm({
      title: this.translate.instant('nmsTemplates.form.discardDialog.title'),
      message: this.translate.instant('nmsTemplates.form.discardDialog.message'),
      icon: 'bi-exclamation-triangle',
      variant: 'warning',
      confirmText: this.translate.instant('shared.adminCrud.discard.confirm'),
      cancelText: this.translate.instant('shared.adminCrud.discard.cancel'),
    });
  }

  private loadTemplate(id: string): void {
    this.loading = true;
    this.svc.getById(id).subscribe({
      next: (t: NmsTemplate) => {
        this.form = {
          event: t.event,
          channel: t.channel,
          name: t.name,
          subject: t.subject ?? '',
          body: this.svc.decode(t.body),
          externalTemplateId: t.externalTemplateId ?? '',
          active: t.active,
          organizationOnly: !!t.tenantId,
        };
        this.setSavedSnapshot();
        // Full-page HTML (tables, styles) would be rewritten by the rich-text editor, so open it as HTML.
        this.bodyMode = isAdvancedHtml(this.form.body) ? 'html' : 'visual';
        this.onEventChange(t.event);
        this.loading = false;
      },
      error: () => {
        this.snack.open(this.translate.instant('nmsTemplates.form.messages.loadFailed'), this.translate.instant('nmsTemplates.list.close'), { duration: 3000 });
        this.loading = false;
      },
    });
  }

  onEventChange(eventValue: string): void {
    this.form.event = eventValue;
    this.loadAttributesForEvent(eventValue);
  }

  private loadAttributesForEvent(eventValue: string): void {
    const loadId = ++this.attributeLoadId;
    this.attributes = [];
    if (!eventValue) return;

    const configuredEvent = this.eventDefinitions.find(event => event.value === eventValue);
    if (configuredEvent) {
      this.attributes = [...configuredEvent.attributes];
      return;
    }

    this.svc.getAttributes(eventValue).subscribe(attrs => {
      if (loadId !== this.attributeLoadId || this.form.event !== eventValue) return;
      this.attributes = attrs;
    });
  }

  copyAttribute(attr: string): void {
    const el = document.createElement('textarea');
    el.value = attr;
    document.body.appendChild(el);
    el.select();
    document.execCommand('copy');
    document.body.removeChild(el);
    this.snack.open(this.translate.instant('nmsTemplates.form.copiedAttr', { attr }), '', { duration: 1500 });
  }

  get advancedHtml(): boolean {
    return isAdvancedHtml(this.form.body);
  }

  setBodyMode(mode: BodyMode): void {
    if (mode === 'visual' && this.advancedHtml) return;
    this.bodyMode = mode;
  }

  get htmlPreview(): boolean {
    return !!this.form.channel && HTML_PREVIEW_CHANNELS.includes(this.form.channel);
  }

  get previewWidth(): string {
    return PREVIEW_FRAME_WIDTH[this.previewDevice];
  }

  /** Every placeholder from the event's attributes plus any typed into the subject or body. */
  get placeholderNames(): string[] {
    const fromEvent = this.attributes.map(attributeName);
    return [...new Set([...fromEvent, ...extractPlaceholders(this.form.subject, this.form.body)])];
  }

  sampleFor(name: string): string {
    return this.sampleValues[name] ?? sampleValueFor(name);
  }

  setSample(name: string, value: string): void {
    this.sampleValues = { ...this.sampleValues, [name]: value };
  }

  resetSamples(): void {
    this.sampleValues = {};
  }

  get canSendTest(): boolean {
    return !!this.form.channel && TEST_SEND_CHANNELS.includes(this.form.channel) && !!this.form.body.trim();
  }

  get testRecipientLabelKey(): string {
    return this.form.channel === 'EMAIL'
      ? 'nmsTemplates.form.test.recipientEmail'
      : 'nmsTemplates.form.test.recipientPhone';
  }

  /** Sends the content as typed (saved or not) to the typed recipient, marked [TEST]. */
  sendTest(): void {
    const recipient = this.testRecipient.trim();
    if (!recipient) {
      this.snack.open(this.translate.instant('nmsTemplates.form.test.recipientRequired'), this.translate.instant('nmsTemplates.list.close'), { duration: 3000 });
      return;
    }
    if (!this.canSendTest || this.testSending) return;

    this.testSending = true;
    this.svc.testSend({
      channel: this.form.channel as NmsChannel,
      recipient,
      subject: this.form.subject || undefined,
      body: this.form.body,
      variables: this.currentSamples(),
    }).subscribe({
      next: (result) => {
        this.testSending = false;
        const text = result.sent
          ? this.translate.instant('nmsTemplates.form.test.sent', { recipient })
          : this.translate.instant('nmsTemplates.form.test.failed', { reason: result.message || result.errorCode || '' });
        this.snack.open(text, this.translate.instant('nmsTemplates.list.close'), { duration: 5000 });
      },
      error: (err) => {
        this.testSending = false;
        const key = err?.status === 429 ? 'nmsTemplates.form.test.tooMany' : 'nmsTemplates.form.test.failed';
        this.snack.open(
          this.translate.instant(key, { reason: err?.error?.message || '' }),
          this.translate.instant('nmsTemplates.list.close'),
          { duration: 5000 },
        );
      },
    });
  }

  private currentSamples(): Record<string, string> {
    return Object.fromEntries(this.placeholderNames.map(name => [name, this.sampleFor(name)]));
  }

  get previewSubject(): string {
    return fillPlaceholders(this.form.subject, this.currentSamples(), false);
  }

  /** Rendered email as a full page for the sandboxed frame (scripts never run in it). */
  get previewDocument(): SafeHtml {
    const html = buildPreviewDocument(fillPlaceholders(this.form.body, this.currentSamples(), true));
    // Same object for the same HTML, otherwise the frame would reload on every change-detection pass.
    if (html !== this.previewCacheKey) {
      this.previewCacheKey = html;
      this.previewCacheValue = this.sanitizer.bypassSecurityTrustHtml(html);
    }
    return this.previewCacheValue!;
  }

  /** Rendered text for channels without markup. */
  get previewText(): string {
    return htmlToPlainText(fillPlaceholders(this.form.body, this.currentSamples(), true));
  }

  get showSubject(): boolean {
    return !!this.form.channel;
  }

  get subjectRequired(): boolean {
    return this.form.channel === 'EMAIL' || this.form.channel === 'FCM_PUSH';
  }

  save(): void {
    this.submitted = true;
    const bodyMissing = !this.form.body.trim();
    const formInvalid = !this.form.name.trim() || !this.form.channel || !this.form.event ||
                        (this.subjectRequired && !this.form.subject.trim()) || bodyMissing;
    if (formInvalid) {
      if (bodyMissing) this.snack.open(this.translate.instant('nmsTemplates.form.fields.body.required'), this.translate.instant('nmsTemplates.list.close'), { duration: 3000 });
      return;
    }

    this.saving = true;
    const payload: Omit<NmsTemplate, 'id' | 'createdAt' | 'updatedAt'> = {
      event: this.form.event,
      channel: this.form.channel as NmsChannel,
      name: this.form.name,
      subject: this.form.subject || undefined,
      body: this.form.body,
      externalTemplateId: this.form.externalTemplateId || undefined,
      active: this.form.active,
      // Only on create: an existing template keeps the owner it has.
      tenantId: !this.isEdit && this.form.organizationOnly ? this.organizationId : null,
    };

    const call = this.isEdit
      ? this.svc.update(this.editId!, payload)
      : this.svc.create(payload);

    call.subscribe({
      next: () => {
        this.setSavedSnapshot();
        this.snack.open(this.translate.instant(this.isEdit ? 'nmsTemplates.form.messages.updated' : 'nmsTemplates.form.messages.created'), this.translate.instant('nmsTemplates.list.close'), { duration: 3000 });
        this.router.navigate(['/nms-templates'], { queryParams: { channel: this.form.channel } });
      },
      error: () => {
        this.snack.open(this.translate.instant('nmsTemplates.form.messages.saveFailed'), this.translate.instant('nmsTemplates.list.close'), { duration: 3000 });
        this.saving = false;
      },
    });
  }

  cancel(): void {
    if (this.hasChanges) {
      this.confirmDiscardChanges().subscribe(confirmed => {
        if (confirmed) this.router.navigate(['/nms-templates'], { queryParams: { channel: this.form.channel } });
      });
      return;
    }
    this.router.navigate(['/nms-templates'], { queryParams: { channel: this.form.channel } });
  }

  private setSavedSnapshot(): void {
    this.savedSnapshot = this.currentSnapshot();
  }

  private currentSnapshot(): string {
    const normalized = {
      event: this.form.event,
      channel: this.form.channel,
      name: this.form.name.trim(),
      subject: this.form.subject.trim(),
      body: this.form.body,
      externalTemplateId: this.form.externalTemplateId.trim(),
      active: this.form.active,
      organizationOnly: this.form.organizationOnly,
    };
    return JSON.stringify(normalized);
  }
}
