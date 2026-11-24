import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { UamService } from '../../shared/services/uam-service';

/**
 * The names of the people a workflow shows by user id (who decided, who a step is assigned to). Each id is read from UAM once
 * per page and remembered; an id UAM cannot answer for is shown as it is.
 */
@Injectable({ providedIn: 'root' })
export class WorkflowPeopleService {
  private readonly uam = inject(UamService);
  private readonly known = new Map<string, string>();

  /** The display name of each id (the id itself when it cannot be read). */
  names(ids: readonly (string | null | undefined)[]): Observable<Record<string, string>> {
    const wanted = Array.from(new Set(ids.filter((id): id is string => !!id && id.trim().length > 0)));
    const missing = wanted.filter((id) => !this.known.has(id));
    const reads = missing.map((id) => this.uam.getUser(id).pipe(
      map((u) => [id, u.displayName || u.userName || id] as const),
      catchError(() => of([id, id] as const)),
    ));
    return (reads.length ? forkJoin(reads) : of([] as (readonly [string, string])[])).pipe(
      map((pairs) => {
        for (const [id, name] of pairs) this.known.set(id, name);
        return Object.fromEntries(wanted.map((id) => [id, this.known.get(id) ?? id]));
      }),
    );
  }
}
