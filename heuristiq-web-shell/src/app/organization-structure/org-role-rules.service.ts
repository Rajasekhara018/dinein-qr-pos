import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { UamRole } from '../shared/models/uam.models';
import { UamService } from '../shared/services/uam-service';
import { ROLE_OPTIONS_LIMIT } from './org-structure.constants';
import { OrgRoleRule } from './org-structure.models';
import { OrgStructureService } from './org-structure.service';

/**
 * Where the role rules and the roles they may give are read from. The Org logins screen and the "Roles" dialog of the Org builder both use
 * it, so they always show the same rules and offer the same roles.
 */
@Injectable({ providedIn: 'root' })
export class OrgRoleRulesService {
  private readonly org = inject(OrgStructureService);
  private readonly uam = inject(UamService);

  /** Every role rule of the organization. */
  rules(): Observable<OrgRoleRule[]> {
    return this.org.roleRules();
  }

  /** The active roles this person may see (the organization's own and the shared ones), up to the dropdown limit. */
  roles(): Observable<UamRole[]> {
    return this.uam.list<UamRole>('/roles', 0, ROLE_OPTIONS_LIMIT).pipe(
      map((page) => (page.content ?? []).filter((role) => role.active !== false)),
    );
  }
}
