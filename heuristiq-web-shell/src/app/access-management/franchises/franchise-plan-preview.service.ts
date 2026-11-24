import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqPrivilegeAccessService } from 'hq-shared-lib';
import { Observable, catchError, forkJoin, map, of } from 'rxjs';
import { PARTY_LABEL_KEYS } from '../wallet-statement/wallet-statement.constants';
import { FranchiseApi } from './franchise-api';
import { LOCATION_TYPE_LABEL_KEYS } from './franchise-locations.constants';
import { appliedRuleRows, eventTypeLabel, isoDay, planSummary } from './franchise-named-plans.helpers';
import { GST_LABEL_KEYS, NAMED_PLAN_LIST_PRIVILEGES } from './franchise-packages.constants';
import { AppliedRule, NamedPlanKind, NamedPlanRow, PackageTemplate } from './franchise-packages.models';
import { namedPlanName, namedPlanRuleRows } from './franchise-plan-preview.helpers';
import { PREVIEW_KINDS } from './franchise-plan-preview.constants';
import { PlanPreview, PlanPreviewResult } from './franchise-plan-preview.models';

/**
 * What a franchise will follow for one application before it is opened (or for one whose package is not known): the commission and
 * referral of the chosen package's named plans, or the application's rules that apply when it picks none, per event type and type of
 * place. Reads only what the caller may list.
 */
@Injectable({ providedIn: 'root' })
export class FranchisePlanPreviewService {
  private readonly api = inject(FranchiseApi);
  private readonly access = inject(HqPrivilegeAccessService);
  private readonly translate = inject(TranslateService);

  preview(applicationCode: string, template: PackageTemplate | null): Observable<PlanPreview> {
    const [commission, referral] = PREVIEW_KINDS.map(({ kind, code }) => this.section(kind, applicationCode, template ? code(template)?.trim() ?? '' : ''));
    return forkJoin({ commission, referral });
  }

  /** The application's rules that apply (no named plan) for one kind; null without the privilege to list them. */
  rules(kind: Extract<NamedPlanKind, 'commission' | 'referral'>, applicationCode: string): Observable<PlanPreviewResult> {
    return this.section(kind, applicationCode, '');
  }

  private section(kind: NamedPlanKind, applicationCode: string, planCode: string): Observable<PlanPreviewResult> {
    if (!applicationCode || !this.access.can(NAMED_PLAN_LIST_PRIVILEGES[kind])) { return of(null); }
    const today = isoDay(new Date());
    const read = planCode ? this.api.namedPlans(kind, applicationCode) : this.api.appliedRules(kind, applicationCode);
    return read.pipe(
      map((rows): PlanPreviewResult => planCode
        ? { source: 'plan', planName: namedPlanName(rows, planCode), lines: this.lines(kind, namedPlanRuleRows(rows, planCode, today)) }
        : { source: 'rules', planName: '', lines: this.lines(kind, appliedRuleRows(rows, today)) }),
      catchError(() => of('failed' as const)),
    );
  }

  private lines(kind: NamedPlanKind, rows: readonly NamedPlanRow[]): AppliedRule[] {
    const labels = { gst: (code: string) => this.label(GST_LABEL_KEYS, code), party: (code: string) => this.label(PARTY_LABEL_KEYS, code) };
    return rows.map((row) => ({
      eventType: row.scope.eventType ? eventTypeLabel(row.scope.eventType) : '',
      placeType: row.scope.placeType ? this.label(LOCATION_TYPE_LABEL_KEYS, row.scope.placeType) : '',
      summary: planSummary(kind, row, labels),
    }));
  }

  private label(keys: Readonly<Record<string, string>>, code: string): string {
    const key = keys[code];
    return key ? this.translate.instant(key) : code;
  }
}
