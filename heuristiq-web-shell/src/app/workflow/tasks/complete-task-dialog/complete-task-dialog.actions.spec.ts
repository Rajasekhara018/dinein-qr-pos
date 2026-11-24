import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { CompleteTaskDialog } from './complete-task-dialog';

function make(variables: Record<string, unknown>, extras: { download?: unknown; router?: unknown } = {}) {
  const ref = { close: vi.fn() };
  const workflow = { changeRequestDetail: vi.fn().mockReturnValue(of({ request: { id: 'cr' }, diff: { entries: [{ path: 'a', change: 'CHANGED', before: 1, after: 2 }] } })) };
  const onboarding = {
    get: vi.fn().mockReturnValue(of({ answers: Object.fromEntries(Array.from({ length: 10 }, (_, i) => [`field_${i}`, i])), documents: [{ fieldKey: 'licence', label: 'L' }] })),
    downloadDocument: extras.download ?? vi.fn().mockReturnValue(of(new Blob(['x']))),
  };
  const router = extras.router ?? { createUrlTree: vi.fn((c: string[]) => c), serializeUrl: vi.fn((c: string[]) => c.join('/')) };
  const d = new CompleteTaskDialog(ref as never, { task: { taskId: 't', processInstanceId: 'p', processKey: 'k', variables } } as never, workflow as never, router as never, onboarding as never);
  d.ngOnInit();
  return { d, ref, workflow, onboarding, router };
}

const onboardingVars = { subjectType: 'ONBOARDING_SUBMISSION', subjectId: 'sub-1' };

describe('CompleteTaskDialog submit', () => {
  it('closes with the decision, the extra variables and the trimmed comment', () => {
    const { d, ref } = make({});
    d.decision = 'APPROVED';
    d.comment = '  looks fine ';
    d.variablesText = '{"x":1}';
    d.submit();
    expect(ref.close).toHaveBeenCalledWith({ variables: { x: 1, decision: 'APPROVED' }, comment: 'looks fine' });
    expect(d.invalidJson).toBe(false);
  });

  it('sends no decision variable when none was chosen', () => {
    const { d, ref } = make({});
    d.submit();
    expect(ref.close).toHaveBeenCalledWith({ variables: {}, comment: '' });
  });

  it('refuses invalid variable JSON and keeps the dialog open', () => {
    const { d, ref } = make({});
    d.variablesText = '[1,2]';
    d.submit();
    expect(d.invalidJson).toBe(true);
    expect(ref.close).not.toHaveBeenCalled();
  });

  it('closes with null on cancel', () => {
    const { d, ref } = make({});
    d.cancel();
    expect(ref.close).toHaveBeenCalledWith(null);
  });
});

describe('CompleteTaskDialog reading helpers', () => {
  it('lists existing variables with the core ones first and objects as JSON', () => {
    const { d } = make({ zed: 1, subjectId: 's', alpha: { a: 1 }, subjectType: 'T' });
    expect(d.existing.map((e) => e.key)).toEqual(['subjectId', 'subjectType', 'alpha', 'zed']);
    expect(d.existing.find((e) => e.key === 'alpha')?.value).toBe('{"a":1}');
  });

  it('turns field names into readable labels', () => {
    const { d } = make({});
    expect(d.humanise('fullName')).toBe('Full name');
    expect(d.humanise('vehicle_number')).toBe('Vehicle number');
    expect(d.humanise('a-b_c')).toBe('A b c');
    expect(d.humanise('')).toBe('');
  });

  it('formats file sizes', () => {
    expect(make({}).d.size(2048)).toBe('2.0 KB');
    expect(make({}).d.size(null)).toBe('—');
  });

  it('shows only the first 8 answers until all are expanded', () => {
    const { d } = make(onboardingVars);
    expect(d.submissionRows).toHaveLength(10);
    expect(d.visibleRows).toHaveLength(8);
    expect(d.hiddenRowCount).toBe(2);
    d.showAllAnswers = true;
    expect(d.visibleRows).toHaveLength(10);
  });

  it('has no ids or references for an unrelated task', () => {
    const { d } = make({ subjectId: 5, changeRequestId: '' });
    expect(d.changeRequestId).toBeNull();
    expect(d.submissionId).toBeNull();
    expect(d.submissionReference).toBeNull();
    expect(d.documents).toEqual([]);
    expect(d.diffRows).toEqual([]);
    d.retryChange();
    d.retrySubmission();
    expect(d.change).toBeNull();
  });

  it('computes the diff rows once per loaded change', () => {
    const { d } = make({ changeRequestId: 'cr' });
    const rows = d.diffRows;
    expect(rows).toEqual([{ path: 'a', kind: 'CHANGED', before: 1, after: 2 }]);
    expect(d.diffRows).toBe(rows);
  });

  it('treats a task without variables as unrelated', () => {
    const { d } = make(undefined as never);
    expect(d.reviewKind).toBe('NONE');
    expect(d.existing).toEqual([]);
  });

  it('opens the full change request page in a new tab', () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    const { d, router } = make({ changeRequestId: 'cr' });
    d.openInNewTab('CHANGE');
    expect((router as { createUrlTree: ReturnType<typeof vi.fn> }).createUrlTree).toHaveBeenCalledWith(['/admin', 'change-requests', 'cr']);
    expect(open).toHaveBeenCalledWith('/admin/change-requests/cr', '_blank', 'noopener');
    open.mockRestore();
  });
});

describe('CompleteTaskDialog documents', () => {
  const doc = { fieldKey: 'licence', label: 'L' } as never;
  let created: string[];
  beforeEach(() => {
    created = [];
    (URL as unknown as { createObjectURL: unknown }).createObjectURL = vi.fn(() => { const u = `blob:${created.length}`; created.push(u); return u; });
    (URL as unknown as { revokeObjectURL: unknown }).revokeObjectURL = vi.fn();
    vi.useFakeTimers();
  });
  afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); });

  it('points the pre-opened tab at the fetched file and revokes the address after a minute', () => {
    const tab = { location: { href: '' }, close: vi.fn() };
    vi.spyOn(window, 'open').mockReturnValue(tab as never);
    const { d, onboarding } = make(onboardingVars);
    d.openDocument(doc);
    expect(onboarding.downloadDocument).toHaveBeenCalledWith('sub-1', 'licence');
    expect(tab.location.href).toBe('blob:0');
    expect(d.openingDocument).toBeNull();
    vi.advanceTimersByTime(60_000);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:0');
  });

  it('opens a new tab itself when the browser blocked the pre-opened one', () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    const { d } = make(onboardingVars);
    d.openDocument(doc);
    expect(open).toHaveBeenLastCalledWith('blob:0', '_blank');
  });

  it('closes the tab and flags the failure when the file cannot be fetched', () => {
    const tab = { location: { href: '' }, close: vi.fn() };
    vi.spyOn(window, 'open').mockReturnValue(tab as never);
    const { d } = make(onboardingVars, { download: vi.fn().mockReturnValue(throwError(() => new Error('x'))) });
    d.openDocument(doc);
    expect(d.documentFailed).toBe(true);
    expect(d.openingDocument).toBeNull();
    expect(tab.close).toHaveBeenCalled();
  });

  it('ignores a click while one is already opening, or when there is no submission', () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    const none = make({});
    none.d.openDocument(doc);
    expect(open).not.toHaveBeenCalled();
    const busy = make(onboardingVars);
    busy.d.openingDocument = 'other';
    busy.d.openDocument(doc);
    expect(open).not.toHaveBeenCalled();
  });
});
