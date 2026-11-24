import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule, HqTimezoneService } from 'hq-shared-lib';
import { EMPTY, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AppDialogService } from '../shared/services/app-dialog-service';
import { NotificationService } from '../shared/services/notification-service';
import { OnboardingSchemaService } from '../shared/services/onboarding-schema-service';
import { FieldDialog } from './field-dialog/field-dialog';
import { OnboardingForms } from './onboarding-forms';
import { OnboardingSchemaDefinition, OnboardingSchemaResponse, OnboardingType, SchemaField, SchemaStep } from './onboarding-forms.models';
import { PreviewDialog } from './preview-dialog/preview-dialog';

const type = (code: string, over: Partial<OnboardingType> = {}): OnboardingType => ({ id: code, code, displayName: code + ' name', active: true, requiresReview: true, ...over });
const step = (key: string, fields: SchemaField[] = []): SchemaStep => ({ key, title: key, fields });
const f = (key: string): SchemaField => ({ key, label: key, type: 'TEXT', required: false });
const version = (v: number, status: OnboardingSchemaResponse['status'], steps: SchemaStep[] = [step('s1', [f('a')])]): OnboardingSchemaResponse =>
  ({ id: 'id' + v, typeCode: 'CUSTOMER', version: v, status, definition: { steps }, createdAt: '2026-01-01T00:00:00Z' });

describe('OnboardingForms', () => {
  const api = {
    listTypes: vi.fn((): Observable<OnboardingType[]> => of([type('CUSTOMER'), type('DRIVER', { requiresReview: false }), type('VENDOR')])),
    createType: vi.fn((_b: unknown): Observable<unknown> => of({})),
    setRequiresReview: vi.fn((_c: string, _v: boolean): Observable<unknown> => of({})),
    listSchemasNewestFirst: vi.fn((_c: string): Observable<OnboardingSchemaResponse[]> => of([version(3, 'DRAFT'), version(2, 'PUBLISHED'), version(1, 'ARCHIVED')])),
    defaultSchema: vi.fn((_c: string): Observable<OnboardingSchemaDefinition> => of({ steps: [step('d1', [f('x')])] })),
    createDraft: vi.fn((_c: string, _d: unknown): Observable<unknown> => of({})),
    updateDraft: vi.fn((_c: string, _v: number, _d: unknown): Observable<unknown> => of({})),
    publish: vi.fn((_c: string, _v: number): Observable<unknown> => of({})),
    republish: vi.fn((_c: string, _v: number): Observable<unknown> => of({})),
  };
  const notify = { error: vi.fn(), success: vi.fn() };
  const appDialog = { confirm: vi.fn((_: unknown): Observable<boolean> => of(true)) };
  const dialogResult = { value: null as SchemaField | null };
  const matDialog = { open: vi.fn((_c: unknown, _o: unknown) => ({ afterClosed: () => of(dialogResult.value) })) };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };

  function create(): ComponentFixture<OnboardingForms> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [OnboardingForms],
      imports: [FormsModule, ReactiveFormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: OnboardingSchemaService, useValue: api },
        { provide: NotificationService, useValue: notify },
        { provide: AppDialogService, useValue: appDialog },
        { provide: MatDialog, useValue: matDialog },
        { provide: HqTimezoneService, useValue: { effectiveTimezone: () => 'UTC' } },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    const fixture = TestBed.createComponent(OnboardingForms);
    fixture.detectChanges();
    return fixture;
  }
  const make = () => create().componentInstance;
  /** A component with CUSTOMER selected and its versions loaded. */
  function selected() {
    const c = make();
    c.selectType(c.types[0]);
    return c;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    dialogResult.value = null;
    appDialog.confirm.mockImplementation(() => of(true));
    api.listTypes.mockImplementation(() => of([type('CUSTOMER'), type('DRIVER', { requiresReview: false }), type('VENDOR')]));
  });

  describe('types', () => {
    it('loads the onboarding types', () => {
      const c = make();
      expect(c.types.map((t) => t.code)).toEqual(['CUSTOMER', 'DRIVER', 'VENDOR']);
      expect(c.isLoadingTypes).toBe(false);
    });

    it('reports a failed load, using the server message when there is one', () => {
      api.listTypes.mockReturnValueOnce(throwError(() => ({ error: { detail: 'No access' } })));
      make();
      expect(notify.error).toHaveBeenCalledWith('No access');
      api.listTypes.mockReturnValueOnce(throwError(() => ({})));
      make();
      expect(notify.error).toHaveBeenLastCalledWith('onboardingForms.toasts.loadTypesFailed');
    });

    it('treats an empty reply as no types and keeps the selection fresh on reload', () => {
      const c = selected();
      api.listTypes.mockReturnValueOnce(of([type('CUSTOMER', { displayName: 'Renamed' })]));
      c.loadTypes();
      expect(c.selectedType?.displayName).toBe('Renamed');
      api.listTypes.mockReturnValueOnce(of(null as unknown as OnboardingType[]));
      c.loadTypes();
      expect(c.types).toEqual([]);
    });

    it('creates a type, resets the form and reloads', () => {
      const c = make();
      c.toggleNewTypeForm();
      expect(c.showNewTypeForm).toBe(true);
      c.newTypeForm.setValue({ code: 'AGENT', displayName: 'Agent', requiresReview: false });
      c.createType();
      expect(api.createType).toHaveBeenCalledWith({ code: 'AGENT', displayName: 'Agent', requiresReview: false });
      expect(notify.success).toHaveBeenCalledWith('onboardingForms.toasts.typeCreated');
      expect(c.showNewTypeForm).toBe(false);
      expect(c.newTypeForm.value).toEqual({ code: '', displayName: '', requiresReview: true });
      expect(api.listTypes).toHaveBeenCalledTimes(2);
    });

    it('does not create from an invalid form (bad code) or while already creating', () => {
      const c = make();
      c.newTypeForm.setValue({ code: '9bad', displayName: 'X', requiresReview: true });
      c.createType();
      expect(api.createType).not.toHaveBeenCalled();
      expect(c.newTypeForm.touched).toBe(true);
      c.newTypeForm.setValue({ code: 'OK', displayName: 'X', requiresReview: true });
      c.isCreatingType = true;
      c.createType();
      expect(api.createType).not.toHaveBeenCalled();
    });

    it('reports a failed create and stays open', () => {
      const c = make();
      c.toggleNewTypeForm();
      c.newTypeForm.setValue({ code: 'OK', displayName: 'X', requiresReview: true });
      api.createType.mockReturnValueOnce(throwError(() => ({ error: { message: 'Duplicate' } })));
      c.createType();
      expect(notify.error).toHaveBeenCalledWith('Duplicate');
      expect(c.isCreatingType).toBe(false);
      expect(c.showNewTypeForm).toBe(true);
      c.toggleNewTypeForm();
      expect(c.newTypeForm.value.code).toBe('');
    });

    it('selecting a type loads its versions, newest first, and ignores a second click on it', () => {
      const c = selected();
      expect(api.listSchemasNewestFirst).toHaveBeenCalledWith('CUSTOMER');
      expect(c.schemaVersions.map((v) => v.version)).toEqual([3, 2, 1]);
      c.selectType(c.types[0]);
      expect(api.listSchemasNewestFirst).toHaveBeenCalledTimes(1);
    });

    it('reports a failed version load', () => {
      const c = make();
      api.listSchemasNewestFirst.mockReturnValueOnce(throwError(() => ({})));
      c.selectType(c.types[0]);
      expect(c.schemaVersions).toEqual([]);
      expect(notify.error).toHaveBeenCalledWith('onboardingForms.toasts.loadVersionsFailed');
    });

    it('stays on the current type when the discard prompt is declined', () => {
      const c = selected();
      c.newDraft();
      c.addStep();
      appDialog.confirm.mockImplementation(() => of(false));
      c.selectType(c.types[1]);
      expect(c.selectedType?.code).toBe('CUSTOMER');
      expect(c.builderOpen).toBe(true);
      appDialog.confirm.mockImplementation(() => of(true));
      c.selectType(c.types[1]);
      expect(c.selectedType?.code).toBe('DRIVER');
      expect(c.builderOpen).toBe(false);
    });
  });

  describe('review requirement', () => {
    it('switches a type to auto-approve after a warning, then reloads', () => {
      const c = make();
      c.toggleRequiresReview(c.types[0], { stopPropagation: vi.fn() } as unknown as Event);
      expect(appDialog.confirm).toHaveBeenCalledWith(expect.objectContaining({ variant: 'warning' }));
      expect(api.setRequiresReview).toHaveBeenCalledWith('CUSTOMER', false);
      expect(notify.success).toHaveBeenCalledWith('onboardingForms.toasts.typeUpdated');
    });

    it('switches back to requiring review with a plain confirmation', () => {
      const c = make();
      c.toggleRequiresReview(c.types[1], { stopPropagation: vi.fn() } as unknown as Event);
      expect(appDialog.confirm).toHaveBeenCalledWith(expect.objectContaining({ variant: undefined, title: 'onboardingForms.dialogs.requireReview.title' }));
      expect(api.setRequiresReview).toHaveBeenCalledWith('DRIVER', true);
    });

    it('does nothing when declined, and reports a failure', () => {
      const c = make();
      const stop = vi.fn();
      appDialog.confirm.mockImplementation(() => of(false));
      c.toggleRequiresReview(c.types[0], { stopPropagation: stop } as unknown as Event);
      expect(stop).toHaveBeenCalled();
      expect(api.setRequiresReview).not.toHaveBeenCalled();
      appDialog.confirm.mockImplementation(() => of(true));
      api.setRequiresReview.mockReturnValueOnce(throwError(() => ({})));
      c.toggleRequiresReview(c.types[0], { stopPropagation: stop } as unknown as Event);
      expect(notify.error).toHaveBeenCalledWith('onboardingForms.toasts.updateTypeFailed');
    });

    it('lets the privilege decide whether the badge can be toggled', () => {
      granted = new Set(['ONBOARDING_ADMIN_TYPE_REQUIRES_REVIEW_UPDATE']);
      expect(make().canToggleRequiresReview).toBe(true);
      granted = new Set(['ONBOARDING_ADMIN_TYPE_CREATE']);
      expect(make().canToggleRequiresReview).toBe(false);
    });
  });

  describe('builder', () => {
    it('opens a blank draft that has no unsaved changes until a step is added', () => {
      const c = selected();
      c.newDraft();
      expect(c.builderOpen).toBe(true);
      expect(c.hasChanges).toBe(false);
      expect(c.hasPendingChanges()).toBe(false);
      c.addStep();
      c.addStep();
      expect(c.builderSteps.map((s) => s.key)).toEqual(['step_1', 'step_2']);
      expect(c.hasChanges).toBe(true);
    });

    it('does not open a draft without a type', () => {
      const c = make();
      c.newDraft();
      expect(c.builderOpen).toBe(false);
    });

    it('edits an existing draft on a copy, leaving the history untouched', () => {
      const c = selected();
      const draft = c.schemaVersions[0];
      c.editDraft(draft);
      c.setStepTitle(0, 'Renamed');
      c.setStepKey(0, 'renamed_key');
      c.removeField(0, 0);
      expect(c.builderSteps[0]).toMatchObject({ title: 'Renamed', key: 'renamed_key', fields: [] });
      expect(draft.definition.steps[0].title).toBe('s1');
      expect(draft.definition.steps[0].fields).toHaveLength(1);
      expect(c.totalFieldCount).toBe(0);
    });

    it('removes steps, reads input values and reorders steps and fields', () => {
      const c = selected();
      c.editDraft(version(5, 'DRAFT', [step('a', [f('x'), f('y')]), step('b'), step('c')]));
      expect(c.inputValue({ target: { value: 'typed' } } as unknown as Event)).toBe('typed');
      c.dropStep({ previousIndex: 0, currentIndex: 2 } as never);
      expect(c.builderSteps.map((s) => s.key)).toEqual(['b', 'c', 'a']);
      c.dropField({ previousIndex: 0, currentIndex: 1 } as never, 2);
      expect(c.builderSteps[2].fields.map((x) => x.key)).toEqual(['y', 'x']);
      c.removeStep(0);
      expect(c.builderSteps.map((s) => s.key)).toEqual(['c', 'a']);
    });

    it('closes a clean builder at once, and asks before dropping edits', () => {
      const c = selected();
      c.newDraft();
      c.closeBuilder();
      expect(c.builderOpen).toBe(false);
      c.newDraft();
      c.addStep();
      appDialog.confirm.mockImplementation(() => of(false));
      c.closeBuilder();
      expect(c.builderOpen).toBe(true);
      appDialog.confirm.mockImplementation(() => of(true));
      c.closeBuilder();
      expect(c.builderOpen).toBe(false);
      expect(c.builderSteps).toEqual([]);
    });

    it('blocks the browser unload only while the builder has edits', () => {
      const c = selected();
      const clean = { preventDefault: vi.fn(), returnValue: 'x' } as unknown as BeforeUnloadEvent;
      c.beforeUnload(clean);
      expect(clean.preventDefault).not.toHaveBeenCalled();
      c.newDraft();
      c.addStep();
      const dirty = { preventDefault: vi.fn(), returnValue: 'x' } as unknown as BeforeUnloadEvent;
      c.beforeUnload(dirty);
      expect(dirty.preventDefault).toHaveBeenCalled();
    });

    it('adds a field from the dialog result, replaces one on edit, and ignores a cancelled dialog', () => {
      const c = selected();
      c.editDraft(version(5, 'DRAFT', [step('a', [f('x')]), step('b')]));
      c.addField(1);
      expect(matDialog.open).toHaveBeenCalledWith(FieldDialog, expect.objectContaining({ data: { field: null, takenKeys: ['x'] } }));
      expect(c.builderSteps[1].fields).toEqual([]);
      dialogResult.value = f('new');
      c.addField(1);
      expect(c.builderSteps[1].fields.map((x) => x.key)).toEqual(['new']);
      dialogResult.value = { ...f('x'), label: 'Edited' };
      c.editField(0, 0);
      expect(matDialog.open).toHaveBeenLastCalledWith(FieldDialog, expect.objectContaining({ data: { field: f('x'), takenKeys: ['new'] } }));
      expect(c.builderSteps[0].fields[0].label).toBe('Edited');
      expect(c.builderSteps[1].fields).toHaveLength(1);
    });

    it('previews the builder or a saved version, but never an empty schema', () => {
      const c = selected();
      c.openPreview();
      expect(matDialog.open).not.toHaveBeenCalled();
      c.editDraft(version(5, 'DRAFT'));
      c.openPreview();
      expect(matDialog.open).toHaveBeenCalledWith(PreviewDialog, expect.objectContaining({ data: { typeLabel: 'CUSTOMER name', steps: c.builderSteps } }));
      c.previewVersion(version(6, 'PUBLISHED', []));
      expect(matDialog.open).toHaveBeenCalledTimes(1);
      c.previewVersion(version(6, 'PUBLISHED'));
      expect(matDialog.open).toHaveBeenCalledTimes(2);
      const other = make();
      other.previewVersion(version(1, 'DRAFT'));
      other.openPreview();
      expect(matDialog.open).toHaveBeenCalledTimes(2);
    });

    it('counts fields across steps and picks icons by type', () => {
      const c = selected();
      c.editDraft(version(5, 'DRAFT', [step('a', [f('x'), f('y')]), step('b', [f('z')])]));
      expect(c.totalFieldCount).toBe(3);
      expect(c.fieldTypeIcon('EMAIL')).toBe('bi-envelope');
      expect(c.fieldTypeIcon('NOPE' as never)).toBe('bi-question-circle');
    });
  });

  describe('loading a starting point', () => {
    it('offers defaults only for built-in types and loads them into the builder', () => {
      const c = selected();
      expect(c.hasDefaultDefinition).toBe(true);
      c.newDraft();
      c.loadDefaults();
      expect(api.defaultSchema).toHaveBeenCalledWith('CUSTOMER');
      expect(c.builderSteps.map((s) => s.key)).toEqual(['d1']);
      expect(c.isLoadingDefaults).toBe(false);
      c.selectedType = type('VENDOR');
      expect(c.hasDefaultDefinition).toBe(false);
    });

    it('reports a failed default load, and does nothing without a type', () => {
      const c = selected();
      api.defaultSchema.mockReturnValueOnce(throwError(() => ({})));
      c.loadDefaults();
      expect(notify.error).toHaveBeenCalledWith('onboardingForms.toasts.loadDefaultsFailed');
      const none = make();
      none.loadDefaults();
      expect(api.defaultSchema).toHaveBeenCalledTimes(1);
    });

    it('lists every other type as a copy source', () => {
      const c = selected();
      expect(c.copySourceTypes.map((t) => t.code)).toEqual(['DRIVER', 'VENDOR']);
    });

    it('copies the published version of another type, else its newest', () => {
      const c = selected();
      c.newDraft();
      api.listSchemasNewestFirst.mockReturnValueOnce(of([version(3, 'DRAFT', [step('draft')]), version(2, 'PUBLISHED', [step('live')])]));
      c.copyFromType('DRIVER');
      expect(c.builderSteps.map((s) => s.key)).toEqual(['live']);
      api.listSchemasNewestFirst.mockReturnValueOnce(of([version(3, 'DRAFT', [step('draft')])]));
      c.copyFromType('DRIVER');
      expect(c.builderSteps.map((s) => s.key)).toEqual(['draft']);
    });

    it('reports when the source has no versions or cannot be read, and ignores an empty choice', () => {
      const c = selected();
      c.newDraft();
      api.listSchemasNewestFirst.mockReturnValueOnce(of([]));
      c.copyFromType('VENDOR');
      expect(notify.error).toHaveBeenCalledWith('onboardingForms.toasts.copyNoVersions');
      api.listSchemasNewestFirst.mockReturnValueOnce(throwError(() => ({})));
      c.copyFromType('VENDOR');
      expect(notify.error).toHaveBeenLastCalledWith('onboardingForms.toasts.copyFailed');
      api.listSchemasNewestFirst.mockClear();
      c.copyFromType('');
      expect(api.listSchemasNewestFirst).not.toHaveBeenCalled();
    });
  });

  describe('saving and publishing', () => {
    it('creates a draft for a new version, closes the builder and reloads the versions', () => {
      const c = selected();
      c.newDraft();
      c.addStep();
      api.listSchemasNewestFirst.mockClear();
      c.saveDraft();
      expect(api.createDraft).toHaveBeenCalledWith('CUSTOMER', { steps: [expect.objectContaining({ key: 'step_1' })] });
      expect(notify.success).toHaveBeenCalledWith('onboardingForms.toasts.draftCreated');
      expect(c.builderOpen).toBe(false);
      expect(api.listSchemasNewestFirst).toHaveBeenCalledTimes(1);
    });

    it('updates the draft being edited', () => {
      const c = selected();
      c.editDraft(c.schemaVersions[0]);
      c.saveDraft();
      expect(api.updateDraft).toHaveBeenCalledWith('CUSTOMER', 3, { steps: [expect.objectContaining({ key: 's1' })] });
      expect(notify.success).toHaveBeenCalledWith('onboardingForms.toasts.draftUpdated');
    });

    it('reports a failed save and keeps the builder open', () => {
      const c = selected();
      c.newDraft();
      api.createDraft.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Bad field' } })));
      c.saveDraft();
      expect(notify.error).toHaveBeenCalledWith('Bad field');
      expect(c.builderOpen).toBe(true);
      expect(c.isSavingDraft).toBe(false);
    });

    it('does not save a form with a broken pattern or a minimum above its maximum, and says which field', () => {
      const c = selected();
      c.editDraft(version(5, 'DRAFT', [step('a', [{ ...f('licence'), pattern: '[', minLength: 10, maxLength: 2 }])]));
      c.saveDraft();
      expect(api.updateDraft).not.toHaveBeenCalled();
      expect(c.isSavingDraft).toBe(false);
      const [message] = (notify.error.mock.calls.at(-1) ?? []) as string[];
      expect(message).toBe('onboardingForms.rules.cannotSave');
    });

    it('names every problem of a broken form in the message it shows', () => {
      const c = selected();
      c.editDraft(version(5, 'DRAFT', [step('a', [{ ...f('licence'), pattern: '[' }]), step('b', [f('licence')])]));
      const translate = (c as unknown as { translate: { instant: (k: string, p?: unknown) => string } }).translate;
      const spy = vi.spyOn(translate, 'instant');
      c.saveDraft();
      expect(spy).toHaveBeenCalledWith('onboardingForms.rules.patternInvalid', { field: 'licence', max: 500 });
      expect(spy).toHaveBeenCalledWith('onboardingForms.rules.keyDuplicate', { field: 'licence', max: 500 });
      expect(api.createDraft).not.toHaveBeenCalled();
      expect(api.updateDraft).not.toHaveBeenCalled();
    });

    it('does not publish, or restore, a stored version that is broken, and does not even ask', () => {
      const c = selected();
      const broken = version(7, 'DRAFT', [step('a', [{ ...f('x'), minValue: 9, maxValue: 1, type: 'NUMBER' }])]);
      c.publish(broken);
      c.republish({ ...broken, status: 'ARCHIVED' });
      expect(appDialog.confirm).not.toHaveBeenCalled();
      expect(api.publish).not.toHaveBeenCalled();
      expect(api.republish).not.toHaveBeenCalled();
      expect(notify.error).toHaveBeenCalledTimes(2);
    });

    it('does not save without a type or while saving', () => {
      make().saveDraft();
      const c = selected();
      c.isSavingDraft = true;
      c.saveDraft();
      expect(api.createDraft).not.toHaveBeenCalled();
    });

    it('publishes a draft after confirmation and reloads', () => {
      const c = selected();
      c.publish(c.schemaVersions[0]);
      expect(appDialog.confirm).toHaveBeenCalledWith(expect.objectContaining({ title: 'onboardingForms.dialogs.publish.title' }));
      expect(api.publish).toHaveBeenCalledWith('CUSTOMER', 3);
      expect(notify.success).toHaveBeenCalledWith('onboardingForms.toasts.published');
      expect(c.isPublishing).toBe(false);
    });

    it('does not publish when declined, busy or without a type; reports failures', () => {
      const c = selected();
      appDialog.confirm.mockImplementation(() => of(false));
      c.publish(c.schemaVersions[0]);
      expect(api.publish).not.toHaveBeenCalled();
      appDialog.confirm.mockImplementation(() => of(true));
      api.publish.mockReturnValueOnce(throwError(() => ({})));
      c.publish(c.schemaVersions[0]);
      expect(notify.error).toHaveBeenCalledWith('onboardingForms.toasts.publishFailed');
      expect(c.isPublishing).toBe(false);
      c.isPublishing = true;
      c.publish(c.schemaVersions[0]);
      c.republish(c.schemaVersions[2]);
      expect(api.publish).toHaveBeenCalledTimes(1);
      make().publish(version(1, 'DRAFT'));
      expect(api.publish).toHaveBeenCalledTimes(1);
    });

    it('restores an archived version after confirmation', () => {
      const c = selected();
      c.republish(c.schemaVersions[2]);
      expect(api.republish).toHaveBeenCalledWith('CUSTOMER', 1);
      expect(notify.success).toHaveBeenCalledWith('onboardingForms.toasts.restored');
    });

    it('does not restore when declined, and reports a failure', () => {
      const c = selected();
      appDialog.confirm.mockImplementation(() => of(false));
      c.republish(c.schemaVersions[2]);
      expect(api.republish).not.toHaveBeenCalled();
      appDialog.confirm.mockImplementation(() => of(true));
      api.republish.mockReturnValueOnce(throwError(() => ({})));
      c.republish(c.schemaVersions[2]);
      expect(notify.error).toHaveBeenCalledWith('onboardingForms.toasts.restoreFailed');
    });
  });

  it('formats dates in the effective time zone, and shows a dash for none', () => {
    const c = make();
    expect(c.formatDate(undefined)).toBe('-');
    expect(c.formatDate('2026-03-05T20:00:00Z')).toContain('2026');
  });

  describe('privileges', () => {
    const texts = (el: HTMLElement) => Array.from(el.querySelectorAll('button')).map((b) => b.textContent?.trim() ?? '');
    const has = (el: HTMLElement, key: string) => texts(el).some((t) => t.includes(key));
    function withVersions(): ComponentFixture<OnboardingForms> {
      const fixture = create();
      fixture.componentInstance.selectType(fixture.componentInstance.types[0]);
      fixture.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      return fixture;
    }

    it('a full administrator sees every action', () => {
      const el: HTMLElement = withVersions().nativeElement;
      for (const key of ['onboardingForms.newType.button', 'onboardingForms.versions.newDraft', 'onboardingForms.versions.editDraft', 'onboardingForms.versions.publish', 'onboardingForms.versions.restore', 'onboardingForms.versions.preview']) {
        expect(has(el, key)).toBe(true);
      }
    });

    it('a viewer can only preview versions', () => {
      granted = new Set(['ONBOARDING_ADMIN_TYPE_LIST']);
      const el: HTMLElement = withVersions().nativeElement;
      expect(el.querySelectorAll('.type-row').length).toBe(3);
      expect(has(el, 'onboardingForms.versions.preview')).toBe(true);
      for (const key of ['onboardingForms.newType.button', 'onboardingForms.versions.newDraft', 'onboardingForms.versions.editDraft', 'onboardingForms.versions.publish', 'onboardingForms.versions.restore']) {
        expect(has(el, key)).toBe(false);
      }
    });

    it('each privilege unlocks its own button', () => {
      granted = new Set(['ONBOARDING_ADMIN_SCHEMA_PUBLISH']);
      let el: HTMLElement = withVersions().nativeElement;
      expect(has(el, 'onboardingForms.versions.publish')).toBe(true);
      expect(has(el, 'onboardingForms.versions.editDraft')).toBe(false);
      granted = new Set(['ONBOARDING_ADMIN_SCHEMA_UPDATE']);
      el = withVersions().nativeElement;
      expect(has(el, 'onboardingForms.versions.editDraft')).toBe(true);
      expect(has(el, 'onboardingForms.versions.publish')).toBe(false);
      granted = new Set(['ONBOARDING_ADMIN_SCHEMA_CREATE']);
      el = withVersions().nativeElement;
      expect(has(el, 'onboardingForms.versions.newDraft')).toBe(true);
      granted = new Set(['ONBOARDING_ADMIN_TYPE_CREATE']);
      el = withVersions().nativeElement;
      expect(has(el, 'onboardingForms.newType.button')).toBe(true);
    });

    it('the builder shows Save draft only with a schema write privilege', () => {
      granted = new Set(['ONBOARDING_ADMIN_SCHEMA_CREATE']);
      let fixture = withVersions();
      fixture.componentInstance.newDraft();
      fixture.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      expect(has(fixture.nativeElement, 'onboardingForms.builder.saveDraft')).toBe(true);
      granted = new Set(['ONBOARDING_ADMIN_SCHEMA_PUBLISH']);
      fixture = withVersions();
      fixture.componentInstance.newDraft();
      fixture.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      expect(has(fixture.nativeElement, 'onboardingForms.builder.saveDraft')).toBe(false);
    });
  });
});
