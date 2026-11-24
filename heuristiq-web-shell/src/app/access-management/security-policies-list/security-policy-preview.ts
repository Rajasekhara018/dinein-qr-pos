import { Component, DestroyRef, EventEmitter, Input, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HqAppDialogFormValues, HqSharedLibModule } from 'hq-shared-lib';
import { UamService } from '../../shared/services/uam-service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import { SECURITY_POLICY_CRUD_CONFIG } from '../access-management-configs';

export interface EffectivePolicyPreview {
  fingerprint: string;
  settings: Record<string, { value: boolean | number; rule: string; sources: string[] }>;
  policies: { id: string; name: string; scope: string; revision: string }[];
}
interface PolicyTemplate { key: string; fields: Record<string, boolean | number>; }

@Component({
  selector: 'app-security-policy-preview', standalone: true,
  imports: [CommonModule, TranslateModule, HqSharedLibModule],
  template: `
    <section class="policy-preview" aria-labelledby="policy-preview-title">
      <h3 id="policy-preview-title">{{ ns + '.effectiveTitle' | translate }}</h3>
      <p>{{ ns + '.inheritanceHint' | translate }}</p>
      <p>{{ ns + '.activationHint' | translate }}</p>
      <div class="policy-preview__actions">
        <button hqButton="secondary" type="button" [disabled]="loading" (click)="load()">{{ ns + '.preview' | translate }}</button>
        <button hqButton="secondary" appearance="text" type="button" *ngFor="let template of templates" (click)="apply.emit(template.fields)">{{ ns + '.templates.' + template.key | translate }}</button>
      </div>
      <p role="alert" *ngIf="error">{{ error }}</p>
      <div class="policy-preview__table" *ngIf="policy && loadedScope === scopeKey">
        <p>{{ ns + '.savedValuesHint' | translate }}</p>
        <table>
          <thead><tr><th>{{ ns + '.setting' | translate }}</th><th>{{ ns + '.effectiveValue' | translate }}</th><th>{{ ns + '.source' | translate }}</th></tr></thead>
          <tbody><tr *ngFor="let item of policy.settings | keyvalue"><td>{{ label(item.key) | translate }}</td><td>{{ value(item.value.value) }}</td><td>{{ sources(item.value.sources) }}</td></tr></tbody>
        </table>
      </div>
    </section>`,
  styles: [`
    .policy-preview { padding: 16px; margin-bottom: 24px; border: 1px solid var(--workspace-border,#dce2ea); border-radius: 12px; background: var(--theme-soft-surface,#f5f8fa); }
    h3 { margin: 0 0 8px; font-size: 1rem; } p { font-size: .85rem; color: var(--edu-muted,#64748b); }
    .policy-preview__actions { display: flex; flex-wrap: wrap; gap: 8px; }
    .policy-preview__table { overflow-x: auto; margin-top: 16px; } table { width: 100%; border-collapse: collapse; text-align: start; }
    th,td { padding: 8px; border-bottom: 1px solid var(--workspace-border,#dce2ea); font-size: .8rem; } th { white-space: nowrap; }
  `],
})
export class SecurityPolicyPreview {
  private readonly uam = inject(UamService);
  private readonly destroyRef = inject(DestroyRef);
  readonly ns = 'accessManagement.securityPolicies';
  @Input() values: HqAppDialogFormValues = {};
  @Output() readonly apply = new EventEmitter<Record<string, boolean | number>>();
  policy: EffectivePolicyPreview | null = null;
  templates: PolicyTemplate[] = [];
  loadedScope = '';
  loading = false;
  error = '';
  get scopeKey(): string { return `${this.values['tenantId'] ?? ''}|${this.values['applicationId'] ?? ''}`; }

  load(): void {
    const scope = this.scopeKey;
    const params = new URLSearchParams();
    if (this.values['tenantId']) params.set('tenantId', String(this.values['tenantId']));
    if (this.values['applicationId']) params.set('applicationId', String(this.values['applicationId']));
    this.loading = true;
    this.error = '';
    this.uam.get<EffectivePolicyPreview>('/security-policies/effective?' + params).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => { this.policy = result; this.loadedScope = scope; this.loading = false; },
      error: error => { this.error = apiErrorMessage(error, 'Could not load the effective policy'); this.loading = false; },
    });
    this.uam.get<PolicyTemplate[]>('/security-policies/templates?' + params).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: templates => this.templates = templates,
      error: () => this.templates = [],
    });
  }

  label(key: string): string { return SECURITY_POLICY_CRUD_CONFIG.fields.find(field => field.key === key)?.label ?? key; }
  value(value: boolean | number): string { return String(value); }
  sources(ids: string[]): string {
    return ids.map(id => id === 'BASELINE' ? 'Platform baseline' : this.policy?.policies.find(policy => policy.id === id)?.name || id).join(', ');
  }
}
