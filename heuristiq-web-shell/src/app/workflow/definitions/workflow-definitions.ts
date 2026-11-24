import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { HqCommonTableColumn } from 'hq-shared-lib';
import { watchColumns } from '../../shared/table-columns.helpers';
import { WORKFLOW_DEFINITION_COLUMNS } from './workflow-definitions.constants';
import { Router } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { WorkflowService } from '../workflow-service';
import { ProcessDefinitionSummary } from '../workflow.models';
import { DEFINITION_EDITOR_ROLES, WORKFLOW_ROUTE } from '../workflow.constants';
import { AuthService } from '../../shared/services/auth-service';
import { NotificationService } from '../../shared/services/notification-service';
import { TranslateService } from '@ngx-translate/core';
import { PROCESS_KEY_PATTERN, WORKFLOW_TEMPLATES } from './workflow-templates';

/** Every process key with its latest deployed version. */
@Component({
  selector: 'app-workflow-definitions',
  standalone: false,
  templateUrl: './workflow-definitions.html',
  styleUrl: './workflow-definitions.scss',
})
export class WorkflowDefinitions implements OnInit, OnDestroy {
  definitions: ProcessDefinitionSummary[] = [];
  columns: HqCommonTableColumn<ProcessDefinitionSummary>[] = [];
  private columnsSub?: Subscription;
  isLoading = false;
  hasError = false;

  constructor(
    private workflow: WorkflowService,
    private router: Router,
    private auth: AuthService,
    private notify: NotificationService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
  ) {}

  readonly templates = WORKFLOW_TEMPLATES;
  templateId = WORKFLOW_TEMPLATES[0].id;
  newKey = '';
  newName = '';

  /** Creating a workflow is for the roles that may deploy one. */
  get canCreate(): boolean {
    return DEFINITION_EDITOR_ROLES.includes(this.auth.currentUser?.role ?? '');
  }

  get selectedTemplate() {
    return this.templates.find((t) => t.id === this.templateId) ?? this.templates[0];
  }

  /** Opens the editor with the chosen template filled in (nothing is deployed until you click Deploy). */
  createFromTemplate(): void {
    const key = this.newKey.trim();
    if (!PROCESS_KEY_PATTERN.test(key)) {
      this.notify.error(this.translate.instant('workflow.definitions.templates.badKey'));
      return;
    }
    if (this.definitions.some((d) => d.key === key)) {
      this.notify.error(this.translate.instant('workflow.definitions.templates.keyTaken'));
      return;
    }
    void this.router.navigate([WORKFLOW_ROUTE, 'definitions', 'new'], {
      queryParams: { template: this.templateId, key, name: this.newName.trim() || key },
    });
  }

  ngOnInit(): void {
    this.columnsSub = watchColumns(this.translate, WORKFLOW_DEFINITION_COLUMNS, (columns) => (this.columns = columns));
    this.load();
  }

  ngOnDestroy(): void {
    this.columnsSub?.unsubscribe();
  }

  load(): void {
    this.isLoading = true;
    this.hasError = false;
    this.workflow.definitions()
      .pipe(finalize(() => { this.isLoading = false; this.cdr.detectChanges(); }))
      .subscribe({
        next: (list) => (this.definitions = list),
        error: () => { this.definitions = []; this.hasError = true; },
      });
  }

  open(definition: ProcessDefinitionSummary): void {
    void this.router.navigate([WORKFLOW_ROUTE, 'definitions', definition.key]);
  }

  trackByKey(_: number, definition: ProcessDefinitionSummary): string {
    return definition.key;
  }
}
