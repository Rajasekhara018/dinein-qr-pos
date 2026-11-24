import { ChangeDetectorRef, Component, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn, HqPrivilegeAccessService } from 'hq-shared-lib';
import { Observable, Subscription } from 'rxjs';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { watchColumns } from '../../shared/table-columns.helpers';
import { errorDetail } from '../admin-form.utils';
import {
  OPERATING_MODEL_DESCRIPTION_KEYS,
  OPERATING_MODEL_LABEL_KEYS,
  OPERATING_MODEL_SCHEDULE_COLUMNS,
  ORGANIZATION_OPERATING_MODEL_PRIVILEGES,
} from './organization-operating-model.constants';
import { earliestStart, switchOptions } from './organization-operating-model.helpers';
import { OrganizationOperatingModelService } from './organization-operating-model.service';
import { OperatingModel, OperatingModelChange, OperatingModelView } from './organization-operating-model.types';

/**
 * Whether the organization runs its sales itself (DIRECT), through franchises (FRANCHISE) or through a franchise where one
 * covers the place and itself elsewhere (HYBRID), and the switches scheduled from a chosen date. A switch already in force cannot be changed, so past sales keep their operator. Shown on the
 * organization's edit page.
 */
@Component({
  selector: 'app-organization-operating-model',
  standalone: false,
  templateUrl: './organization-operating-model.html',
  styleUrl: './organization-operating-model.scss',
})
export class OrganizationOperatingModel implements OnInit, OnChanges, OnDestroy {
  @Input({ required: true }) tenantId!: string;

  readonly privileges = ORGANIZATION_OPERATING_MODEL_PRIVILEGES;

  view: OperatingModelView | null = null;
  columns: HqCommonTableColumn<OperatingModelChange>[] = [];
  private columnsSub?: Subscription;
  isLoading = false;
  busy = false;
  effectiveFrom = '';
  /** The model the next switch goes to, chosen among {@link options}. */
  selectedModel: OperatingModel | null = null;

  constructor(
    private operatingModels: OrganizationOperatingModelService,
    private notify: NotificationService,
    private dialogs: AppDialogService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
    private access: HqPrivilegeAccessService,
  ) {}

  ngOnInit(): void {
    this.columnsSub = watchColumns(this.translate, OPERATING_MODEL_SCHEDULE_COLUMNS, (columns) => (this.columns = columns));
  }

  ngOnChanges(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.columnsSub?.unsubscribe();
  }

  /** The models a new switch may go to: every model except the one in force before it. */
  get options(): OperatingModel[] {
    return this.view ? switchOptions(this.view) : [];
  }

  /** The model a new switch goes to: the chosen one while it is still allowed, else the first allowed one. */
  get target(): OperatingModel | null {
    const options = this.options;
    return this.selectedModel && options.includes(this.selectedModel) ? this.selectedModel : (options[0] ?? null);
  }

  /** The first day the date picker offers. */
  get minDate(): string {
    return this.view ? earliestStart(this.view) : '';
  }

  /** The translation key of a model's label, or the raw code when there is none. */
  label(model: OperatingModel): string {
    return OPERATING_MODEL_LABEL_KEYS[model] ?? model;
  }

  /** The translation key of a model's one-line explanation, or null when there is none. */
  description(model: OperatingModel | null): string | null {
    return model ? (OPERATING_MODEL_DESCRIPTION_KEYS[model] ?? null) : null;
  }

  load(): void {
    // The section is hidden without the read privilege; asking anyway would only earn a refusal and an error toast.
    if (!this.access.can(this.privileges.read)) return;
    this.isLoading = true;
    this.operatingModels.load(this.tenantId).subscribe({
      next: (view) => this.show(view),
      error: (err: unknown) => this.fail(err, 'accessManagement.organizations.operatingModel.messages.loadFailed'),
    });
  }

  scheduleSwitch(): void {
    const view = this.view;
    const target = this.target;
    if (!view || !target || !this.effectiveFrom || this.busy) return;
    this.dialogs.confirm({
      title: this.translate.instant('accessManagement.organizations.operatingModel.switchDialog.title'),
      message: this.translate.instant('accessManagement.organizations.operatingModel.switchDialog.message', {
        model: this.translate.instant(this.label(target)),
        date: this.effectiveFrom,
      }),
      confirmText: this.translate.instant('accessManagement.organizations.operatingModel.schedule'),
    }).subscribe((confirmed) => {
      if (!confirmed) return;
      this.run(this.operatingModels.schedule(this.tenantId, view, target, this.effectiveFrom),
        'accessManagement.organizations.operatingModel.messages.scheduled');
    });
  }

  cancel(change: OperatingModelChange): void {
    const view = this.view;
    if (!view || change.inForce || this.busy) return;
    this.dialogs.confirm({
      title: this.translate.instant('accessManagement.organizations.operatingModel.cancelDialog.title'),
      message: this.translate.instant('accessManagement.organizations.operatingModel.cancelDialog.message', {
        model: this.translate.instant(this.label(change.model)),
        date: change.effectiveFrom,
      }),
      variant: 'danger',
      confirmText: this.translate.instant('accessManagement.organizations.operatingModel.cancel'),
    }).subscribe((confirmed) => {
      if (!confirmed) return;
      this.run(this.operatingModels.cancel(this.tenantId, view, change),
        'accessManagement.organizations.operatingModel.messages.cancelled');
    });
  }

  trackChange(_: number, change: OperatingModelChange): string {
    return change.effectiveFrom;
  }

  private run(call: Observable<OperatingModelView>, successKey: string): void {
    this.busy = true;
    call.subscribe({
      next: (view) => {
        this.effectiveFrom = '';
        this.selectedModel = null;
        this.notify.success(this.translate.instant(successKey));
        this.show(view);
      },
      error: (err: unknown) => this.fail(err, 'accessManagement.organizations.operatingModel.messages.actionFailed'),
    });
  }

  private show(view: OperatingModelView): void {
    this.view = view;
    this.isLoading = false;
    this.busy = false;
    this.cdr.detectChanges();
  }

  private fail(err: unknown, fallbackKey: string): void {
    this.isLoading = false;
    this.busy = false;
    this.notify.error(errorDetail(err) ?? this.translate.instant(fallbackKey));
    this.cdr.detectChanges();
  }
}
