import { Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Observable, catchError, map, of, switchMap, tap } from 'rxjs';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import { FranchiseApi } from './franchise-api';

/**
 * Takes an agent out of a franchise: asks first, removes, and tells the person how it went. Shared by the members screen and My
 * franchises so both behave alike; an administrator removes through the administrator's call (any franchise of the organization). Emits true once the agent was removed, false when the person declined or
 * the server refused (the refusal is already shown).
 */
@Injectable({ providedIn: 'root' })
export class FranchiseAgentRemoval {
  constructor(
    private api: FranchiseApi,
    private dialogs: AppDialogService,
    private notify: NotificationService,
    private translate: TranslateService,
  ) {}

  /** @param asAdmin remove as an administrator (any franchise of the organization) instead of as the franchise's owner */
  confirmAndRemove(franchiseId: string, memberId: string, name: string, asAdmin = false): Observable<boolean> {
    return this.dialogs.confirm({
      title: this.translate.instant('accessManagement.franchises.removeAgent.title'),
      message: this.translate.instant('accessManagement.franchises.removeAgent.message', { name }),
      confirmText: this.translate.instant('accessManagement.franchises.removeAgent.button'),
      cancelText: this.translate.instant('shared.adminCrud.discard.cancel'),
      variant: 'danger',
    }).pipe(
      switchMap((confirmed) => {
        if (!confirmed) {
          return of(false);
        }
        const remove$ = asAdmin ? this.api.adminRemoveAgent(franchiseId, memberId) : this.api.removeAgent(franchiseId, memberId);
        return remove$.pipe(
          tap(() => this.notify.success(this.translate.instant('accessManagement.franchises.removeAgent.done', { name }))),
          map(() => true),
          catchError((err: { error?: { detail?: string; message?: string } }) => {
            this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.franchises.removeAgent.failed')));
            return of(false);
          }),
        );
      }),
    );
  }
}
