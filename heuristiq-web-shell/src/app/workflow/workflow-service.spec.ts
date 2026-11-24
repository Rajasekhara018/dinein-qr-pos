import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { environment } from '../../environments/environment';
import { parseVariablesJson, WorkflowService, workflowErrorMessage } from './workflow-service';

const BASE = environment.workflowApiUrl;
const ok = <T>(object: T) => ({ status: true, object });

describe('WorkflowService', () => {
  let service: WorkflowService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(WorkflowService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('tasks', () => {
    it('sends paging and every filter as query params and reads the total from X-Total-Count', () => {
      let result: unknown;
      service.tasks('proc', 'SUBJ', 'MINE', 2, 5, { q: 'abc', step: 's1', createdFrom: '2026-01-01', createdTo: '2026-02-01' })
        .subscribe((r) => (result = r));
      const req = http.expectOne((r) => r.url === `${BASE}/tasks`);
      expect(req.request.method).toBe('GET');
      const p = req.request.params;
      expect(p.get('page')).toBe('2');
      expect(p.get('size')).toBe('5');
      expect(p.get('q')).toBe('abc');
      expect(p.get('step')).toBe('s1');
      expect(p.get('createdFrom')).toBe('2026-01-01');
      expect(p.get('createdTo')).toBe('2026-02-01');
      expect(p.get('view')).toBe('MINE');
      expect(p.get('processKey')).toBe('proc');
      expect(p.get('subjectType')).toBe('SUBJ');
      req.flush(ok([{ taskId: 't1' }]), { headers: { 'X-Total-Count': '42' } });
      expect(result).toEqual({ items: [{ taskId: 't1' }], total: 42 });
    });

    it('omits optional params, treats view ALL as no view and defaults to page 0 size 20', () => {
      service.tasks(undefined, undefined, 'ALL').subscribe();
      const req = http.expectOne((r) => r.url === `${BASE}/tasks`);
      expect(req.request.params.keys().sort()).toEqual(['page', 'size']);
      expect(req.request.params.get('page')).toBe('0');
      expect(req.request.params.get('size')).toBe('20');
      req.flush(ok([]));
    });

    it('falls back to the item count when the total header is missing or invalid', () => {
      const totals: number[] = [];
      service.tasks().subscribe((r) => totals.push(r.total));
      http.expectOne((r) => r.url === `${BASE}/tasks`).flush(ok([{ taskId: 'a' }, { taskId: 'b' }]));
      service.tasks().subscribe((r) => totals.push(r.total));
      http.expectOne((r) => r.url === `${BASE}/tasks`).flush(ok([{ taskId: 'a' }]), { headers: { 'X-Total-Count': 'nope' } });
      service.tasks().subscribe((r) => totals.push(r.total));
      http.expectOne((r) => r.url === `${BASE}/tasks`).flush(ok([{ taskId: 'a' }]), { headers: { 'X-Total-Count': '-3' } });
      expect(totals).toEqual([2, 1, 1]);
    });

    it('treats a null object as an empty page', () => {
      let items: unknown[] | undefined;
      service.tasks().subscribe((r) => (items = r.items));
      http.expectOne((r) => r.url === `${BASE}/tasks`).flush({ status: true, object: null });
      expect(items).toEqual([]);
    });

    it('raises the backend message when the envelope says status false', () => {
      let err: unknown;
      service.tasks().subscribe({ error: (e) => (err = e) });
      http.expectOne((r) => r.url === `${BASE}/tasks`).flush({ status: false, errorMsg: 'nope' });
      expect(workflowErrorMessage(err)).toBe('nope');
    });
  });

  describe('task actions', () => {
    it('claims a task with an encoded id', () => {
      let done = false;
      service.claimTask('a/b').subscribe(() => (done = true));
      const req = http.expectOne(`${BASE}/tasks/a%2Fb/claim`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(ok(null));
      expect(done).toBe(true);
    });

    it('completes with variables, adding the comment only when given', () => {
      service.completeTask('t1', { decision: 'APPROVED' }, 'ok').subscribe();
      const first = http.expectOne(`${BASE}/tasks/t1/complete`);
      expect(first.request.body).toEqual({ variables: { decision: 'APPROVED' }, comment: 'ok' });
      first.flush(ok(null));
      service.completeTask('t1', {}).subscribe();
      const second = http.expectOne(`${BASE}/tasks/t1/complete`);
      expect(second.request.body).toEqual({ variables: {} });
      second.flush(ok(null));
    });

    it('releases, reassigns and comments', () => {
      service.releaseTask('t1').subscribe();
      http.expectOne(`${BASE}/tasks/t1/release`).flush(ok(null));
      service.reassignTask('t1', 'u9').subscribe();
      const re = http.expectOne(`${BASE}/tasks/t1/reassign`);
      expect(re.request.body).toEqual({ userId: 'u9' });
      re.flush(ok(null));
      service.addComment('t1', 'hello').subscribe();
      const c = http.expectOne(`${BASE}/tasks/t1/comments`);
      expect(c.request.body).toEqual({ text: 'hello' });
      c.flush(ok(null));
    });

    it('surfaces a failed action as an error', () => {
      let err: unknown;
      service.claimTask('t1').subscribe({ error: (e) => (err = e) });
      http.expectOne(`${BASE}/tasks/t1/claim`).flush({ status: false, errorMsg: 'taken' });
      expect(workflowErrorMessage(err)).toBe('taken');
    });

    it('uploads a file as multipart form data', () => {
      const file = new File(['x'], 'a.txt', { type: 'text/plain' });
      service.uploadAttachment('t1', file).subscribe();
      const req = http.expectOne(`${BASE}/tasks/t1/attachments`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body instanceof FormData).toBe(true);
      expect((req.request.body as FormData).get('file')).toBeInstanceOf(File);
      req.flush(ok(null));
    });

    it('downloads an attachment by saving the blob under the given name', () => {
      const create = vi.fn().mockReturnValue('blob:x');
      const revoke = vi.fn();
      (URL as unknown as { createObjectURL: unknown }).createObjectURL = create;
      (URL as unknown as { revokeObjectURL: unknown }).revokeObjectURL = revoke;
      const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
      let saved: HTMLAnchorElement | undefined;
      click.mockImplementation(function (this: HTMLAnchorElement) { saved = this; });
      service.downloadAttachment('d 1', 'file.pdf').subscribe();
      const req = http.expectOne(`${BASE}/attachments/d%201`);
      expect(req.request.responseType).toBe('blob');
      req.flush(new Blob(['x']));
      expect(create).toHaveBeenCalled();
      expect(saved?.download).toBe('file.pdf');
      expect(saved?.href).toContain('blob:x');
      expect(revoke).toHaveBeenCalledWith('blob:x');
      service.downloadAttachment('d2', '').subscribe();
      http.expectOne(`${BASE}/attachments/d2`).flush(new Blob(['y']));
      expect(saved?.download).toBe('attachment');
      click.mockRestore();
    });
  });

  describe('reads', () => {
    it('report sends only the filters given', () => {
      service.report('2026-01-01', undefined, 'p').subscribe();
      const req = http.expectOne((r) => r.url === `${BASE}/reports/summary`);
      expect(req.request.params.get('from')).toBe('2026-01-01');
      expect(req.request.params.has('to')).toBe(false);
      expect(req.request.params.get('processKey')).toBe('p');
      req.flush(ok({}));
      service.report('a', 'b').subscribe();
      const r2 = http.expectOne((r) => r.url === `${BASE}/reports/summary`);
      expect(r2.request.params.get('to')).toBe('b');
      expect(r2.request.params.has('processKey')).toBe(false);
      r2.flush(ok({}));
    });

    it('looks up an instance id by reference', () => {
      let id = '';
      service.instanceIdByReference('DRV 1').subscribe((v) => (id = v));
      http.expectOne(`${BASE}/instances/by-reference/DRV%201`).flush(ok({ instanceId: 'i-1' }));
      expect(id).toBe('i-1');
    });

    it('fetches timeline and instance', () => {
      service.timeline('i1').subscribe();
      http.expectOne(`${BASE}/instances/i1/timeline`).flush(ok({}));
      let inst: unknown;
      service.instance('i1').subscribe((v) => (inst = v));
      http.expectOne(`${BASE}/instances/i1`).flush(ok({ id: 'i1' }));
      expect(inst).toEqual({ id: 'i1' });
    });

    it('starts an instance and returns its id', () => {
      let id = '';
      service.startInstance('k/1', { subjectType: 'S', subjectId: '1' } as never).subscribe((v) => (id = v));
      const req = http.expectOne(`${BASE}/processes/k%2F1/instances`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ subjectType: 'S', subjectId: '1' });
      req.flush(ok({ instanceId: 'i9' }));
      expect(id).toBe('i9');
    });

    it('reads a change request', () => {
      service.changeRequestDetail('c 1').subscribe();
      http.expectOne(`${BASE}/change-requests/c%201`).flush(ok({}));
    });
  });

  describe('definitions', () => {
    it('lists definitions, defaulting null to empty', () => {
      let list: unknown[] | undefined;
      service.definitions().subscribe((v) => (list = v));
      http.expectOne(`${BASE}/definitions`).flush({ status: true, object: null });
      expect(list).toEqual([]);
    });

    it('adds the version param only when a version is given', () => {
      service.definition('k').subscribe();
      const a = http.expectOne((r) => r.url === `${BASE}/definitions/k`);
      expect(a.request.params.has('version')).toBe(false);
      a.flush(ok({}));
      service.definition('k', 3).subscribe();
      const b = http.expectOne((r) => r.url === `${BASE}/definitions/k`);
      expect(b.request.params.get('version')).toBe('3');
      b.flush(ok({}));
      service.definitionGraph('k', 2).subscribe();
      const g = http.expectOne((r) => r.url === `${BASE}/definitions/k/graph`);
      expect(g.request.params.get('version')).toBe('2');
      g.flush(ok({}));
    });

    it('validates, previews and deploys XML', () => {
      service.validateDefinition('<x/>').subscribe();
      const v = http.expectOne(`${BASE}/definitions/validate`);
      expect(v.request.body).toEqual({ xml: '<x/>' });
      v.flush(ok({}));
      service.previewGraph('<x/>').subscribe();
      const p = http.expectOne(`${BASE}/definitions/graph`);
      expect(p.request.body).toEqual({ xml: '<x/>' });
      p.flush(ok({}));
      service.deployDefinition('<x/>', 'note').subscribe();
      const d = http.expectOne(`${BASE}/definitions`);
      expect(d.request.method).toBe('POST');
      expect(d.request.body).toEqual({ xml: '<x/>', note: 'note' });
      d.flush(ok({}));
    });

    it('reads settings and migrates with the dry-run flag', () => {
      service.definitionSettings().subscribe();
      http.expectOne(`${BASE}/definitions/settings`).flush(ok({}));
      service.migrateRunning('k', true).subscribe();
      const m = http.expectOne((r) => r.url === `${BASE}/definitions/k/migrate`);
      expect(m.request.params.get('dryRun')).toBe('true');
      m.flush(ok({}));
    });

    it('handles proposals: list, read, propose, approve, reject, withdraw', () => {
      let list: unknown[] | undefined;
      service.proposals('k').subscribe((v) => (list = v));
      const l = http.expectOne((r) => r.url === `${BASE}/definitions/proposals`);
      expect(l.request.params.get('key')).toBe('k');
      l.flush({ status: true, object: null });
      expect(list).toEqual([]);
      service.proposal('p1').subscribe();
      http.expectOne(`${BASE}/definitions/proposals/p1`).flush(ok({}));
      service.propose('<x/>', 'why').subscribe();
      const pr = http.expectOne(`${BASE}/definitions/proposals`);
      expect(pr.request.body).toEqual({ xml: '<x/>', note: 'why' });
      pr.flush(ok({}));
      service.approveProposal('p1').subscribe();
      http.expectOne(`${BASE}/definitions/proposals/p1/approve`).flush(ok({}));
      service.rejectProposal('p1', 'bad').subscribe();
      const rj = http.expectOne(`${BASE}/definitions/proposals/p1/reject`);
      expect(rj.request.body).toEqual({ reason: 'bad' });
      rj.flush(ok(null));
      service.withdrawProposal('p1').subscribe();
      http.expectOne(`${BASE}/definitions/proposals/p1/withdraw`).flush(ok(null));
    });
  });
});

describe('workflowErrorMessage', () => {
  it('prefers errorMsg, then message, else null', () => {
    expect(workflowErrorMessage({ error: { errorMsg: 'a', message: 'b' } })).toBe('a');
    expect(workflowErrorMessage({ error: { message: 'b' } })).toBe('b');
    expect(workflowErrorMessage({ error: {} })).toBeNull();
    expect(workflowErrorMessage(null)).toBeNull();
  });
});

describe('parseVariablesJson', () => {
  it('treats blank as an empty object', () => {
    expect(parseVariablesJson('  ')).toEqual({ ok: true, value: {} });
    expect(parseVariablesJson(undefined as unknown as string)).toEqual({ ok: true, value: {} });
  });
  it('accepts a JSON object', () => {
    expect(parseVariablesJson('{"a":1}')).toEqual({ ok: true, value: { a: 1 } });
  });
  it('rejects arrays, scalars, null and invalid JSON', () => {
    expect(parseVariablesJson('[1]')).toEqual({ ok: false });
    expect(parseVariablesJson('5')).toEqual({ ok: false });
    expect(parseVariablesJson('null')).toEqual({ ok: false });
    expect(parseVariablesJson('{bad')).toEqual({ ok: false });
  });
});
