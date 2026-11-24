import { HttpErrorResponse } from '@angular/common/http';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { NEVER, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { QA_DASHBOARD_PRIVILEGES } from '../qa-dashboard.constants';
import { accessMock, endpoint, endpointPage, routerStub, routeStub, studioCase } from '../qa-dashboard.fixtures';
import { QaEndpointPage, QaEndpointQuery } from '../qa-endpoint-inventory/qa-endpoint-inventory.models';
import { QaEndpointService } from '../qa-endpoint-inventory/qa-endpoint-inventory.service';
import { QA_STUDIO_ISSUE_KEYS, QA_STUDIO_MESSAGE_KEYS, QA_STUDIO_PRIVILEGES as P, QA_STUDIO_ROUTES, QA_STUDIO_SUGGESTION_DEBOUNCE_MS } from './qa-studio.constants';
import { QaStudioCase, QaStudioInput } from './qa-studio.models';
import { QaStudioEditorPage } from './qa-studio-editor';
import { QaStudioService } from './qa-studio.service';

const ALL = new Set<string>([P.list, P.read, P.create, P.update, P.enable, P.delete, QA_DASHBOARD_PRIVILEGES.endpointList]);
const without = (...codes: string[]): Set<string> => new Set([...ALL].filter((c) => !codes.includes(c)));

describe('QaStudioEditorPage', () => {
  const studio = {
    get: vi.fn((_id: string): Observable<QaStudioCase> => of(studioCase('a'))),
    create: vi.fn((_i: QaStudioInput): Observable<QaStudioCase> => of(studioCase('new', { tcId: 'HQ_ST_010', version: 1 }))),
    update: vi.fn((_id: string, _i: QaStudioInput): Observable<QaStudioCase> => of(studioCase('a', { version: 4, name: 'Renamed' }))),
  };
  const endpoints = { list: vi.fn((_q: QaEndpointQuery, _p: number, _s: number): Observable<QaEndpointPage> => of(endpointPage([endpoint('e1'), endpoint('e2', { method: 'TRACE', module: 'zzz', subModule: '' })]))) };
  const dialogs = { confirm: vi.fn((_d: unknown): Observable<boolean> => of(true)) };
  const notify = { success: vi.fn(), error: vi.fn() };
  const router = routerStub();
  let granted: Set<string> | null;
  let fixture: ComponentFixture<QaStudioEditorPage>;
  let page: QaStudioEditorPage;

  /** Opens the editor on a new case (no id) or on the case with this id. */
  function open(id?: string): QaStudioEditorPage {
    const route = routeStub({}, {}, id ? { id } : {});
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [QaStudioEditorPage],
      imports: [ReactiveFormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: QaStudioService, useValue: studio },
        { provide: QaEndpointService, useValue: endpoints },
        { provide: HqPrivilegeAccessService, useValue: accessMock(granted) },
        { provide: ActivatedRoute, useValue: route.route },
        { provide: Router, useValue: router },
        { provide: AppDialogService, useValue: dialogs },
        { provide: NotificationService, useValue: notify },
      ],
    });
    fixture = TestBed.createComponent(QaStudioEditorPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
    return page;
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const q = (selector: string): HTMLElement | null => el().querySelector(selector);
  const all = (selector: string): HTMLElement[] => Array.from(el().querySelectorAll<HTMLElement>(selector));
  const refresh = (): void => {
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
  };
  const click = (selector: string): void => {
    (q(selector) as HTMLElement).click();
    refresh();
  };
  const fillValid = (): void => {
    page.form.controls.name.setValue('Block user rejects a missing token');
    page.form.controls.path.setValue('/api/v1/uam/users/42/block');
  };

  beforeEach(() => {
    vi.clearAllMocks();
    studio.get.mockImplementation(() => of(studioCase('a')));
    studio.create.mockImplementation(() => of(studioCase('new', { tcId: 'HQ_ST_010', version: 1 })));
    studio.update.mockImplementation(() => of(studioCase('a', { version: 4, name: 'Renamed' })));
    endpoints.list.mockImplementation(() => of(endpointPage([endpoint('e1'), endpoint('e2', { method: 'TRACE', module: 'zzz', subModule: '' })])));
    dialogs.confirm.mockImplementation(() => of(true));
    granted = new Set(ALL);
  });

  describe('a new case', () => {
    it('starts with a path on the API, one 200 status check and nothing loaded', () => {
      open();
      expect(page.isNew).toBe(true);
      expect(studio.get).not.toHaveBeenCalled();
      expect(page.form.controls.path.value).toBe('/api/');
      expect(page.form.controls.assertions.length).toBe(1);
      expect(page.form.controls.assertions.at(0).controls.expected.value).toBe('200');
      expect(page.readOnly).toBe(false);
      expect(q('.qa-save')).not.toBeNull();
      expect(page.hasPendingChanges()).toBe(false);
    });

    it('is read-only for a user who cannot create', () => {
      granted = without(P.create);
      open();
      expect(page.readOnly).toBe(true);
      expect(page.form.disabled).toBe(true);
      expect(q('.qa-save')).toBeNull();
      expect(q('.qa-read-only')).not.toBeNull();
    });
  });

  describe('an existing case', () => {
    it('loads it by id and fills the whole form', () => {
      open('a');
      expect(studio.get).toHaveBeenCalledWith('a');
      expect(page.isNew).toBe(false);
      const v = page.form.getRawValue();
      expect(v).toMatchObject({ name: 'Block user rejects a missing token', module: 'uam', method: 'POST', body: '{"reason":"test"}' });
      expect(v.query).toEqual([{ name: 'dryRun', value: 'true' }]);
      expect(v.headers).toEqual([{ name: 'X-Trace', value: 'abc' }]);
      expect(v.assertions.map((a) => a.type)).toEqual(['STATUS', 'JSON_PATH_EQUALS', 'MAX_TIME_MS']);
      expect(all('.qa-assertion')).toHaveLength(3);
      expect(all('.qa-kv')).toHaveLength(2);
      expect(page.hasPendingChanges()).toBe(false);
      expect(page.form.valid).toBe(true);
    });

    it('loads nothing and says so for a user without the read privilege', () => {
      granted = without(P.read);
      open('a');
      expect(studio.get).not.toHaveBeenCalled();
      expect(page.loadFailed).toBe(true);
      expect(q('.qa-load-failed')).not.toBeNull();
      expect(q('form')).toBeNull();
    });

    it('says so when the case cannot be loaded, and shows a loading message while waiting', () => {
      studio.get.mockImplementation(() => throwError(() => new Error('down')));
      open('a');
      expect(page.loadFailed).toBe(true);
      expect(page.isLoading).toBe(false);
      studio.get.mockImplementation(() => NEVER);
      open('a');
      expect(page.isLoading).toBe(true);
      expect(q('.qa-state')?.textContent).toContain('qaDashboard.loading');
      expect(q('form')).toBeNull();
    });

    it('is read-only, with no add, remove, format or save action, for a user who cannot update', () => {
      granted = without(P.update);
      open('a');
      expect(page.readOnly).toBe(true);
      expect(page.form.disabled).toBe(true);
      ['.qa-save', '.qa-add-query', '.qa-add-header', '.qa-add-assertion', '.qa-remove-query', '.qa-remove-header', '.qa-remove-assertion', '.qa-format'].forEach((s) =>
        expect(q(s), s).toBeNull(),
      );
      expect(q('.qa-read-only')).not.toBeNull();
      page.form.controls.name.setValue('changed');
      expect(page.hasPendingChanges()).toBe(false);
      page.save();
      expect(studio.update).not.toHaveBeenCalled();
    });

    it('never turns the body into HTML: it stays text in the box', () => {
      studio.get.mockImplementation(() => of(studioCase('a', { body: '{"a":"<img src=x onerror=alert(1)>"}' })));
      open('a');
      const box = q('#studio-body') as HTMLTextAreaElement;
      expect(box.value).toContain('<img src=x onerror=alert(1)>');
      expect(el().querySelector('img')).toBeNull();
    });
  });

  describe('rows', () => {
    it('adds and removes query parameters, headers and checks, and marks the page as changed', () => {
      open();
      click('.qa-add-query');
      click('.qa-add-header');
      click('.qa-add-assertion');
      expect(page.form.controls.query.length).toBe(1);
      expect(page.form.controls.headers.length).toBe(1);
      expect(page.form.controls.assertions.length).toBe(2);
      expect(page.hasPendingChanges()).toBe(true);
      click('.qa-remove-query');
      click('.qa-remove-header');
      click('.qa-remove-assertion');
      expect(page.form.controls.query.length).toBe(0);
      expect(page.form.controls.headers.length).toBe(0);
      expect(page.form.controls.assertions.length).toBe(1);
    });

    it('stops at the limits and ignores a removal that is out of range', () => {
      open();
      for (let i = 0; i < 25; i++) page.addQuery();
      for (let i = 0; i < 15; i++) page.addHeader();
      for (let i = 0; i < 25; i++) page.addAssertion();
      expect(page.form.controls.query.length).toBe(20);
      expect(page.form.controls.headers.length).toBe(10);
      expect(page.form.controls.assertions.length).toBe(20);
      refresh();
      expect((q('.qa-add-query') as HTMLButtonElement).disabled).toBe(true);
      page.removeQuery(99);
      page.removeHeader(-1);
      page.removeAssertion(50);
      expect(page.form.controls.query.length).toBe(20);
    });

    it('shows only the inputs a check type needs and clears the old values when the type changes', () => {
      open();
      const row = page.form.controls.assertions.at(0);
      expect(all('.qa-assertion__expected')).toHaveLength(1);
      expect(all('.qa-assertion__path')).toHaveLength(0);
      row.controls.type.setValue('JSON_PATH_EQUALS');
      refresh();
      expect(row.getRawValue()).toEqual({ type: 'JSON_PATH_EQUALS', path: '', name: '', expected: '' });
      expect(all('.qa-assertion__path')).toHaveLength(1);
      expect(all('.qa-assertion__expected')).toHaveLength(1);
      row.controls.type.setValue('JSON_PATH_EXISTS');
      refresh();
      expect(all('.qa-assertion__expected')).toHaveLength(0);
      row.controls.type.setValue('HEADER_CONTAINS');
      refresh();
      expect(all('.qa-assertion__name')).toHaveLength(1);
      expect(all('.qa-assertion__path')).toHaveLength(0);
      row.controls.type.setValue('STATUS');
      expect(row.controls.expected.value).toBe('200');
      row.controls.type.setValue('MAX_TIME_MS');
      expect(row.controls.expected.value).toBe('');
      expect(page.hasPendingChanges()).toBe(true);
    });
  });

  describe('body', () => {
    it('is shown only for POST, PUT and PATCH, and checked against the method', () => {
      open();
      expect(q('.qa-body')).toBeNull();
      page.form.controls.method.setValue('POST');
      refresh();
      expect(q('.qa-body')).not.toBeNull();
      page.form.controls.body.setValue('{"a":1}');
      expect(page.form.controls.body.valid).toBe(true);
      page.form.controls.method.setValue('GET');
      expect(page.form.controls.body.errors).toHaveProperty('studioIssue');
      refresh();
      expect(q('.qa-body')).toBeNull();
    });

    it('lays out valid JSON on request and leaves invalid text alone', () => {
      open();
      page.form.controls.method.setValue('POST');
      page.form.controls.body.setValue('{"a":[1,2]}');
      refresh();
      click('.qa-format');
      expect(page.form.controls.body.value).toBe('{\n  "a": [\n    1,\n    2\n  ]\n}');
      expect(page.form.controls.body.dirty).toBe(true);
      page.form.controls.body.setValue('{a');
      page.formatBody();
      expect(page.form.controls.body.value).toBe('{a');
    });

    it('shows the size in bytes and a message for invalid JSON once visited', () => {
      open();
      page.form.controls.method.setValue('POST');
      page.form.controls.body.setValue('{"€":1}');
      refresh();
      expect(page.bodyBytes).toBe(9);
      expect(q('.qa-body__counter')?.textContent).toContain('qaDashboard.studio.editor.bodyCounter');
      page.form.controls.body.setValue('{a');
      page.form.controls.body.markAsTouched();
      refresh();
      expect(q('#studio-body-error')?.textContent).toContain(QA_STUDIO_ISSUE_KEYS.bodyJson);
      expect(q('#studio-body')?.getAttribute('aria-invalid')).toBe('true');
    });
  });

  describe('validation', () => {
    it('does not save an incomplete case, shows what is wrong everywhere and says how many things need attention', () => {
      open();
      page.form.controls.assertions.clear();
      page.save();
      refresh();
      expect(studio.create).not.toHaveBeenCalled();
      expect(page.submitted).toBe(true);
      expect(q('#studio-name-error')?.textContent).toContain(QA_STUDIO_ISSUE_KEYS.required);
      // The starting path (/api/) is allowed, so only the name and the missing status check are wrong.
      expect(q('#studio-path-error')).toBeNull();
      expect(q('.qa-assertions-error')?.textContent).toContain(QA_STUDIO_ISSUE_KEYS.statusRequired);
      expect(q('.qa-summary')).not.toBeNull();
      expect(page.issueCount).toBe(2);
    });

    it('does not show an error on a field before it was visited or a save was tried', () => {
      open();
      expect(q('#studio-name-error')).toBeNull();
      page.form.controls.name.markAsTouched();
      refresh();
      expect(q('#studio-name-error')).not.toBeNull();
      expect(page.errorOf(null)).toBeNull();
    });

    it('explains a row that breaks a rule: a forbidden header, a secret value, a bad parameter name', () => {
      open();
      fillValid();
      page.addHeader();
      page.form.controls.headers.at(0).patchValue({ name: 'Authorization', value: 'x' });
      page.addQuery();
      page.form.controls.query.at(0).patchValue({ name: 'token', value: 'abc' });
      page.save();
      refresh();
      const errors = all('.qa-kv__error').map((e) => e.textContent);
      expect(errors).toHaveLength(2);
      expect(errors.join(' ')).toContain(QA_STUDIO_ISSUE_KEYS.headerForbidden);
      expect(errors.join(' ')).toContain(QA_STUDIO_ISSUE_KEYS.secret);
      expect(studio.create).not.toHaveBeenCalled();
      expect(page.rowError(page.form.controls.headers.at(0))).toContain(QA_STUDIO_ISSUE_KEYS.headerForbidden);
    });

    it('refuses a table with a header twice and shows the message under the table', () => {
      open();
      fillValid();
      page.addHeader();
      page.addHeader();
      page.form.controls.headers.at(0).patchValue({ name: 'X-A', value: '1' });
      page.form.controls.headers.at(1).patchValue({ name: 'x-a', value: '2' });
      page.save();
      refresh();
      expect(page.form.controls.headers.invalid).toBe(true);
      expect(el().textContent).toContain(QA_STUDIO_ISSUE_KEYS.duplicate);
    });

    it('explains an assertion row that is not complete', () => {
      open();
      fillValid();
      page.form.controls.assertions.at(0).controls.expected.setValue('99');
      page.save();
      refresh();
      expect(q('.qa-assertion__error')?.textContent).toContain(QA_STUDIO_ISSUE_KEYS.statusCodes);
      expect(q('.qa-assertion__expected')?.getAttribute('aria-invalid')).toBe('true');
    });

    it('moves the focus to the first field that is wrong', () => {
      open();
      page.save();
      refresh();
      const focus = vi.spyOn(HTMLElement.prototype, 'focus').mockImplementation(() => undefined);
      page.save();
      expect(focus).toHaveBeenCalled();
      focus.mockRestore();
    });
  });

  describe('saving', () => {
    it('creates a new case with its version left out, then opens it at its own address with the page unchanged', () => {
      open();
      fillValid();
      page.form.controls.module.setValue('uam');
      click('.qa-save');
      const sent = studio.create.mock.calls[0][0];
      expect(sent).toMatchObject({ name: 'Block user rejects a missing token', path: '/api/v1/uam/users/42/block', module: 'uam', suite: 'REGRESSION', enabled: true, body: null });
      expect('version' in sent).toBe(false);
      expect(sent.assertions).toEqual([{ type: 'STATUS', expected: [200] }]);
      expect(notify.success).toHaveBeenCalledWith(QA_STUDIO_MESSAGE_KEYS.saved);
      expect(router.navigate).toHaveBeenCalledWith([QA_STUDIO_ROUTES.list, 'new'], { replaceUrl: true });
      expect(page.isNew).toBe(false);
      expect(page.current?.tcId).toBe('HQ_ST_010');
      expect(page.hasPendingChanges()).toBe(false);
      expect(page.isSaving).toBe(false);
    });

    it('replaces an existing case with the version it was loaded at, and shows what the server saved', () => {
      open('a');
      page.form.controls.description.setValue('Changed');
      page.form.controls.description.markAsDirty();
      expect(page.hasPendingChanges()).toBe(true);
      click('.qa-save');
      expect(studio.update).toHaveBeenCalledWith('a', expect.objectContaining({ description: 'Changed', version: 3 }));
      expect(studio.create).not.toHaveBeenCalled();
      expect(router.navigate).not.toHaveBeenCalled();
      expect(page.form.controls.name.value).toBe('Renamed');
      expect(page.current?.version).toBe(4);
      expect(page.hasPendingChanges()).toBe(false);
    });

    it('sends a body only with a method that carries one, and drops empty rows', () => {
      open();
      fillValid();
      page.form.controls.method.setValue('POST');
      page.form.controls.body.setValue('{"a":1}');
      page.addQuery();
      page.save();
      expect(studio.create.mock.calls[0][0]).toMatchObject({ method: 'POST', body: '{"a":1}', query: {} });
    });

    it('does not save twice at once and does not save while saving', () => {
      studio.create.mockImplementation(() => NEVER);
      open();
      fillValid();
      page.save();
      page.save();
      expect(studio.create).toHaveBeenCalledTimes(1);
      expect(page.isSaving).toBe(true);
      refresh();
      expect((q('.qa-save') as HTMLButtonElement).disabled).toBe(true);
    });

    it('says clearly when someone else saved first, and offers to reload their version', () => {
      studio.update.mockImplementation(() => throwError(() => new HttpErrorResponse({ status: 409 })));
      open('a');
      page.form.controls.description.setValue('Mine');
      click('.qa-save');
      expect(page.conflict).toBe(true);
      expect(q('.qa-conflict')?.textContent).toContain('qaDashboard.studio.editor.conflict');
      expect(notify.error).not.toHaveBeenCalled();
      studio.get.mockImplementation(() => of(studioCase('a', { description: 'Theirs', version: 5 })));
      click('.qa-reload');
      expect(page.conflict).toBe(false);
      expect(page.form.controls.description.value).toBe('Theirs');
      expect(page.current?.version).toBe(5);
      expect(page.hasPendingChanges()).toBe(false);
    });

    it('reports any other failure and keeps what was typed', () => {
      studio.create.mockImplementation(() => throwError(() => new HttpErrorResponse({ status: 500 })));
      open();
      fillValid();
      page.save();
      expect(notify.error).toHaveBeenCalledWith(QA_STUDIO_MESSAGE_KEYS.saveFailed);
      expect(page.conflict).toBe(false);
      expect(page.form.controls.name.value).toBe('Block user rejects a missing token');
      expect(page.isSaving).toBe(false);
    });

    it('does nothing when asked to reload a case that was never saved', () => {
      open();
      page.reload();
      expect(studio.get).not.toHaveBeenCalled();
    });
  });

  describe('leaving', () => {
    it('lets the user go when nothing changed, and asks first when something did', () => {
      open('a');
      expect(page.hasPendingChanges()).toBe(false);
      page.form.controls.name.setValue('Another name');
      page.form.controls.name.markAsDirty();
      expect(page.hasPendingChanges()).toBe(true);
      let answer: boolean | undefined;
      page.confirmDiscardChanges().subscribe((a) => (answer = a));
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ title: QA_STUDIO_MESSAGE_KEYS.discardTitle, variant: 'warning' }));
      expect(answer).toBe(true);
    });

    it('asks the browser to confirm when the tab is closed with unsaved changes', () => {
      open();
      const clean = new Event('beforeunload', { cancelable: true });
      page.onBeforeUnload(clean as BeforeUnloadEvent);
      expect(clean.defaultPrevented).toBe(false);
      page.form.controls.name.markAsDirty();
      const dirty = new Event('beforeunload', { cancelable: true });
      page.onBeforeUnload(dirty as BeforeUnloadEvent);
      expect(dirty.defaultPrevented).toBe(true);
    });

    it('goes back to the list', () => {
      open();
      page.goBack();
      expect(router.navigate).toHaveBeenCalledWith([QA_STUDIO_ROUTES.list]);
    });
  });

  describe('path suggestions', () => {
    function type(text: string): void {
      page.form.controls.path.setValue(text);
      page.onPathInput();
      vi.advanceTimersByTime(QA_STUDIO_SUGGESTION_DEBOUNCE_MS + 10);
      refresh();
    }

    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    it('offers endpoints of the inventory after a pause in typing, with their method, path and module', () => {
      open();
      type('/api/v1/uam');
      expect(endpoints.list).toHaveBeenCalledWith(expect.objectContaining({ q: '/api/v1/uam' }), 0, 8);
      expect(page.suggestionsOpen).toBe(true);
      const options = all('.qa-suggestion');
      expect(options).toHaveLength(2);
      expect(options[0].textContent).toContain('POST');
      expect(options[0].textContent).toContain('/api/v1/uam/users/{userId}/block');
      expect(q('#studio-path')?.getAttribute('aria-expanded')).toBe('true');
    });

    it('fills the path, method, module and sub-module from the chosen endpoint and closes the list', () => {
      open();
      type('/api/v1/uam');
      (all('.qa-suggestion')[0] as HTMLButtonElement).click();
      expect(page.form.controls.path.value).toBe('/api/v1/uam/users/{userId}/block');
      expect(page.form.controls.method.value).toBe('POST');
      expect(page.form.controls.module.value).toBe('uam');
      expect(page.form.controls.subModule.value).toBe('users');
      expect(page.suggestionsOpen).toBe(false);
      expect(page.hasPendingChanges()).toBe(true);
    });

    it('keeps the method and module when the endpoint has ones the studio does not know', () => {
      open();
      page.form.patchValue({ method: 'GET', module: 'wallet', subModule: 'keep' });
      type('/api/v1/zzz');
      (all('.qa-suggestion')[1] as HTMLButtonElement).click();
      expect(page.form.controls.method.value).toBe('GET');
      expect(page.form.controls.module.value).toBe('wallet');
      expect(page.form.controls.subModule.value).toBe('keep');
    });

    it('asks nothing for a short text, without the privilege, when read-only, or when the call fails', () => {
      open();
      type('/');
      expect(endpoints.list).not.toHaveBeenCalled();
      expect(page.suggestionsOpen).toBe(false);

      granted = without(QA_DASHBOARD_PRIVILEGES.endpointList);
      open();
      type('/api/v1/uam');
      expect(endpoints.list).not.toHaveBeenCalled();

      granted = without(P.create);
      open();
      type('/api/v1/uam');
      expect(endpoints.list).not.toHaveBeenCalled();

      granted = new Set(ALL);
      endpoints.list.mockImplementation(() => throwError(() => new Error('down')));
      open();
      type('/api/v1/uam');
      expect(page.suggestionsOpen).toBe(false);
      expect(page.form.controls.path.value).toBe('/api/v1/uam');
    });

    it('closes the list when there is nothing to suggest', () => {
      endpoints.list.mockImplementation(() => of(endpointPage([])));
      open();
      type('/api/v1/none');
      expect(page.suggestionsOpen).toBe(false);
      expect(q('.qa-suggestions')).toBeNull();
    });

    it('moves through the suggestions with the arrow keys and closes the list with Escape', () => {
      open();
      type('/api/v1/uam');
      const options = all('.qa-suggestion');
      const focus = vi.fn();
      options.forEach((o, i) => (o.focus = () => focus(i)));
      const path = q('#studio-path') as HTMLInputElement;
      const pathFocus = vi.fn();
      path.focus = pathFocus;
      const key = (name: string): KeyboardEvent => new KeyboardEvent('keydown', { key: name, cancelable: true });

      const down = key('ArrowDown');
      page.onPathKey(down);
      expect(down.defaultPrevented).toBe(true);
      expect(focus).toHaveBeenLastCalledWith(0);
      page.onSuggestionKey(key('ArrowDown'), 0);
      expect(focus).toHaveBeenLastCalledWith(1);
      page.onSuggestionKey(key('ArrowDown'), 1);
      expect(focus).toHaveBeenLastCalledWith(1);
      page.onSuggestionKey(key('ArrowUp'), 1);
      expect(focus).toHaveBeenLastCalledWith(0);
      page.onSuggestionKey(key('ArrowUp'), 0);
      expect(pathFocus).toHaveBeenCalledTimes(1);
      page.onSuggestionKey(key('Tab'), 0);
      expect(page.suggestionsOpen).toBe(true);
      page.onSuggestionKey(key('Escape'), 0);
      expect(page.suggestionsOpen).toBe(false);
      expect(pathFocus).toHaveBeenCalledTimes(2);

      type('/api/v1/uam/x');
      expect(page.suggestionsOpen).toBe(true);
      page.onPathKey(key('Escape'));
      expect(page.suggestionsOpen).toBe(false);
      page.onPathKey(key('ArrowDown'));
      page.onPathKey(key('a'));
    });
  });

  it('labels the method badge and tracks rows by position', () => {
    open();
    expect(page.methodClass('PUT')).toBe('qa-method--put');
    expect(page.trackByIndex(3)).toBe(3);
    page.closeSuggestions();
  });
});
