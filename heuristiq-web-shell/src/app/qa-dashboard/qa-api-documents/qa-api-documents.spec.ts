import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { NEVER, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { QA_DASHBOARD_PRIVILEGES } from '../qa-dashboard.constants';
import { accessMock, endpoint, endpointOptions, endpointPage } from '../qa-dashboard.fixtures';
import { QaEndpointFilterOptions, QaEndpointPage, QaEndpointQuery } from '../qa-endpoint-inventory/qa-endpoint-inventory.models';
import { QaEndpointService } from '../qa-endpoint-inventory/qa-endpoint-inventory.service';
import { QA_API_DOC_MAX_BYTES, QA_API_DOC_MESSAGE_KEYS, QA_API_DOC_PRIVILEGES as P } from './qa-api-documents.constants';
import { QaApiDocInfo, QaApiDocInput, QaApiDocument } from './qa-api-documents.models';
import { QaApiDocumentsPage } from './qa-api-documents';
import { QaApiDocumentsService } from './qa-api-documents.service';

const info = (module: string): QaApiDocInfo => ({ name: `api-${module}`, title: `API: ${module}`, updatedAt: '2026-10-26T10:00:00Z', sizeBytes: 20, category: 'API', module });
const full = (module: string, markdown = '# Users\n\nBlocks a user.'): QaApiDocument => ({ ...info(module), markdown });

const ALL = new Set<string>([...Object.values(P), QA_DASHBOARD_PRIVILEGES.endpointList, QA_DASHBOARD_PRIVILEGES.endpointFilterRead]);
const without = (...codes: string[]): Set<string> => new Set([...ALL].filter((c) => !codes.includes(c)));

describe('QaApiDocumentsPage', () => {
  const docs = {
    list: vi.fn((): Observable<QaApiDocInfo[]> => of([info('uam')])),
    get: vi.fn((_n: string): Observable<QaApiDocument> => of(full('uam'))),
    save: vi.fn((_i: QaApiDocInput): Observable<QaApiDocument> => of(full('wallet', '# Wallet'))),
    delete: vi.fn((_n: string): Observable<void> => of(undefined)),
    endpointCounts: vi.fn((_m: readonly string[]): Observable<Map<string, number>> => of(new Map([['uam', 12], ['wallet', 3]]))),
  };
  const endpoints = {
    filters: vi.fn((): Observable<QaEndpointFilterOptions> => of(endpointOptions())),
    list: vi.fn((_q: QaEndpointQuery, _p: number, _s: number): Observable<QaEndpointPage> => of(endpointPage([endpoint('e1'), endpoint('e2', { method: 'GET', hitCount: 0 })], { totalElements: 12 }))),
  };
  const dialogs = { confirm: vi.fn((_d: unknown): Observable<boolean> => of(true)) };
  const notify = { success: vi.fn(), error: vi.fn() };
  let granted: Set<string> | null;
  let fixture: ComponentFixture<QaApiDocumentsPage>;
  let page: QaApiDocumentsPage;

  function create(): QaApiDocumentsPage {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [QaApiDocumentsPage],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: QaApiDocumentsService, useValue: docs },
        { provide: QaEndpointService, useValue: endpoints },
        { provide: HqPrivilegeAccessService, useValue: accessMock(granted) },
        { provide: AppDialogService, useValue: dialogs },
        { provide: NotificationService, useValue: notify },
      ],
    });
    fixture = TestBed.createComponent(QaApiDocumentsPage);
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
  const pick = (module: string): void => {
    const button = all('.qa-module-item').find((b) => b.querySelector('.qa-module-item__name')?.textContent === module) as HTMLButtonElement;
    button.click();
    refresh();
  };

  beforeEach(() => {
    vi.clearAllMocks();
    docs.list.mockImplementation(() => of([info('uam')]));
    docs.get.mockImplementation(() => of(full('uam')));
    docs.save.mockImplementation(() => of(full('wallet', '# Wallet')));
    docs.delete.mockImplementation(() => of(undefined));
    docs.endpointCounts.mockImplementation(() => of(new Map([['uam', 12], ['wallet', 3]])));
    endpoints.filters.mockImplementation(() => of(endpointOptions()));
    endpoints.list.mockImplementation(() => of(endpointPage([endpoint('e1'), endpoint('e2', { method: 'GET', hitCount: 0 })], { totalElements: 12 })));
    dialogs.confirm.mockImplementation(() => of(true));
    granted = new Set(ALL);
  });

  describe('the modules', () => {
    it('lists the modules of the real privilege catalog, which writes them in capitals, and asks the inventory with the catalog name', () => {
      endpoints.filters.mockImplementation(() => of({ ...endpointOptions(), modules: [{ module: 'ADMINISTRATION', subModules: [] }, { module: 'BUSINESS', subModules: [] }] }));
      docs.endpointCounts.mockImplementation(() => of(new Map([['ADMINISTRATION', 5], ['BUSINESS', 9]])));
      create();
      expect(docs.endpointCounts).toHaveBeenCalledWith(['ADMINISTRATION', 'BUSINESS', 'uam']);   // plus the module that has a document
      const items = all('.qa-module-item');
      expect(items.map((i) => i.querySelector('.qa-module-item__name')?.textContent)).toEqual(['administration', 'business', 'uam']);
      expect(items[1].querySelector('.qa-module-item__count')?.textContent).toContain('qaDashboard.apiDocuments.endpointCount');
      (items[1] as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(endpoints.list.mock.calls.at(-1)?.[0]).toMatchObject({ module: 'BUSINESS' });
    });

    it('lists the modules of the inventory and of the documents, each with its endpoint count and whether it has a document', () => {
      create();
      expect(docs.endpointCounts).toHaveBeenCalledWith(['uam', 'wallet']);
      const items = all('.qa-module-item');
      expect(items.map((i) => i.querySelector('.qa-module-item__name')?.textContent)).toEqual(['uam', 'wallet']);
      expect(items[0].querySelector('.qa-module-item__count')?.textContent).toContain('qaDashboard.apiDocuments.endpointCount');
      expect(items[0].querySelector('.qa-module-item__doc')?.textContent).toContain('qaDashboard.apiDocuments.hasDocument');
      expect(items[1].querySelector('.qa-module-item__doc')?.textContent).toContain('qaDashboard.apiDocuments.noDocument');
      expect(q('.qa-pick')).not.toBeNull();
    });

    it('asks for nothing and shows nothing without the list privilege', () => {
      granted = new Set([P.read]);
      create();
      expect(docs.list).not.toHaveBeenCalled();
      expect(q('.qa-docs')).toBeNull();
    });

    it('lists only the modules that have a document when the user may not read the inventory', () => {
      granted = without(QA_DASHBOARD_PRIVILEGES.endpointFilterRead, QA_DASHBOARD_PRIVILEGES.endpointList);
      create();
      expect(endpoints.filters).not.toHaveBeenCalled();
      expect(docs.endpointCounts).not.toHaveBeenCalled();
      expect(all('.qa-module-item')).toHaveLength(1);
      expect(q('.qa-module-item__count')?.textContent).toBe('—');
    });

    it('keeps the modules of the documents when the inventory cannot be read', () => {
      endpoints.filters.mockImplementation(() => throwError(() => new Error('down')));
      create();
      expect(all('.qa-module-item').map((i) => i.querySelector('.qa-module-item__name')?.textContent)).toEqual(['uam']);
    });

    it('says so when there are no modules, when loading fails and while it loads', () => {
      docs.list.mockImplementation(() => of([]));
      endpoints.filters.mockImplementation(() => of({ modules: [], methods: [] }));
      create();
      expect(q('.qa-empty')).not.toBeNull();
      docs.list.mockImplementation(() => throwError(() => new Error('down')));
      create();
      expect(page.modulesFailed).toBe(true);
      expect(q('.qa-state--error')).not.toBeNull();
      docs.list.mockImplementation(() => NEVER);
      create();
      expect(page.isLoadingModules).toBe(true);
      expect(q('.qa-state')?.textContent).toContain('qaDashboard.loading');
    });

    it('goes on without counts when they cannot be read', () => {
      docs.endpointCounts.mockImplementation(() => of(new Map()));
      create();
      expect(q('.qa-module-item__count')?.textContent).toBe('—');
    });
  });

  describe('reading a document', () => {
    it('opens a module: its document as safe markdown, its endpoints and the links to Swagger and Javadoc', () => {
      create();
      pick('uam');
      expect(docs.get).toHaveBeenCalledWith('api-uam');
      expect(q('.qa-document h1')?.textContent).toBe('Users');
      expect(page.current?.module).toBe('uam');
      expect(endpoints.list).toHaveBeenCalledWith(expect.objectContaining({ module: 'uam' }), 0, 50);
      expect(all('.qa-module-endpoint-row')).toHaveLength(2);
      expect(all('.qa-module-endpoint-row')[0].querySelector('.qa-pill--success')).not.toBeNull();
      expect(all('.qa-module-endpoint-row')[1].querySelector('.qa-pill--neutral')).not.toBeNull();
      expect(q('.qa-docs__endpoints .qa-hint')?.textContent).toContain('qaDashboard.apiDocuments.moreEndpoints');
      expect(q('.qa-link-swagger')).not.toBeNull();
      expect(q('.qa-link-javadoc')).not.toBeNull();
      expect(q('.qa-link-inventory')).not.toBeNull();
    });

    it('shows raw HTML in the markdown as text and never as elements', () => {
      docs.get.mockImplementation(() => of(full('uam', '<script>alert(1)</script>\n\n<img src=x onerror=alert(1)>\n\n[bad](javascript:alert(1))')));
      create();
      pick('uam');
      const article = q('.qa-document') as HTMLElement;
      expect(article.querySelector('script')).toBeNull();
      expect(article.querySelector('img')).toBeNull();
      expect(article.textContent).toContain('<script>');
      expect(article.querySelector('a[href^="javascript"]')).toBeNull();
    });

    it('says a module has no document yet, and offers to write one to a user who may', () => {
      create();
      pick('wallet');
      expect(docs.get).not.toHaveBeenCalled();
      expect(q('.qa-no-document')).not.toBeNull();
      expect((q('.qa-edit-doc') as HTMLElement).textContent).toContain('qaDashboard.apiDocuments.write');
      expect(q('.qa-delete-doc')).toBeNull();
    });

    it('says when a document cannot be loaded', () => {
      docs.get.mockImplementation(() => throwError(() => new Error('down')));
      create();
      pick('uam');
      expect(page.documentFailed).toBe(true);
      expect(q('.qa-docs__doc .qa-state--error')).not.toBeNull();
      expect(q('.qa-document')).toBeNull();
    });

    it('does not read the document without the read privilege', () => {
      granted = without(P.read);
      create();
      pick('uam');
      expect(docs.get).not.toHaveBeenCalled();
    });

    it('has no endpoint table without the inventory privilege', () => {
      granted = without(QA_DASHBOARD_PRIVILEGES.endpointList);
      create();
      pick('uam');
      expect(endpoints.list).not.toHaveBeenCalled();
      expect(q('.qa-docs__endpoints')).toBeNull();
    });

    it('says there are no endpoints, and ignores an endpoint list that fails', () => {
      endpoints.list.mockImplementation(() => of(endpointPage([])));
      create();
      pick('uam');
      expect(q('.qa-no-endpoints')).not.toBeNull();
      endpoints.list.mockImplementation(() => throwError(() => new Error('down')));
      pick('wallet');
      expect(page.isLoadingEndpoints).toBe(false);
      expect(page.moduleEndpoints).toEqual([]);
    });

    it('shows nothing of a module that was left before its endpoints came', () => {
      let release: ((p: QaEndpointPage) => void) | undefined;
      endpoints.list.mockImplementationOnce(() => new Observable<QaEndpointPage>((s) => { release = (p) => { s.next(p); s.complete(); }; }));
      create();
      pick('uam');
      endpoints.list.mockImplementation(() => of(endpointPage([endpoint('w1')], { totalElements: 1 })));
      pick('wallet');
      release?.(endpointPage([endpoint('late')], { totalElements: 1 }));
      expect(page.moduleEndpoints.map((e) => e.id)).toEqual(['w1']);
    });

    it('does nothing when the open module is picked again', () => {
      create();
      pick('uam');
      pick('uam');
      expect(docs.get).toHaveBeenCalledTimes(1);
    });
  });

  describe('writing', () => {
    it('starts from the existing document, or from a heading, and lets only a user who may write do it', () => {
      create();
      pick('uam');
      (q('.qa-edit-doc') as HTMLButtonElement).click();
      refresh();
      expect(page.editing).toBe(true);
      expect(page.draftMarkdown).toBe('# Users\n\nBlocks a user.');
      expect(page.draftTitle).toBe('API: uam');
      page.editing = false;
      pick('wallet');
      page.startEdit();
      expect(page.draftMarkdown).toBe('# wallet\n\n');
      expect(page.draftTitle).toBe(QA_API_DOC_MESSAGE_KEYS.titleFor);

      granted = without(P.create);
      create();
      pick('uam');
      expect(q('.qa-edit-doc')).toBeNull();
      page.startEdit();
      expect(page.editing).toBe(false);
    });

    it('shows a live preview of what is typed, as safe markdown, and the size in bytes', () => {
      create();
      pick('uam');
      page.startEdit();
      refresh();
      page.onDraftInput('## New heading\n\n<script>x</script>');
      refresh();
      expect(q('.qa-doc-preview h2')?.textContent).toBe('New heading');
      expect(q('.qa-doc-preview script')).toBeNull();
      expect(page.draftBytes).toBe(34);
      expect(q('.qa-doc-counter')?.textContent).toContain('qaDashboard.apiDocuments.counter');
    });

    it('refuses an empty or too large draft and says why', () => {
      create();
      pick('uam');
      page.startEdit();
      page.onDraftInput('  ');
      refresh();
      expect(page.draftIssueKey).toBe('qaDashboard.apiDocuments.errors.empty');
      expect(q('#api-doc-error')?.textContent).toContain('errors.empty');
      expect((q('.qa-save-doc') as HTMLButtonElement).disabled).toBe(true);
      page.onDraftInput('x'.repeat(QA_API_DOC_MAX_BYTES + 1));
      expect(page.draftIssueKey).toBe('qaDashboard.apiDocuments.errors.tooLarge');
      page.save();
      expect(docs.save).not.toHaveBeenCalled();
    });

    it('saves the draft as the API document of the module and shows what was saved', () => {
      docs.save.mockImplementation(() => of(full('uam', '# Saved')));
      create();
      pick('uam');
      page.startEdit();
      page.draftTitle = '  ';
      page.onDraftInput('# Saved');
      refresh();
      docs.list.mockImplementation(() => of([info('uam'), info('wallet')]));
      (q('.qa-save-doc') as HTMLButtonElement).click();
      refresh();
      expect(docs.save).toHaveBeenCalledWith({ name: 'api-uam', title: QA_API_DOC_MESSAGE_KEYS.titleFor, markdown: '# Saved', category: 'API', module: 'uam' });
      expect(notify.success).toHaveBeenCalledWith(QA_API_DOC_MESSAGE_KEYS.saved);
      expect(page.editing).toBe(false);
      expect(q('.qa-document h1')?.textContent).toBe('Saved');
      expect(all('.qa-module-item')[1].querySelector('.qa-module-item__doc')?.textContent).toContain('hasDocument');
      // The counts already read are kept when the list of documents is read again.
      expect(all('.qa-module-item')[0].querySelector('.qa-module-item__count')?.textContent).not.toBe('—');
      expect(page.hasPendingChanges()).toBe(false);
    });

    it('reports a failed save and stays in the editor', () => {
      docs.save.mockImplementation(() => throwError(() => new Error('down')));
      create();
      pick('uam');
      page.startEdit();
      page.onDraftInput('# Changed');
      page.save();
      expect(notify.error).toHaveBeenCalledWith(QA_API_DOC_MESSAGE_KEYS.saveFailed);
      expect(page.editing).toBe(true);
      expect(page.isSaving).toBe(false);
    });

    it('does not save while saving', () => {
      docs.save.mockImplementation(() => NEVER);
      create();
      pick('uam');
      page.startEdit();
      page.onDraftInput('# Changed');
      page.save();
      page.save();
      expect(docs.save).toHaveBeenCalledTimes(1);
    });

    it('asks before throwing away typed text, from cancel and from picking another module', () => {
      create();
      pick('uam');
      page.startEdit();
      expect(page.hasPendingChanges()).toBe(false);
      page.cancelEdit();
      expect(dialogs.confirm).not.toHaveBeenCalled();
      expect(page.editing).toBe(false);

      page.startEdit();
      page.onDraftInput('# Changed');
      expect(page.hasPendingChanges()).toBe(true);
      dialogs.confirm.mockImplementation(() => of(false));
      page.cancelEdit();
      expect(page.editing).toBe(true);
      pick('wallet');
      expect(page.selected).toBe('uam');
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ title: QA_API_DOC_MESSAGE_KEYS.discardTitle, variant: 'warning' }));

      dialogs.confirm.mockImplementation(() => of(true));
      pick('wallet');
      expect(page.selected).toBe('wallet');
      expect(page.editing).toBe(false);
      page.startEdit();
      page.onDraftInput('# Again');
      page.cancelEdit();
      expect(page.editing).toBe(false);
    });
  });

  describe('deleting', () => {
    it('asks first, then deletes the document and shows that the module has none', () => {
      create();
      pick('uam');
      docs.list.mockImplementation(() => of([]));
      (q('.qa-delete-doc') as HTMLButtonElement).click();
      refresh();
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ title: QA_API_DOC_MESSAGE_KEYS.deleteTitle, variant: 'danger' }));
      expect(docs.delete).toHaveBeenCalledWith('api-uam');
      expect(notify.success).toHaveBeenCalledWith(QA_API_DOC_MESSAGE_KEYS.deleted);
      expect(page.document).toBeNull();
      expect(q('.qa-no-document')).not.toBeNull();
    });

    it('does nothing when the user declines, and reports a failed delete', () => {
      create();
      pick('uam');
      dialogs.confirm.mockImplementation(() => of(false));
      page.confirmDelete();
      expect(docs.delete).not.toHaveBeenCalled();
      dialogs.confirm.mockImplementation(() => of(true));
      docs.delete.mockImplementation(() => throwError(() => new Error('down')));
      page.confirmDelete();
      expect(notify.error).toHaveBeenCalledWith(QA_API_DOC_MESSAGE_KEYS.deleteFailed);
      expect(page.document).not.toBeNull();
    });

    it('is offered only with the delete privilege, and only for a module that has a document', () => {
      granted = without(P.delete);
      create();
      pick('uam');
      expect(q('.qa-delete-doc')).toBeNull();
      page.confirmDelete();
      expect(dialogs.confirm).not.toHaveBeenCalled();
      granted = new Set(ALL);
      create();
      pick('wallet');
      page.confirmDelete();
      expect(dialogs.confirm).not.toHaveBeenCalled();
    });
  });

  it('tracks modules by name, and leaves cleanly', () => {
    create();
    expect(page.trackByModule(0, { module: 'uam', inventoryModule: null, endpointCount: null, document: null })).toBe('uam');
    expect(page.methodClass('DELETE')).toBe('qa-method--delete');
    fixture.destroy();
  });
});
