import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { UamRole } from '../../shared/models/uam.models';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { OrgRoleRulesService } from '../org-role-rules.service';
import { ORG_LOGIN_PRIVILEGES, ORG_LOGINS_ROUTE, ORG_ROLES_DIALOG_TITLE_ID } from '../org-structure.constants';
import { rolesToOffer, rulesOfUnit } from '../org-structure.helpers';
import { OrgRoleRule } from '../org-structure.models';
import { OrgStructureService } from '../org-structure.service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';

/** What the dialog is opened with: the department whose roles it shows. */
export interface OrgDeptRolesDialogData {
  unitId: string;
  unitName: string;
  /** People in the department and below, when the chart knows it. */
  headcount: number | null;
}

type LoadState = 'loading' | 'ready' | 'error';

/**
 * The roles given to everyone in one department (and the departments below it) when their login is created: the rules that target exactly
 * this department, with a way to add one more and to remove one. A change takes effect at once; it is not part of the draft and does not touch
 * logins that already exist. It reads the same rules and roles as the Org logins screen.
 */
@Component({
  selector: 'app-org-dept-roles-dialog',
  standalone: false,
  templateUrl: './org-dept-roles-dialog.html',
  styleUrl: './org-dept-roles-dialog.scss',
})
export class OrgDeptRolesDialog implements OnInit {
  readonly data = inject<OrgDeptRolesDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<OrgDeptRolesDialog, boolean>>(MatDialogRef);
  private readonly source = inject(OrgRoleRulesService);
  private readonly api = inject(OrgStructureService);
  private readonly dialogs = inject(AppDialogService);
  private readonly notify = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);

  readonly privileges = ORG_LOGIN_PRIVILEGES;
  readonly titleId = ORG_ROLES_DIALOG_TITLE_ID;
  readonly loginsRoute = ORG_LOGINS_ROUTE;

  state: LoadState = 'loading';
  rules: OrgRoleRule[] = [];
  roles: UamRole[] = [];
  /** True when the roles could not be read: rules still show, but nothing can be added. */
  rolesFailed = false;
  newRole = '';
  busy = false;
  /** The sentence for the last failed add or remove, shown in the dialog (no toast). */
  actionError: string | null = null;
  /** True once something was added or removed, so the opener knows. */
  changed = false;

  ngOnInit(): void {
    this.loadRoles();
    this.loadRules();
  }

  /** The rules of this department only. */
  get unitRules(): OrgRoleRule[] {
    return rulesOfUnit(this.rules, this.data.unitId);
  }

  /** The roles still to offer for this department. */
  get offeredRoles(): UamRole[] {
    return rolesToOffer(this.roles, this.unitRules);
  }

  get canAdd(): boolean {
    return !!this.newRole && !this.busy;
  }

  /** The role's name beside its code, when it has one. */
  roleName(code: string): string | null {
    return this.roles.find((role) => role.code === code)?.name ?? null;
  }

  add(): void {
    if (!this.canAdd) return;
    this.busy = true;
    this.actionError = null;
    this.api.createRoleRule(this.data.unitId, null, this.newRole).subscribe({
      next: () => {
        this.busy = false;
        this.changed = true;
        this.newRole = '';
        this.notify.success(this.translate.instant('orgStructure.logins.messages.ruleAdded'));
        this.loadRules();
      },
      error: (err) => this.fail(err, 'orgStructure.logins.messages.ruleFailed'),
    });
  }

  remove(rule: OrgRoleRule): void {
    this.dialogs.confirm({
      title: this.translate.instant('orgStructure.logins.deleteRuleDialog.title'),
      message: this.translate.instant('orgStructure.logins.deleteRuleDialog.message', { role: rule.roleCode }),
      variant: 'danger',
      confirmText: this.translate.instant('orgStructure.logins.deleteRule'),
    }).subscribe((confirmed) => {
      if (!confirmed) return;
      this.busy = true;
      this.actionError = null;
      this.api.deleteRoleRule(rule.id).subscribe({
        next: () => {
          this.busy = false;
          this.changed = true;
          this.notify.success(this.translate.instant('orgStructure.logins.messages.ruleDeleted'));
          this.loadRules();
        },
        error: (err) => this.fail(err, 'orgStructure.logins.messages.ruleFailed'),
      });
    });
  }

  retry(): void {
    this.state = 'loading';
    this.loadRoles();
    this.loadRules();
  }

  close(): void {
    this.ref.close(this.changed);
  }

  private loadRules(): void {
    this.source.rules().subscribe({
      next: (rules) => { this.rules = rules; this.state = 'ready'; this.cdr.detectChanges(); },
      error: () => { this.state = 'error'; this.cdr.detectChanges(); },
    });
  }

  private loadRoles(): void {
    this.rolesFailed = false;
    this.source.roles().subscribe({
      next: (roles) => { this.roles = roles; this.cdr.detectChanges(); },
      error: () => { this.rolesFailed = true; this.cdr.detectChanges(); },
    });
  }

  private fail(err: { error?: { detail?: string } }, fallbackKey: string): void {
    this.busy = false;
    this.actionError = apiErrorMessage(err, this.translate.instant(fallbackKey));
    this.cdr.detectChanges();
  }
}
