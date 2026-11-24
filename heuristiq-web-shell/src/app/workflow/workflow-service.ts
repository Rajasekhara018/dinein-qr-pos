import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import {
  BpmnGraph, ChangeRequestDetail, DefinitionProposal, DefinitionSettings, DefinitionValidation, InstanceTimeline, MigrationResult, TaskSearch, WorkflowReport, ProcessDefinitionDetail, ProcessDefinitionSummary,
  StartProcessRequest, TaskView, WorkflowInstance, WorkflowResponse, WorkflowTask,
} from './workflow.models';

/**
 * Talks to Rider_WorkflowService through the gateway's rider-workflow route
 * ({workflowApiUrl}/... = /api/v1/workflow/...). Responses use its
 * { status, object, errorMsg } envelope.
 */
@Injectable({ providedIn: 'root' })
export class WorkflowService {
  private readonly base = `${environment.workflowApiUrl}`;

  constructor(private http: HttpClient) {}

  /** One page of open tasks (newest first) and the total across all pages (the X-Total-Count header). */
  tasks(processKey?: string, subjectType?: string, view?: TaskView, page = 0, size = 20, filter: TaskSearch = {}): Observable<{ items: WorkflowTask[]; total: number }> {
    let params = new HttpParams().set('page', String(page)).set('size', String(size));
    if (filter.q) params = params.set('q', filter.q);
    if (filter.step) params = params.set('step', filter.step);
    if (filter.createdFrom) params = params.set('createdFrom', filter.createdFrom);
    if (filter.createdTo) params = params.set('createdTo', filter.createdTo);
    if (view && view !== 'ALL') params = params.set('view', view);
    if (processKey) params = params.set('processKey', processKey);
    if (subjectType) params = params.set('subjectType', subjectType);
    return this.http.get<WorkflowResponse<WorkflowTask[]>>(`${this.base}/tasks`, { params, observe: 'response' })
      .pipe(map((res) => {
        const items = this.unwrap(res.body as WorkflowResponse<WorkflowTask[]>) ?? [];
        const total = Number(res.headers.get('X-Total-Count'));
        return { items, total: Number.isFinite(total) && total >= 0 && res.headers.has('X-Total-Count') ? total : items.length };
      }));
  }

  /** Takes an unassigned task for the signed-in user. */
  claimTask(taskId: string): Observable<void> {
    return this.http.post<WorkflowResponse<unknown>>(`${this.base}/tasks/${encodeURIComponent(taskId)}/claim`, {})
      .pipe(map((res) => { this.unwrap(res); }));
  }

  completeTask(taskId: string, variables: Record<string, unknown>, comment?: string): Observable<void> {
    const body = comment ? { variables, comment } : { variables };
    return this.http.post<WorkflowResponse<unknown>>(`${this.base}/tasks/${encodeURIComponent(taskId)}/complete`, body)
      .pipe(map((res) => { this.unwrap(res); }));
  }

  /** Gives a task you took back to its pool. */
  releaseTask(taskId: string): Observable<void> {
    return this.http.post<WorkflowResponse<unknown>>(`${this.base}/tasks/${encodeURIComponent(taskId)}/release`, {})
      .pipe(map((res) => { this.unwrap(res); }));
  }

  /** Hands a task to another UAM user. */
  reassignTask(taskId: string, userId: string): Observable<void> {
    return this.http.post<WorkflowResponse<unknown>>(`${this.base}/tasks/${encodeURIComponent(taskId)}/reassign`, { userId })
      .pipe(map((res) => { this.unwrap(res); }));
  }

  /** Adds a note to an open task. */
  addComment(taskId: string, text: string): Observable<void> {
    return this.http.post<WorkflowResponse<unknown>>(`${this.base}/tasks/${encodeURIComponent(taskId)}/comments`, { text })
      .pipe(map((res) => { this.unwrap(res); }));
  }

  /** Volumes, step durations, SLA misses and reviewer workload; dates are yyyy-MM-dd (India time). */
  report(from?: string, to?: string, processKey?: string): Observable<WorkflowReport> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    if (processKey) params = params.set('processKey', processKey);
    return this.http.get<WorkflowResponse<WorkflowReport>>(`${this.base}/reports/summary`, { params })
      .pipe(map((res) => this.unwrap(res)));
  }

  /** The newest request started with this short reference (e.g. DRV-2026-000042). */
  instanceIdByReference(reference: string): Observable<string> {
    return this.http.get<WorkflowResponse<{ instanceId: string }>>(`${this.base}/instances/by-reference/${encodeURIComponent(reference)}`)
      .pipe(map((res) => this.unwrap(res).instanceId));
  }

  /** Attaches a file to an open task. */
  uploadAttachment(taskId: string, file: File): Observable<void> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<WorkflowResponse<unknown>>(`${this.base}/tasks/${encodeURIComponent(taskId)}/attachments`, form)
      .pipe(map((res) => { this.unwrap(res); }));
  }

  /** Downloads an attached file (the call carries the user's login, so it is fetched and then saved, not linked). */
  downloadAttachment(documentId: string, fileName: string): Observable<void> {
    return this.http.get(`${this.base}/attachments/${encodeURIComponent(documentId)}`, { responseType: 'blob' })
      .pipe(map((blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName || 'attachment';
        link.click();
        URL.revokeObjectURL(url);
      }));
  }

  /** Who did what, when, to a request: the end-to-end history. */
  timeline(instanceId: string): Observable<InstanceTimeline> {
    return this.http.get<WorkflowResponse<InstanceTimeline>>(`${this.base}/instances/${encodeURIComponent(instanceId)}/timeline`)
      .pipe(map((res) => this.unwrap(res)));
  }

  startInstance(processKey: string, request: StartProcessRequest): Observable<string> {
    return this.http.post<WorkflowResponse<{ instanceId: string }>>(
      `${this.base}/processes/${encodeURIComponent(processKey)}/instances`, request)
      .pipe(map((res) => this.unwrap(res).instanceId));
  }

  instance(instanceId: string): Observable<WorkflowInstance> {
    return this.http.get<WorkflowResponse<WorkflowInstance>>(`${this.base}/instances/${encodeURIComponent(instanceId)}`)
      .pipe(map((res) => this.unwrap(res)));
  }

  // ===== Process definitions =====

  definitions(): Observable<ProcessDefinitionSummary[]> {
    return this.http.get<WorkflowResponse<ProcessDefinitionSummary[]>>(`${this.base}/definitions`)
      .pipe(map((res) => this.unwrap(res) ?? []));
  }

  definition(key: string, version?: number): Observable<ProcessDefinitionDetail> {
    return this.http.get<WorkflowResponse<ProcessDefinitionDetail>>(
      `${this.base}/definitions/${encodeURIComponent(key)}`, { params: this.versionParams(version) })
      .pipe(map((res) => this.unwrap(res)));
  }

  definitionGraph(key: string, version?: number): Observable<BpmnGraph> {
    return this.http.get<WorkflowResponse<BpmnGraph>>(
      `${this.base}/definitions/${encodeURIComponent(key)}/graph`, { params: this.versionParams(version) })
      .pipe(map((res) => this.unwrap(res)));
  }

  /** Checks edited XML without deploying it. */
  validateDefinition(xml: string): Observable<DefinitionValidation> {
    return this.http.post<WorkflowResponse<DefinitionValidation>>(`${this.base}/definitions/validate`, { xml })
      .pipe(map((res) => this.unwrap(res)));
  }

  /** The graph of edited XML, for the live preview. */
  previewGraph(xml: string): Observable<BpmnGraph> {
    return this.http.post<WorkflowResponse<BpmnGraph>>(`${this.base}/definitions/graph`, { xml })
      .pipe(map((res) => this.unwrap(res)));
  }

  /** Deploys the XML as a new version of its process key. */
  deployDefinition(xml: string, note?: string): Observable<ProcessDefinitionSummary> {
    return this.http.post<WorkflowResponse<ProcessDefinitionSummary>>(`${this.base}/definitions`, { xml, note })
      .pipe(map((res) => this.unwrap(res)));
  }

  definitionSettings(): Observable<DefinitionSettings> {
    return this.http.get<WorkflowResponse<DefinitionSettings>>(`${this.base}/definitions/settings`)
      .pipe(map((res) => this.unwrap(res)));
  }

  /** Moves running requests on older versions of a flow onto its newest; a dry run only reports what would move. */
  migrateRunning(key: string, dryRun: boolean): Observable<MigrationResult> {
    return this.http.post<WorkflowResponse<MigrationResult>>(
      `${this.base}/definitions/${encodeURIComponent(key)}/migrate`, {}, { params: new HttpParams().set('dryRun', String(dryRun)) })
      .pipe(map((res) => this.unwrap(res)));
  }

  proposals(key: string): Observable<DefinitionProposal[]> {
    return this.http.get<WorkflowResponse<DefinitionProposal[]>>(`${this.base}/definitions/proposals`, { params: new HttpParams().set('key', key) })
      .pipe(map((res) => this.unwrap(res) ?? []));
  }

  proposal(id: string): Observable<DefinitionProposal> {
    return this.http.get<WorkflowResponse<DefinitionProposal>>(`${this.base}/definitions/proposals/${encodeURIComponent(id)}`)
      .pipe(map((res) => this.unwrap(res)));
  }

  /** Puts a change forward for a second person to approve. */
  propose(xml: string, note: string): Observable<DefinitionProposal> {
    return this.http.post<WorkflowResponse<DefinitionProposal>>(`${this.base}/definitions/proposals`, { xml, note })
      .pipe(map((res) => this.unwrap(res)));
  }

  approveProposal(id: string): Observable<ProcessDefinitionSummary> {
    return this.http.post<WorkflowResponse<ProcessDefinitionSummary>>(`${this.base}/definitions/proposals/${encodeURIComponent(id)}/approve`, {})
      .pipe(map((res) => this.unwrap(res)));
  }

  rejectProposal(id: string, reason: string): Observable<void> {
    return this.http.post<WorkflowResponse<unknown>>(`${this.base}/definitions/proposals/${encodeURIComponent(id)}/reject`, { reason })
      .pipe(map((res) => { this.unwrap(res); }));
  }

  withdrawProposal(id: string): Observable<void> {
    return this.http.post<WorkflowResponse<unknown>>(`${this.base}/definitions/proposals/${encodeURIComponent(id)}/withdraw`, {})
      .pipe(map((res) => { this.unwrap(res); }));
  }

  private versionParams(version?: number): HttpParams {
    return version ? new HttpParams().set('version', String(version)) : new HttpParams();
  }

  /** A change request with its before/after values and field-level difference, for the reviewer. */
  changeRequestDetail(id: string): Observable<ChangeRequestDetail> {
    return this.http.get<WorkflowResponse<ChangeRequestDetail>>(`${this.base}/change-requests/${encodeURIComponent(id)}`)
      .pipe(map((res) => this.unwrap(res)));
  }

  private unwrap<T>(res: WorkflowResponse<T>): T {
    if (res && res.status === false) {
      throw { error: { errorMsg: res.errorMsg } };
    }
    return res?.object as T;
  }
}

/** The message the backend sent with a failed call (envelope errorMsg), if any. */
export function workflowErrorMessage(err: unknown): string | null {
  const e = err as { error?: { errorMsg?: string; message?: string } } | null;
  return e?.error?.errorMsg || e?.error?.message || null;
}

/** Parses an optional JSON-object text field; blank is an empty object, anything else must be an object. */
export function parseVariablesJson(text: string): { ok: true; value: Record<string, unknown> } | { ok: false } {
  const trimmed = (text ?? '').trim();
  if (!trimmed) return { ok: true, value: {} };
  try {
    const parsed: unknown = JSON.parse(trimmed);
    if (parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return { ok: true, value: parsed as Record<string, unknown> };
    }
  } catch {
    // fall through
  }
  return { ok: false };
}
