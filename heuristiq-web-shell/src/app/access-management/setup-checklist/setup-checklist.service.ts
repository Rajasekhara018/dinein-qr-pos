import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { UamCommonResponse } from '../../shared/models/uam.models';
import { SETUP_CHECKLIST_PATH } from './setup-checklist.constants';
import { SetupChecklist } from './setup-checklist.models';

/** Reads the organization set-up checklist from the franchise service; the organization is the signed-in session's. */
@Injectable({ providedIn: 'root' })
export class SetupChecklistApi {
  private readonly http = inject(HttpClient);
  private readonly translate = inject(TranslateService);

  checklist(): Observable<SetupChecklist> {
    return this.http.get<UamCommonResponse<SetupChecklist>>(`${environment.franchiseApiUrl}${SETUP_CHECKLIST_PATH}`).pipe(map((res) => {
      if (!res.success || !res.responseObject) {
        const fallback = this.translate.instant('shared.errors.requestFailed');
        throw { error: { detail: res.message || fallback, message: res.message || fallback } };
      }
      return res.responseObject;
    }));
  }
}
