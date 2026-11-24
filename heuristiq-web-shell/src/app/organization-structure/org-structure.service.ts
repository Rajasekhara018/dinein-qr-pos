import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';
import { ORG_API_BASE } from './org-structure.constants';
import { OrgEnvelope, unwrapOrgResponse, wrapOrgRequest } from './org-structure.helpers';
import {
  ChangePayload, ChangeType, CreateEmployeeRequest, CreateLoginsRequest, OrgChange, OrgDraft, OrgDraftResult, OrgDepartmentImportErrorRow, OrgEmployee, OrgImportBatch, OrgImportErrorRow, OrgLadder, OrgLadderImportErrorRow,
  OrgLoginCandidate, OrgLoginRun, OrgPerson, OrgPosition, OrgRoleRule, OrgUnit,
} from './org-structure.models';

/** Talks to Rider_UAM's /org-structure API (departments, ladders, people), edited as a draft and published. */
@Injectable({ providedIn: 'root' })
export class OrgStructureService {
  private readonly base = `${environment.uamApiUrl}${ORG_API_BASE}`;

  constructor(private http: HttpClient, private translate: TranslateService) {}

  // ---- draft -------------------------------------------------------------------------------

  openDraft(note: string | null = null): Observable<OrgDraft> {
    return this.post<OrgDraft>('/drafts', { note });
  }

  currentDraft(): Observable<OrgDraft> {
    return this.get<OrgDraft>('/drafts/current');
  }

  changes(draftId: string): Observable<OrgChange[]> {
    return this.get<OrgChange[]>(`/drafts/${draftId}/changes`);
  }

  addChange(draftId: string, type: ChangeType, payload: ChangePayload): Observable<OrgChange> {
    return this.post<OrgChange>(`/drafts/${draftId}/changes`, { type, payload });
  }

  removeChange(draftId: string, changeId: string): Observable<void> {
    return this.http.delete<OrgEnvelope<void>>(`${this.base}/drafts/${draftId}/changes/${changeId}`)
      .pipe(map((res) => this.unwrap(res)));
  }

  preview(draftId: string): Observable<OrgDraftResult> {
    return this.postEmpty<OrgDraftResult>(`/drafts/${draftId}/preview`);
  }

  publish(draftId: string): Observable<OrgDraftResult> {
    return this.postEmpty<OrgDraftResult>(`/drafts/${draftId}/publish`);
  }

  discard(draftId: string): Observable<void> {
    return this.postEmpty<void>(`/drafts/${draftId}/discard`);
  }

  /** Undoes the most recent publish; the API refuses when something newer exists. */
  rollbackLast(): Observable<void> {
    return this.postEmpty<void>('/rollback');
  }

  // ---- live organization -----------------------------------------------------------------------

  /** Direct sub-units of a unit, or the top-level units when no parent is given. */
  children(parentId?: string): Observable<OrgUnit[]> {
    return this.get<OrgUnit[]>('/units', parentId ? { parentId } : undefined);
  }

  searchUnits(query: string, limit: number): Observable<OrgUnit[]> {
    return this.get<OrgUnit[]>('/units/search', { q: query, limit });
  }

  /** Everyone under a unit (its sub-units included); pass the last employee id to get the next page. */
  people(unitId: string, afterId: string | null, limit: number): Observable<OrgPerson[]> {
    return this.get<OrgPerson[]>(`/units/${unitId}/people`, afterId ? { afterId, limit } : { limit });
  }

  ladders(): Observable<OrgLadder[]> {
    return this.get<OrgLadder[]>('/ladders');
  }

  positions(ladderId: string): Observable<OrgPosition[]> {
    return this.get<OrgPosition[]>(`/ladders/${ladderId}/positions`);
  }

  /** Adds a person to the employee list (an employee need not have a login; `userId` links an existing user). */
  createEmployee(request: CreateEmployeeRequest): Observable<OrgEmployee> {
    return this.post<OrgEmployee>('/employees', request);
  }

  searchEmployees(query: string, limit: number): Observable<OrgEmployee[]> {
    return this.get<OrgEmployee[]>('/employees', { search: query, limit });
  }

  // ---- bulk import -------------------------------------------------------------------------

  /**
   * Starts an import from a CSV already uploaded to the document store; the server validates every row and
   * stages the result without changing the live organization.
   */
  createImport(documentId: string): Observable<OrgImportBatch> {
    return this.post<OrgImportBatch>('/imports', { documentId });
  }

  recentImports(limit: number): Observable<OrgImportBatch[]> {
    return this.get<OrgImportBatch[]>('/imports', { limit });
  }

  importBatch(id: string): Observable<OrgImportBatch> {
    return this.get<OrgImportBatch>(`/imports/${id}`);
  }

  importErrors(id: string, afterRow: number, limit: number): Observable<OrgImportErrorRow[]> {
    return this.get<OrgImportErrorRow[]>(`/imports/${id}/errors`, { afterRow, limit });
  }

  confirmImport(id: string): Observable<OrgImportBatch> {
    return this.postEmpty<OrgImportBatch>(`/imports/${id}/confirm`);
  }

  cancelImport(id: string): Observable<void> {
    return this.postEmpty<void>(`/imports/${id}/cancel`);
  }

  // ---- bulk department import --------------------------------------------------------------

  /** Starts a department import from a CSV already uploaded to the document store; validated and staged like an employee import. */
  createDepartmentImport(documentId: string): Observable<OrgImportBatch> {
    return this.post<OrgImportBatch>('/department-imports', { documentId });
  }

  recentDepartmentImports(limit: number): Observable<OrgImportBatch[]> {
    return this.get<OrgImportBatch[]>('/department-imports', { limit });
  }

  departmentImportBatch(id: string): Observable<OrgImportBatch> {
    return this.get<OrgImportBatch>(`/department-imports/${id}`);
  }

  departmentImportErrors(id: string, afterRow: number, limit: number): Observable<OrgDepartmentImportErrorRow[]> {
    return this.get<OrgDepartmentImportErrorRow[]>(`/department-imports/${id}/errors`, { afterRow, limit });
  }

  confirmDepartmentImport(id: string): Observable<OrgImportBatch> {
    return this.postEmpty<OrgImportBatch>(`/department-imports/${id}/confirm`);
  }

  cancelDepartmentImport(id: string): Observable<void> {
    return this.postEmpty<void>(`/department-imports/${id}/cancel`);
  }

  /** The ladders and positions import: the same steps as the department import, on /ladder-imports. */
  createLadderImport(documentId: string): Observable<OrgImportBatch> {
    return this.post<OrgImportBatch>('/ladder-imports', { documentId });
  }

  recentLadderImports(limit: number): Observable<OrgImportBatch[]> {
    return this.get<OrgImportBatch[]>('/ladder-imports', { limit });
  }

  ladderImportBatch(id: string): Observable<OrgImportBatch> {
    return this.get<OrgImportBatch>(`/ladder-imports/${id}`);
  }

  ladderImportErrors(id: string, afterRow: number, limit: number): Observable<OrgLadderImportErrorRow[]> {
    return this.get<OrgLadderImportErrorRow[]>(`/ladder-imports/${id}/errors`, { afterRow, limit });
  }

  confirmLadderImport(id: string): Observable<OrgImportBatch> {
    return this.postEmpty<OrgImportBatch>(`/ladder-imports/${id}/confirm`);
  }

  cancelLadderImport(id: string): Observable<void> {
    return this.postEmpty<void>(`/ladder-imports/${id}/cancel`);
  }

  // ---- role rules and logins ---------------------------------------------------------------

  roleRules(): Observable<OrgRoleRule[]> {
    return this.get<OrgRoleRule[]>('/role-rules');
  }

  createRoleRule(unitId: string | null, positionId: string | null, roleCode: string): Observable<OrgRoleRule> {
    return this.post<OrgRoleRule>('/role-rules', { unitId, positionId, roleCode });
  }

  deleteRoleRule(ruleId: string): Observable<void> {
    return this.http.delete<OrgEnvelope<void>>(`${this.base}/role-rules/${ruleId}`).pipe(map((res) => this.unwrap(res)));
  }

  /** Preview: employees without a login and the roles the rules would give them; creates nothing. */
  loginCandidates(unitId: string | null, afterId: string | null, limit: number): Observable<OrgLoginCandidate[]> {
    const params: Record<string, string | number> = { limit };
    if (unitId) params['unitId'] = unitId;
    if (afterId) params['afterId'] = afterId;
    return this.get<OrgLoginCandidate[]>('/logins/candidates', params);
  }

  createLogins(request: CreateLoginsRequest): Observable<OrgLoginRun> {
    return this.post<OrgLoginRun>('/logins', request);
  }

  // ---- plumbing --------------------------------------------------------------------------------

  private get<T>(path: string, params?: Record<string, string | number>): Observable<T> {
    let httpParams = new HttpParams();
    Object.entries(params ?? {}).forEach(([key, value]) => {
      if (value !== '' && value !== undefined && value !== null) {
        httpParams = httpParams.set(key, String(value));
      }
    });
    return this.http.get<OrgEnvelope<T>>(`${this.base}${path}`, { params: httpParams }).pipe(map((res) => this.unwrap(res)));
  }

  private post<T>(path: string, body: unknown): Observable<T> {
    return this.http.post<OrgEnvelope<T>>(`${this.base}${path}`, wrapOrgRequest(body)).pipe(map((res) => this.unwrap(res)));
  }

  private postEmpty<T>(path: string): Observable<T> {
    return this.http.post<OrgEnvelope<T>>(`${this.base}${path}`, {}).pipe(map((res) => this.unwrap(res)));
  }

  private unwrap<T>(res: OrgEnvelope<T>): T {
    return unwrapOrgResponse(res, this.translate.instant('shared.errors.requestFailed'));
  }
}
