import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn, HqPrivilegeAccessService } from 'hq-shared-lib';
import { Observable, Subject, forkJoin, of } from 'rxjs';
import { catchError, map, takeUntil } from 'rxjs/operators';

import { PendingChangesAware } from '../../shared/guards/pending-changes.guard';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { watchColumnsUntil } from '../../shared/table-columns.helpers';
import { renderMarkdown } from '../../system-qa/system-qa.helpers';
import { QA_DASHBOARD_PRIVILEGES } from '../qa-dashboard.constants';
import { emptyEndpointQuery, methodClass } from '../qa-endpoint-inventory/qa-endpoint-inventory.helpers';
import { QaEndpointItem } from '../qa-endpoint-inventory/qa-endpoint-inventory.models';
import { QaEndpointService } from '../qa-endpoint-inventory/qa-endpoint-inventory.service';
import { utf8Length } from '../qa-test-case-studio/qa-studio.helpers';
import {
  QA_API_DOC_ENDPOINT_COLUMNS,
  QA_API_DOC_ENDPOINT_ROWS,
  QA_API_DOC_LINKS,
  QA_API_DOC_MAX_BYTES,
  QA_API_DOC_MESSAGE_KEYS,
  QA_API_DOC_PRIVILEGES,
  QA_API_DOC_TEMPLATE,
} from './qa-api-documents.constants';
import { buildModuleList, documentNameOf, markdownIssue } from './qa-api-documents.helpers';
import { QaApiDocInfo, QaApiDocument, QaApiModule } from './qa-api-documents.models';
import { QaApiDocumentsService } from './qa-api-documents.service';

/**
 * QA automation > API documents: one markdown document per API area (module). On the left the modules, each with its
 * endpoint count (from the endpoint inventory) and whether it has a document yet; on the right the document, rendered
 * with the portal's safe markdown (raw HTML is shown as text), an editor with a live preview, the module's endpoints
 * and links to the Swagger and Javadoc screens. Writing and deleting are behind their own privileges.
 */
@Component({
  selector: 'app-qa-api-documents',
  standalone: false,
  templateUrl: './qa-api-documents.html',
  styleUrls: ['../qa-dashboard.shared.scss', '../qa-endpoint-inventory/qa-endpoint-inventory.scss', './qa-api-documents.scss'],
})
export class QaApiDocumentsPage implements OnInit, OnDestroy, PendingChangesAware {
  private readonly docs = inject(QaApiDocumentsService);
  private readonly endpoints = inject(QaEndpointService);
  private readonly access = inject(HqPrivilegeAccessService);
  private readonly translate = inject(TranslateService);
  private readonly dialogs = inject(AppDialogService);
  private readonly notify = inject(NotificationService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroy$ = new Subject<void>();

  readonly privileges = QA_API_DOC_PRIVILEGES;
  readonly dashboardPrivileges = QA_DASHBOARD_PRIVILEGES;
  readonly links = QA_API_DOC_LINKS;
  readonly maxBytes = QA_API_DOC_MAX_BYTES;

  modules: QaApiModule[] = [];
  selected: string | null = null;
  document: QaApiDocument | null = null;
  documentHtml = '';
  moduleEndpoints: QaEndpointItem[] = [];
  moduleEndpointTotal = 0;
  endpointColumns: HqCommonTableColumn<QaEndpointItem>[] = [];
  /** Class of every endpoint row (the row hook the specs and the UI suite look for). */
  readonly endpointRowClass = (): string => 'qa-module-endpoint-row';

  isLoadingModules = false;
  modulesFailed = false;
  isLoadingDocument = false;
  documentFailed = false;
  isLoadingEndpoints = false;
  isSaving = false;

  editing = false;
  draftTitle = '';
  draftMarkdown = '';
  draftHtml = '';
  /** What the editor started from, to tell whether anything was changed. */
  private baselineTitle = '';
  private baselineMarkdown = '';

  ngOnInit(): void {
    watchColumnsUntil(this.translate, QA_API_DOC_ENDPOINT_COLUMNS, (columns) => (this.endpointColumns = columns), this.destroy$);
    this.loadModules();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ---- access ----

  get canRead(): boolean {
    return this.access.can(QA_API_DOC_PRIVILEGES.read);
  }

  get canWrite(): boolean {
    return this.access.can(QA_API_DOC_PRIVILEGES.create);
  }

  // ---- the modules ----

  /** Loads the documents and the modules of the inventory, then the endpoint count of each module. */
  loadModules(): void {
    if (!this.access.can(QA_API_DOC_PRIVILEGES.list)) return;
    this.isLoadingModules = true;
    this.modulesFailed = false;
    const inventory$: Observable<string[]> = this.access.can(QA_DASHBOARD_PRIVILEGES.endpointFilterRead)
      ? this.endpoints.filters().pipe(
          map((f) => f.modules.map((m) => m.module)),
          catchError(() => of<string[]>([])),
        )
      : of<string[]>([]);
    forkJoin({ documents: this.docs.list(), inventory: inventory$ })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: ({ documents, inventory }) => {
          this.setModules(inventory, documents, this.knownCounts());
          this.isLoadingModules = false;
          this.loadCounts();
          this.cdr.markForCheck();
        },
        error: () => {
          this.modulesFailed = true;
          this.isLoadingModules = false;
          this.cdr.markForCheck();
        },
      });
  }

  private inventoryModules: string[] = [];
  private documents: QaApiDocInfo[] = [];

  /** The endpoint counts already read, so a refresh of the list does not lose them. */
  private knownCounts(): Map<string, number> {
    return new Map(this.modules.filter((m) => m.endpointCount !== null).map((m) => [m.module, m.endpointCount as number] as [string, number]));
  }

  private setModules(inventory: string[], documents: QaApiDocInfo[], counts: Map<string, number>): void {
    this.inventoryModules = inventory;
    this.documents = documents;
    this.modules = buildModuleList(inventory, documents, counts);
  }

  private loadCounts(): void {
    if (!this.access.can(QA_DASHBOARD_PRIVILEGES.endpointList) || this.modules.length === 0) return;
    // The inventory knows a module by its catalog name (ADMINISTRATION), the document list by its code (administration).
    const inventoryName = (m: QaApiModule): string => m.inventoryModule ?? m.module;
    this.docs
      .endpointCounts(this.modules.map(inventoryName))
      .pipe(takeUntil(this.destroy$))
      .subscribe((byName) => {
        const counts = new Map<string, number>();
        this.modules.forEach((m) => {
          const count = byName.get(inventoryName(m));
          if (count !== undefined) counts.set(m.module, count);
        });
        this.modules = buildModuleList(this.inventoryModules, this.documents, counts);
        this.cdr.markForCheck();
      });
  }

  // ---- one module ----

  /** Opens a module: its document, and its endpoints from the inventory. Asks first when an edit would be lost. */
  selectModule(module: string): void {
    if (module === this.selected) return;
    if (this.hasPendingChanges()) {
      this.confirmDiscardChanges()
        .pipe(takeUntil(this.destroy$))
        .subscribe((discard) => {
          if (discard) this.openModule(module);
        });
      return;
    }
    this.openModule(module);
  }

  private openModule(module: string): void {
    this.editing = false;
    this.selected = module;
    this.document = null;
    this.documentHtml = '';
    this.documentFailed = false;
    this.moduleEndpoints = [];
    this.moduleEndpointTotal = 0;
    const entry = this.modules.find((m) => m.module === module);
    if (entry?.document && this.canRead) this.loadDocument(entry.document.name);
    this.loadEndpoints(module);
  }

  private loadDocument(name: string): void {
    this.isLoadingDocument = true;
    this.docs
      .get(name)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (doc) => {
          this.document = doc;
          this.documentHtml = renderMarkdown(doc.markdown);
          this.isLoadingDocument = false;
          this.cdr.markForCheck();
        },
        error: () => {
          this.documentFailed = true;
          this.isLoadingDocument = false;
          this.cdr.markForCheck();
        },
      });
  }

  private loadEndpoints(module: string): void {
    if (!this.access.can(QA_DASHBOARD_PRIVILEGES.endpointList)) return;
    this.isLoadingEndpoints = true;
    this.endpoints
      .list(emptyEndpointQuery({ module: this.modules.find((m) => m.module === module)?.inventoryModule ?? module }), 0, QA_API_DOC_ENDPOINT_ROWS)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (page) => {
          if (this.selected === module) {
            this.moduleEndpoints = page.items;
            this.moduleEndpointTotal = page.totalElements;
          }
          this.isLoadingEndpoints = false;
          this.cdr.markForCheck();
        },
        error: () => {
          this.isLoadingEndpoints = false;
          this.cdr.markForCheck();
        },
      });
  }

  /** The entry of the open module in the list. */
  get current(): QaApiModule | null {
    return this.modules.find((m) => m.module === this.selected) ?? null;
  }

  // ---- editing ----

  /** Starts writing the module's document, from the existing one or from a heading. */
  startEdit(): void {
    if (!this.canWrite || !this.selected) return;
    const module = this.selected;
    this.draftTitle = this.document?.title ?? this.translate.instant(QA_API_DOC_MESSAGE_KEYS.titleFor, { module });
    this.draftMarkdown = this.document?.markdown ?? QA_API_DOC_TEMPLATE(module);
    this.baselineTitle = this.draftTitle;
    this.baselineMarkdown = this.draftMarkdown;
    this.draftHtml = renderMarkdown(this.draftMarkdown);
    this.editing = true;
  }

  /** The preview follows what is typed. */
  onDraftInput(text: string): void {
    this.draftMarkdown = text;
    this.draftHtml = renderMarkdown(text);
  }

  cancelEdit(): void {
    if (!this.hasPendingChanges()) {
      this.editing = false;
      return;
    }
    this.confirmDiscardChanges()
      .pipe(takeUntil(this.destroy$))
      .subscribe((discard) => {
        if (discard) this.editing = false;
      });
  }

  get draftBytes(): number {
    return utf8Length(this.draftMarkdown);
  }

  /** What is wrong with the draft, as a translation key, or null. */
  get draftIssueKey(): string | null {
    const issue = markdownIssue(this.draftMarkdown);
    return issue ? `qaDashboard.apiDocuments.errors.${issue}` : null;
  }

  save(): void {
    if (!this.canWrite || !this.selected || this.isSaving || this.draftIssueKey) return;
    const module = this.selected;
    this.isSaving = true;
    this.docs
      .save({
        name: documentNameOf(module),
        title: this.draftTitle.trim() || this.translate.instant(QA_API_DOC_MESSAGE_KEYS.titleFor, { module }),
        markdown: this.draftMarkdown,
        category: 'API',
        module,
      })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (doc) => {
          this.isSaving = false;
          this.editing = false;
          this.document = doc;
          this.documentHtml = renderMarkdown(doc.markdown);
          this.notify.success(this.translate.instant(QA_API_DOC_MESSAGE_KEYS.saved));
          this.refreshDocuments();
          this.cdr.markForCheck();
        },
        error: () => {
          this.isSaving = false;
          this.notify.error(this.translate.instant(QA_API_DOC_MESSAGE_KEYS.saveFailed));
          this.cdr.markForCheck();
        },
      });
  }

  // ---- deleting ----

  confirmDelete(): void {
    const doc = this.document;
    if (!doc || !this.access.can(QA_API_DOC_PRIVILEGES.delete)) return;
    this.dialogs
      .confirm({
        title: this.translate.instant(QA_API_DOC_MESSAGE_KEYS.deleteTitle),
        message: this.translate.instant(QA_API_DOC_MESSAGE_KEYS.deleteMessage, { module: this.selected }),
        confirmText: this.translate.instant(QA_API_DOC_MESSAGE_KEYS.deleteConfirm),
        variant: 'danger',
      })
      .pipe(takeUntil(this.destroy$))
      .subscribe((confirmed) => {
        if (confirmed) this.delete(doc);
      });
  }

  private delete(doc: QaApiDocument): void {
    this.docs
      .delete(doc.name)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.document = null;
          this.documentHtml = '';
          this.editing = false;
          this.notify.success(this.translate.instant(QA_API_DOC_MESSAGE_KEYS.deleted));
          this.refreshDocuments();
          this.cdr.markForCheck();
        },
        error: () => {
          this.notify.error(this.translate.instant(QA_API_DOC_MESSAGE_KEYS.deleteFailed));
          this.cdr.markForCheck();
        },
      });
  }

  /** Reads the list of documents again so the left-hand list says which modules have one now. */
  private refreshDocuments(): void {
    this.docs
      .list()
      .pipe(takeUntil(this.destroy$))
      .subscribe((documents) => {
        this.setModules(this.inventoryModules, documents, this.knownCounts());
        this.cdr.markForCheck();
      });
  }

  // ---- leaving ----

  hasPendingChanges(): boolean {
    return this.editing && (this.draftMarkdown !== this.baselineMarkdown || this.draftTitle !== this.baselineTitle);
  }

  confirmDiscardChanges(): Observable<boolean> {
    return this.dialogs.confirm({
      title: this.translate.instant(QA_API_DOC_MESSAGE_KEYS.discardTitle),
      message: this.translate.instant(QA_API_DOC_MESSAGE_KEYS.discardMessage),
      confirmText: this.translate.instant(QA_API_DOC_MESSAGE_KEYS.discardConfirm),
      variant: 'warning',
    });
  }

  // ---- display ----

  methodClass(method: string): string {
    return methodClass(method);
  }

  trackByModule(_: number, module: QaApiModule): string {
    return module.module;
  }
}
