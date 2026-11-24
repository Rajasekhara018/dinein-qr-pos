import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { UamCommonResponse } from '../shared/models/uam.models';
import { ApplicationReadinessDetail, ReadinessResult } from './application-readiness.models';
import { GoLiveBlockedDialog, GoLiveBlockedDialogData } from './go-live-blocked-dialog/go-live-blocked-dialog';
import {
  GOVERNANCE_FORBIDDEN_STATUS,
  goLiveOverridePath,
  goLiveStepsPath,
  governancePath,
} from './go-live-governance.constants';
import { httpStatusOf, parseGoLiveBlocked } from './go-live-governance.helpers';
import { GoLiveDialogResult, GovernanceState } from './go-live-governance.models';

/**
 * Go-live governance: the go-live refusal dialog, the SUPER_ADMIN override and the governance state. Reads never throw into a view (a result state instead); writes fail as errors the caller maps with
 * `governanceFailure`. Which organization it is about is decided by Rider_UAM from the session token.
 */
@Injectable({ providedIn: 'root' })
export class GoLiveGovernanceService {
  private readonly http = inject(HttpClient);
  private readonly dialog = inject(MatDialog);
  private readonly base = environment.uamApiUrl;

  /** The steps an application needs before go-live, as UAM's readiness detail lists them. */
  steps(code: string): Observable<ReadinessResult<ApplicationReadinessDetail>> {
    return this.http.get<UamCommonResponse<ApplicationReadinessDetail>>(`${this.base}${goLiveStepsPath(code)}`).pipe(
      map((res) => (res?.success && res.responseObject ? { state: 'ready' as const, data: res.responseObject } : this.unavailable<ApplicationReadinessDetail>())),
      catchError((err: unknown) => of(this.failure<ApplicationReadinessDetail>(err))),
    );
  }

  /** Last override and alert times of one application. */
  state(code: string): Observable<ReadinessResult<GovernanceState>> {
    return this.http.get<UamCommonResponse<GovernanceState>>(`${this.base}${governancePath(code)}`).pipe(
      map((res) => (res?.success && res.responseObject ? { state: 'ready' as const, data: this.normalise(res.responseObject) } : this.unavailable<GovernanceState>())),
      catchError((err: unknown) => of(this.failure<GovernanceState>(err))),
    );
  }

  /** SUPER_ADMIN only: switches the application on although required steps fail, recording the reason. */
  override(code: string, reason: string): Observable<GovernanceState> {
    return this.http.post<UamCommonResponse<GovernanceState>>(`${this.base}${goLiveOverridePath(code)}`, this.envelope({ reason: reason.trim() })).pipe(
      map((res) => this.unwrap(res)),
    );
  }

  /**
   * Call from the error handler of any switch-on. When the failure is the go-live refusal, the blocked dialog opens and the
   * answer is what the user did ('overridden' = the application is on now, so the caller refreshes; 'closed' = nothing
   * changed). Any other failure answers null and the caller shows its own error.
   */
  handleRefusal(err: unknown): Observable<GoLiveDialogResult | null> {
    const info = parseGoLiveBlocked(err);
    if (!info) return of(null);
    const data: GoLiveBlockedDialogData = { info };
    return this.dialog
      .open<GoLiveBlockedDialog, GoLiveBlockedDialogData, GoLiveDialogResult>(GoLiveBlockedDialog, {
        data,
        width: '640px',
        maxWidth: '94vw',
        autoFocus: false,
      })
      .afterClosed()
      .pipe(map((result) => result ?? 'closed'));
  }

  private envelope(payload: unknown): { requestObject: unknown; doesInputHaveEncryptedData: boolean } {
    return { requestObject: payload, doesInputHaveEncryptedData: false };
  }

  private unwrap(res: UamCommonResponse<GovernanceState> | null): GovernanceState {
    if (!res?.success || !res.responseObject) throw { status: 0, error: { message: res?.message ?? '' } };
    return this.normalise(res.responseObject);
  }

  private normalise(state: GovernanceState): GovernanceState {
    return {
      applicationCode: state.applicationCode,
      guardEnabled: state.guardEnabled !== false,
      override: state.override ? { ...state.override, blockingSteps: Array.isArray(state.override.blockingSteps) ? state.override.blockingSteps : [] } : null,
      alerts: state.alerts ?? null,
    };
  }

  private unavailable<T>(): ReadinessResult<T> {
    return { state: 'unavailable', data: null };
  }

  private failure<T>(err: unknown): ReadinessResult<T> {
    return { state: httpStatusOf(err) === GOVERNANCE_FORBIDDEN_STATUS ? 'forbidden' : 'unavailable', data: null };
  }
}
