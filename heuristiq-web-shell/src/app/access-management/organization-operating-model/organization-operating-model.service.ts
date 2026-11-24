import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { UamService } from '../../shared/services/uam-service';
import { OPERATING_MODEL_SEGMENT, organizationPath } from './organization-operating-model.constants';
import { futureChanges } from './organization-operating-model.helpers';
import { OperatingModel, OperatingModelChange, OperatingModelView, ScheduledOperatingModelChange } from './organization-operating-model.types';

/**
 * An organization's operating model in UAM. UAM keeps the changes already in force whatever is sent, so saving always sends
 * the whole list of changes that have not started yet: adding one is "the future ones + the new one", cancelling one is
 * "the future ones without it".
 */
@Injectable({ providedIn: 'root' })
export class OrganizationOperatingModelService {
  constructor(private uam: UamService) {}

  load(tenantId: string): Observable<OperatingModelView> {
    return this.uam.get<OperatingModelView>(`${organizationPath(tenantId)}/${OPERATING_MODEL_SEGMENT}`);
  }

  /** Schedules a switch to a model from a day, keeping the other scheduled changes. */
  schedule(tenantId: string, view: OperatingModelView, model: OperatingModel, effectiveFrom: string): Observable<OperatingModelView> {
    return this.save(tenantId, [...futureChanges(view), { model, effectiveFrom }]);
  }

  /** Cancels one change that has not started yet. */
  cancel(tenantId: string, view: OperatingModelView, change: OperatingModelChange): Observable<OperatingModelView> {
    return this.save(tenantId, futureChanges(view).filter((c) => c.effectiveFrom !== change.effectiveFrom));
  }

  private save(tenantId: string, scheduledChanges: ScheduledOperatingModelChange[]): Observable<OperatingModelView> {
    return this.uam.update<{ scheduledChanges: ScheduledOperatingModelChange[] }, OperatingModelView>(
      organizationPath(tenantId), OPERATING_MODEL_SEGMENT, { scheduledChanges });
  }
}
