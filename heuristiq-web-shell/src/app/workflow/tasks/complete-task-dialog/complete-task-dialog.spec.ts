import { describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { MatDialogRef } from '@angular/material/dialog';
import { ChangeRequestDetail, WorkflowTask } from '../../workflow.models';
import { WorkflowService } from '../../workflow-service';
import { OnboardingAdminService } from '../../../shared/services/onboarding-admin-service';
import { CompleteTaskDialog, CompleteTaskDialogResult } from './complete-task-dialog';

function task(variables: Record<string, unknown>): WorkflowTask {
  return { taskId: 't-1', processInstanceId: 'p-1', processKey: 'change-approval', variables };
}

const detail: ChangeRequestDetail = {
  request: { id: 'cr-9', title: 'Change driverEarnings.incentives', status: 'PENDING', maker: 'u-1', subjectType: 'CONFIG_SETTING', subjectId: 'k' },
  before: { a: 1 },
  after: { a: 2 },
  diff: { entries: [{ path: 'a', change: 'CHANGED', before: 1, after: 2 }], added: 0, removed: 0, changed: 1 },
};

const submission = {
  id: 'sub-7',
  answers: { fullName: 'Asha Rao', vehicle_number: 'KA01AB1234' },
  documents: [{ fieldKey: 'licence', label: 'Driving licence', documentId: 'd-1', fileName: 'dl.pdf', sizeBytes: 2048, stored: true }],
};

function dialog(
  variables: Record<string, unknown>,
  workflow: Partial<WorkflowService> = {},
  router: Partial<Router> = {},
  onboarding: Partial<OnboardingAdminService> = { get: vi.fn().mockReturnValue(of(submission)) },
) {
  const ref = { close: vi.fn() } as unknown as MatDialogRef<CompleteTaskDialog, CompleteTaskDialogResult | null>;
  const d = new CompleteTaskDialog(ref, { task: task(variables) }, workflow as WorkflowService, router as Router, onboarding as OnboardingAdminService);
  d.ngOnInit();
  return d;
}

describe('CompleteTaskDialog review section', () => {
  it('loads the change request and exposes its before/after rows for a maker-checker task', () => {
    const changeRequestDetail = vi.fn().mockReturnValue(of(detail));
    const d = dialog({ changeRequestId: 'cr-9', subjectType: 'CONFIG_SETTING' }, { changeRequestDetail });

    expect(d.reviewKind).toBe('CHANGE');
    expect(changeRequestDetail).toHaveBeenCalledWith('cr-9');
    expect(d.change?.diff.entries[0]).toMatchObject({ path: 'a', before: 1, after: 2 });
    expect(d.changeLoading).toBe(false);
    expect(d.changeFailed).toBe(false);
  });

  it('shows a retryable failure when the change cannot be loaded', () => {
    const changeRequestDetail = vi.fn().mockReturnValueOnce(throwError(() => new Error('boom'))).mockReturnValueOnce(of(detail));
    const d = dialog({ changeRequestId: 'cr-9' }, { changeRequestDetail });

    expect(d.changeFailed).toBe(true);
    d.retryChange();
    expect(d.changeFailed).toBe(false);
    expect(d.change?.request.id).toBe('cr-9');
  });

  it('points an onboarding task at its submission and documents', () => {
    const d = dialog({ subjectType: 'ONBOARDING_SUBMISSION', subjectId: 'sub-7', reference: 'DRV-2026-000042' });

    expect(d.reviewKind).toBe('ONBOARDING');
    expect(d.submissionId).toBe('sub-7');
    expect(d.submissionReference).toBe('DRV-2026-000042');
  });

  it('loads what the applicant filled in and the documents they uploaded', () => {
    const get = vi.fn().mockReturnValue(of(submission));
    const d = dialog({ subjectType: 'ONBOARDING_SUBMISSION', subjectId: 'sub-7' }, {}, {}, { get });

    expect(get).toHaveBeenCalledWith('sub-7');
    expect(d.submissionRows).toEqual([
      { field: 'Full name', value: 'Asha Rao' },
      { field: 'Vehicle number', value: 'KA01AB1234' },
    ]);
    expect(d.documents[0].label).toBe('Driving licence');
  });

  it('shows a retryable failure when the submission cannot be loaded', () => {
    const get = vi.fn().mockReturnValueOnce(throwError(() => new Error('boom'))).mockReturnValueOnce(of(submission));
    const d = dialog({ subjectType: 'ONBOARDING_SUBMISSION', subjectId: 'sub-7' }, {}, {}, { get });

    expect(d.submissionFailed).toBe(true);
    d.retrySubmission();
    expect(d.submissionFailed).toBe(false);
    expect(d.submissionRows.length).toBe(2);
  });

  it('opens the right page in a new tab', () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    const router = {
      createUrlTree: vi.fn((commands: string[]) => ({ commands })),
      serializeUrl: vi.fn((tree: { commands: string[] }) => tree.commands.join('/').replace('//', '/')),
    };
    const d = dialog({ subjectType: 'ONBOARDING_SUBMISSION', subjectId: 'sub-7' }, {}, router as unknown as Router);

    d.openInNewTab('ONBOARDING');
    expect(router.createUrlTree).toHaveBeenCalledWith(['/onboarded-users', 'sub-7']);
    expect(open).toHaveBeenCalledWith('/onboarded-users/sub-7', '_blank', 'noopener');
    open.mockRestore();
  });

  it('has no review section for an unrelated task', () => {
    const d = dialog({ subjectType: 'SOMETHING_ELSE', subjectId: 'x' });
    expect(d.reviewKind).toBe('NONE');
  });

  it('formats values for reading', () => {
    const d = dialog({});
    expect(d.show(null)).toBe('—');
    expect(d.show('')).toBe('—');
    expect(d.show(5)).toBe('5');
    expect(d.show({ a: [1, 2] })).toBe('{"a":[1,2]}');
  });
});
