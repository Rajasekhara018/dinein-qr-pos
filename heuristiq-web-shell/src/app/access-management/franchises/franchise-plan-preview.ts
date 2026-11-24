import { Component, Input } from '@angular/core';
import { PLAN_PREVIEW_I18N } from './franchise-plan-preview.constants';
import { PlanPreview, PlanPreviewResult, PlanPreviewSection } from './franchise-plan-preview.models';

/**
 * Shows what a franchise follows for one application: its joining fee and agent places (when given), then its commission and its
 * referral, each as the package's named plan or the application's rules that apply, one line per event type and type of place.
 */
@Component({
  selector: 'app-franchise-plan-preview',
  standalone: false,
  templateUrl: './franchise-plan-preview.html',
  styleUrl: './franchise-plan-preview.scss',
})
export class FranchisePlanPreview {
  @Input() preview: PlanPreview | null = null;
  /** The joining fee as text ('' to leave it out) and the agent places (null to leave them out). */
  @Input() feeText = '';
  @Input() agentLimit: number | null = null;
  @Input() loading = false;

  readonly i18n = PLAN_PREVIEW_I18N;
  readonly kinds = ['commission', 'referral'] as const;

  sectionOf(kind: 'commission' | 'referral'): PlanPreviewResult {
    return this.preview?.[kind] ?? null;
  }

  asSection(result: PlanPreviewResult): PlanPreviewSection | null {
    return result && result !== 'failed' ? result : null;
  }
}
