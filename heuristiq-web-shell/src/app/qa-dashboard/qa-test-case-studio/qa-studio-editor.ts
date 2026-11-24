import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, ElementRef, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { AbstractControl, FormArray, FormControl, FormGroup, NonNullableFormBuilder } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HqPrivilegeAccessService } from 'hq-shared-lib';
import { Observable, Subject, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap, takeUntil } from 'rxjs/operators';

import { PendingChangesAware } from '../../shared/guards/pending-changes.guard';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { QA_DASHBOARD_PRIVILEGES } from '../qa-dashboard.constants';
import { emptyEndpointQuery, methodClass } from '../qa-endpoint-inventory/qa-endpoint-inventory.helpers';
import { QaEndpointItem } from '../qa-endpoint-inventory/qa-endpoint-inventory.models';
import { QaEndpointService } from '../qa-endpoint-inventory/qa-endpoint-inventory.service';
import {
  QA_ASSERTION_DEFAULT_STATUS,
  QA_ASSERTION_INPUTS,
  QA_ASSERTION_TYPES,
  QA_ASSERTION_TYPE_KEYS,
  QA_STUDIO_AUTHS,
  QA_STUDIO_AUTH_KEYS,
  QA_STUDIO_CONFLICT_STATUS,
  QA_STUDIO_ID_PARAM,
  QA_STUDIO_LIMITS,
  QA_STUDIO_MESSAGE_KEYS,
  QA_STUDIO_METHODS,
  QA_STUDIO_MODULES,
  QA_STUDIO_NEW_SEGMENT,
  QA_STUDIO_PRIVILEGES,
  QA_STUDIO_ROUTES,
  QA_STUDIO_SUGGESTION_COUNT,
  QA_STUDIO_SUGGESTION_DEBOUNCE_MS,
  QA_STUDIO_SUGGESTION_MIN_CHARS,
  QA_STUDIO_SUITES,
} from './qa-studio.constants';
import {
  bodyAllowed,
  caseToForm,
  emptyAssertionRow,
  emptyStudioForm,
  formToInput,
  formatJson,
  studioFormIssues,
} from './qa-studio.helpers';
import { QaAssertionRow, QaAssertionType, QaKeyValueRow, QaStudioCase, QaStudioFormValue, QaStudioIssue, QaStudioMethod } from './qa-studio.models';
import { QaStudioService } from './qa-studio.service';
import {
  issueOf,
  studioAssertionRowValidator,
  studioAssertionsValidator,
  studioBodyValidator,
  studioDescriptionValidator,
  studioKeyValueRowValidator,
  studioKeyValueTableValidator,
  studioNameValidator,
  studioPathValidator,
  studioSubModuleValidator,
} from './qa-studio.validators';

type KeyValueGroup = FormGroup<{ name: FormControl<string>; value: FormControl<string> }>;
type AssertionGroup = FormGroup<{
  type: FormControl<QaAssertionType>;
  path: FormControl<string>;
  name: FormControl<string>;
  expected: FormControl<string>;
}>;

/**
 * Test case studio editor: one page, in sections, for one API test case. Every rule the server enforces is checked here
 * first (path on the platform's API, no forbidden headers, no secrets, a valid JSON body of at most 16 KB, at least one
 * status check), so a case that is saved is a case the runner will accept. Saving sends the version the edit started
 * from; if someone else saved first the page says so and offers to reload. The body is only ever text in a box.
 */
@Component({
  selector: 'app-qa-studio-editor',
  standalone: false,
  templateUrl: './qa-studio-editor.html',
  styleUrls: ['../qa-dashboard.shared.scss', '../qa-endpoint-inventory/qa-endpoint-inventory.scss', './qa-studio.scss'],
})
export class QaStudioEditorPage implements OnInit, OnDestroy, PendingChangesAware {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly translate = inject(TranslateService);
  private readonly access = inject(HqPrivilegeAccessService);
  private readonly studio = inject(QaStudioService);
  private readonly endpoints = inject(QaEndpointService);
  private readonly dialogs = inject(AppDialogService);
  private readonly notify = inject(NotificationService);
  private readonly destroy$ = new Subject<void>();
  private readonly pathInput$ = new Subject<string>();

  readonly routes = QA_STUDIO_ROUTES;
  readonly modules = QA_STUDIO_MODULES;
  readonly suites = QA_STUDIO_SUITES;
  readonly methods = QA_STUDIO_METHODS;
  readonly auths = QA_STUDIO_AUTHS;
  readonly authKeys = QA_STUDIO_AUTH_KEYS;
  readonly assertionTypes = QA_ASSERTION_TYPES;
  readonly assertionTypeKeys = QA_ASSERTION_TYPE_KEYS;
  readonly assertionInputs = QA_ASSERTION_INPUTS;
  readonly limits = QA_STUDIO_LIMITS;

  readonly form = this.fb.group({
    name: this.fb.control('', studioNameValidator),
    description: this.fb.control('', studioDescriptionValidator),
    module: this.fb.control('platform'),
    subModule: this.fb.control('', studioSubModuleValidator),
    suite: this.fb.control<'REGRESSION' | 'SMOKE'>('REGRESSION'),
    enabled: this.fb.control(true),
    auth: this.fb.control<'NONE' | 'QA_ADMIN' | 'CUSTOMER_A' | 'CUSTOMER_B'>('QA_ADMIN'),
    method: this.fb.control<QaStudioMethod>('GET'),
    path: this.fb.control('/api/', studioPathValidator),
    query: this.fb.array<KeyValueGroup>([], studioKeyValueTableValidator('query')),
    headers: this.fb.array<KeyValueGroup>([], studioKeyValueTableValidator('headers')),
    body: this.fb.control('', studioBodyValidator),
    assertions: this.fb.array<AssertionGroup>([], studioAssertionsValidator),
  });

  isNew = true;
  current: QaStudioCase | null = null;
  isLoading = false;
  loadFailed = false;
  isSaving = false;
  /** True after a save was tried, so every field shows what is wrong with it, not only the ones already visited. */
  submitted = false;
  /** True when the server refused the save because someone else changed the case first. */
  conflict = false;
  suggestions: QaEndpointItem[] = [];
  suggestionsOpen = false;

  ngOnInit(): void {
    this.form.controls.method.valueChanges.pipe(takeUntil(this.destroy$)).subscribe(() => this.form.controls.body.updateValueAndValidity());
    this.pathInput$
      .pipe(
        debounceTime(QA_STUDIO_SUGGESTION_DEBOUNCE_MS),
        distinctUntilChanged(),
        switchMap((text) => this.suggest(text)),
        takeUntil(this.destroy$),
      )
      .subscribe((items) => {
        this.suggestions = items;
        this.suggestionsOpen = items.length > 0;
        this.cdr.markForCheck();
      });

    const id = this.route.snapshot.paramMap.get(QA_STUDIO_ID_PARAM);
    this.isNew = !id || id === QA_STUDIO_NEW_SEGMENT;
    if (this.isNew) {
      this.setForm(emptyStudioForm());
      this.applyAccess();
    } else if (this.canRead) {
      this.load(id as string);
    } else {
      this.loadFailed = true;
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ---- access ----

  get canRead(): boolean {
    return this.access.can(QA_STUDIO_PRIVILEGES.read);
  }

  /** True when the user may save what is on the page: create for a new case, update for an existing one. */
  get canWrite(): boolean {
    return this.access.can(this.isNew ? QA_STUDIO_PRIVILEGES.create : QA_STUDIO_PRIVILEGES.update);
  }

  get readOnly(): boolean {
    return !this.canWrite;
  }

  private applyAccess(): void {
    if (this.readOnly) this.form.disable({ emitEvent: false });
    else this.form.enable({ emitEvent: false });
  }

  // ---- loading ----

  private load(id: string): void {
    this.isLoading = true;
    this.loadFailed = false;
    this.studio
      .get(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (c) => {
          this.current = c;
          this.setForm(caseToForm(c));
          this.conflict = false;
          this.isLoading = false;
          this.applyAccess();
          this.cdr.markForCheck();
        },
        error: () => {
          this.loadFailed = true;
          this.isLoading = false;
          this.cdr.markForCheck();
        },
      });
  }

  /** Loads the saved version again, throwing away what was typed (offered after a conflict). */
  reload(): void {
    if (this.current) this.load(this.current.id);
  }

  /** Puts a case in the form, building a row for each query, header and assertion, and marks the page as unchanged. */
  private setForm(value: QaStudioFormValue): void {
    const { query, headers, assertions, ...scalars } = value;
    this.form.patchValue(scalars, { emitEvent: false });
    this.fillRows(this.form.controls.query, query, (row) => this.keyValueGroup('query', row));
    this.fillRows(this.form.controls.headers, headers, (row) => this.keyValueGroup('headers', row));
    this.fillRows(this.form.controls.assertions, assertions, (row) => this.assertionGroup(row));
    this.form.markAsPristine();
    this.form.markAsUntouched();
    this.submitted = false;
    this.form.updateValueAndValidity();
  }

  private fillRows<T extends AbstractControl, R>(array: FormArray<T>, rows: readonly R[], make: (row: R) => T): void {
    array.clear({ emitEvent: false });
    rows.forEach((row) => array.push(make(row), { emitEvent: false }));
  }

  private keyValueGroup(kind: 'query' | 'headers', row: QaKeyValueRow = { name: '', value: '' }): KeyValueGroup {
    return this.fb.group({ name: this.fb.control(row.name), value: this.fb.control(row.value) }, { validators: studioKeyValueRowValidator(kind) });
  }

  private assertionGroup(row: QaAssertionRow = emptyAssertionRow()): AssertionGroup {
    const group = this.fb.group(
      {
        type: this.fb.control<QaAssertionType>(row.type),
        path: this.fb.control(row.path),
        name: this.fb.control(row.name),
        expected: this.fb.control(row.expected),
      },
      { validators: studioAssertionRowValidator },
    );
    // A new type needs other inputs: clear the old values when the user picks one.
    group.controls.type.valueChanges.pipe(takeUntil(this.destroy$)).subscribe(() => this.onAssertionTypeChange(group));
    return group;
  }

  // ---- rows ----

  addQuery(): void {
    this.addRow(this.form.controls.query, this.keyValueGroup('query'), this.limits.queryMax);
  }

  addHeader(): void {
    this.addRow(this.form.controls.headers, this.keyValueGroup('headers'), this.limits.headersMax);
  }

  addAssertion(): void {
    this.addRow(this.form.controls.assertions, this.assertionGroup(), this.limits.assertionsMax);
  }

  removeQuery(index: number): void {
    this.removeRow(this.form.controls.query, index);
  }

  removeHeader(index: number): void {
    this.removeRow(this.form.controls.headers, index);
  }

  removeAssertion(index: number): void {
    this.removeRow(this.form.controls.assertions, index);
  }

  private addRow<T extends AbstractControl>(array: FormArray<T>, row: T, max: number): void {
    if (this.readOnly || array.length >= max) return;
    array.push(row);
    array.markAsDirty();
  }

  private removeRow<T extends AbstractControl>(array: FormArray<T>, index: number): void {
    if (this.readOnly || index < 0 || index >= array.length) return;
    array.removeAt(index);
    array.markAsDirty();
  }

  /** A new type needs different inputs, so the old values are cleared and a status check starts with 200. */
  onAssertionTypeChange(group: AssertionGroup): void {
    const type = group.controls.type.value;
    group.patchValue({ path: '', name: '', expected: type === 'STATUS' ? QA_ASSERTION_DEFAULT_STATUS : '' });
    group.markAsDirty();
  }

  // ---- body ----

  get showBody(): boolean {
    return bodyAllowed(this.form.controls.method.value);
  }

  /** Lays the body out with indentation; a body that is not valid JSON is left as it is (the box says why). */
  formatBody(): void {
    const formatted = formatJson(this.form.controls.body.value);
    if (formatted === null || this.readOnly) return;
    this.form.controls.body.setValue(formatted);
    this.form.controls.body.markAsDirty();
  }

  get bodyBytes(): number {
    return new TextEncoder().encode(this.form.controls.body.value).length;
  }

  // ---- path suggestions ----

  onPathInput(): void {
    this.pathInput$.next(this.form.controls.path.value.trim());
  }

  private suggest(text: string): Observable<QaEndpointItem[]> {
    if (this.readOnly || text.length < QA_STUDIO_SUGGESTION_MIN_CHARS || !this.access.can(QA_DASHBOARD_PRIVILEGES.endpointList)) return of([]);
    return this.endpoints.list(emptyEndpointQuery({ q: text }), 0, QA_STUDIO_SUGGESTION_COUNT).pipe(
      map((page) => page.items),
      catchError(() => of([])),
    );
  }

  /** Takes an endpoint of the inventory: its path and method go in the request, its module and sub-module in the basics. */
  chooseSuggestion(endpoint: QaEndpointItem): void {
    const patch: Partial<{ path: string; method: QaStudioMethod; module: string; subModule: string }> = { path: endpoint.path };
    const method = endpoint.method.toUpperCase() as QaStudioMethod;
    if (QA_STUDIO_METHODS.includes(method)) patch.method = method;
    const module = endpoint.module.toLowerCase();
    if (QA_STUDIO_MODULES.includes(module)) patch.module = module;
    if (endpoint.subModule) patch.subModule = endpoint.subModule.slice(0, this.limits.subModuleMax);
    this.form.patchValue(patch);
    this.form.markAsDirty();
    this.closeSuggestions();
  }

  closeSuggestions(): void {
    this.suggestionsOpen = false;
    this.suggestions = [];
  }

  /** Arrow keys move through the suggestions, Escape closes the list and goes back to the path. */
  onSuggestionKey(event: KeyboardEvent, index: number): void {
    const buttons = Array.from(this.host.nativeElement.querySelectorAll<HTMLElement>('.qa-suggestion'));
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      buttons[Math.min(index + 1, buttons.length - 1)]?.focus();
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      if (index === 0) this.focusPath();
      else buttons[index - 1]?.focus();
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.closeSuggestions();
      this.focusPath();
    }
  }

  /** ArrowDown in the path box goes to the first suggestion; Escape closes the list. */
  onPathKey(event: KeyboardEvent): void {
    if (event.key === 'ArrowDown' && this.suggestionsOpen) {
      event.preventDefault();
      this.host.nativeElement.querySelector<HTMLElement>('.qa-suggestion')?.focus();
    } else if (event.key === 'Escape' && this.suggestionsOpen) {
      event.preventDefault();
      this.closeSuggestions();
    }
  }

  private focusPath(): void {
    this.host.nativeElement.querySelector<HTMLElement>('#studio-path')?.focus();
  }

  methodClass(method: string): string {
    return methodClass(method);
  }

  // ---- errors ----

  /** The message under a control, shown once the control was visited or a save was tried. */
  errorOf(control: AbstractControl | null): string | null {
    const found = issueOf(control);
    if (!found || !(control?.touched || this.submitted)) return null;
    return this.text(found);
  }

  /** The message of a row's own rule (a row group carries it). */
  rowError(group: AbstractControl): string | null {
    return this.errorOf(group);
  }

  private text(found: QaStudioIssue): string {
    return this.translate.instant(found.key, found.params);
  }

  /** How many rules the form breaks, for the line above the buttons after a failed save. */
  get issueCount(): number {
    return studioFormIssues(this.form.getRawValue()).length;
  }

  trackByIndex(index: number): number {
    return index;
  }

  // ---- saving ----

  save(): void {
    if (this.readOnly || this.isSaving) return;
    this.submitted = true;
    this.form.markAllAsTouched();
    this.form.updateValueAndValidity();
    if (this.form.invalid) {
      this.cdr.markForCheck();
      this.focusFirstInvalid();
      return;
    }
    const version = this.isNew ? undefined : this.current?.version;
    const input = formToInput(this.form.getRawValue(), version);
    this.isSaving = true;
    this.conflict = false;
    const call = this.isNew || !this.current ? this.studio.create(input) : this.studio.update(this.current.id, input);
    call.pipe(takeUntil(this.destroy$)).subscribe({
      next: (saved) => {
        const wasNew = this.isNew;
        this.current = saved;
        this.isNew = false;
        this.isSaving = false;
        this.setForm(caseToForm(saved));
        this.applyAccess();
        this.notify.success(this.translate.instant(QA_STUDIO_MESSAGE_KEYS.saved, { tcId: saved.tcId }));
        // A new case now has its own address; the page is unchanged, so leaving the old one is not a loss.
        if (wasNew) void this.router.navigate([QA_STUDIO_ROUTES.list, saved.id], { replaceUrl: true });
        this.cdr.markForCheck();
      },
      error: (error: unknown) => {
        this.isSaving = false;
        if ((error as HttpErrorResponse | undefined)?.status === QA_STUDIO_CONFLICT_STATUS) {
          this.conflict = true;
        } else {
          this.notify.error(this.translate.instant(QA_STUDIO_MESSAGE_KEYS.saveFailed));
        }
        this.cdr.markForCheck();
      },
    });
  }

  private focusFirstInvalid(): void {
    const invalid = this.host.nativeElement.querySelector<HTMLElement>('[aria-invalid="true"]');
    invalid?.focus();
  }

  // ---- leaving ----

  goBack(): void {
    void this.router.navigate([QA_STUDIO_ROUTES.list]);
  }

  hasPendingChanges(): boolean {
    return !this.readOnly && this.form.dirty;
  }

  confirmDiscardChanges(): Observable<boolean> {
    return this.dialogs.confirm({
      title: this.translate.instant(QA_STUDIO_MESSAGE_KEYS.discardTitle),
      message: this.translate.instant(QA_STUDIO_MESSAGE_KEYS.discardMessage),
      confirmText: this.translate.instant(QA_STUDIO_MESSAGE_KEYS.discardConfirm),
      variant: 'warning',
    });
  }

  /** Closing the tab or reloading with unsaved changes asks the browser to confirm. */
  @HostListener('window:beforeunload', ['$event'])
  onBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.hasPendingChanges()) event.preventDefault();
  }
}
