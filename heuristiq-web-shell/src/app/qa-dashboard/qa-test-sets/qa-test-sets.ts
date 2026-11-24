import { Component, inject } from '@angular/core';
import { HqCommonTableColumn } from 'hq-shared-lib';
import { takeUntil } from 'rxjs/operators';

import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { watchColumnsUntil } from '../../shared/table-columns.helpers';
import {
  QA_MESSAGE_KEYS,
  QA_RUNNABLE_ENVIRONMENTS,
  QA_SUITES,
  QA_TAG_EXPRESSION_MAX,
  QA_TEST_SET_DESCRIPTION_MAX,
  QA_TEST_SET_NAME_MAX,
  QA_TEST_SET_NAME_MIN,
} from '../qa-dashboard.constants';
import { QaTestSetFormValues, shortMessage, testSetFormProblem } from '../qa-dashboard.helpers';
import { QaSuite, QaTestSet, QaTestSetInput } from '../qa-dashboard.models';
import { QaScreenBase } from '../qa-screen-base';
import { QA_TEST_SET_COLUMNS } from './qa-test-sets.constants';

/**
 * QA automation > Test sets: named groups of tests (a Cucumber tag expression) that staff keep, edit, delete and start
 * on Jenkins in an environment (never live or production). The form and the confirmations are the shared app dialogs.
 */
@Component({
  selector: 'app-qa-test-sets',
  standalone: false,
  templateUrl: './qa-test-sets.html',
  styleUrls: ['../qa-dashboard.shared.scss', './qa-test-sets.scss'],
})
export class QaTestSetsPage extends QaScreenBase {
  private readonly dialogs = inject(AppDialogService);
  private readonly notify = inject(NotificationService);

  /** Every test set, as the API sends them; the table shows the ones of the open workspace. */
  all: QaTestSet[] = [];
  loaded = false;
  busyId: string | null = null;
  columns: HqCommonTableColumn<QaTestSet>[] = [];
  /** Class of every test set row (the row hook the specs and the UI suite look for). */
  readonly rowClassOf = (): string => 'qa-set-row';

  override ngOnInit(): void {
    watchColumnsUntil(this.translate, QA_TEST_SET_COLUMNS, (columns) => (this.columns = columns), this.destroy$);
    super.ngOnInit();
  }

  get testSets(): QaTestSet[] {
    return this.all.filter((s) => s.workspace === this.workspace);
  }

  protected onWorkspaceChange(): void {
    this.reload();
  }

  reload(): void {
    if (!this.access.can(this.privileges.testSetList)) return;
    this.load(
      this.svc.testSets(),
      (sets) => {
        this.all = sets;
        this.loaded = true;
      },
      () => {
        this.all = [];
        this.loaded = false;
      },
    );
  }

  // ---- create and edit ----

  openCreate(): void {
    if (!this.access.can(this.privileges.testSetCreate)) return;
    this.openForm(null, { name: '', suite: 'REGRESSION', tagExpression: '', description: '' });
  }

  openEdit(set: QaTestSet): void {
    if (!this.access.can(this.privileges.testSetUpdate)) return;
    this.openForm(set, { name: set.name, suite: set.suite, tagExpression: set.tagExpression, description: set.description });
  }

  /** Shows the form; a wrong value is explained and the form opens again with what was typed. */
  private openForm(existing: QaTestSet | null, initial: QaTestSetFormValues): void {
    const t = (key: string, params?: object): string => this.translate.instant(key, params);
    this.dialogs
      .form<Record<string, string>>({
        title: t(existing ? QA_MESSAGE_KEYS.formEditTitle : QA_MESSAGE_KEYS.formCreateTitle),
        confirmText: t(QA_MESSAGE_KEYS.formSave),
        fields: [
          { key: 'name', label: t(QA_MESSAGE_KEYS.fieldName), type: 'text', required: true, value: initial.name },
          {
            key: 'suite',
            label: t(QA_MESSAGE_KEYS.fieldSuite),
            type: 'select',
            required: true,
            value: initial.suite,
            options: QA_SUITES.map((suite) => ({ value: suite, label: this.suiteLabel(suite) })),
          },
          {
            key: 'tagExpression',
            label: t(QA_MESSAGE_KEYS.fieldTagExpression),
            type: 'text',
            required: true,
            value: initial.tagExpression,
            helperText: t(QA_MESSAGE_KEYS.fieldTagExpressionHint),
          },
          { key: 'description', label: t(QA_MESSAGE_KEYS.fieldDescription), type: 'textarea', value: initial.description },
        ],
      })
      .pipe(takeUntil(this.destroy$))
      .subscribe((result) => {
        if (!result) return;
        const values: QaTestSetFormValues = {
          name: result['name'] ?? '',
          suite: result['suite'] ?? '',
          tagExpression: result['tagExpression'] ?? '',
          description: result['description'] ?? '',
        };
        const problem = testSetFormProblem(values);
        if (problem) {
          this.notify.error(t(QA_MESSAGE_KEYS.fieldError, { field: t(this.fieldKey(problem)), limits: this.limitsOf(problem) }));
          this.openForm(existing, values);
          return;
        }
        this.save(existing, values);
      });
  }

  private save(existing: QaTestSet | null, values: QaTestSetFormValues): void {
    const input: QaTestSetInput = {
      name: values.name.trim(),
      workspace: this.workspace,
      suite: values.suite as QaSuite,
      tagExpression: values.tagExpression.trim(),
      description: values.description.trim(),
      ...(existing ? { version: existing.version } : {}),
    };
    const call = existing ? this.svc.updateTestSet(existing.id, input) : this.svc.createTestSet(input);
    call.pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.notify.success(this.translate.instant(QA_MESSAGE_KEYS.setSaved));
        this.reload();
      },
      error: () => this.notify.error(this.translate.instant(QA_MESSAGE_KEYS.setSaveFailed)),
    });
  }

  private fieldKey(field: 'name' | 'suite' | 'tagExpression' | 'description'): string {
    return {
      name: QA_MESSAGE_KEYS.fieldName,
      suite: QA_MESSAGE_KEYS.fieldSuite,
      tagExpression: QA_MESSAGE_KEYS.fieldTagExpression,
      description: QA_MESSAGE_KEYS.fieldDescription,
    }[field];
  }

  private limitsOf(field: 'name' | 'suite' | 'tagExpression' | 'description'): string {
    switch (field) {
      case 'name':
        return `${QA_TEST_SET_NAME_MIN}-${QA_TEST_SET_NAME_MAX}`;
      case 'tagExpression':
        return `<= ${QA_TAG_EXPRESSION_MAX}`;
      case 'description':
        return `<= ${QA_TEST_SET_DESCRIPTION_MAX}`;
      default:
        return '';
    }
  }

  // ---- delete ----

  confirmDelete(set: QaTestSet): void {
    if (!this.access.can(this.privileges.testSetDelete) || this.busyId) return;
    this.dialogs
      .confirm({
        title: this.translate.instant(QA_MESSAGE_KEYS.setDeleteTitle),
        message: this.translate.instant(QA_MESSAGE_KEYS.setDeleteMessage, { name: set.name }),
        confirmText: this.translate.instant(QA_MESSAGE_KEYS.deleteConfirm),
        variant: 'danger',
      })
      .pipe(takeUntil(this.destroy$))
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.busyId = set.id;
        this.svc
          .deleteTestSet(set.id)
          .pipe(takeUntil(this.destroy$))
          .subscribe({
            next: () => {
              this.busyId = null;
              this.notify.success(this.translate.instant(QA_MESSAGE_KEYS.setDeleted));
              this.reload();
            },
            error: () => {
              this.busyId = null;
              this.notify.error(this.translate.instant(QA_MESSAGE_KEYS.setDeleteFailed));
              this.cdr.markForCheck();
            },
          });
      });
  }

  // ---- run on Jenkins ----

  /** Asks for the environment, then starts the run; the release name comes back from the server. */
  openRun(set: QaTestSet): void {
    if (!this.access.can(this.privileges.testSetRun) || this.busyId) return;
    this.dialogs
      .form<Record<string, string>>({
        title: this.translate.instant(QA_MESSAGE_KEYS.runTitle),
        message: this.translate.instant(QA_MESSAGE_KEYS.runMessage, { name: set.name }),
        confirmText: this.translate.instant(QA_MESSAGE_KEYS.runConfirm),
        fields: [
          {
            key: 'environment',
            label: this.translate.instant(QA_MESSAGE_KEYS.fieldEnvironment),
            type: 'select',
            required: true,
            options: QA_RUNNABLE_ENVIRONMENTS.map((e) => ({ value: e, label: this.environmentLabel(e) })),
          },
        ],
      })
      .pipe(takeUntil(this.destroy$))
      .subscribe((result) => {
        const environment = result?.['environment'];
        if (!environment) return;
        this.busyId = set.id;
        this.svc
          .runTestSet(set.id, environment)
          .pipe(takeUntil(this.destroy$))
          .subscribe({
            next: (started) => {
              this.busyId = null;
              this.notify.success(this.translate.instant(QA_MESSAGE_KEYS.runStarted, { release: started.release }));
              this.cdr.markForCheck();
            },
            error: () => {
              this.busyId = null;
              this.notify.error(this.translate.instant(QA_MESSAGE_KEYS.runFailed));
              this.cdr.markForCheck();
            },
          });
      });
  }

  summary(text: string | null | undefined): string {
    return shortMessage(text, 120);
  }

  trackBySet(_: number, set: QaTestSet): string {
    return set.id;
  }
}
