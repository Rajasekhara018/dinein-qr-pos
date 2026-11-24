import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { WorkflowHistoryService } from '../../workflow/history/workflow-history.service';
import { OnboardingAdminService } from '../../shared/services/onboarding-admin-service';
import { OnboardingReviewItem } from '../onboarding-reviews.models';
import { OnboardingReviewsList } from './onboarding-reviews-list';

const historyDialog = { open: vi.fn() };

describe('OnboardingReviewsList', () => {
  const onboarding = { listOpenReviews: vi.fn(), approveReview: vi.fn(), returnReview: vi.fn(), rejectReview: vi.fn() };
  const dialogs = { confirm: vi.fn(), prompt: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  const router = { navigate: vi.fn() };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };
  let fixture: ComponentFixture<OnboardingReviewsList>;

  const review = (over: Partial<OnboardingReviewItem> = {}): OnboardingReviewItem => ({
    taskId: 't1',
    submissionId: 's1',
    reference: 'DRV-2026-000042',
    workflowInstanceId: 'wf-1',
    typeCode: 'DRIVER',
    subjectId: null,
    answers: { fullName: 'Asha Rao', mobile: '9876543210', email: 'asha@example.com' },
    status: 'SUBMITTED' as OnboardingReviewItem['status'],
    createdAt: '2026-03-04T10:00:00Z',
    ...over,
  });

  function create(): OnboardingReviewsList {
    TestBed.configureTestingModule({
      declarations: [OnboardingReviewsList],
      imports: [TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        { provide: WorkflowHistoryService, useValue: historyDialog },
        { provide: Router, useValue: router },
        { provide: OnboardingAdminService, useValue: onboarding },
        { provide: AppDialogService, useValue: dialogs },
        { provide: NotificationService, useValue: notify },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    fixture = TestBed.createComponent(OnboardingReviewsList);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  function render(): void {
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
  }

  const text = (): string => fixture.nativeElement.textContent as string;
  const actionTitles = (): string[] =>
    Array.from(fixture.nativeElement.querySelectorAll('tbody .row-action-btn')).map((b) => (b as HTMLElement).getAttribute('title') ?? '');
  const button = (title: string): HTMLButtonElement => fixture.nativeElement.querySelector(`button[title="${title}"]`) as HTMLButtonElement;

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    onboarding.listOpenReviews.mockReturnValue(of([review()]));
    onboarding.approveReview.mockReturnValue(of(undefined));
    onboarding.returnReview.mockReturnValue(of(undefined));
    onboarding.rejectReview.mockReturnValue(of(undefined));
    dialogs.confirm.mockReturnValue(of(true));
    dialogs.prompt.mockReturnValue(of('  not complete  '));
  });

  it('lists the open reviews with reference, name, contact, type, status and submission time', () => {
    const component = create();

    expect(component.rows.length).toBe(1);
    const cells = Array.from(fixture.nativeElement.querySelectorAll('tbody tr:first-child td')).map((c) => (c as HTMLElement).textContent?.trim());
    expect(cells.slice(0, 5)).toEqual([
      'DRV-2026-000042',
      'Asha Rao',
      '9876543210 · asha@example.com',
      'onboardingSubmissions.types.DRIVER',
      'onboardingSubmissions.statuses.SUBMITTED',
    ]);
    expect(cells[5]).toContain('2026');
    expect(text()).toContain('shared.listPage.total');
  });

  it('shows the organization, franchise and place of each review', () => {
    onboarding.listOpenReviews.mockReturnValue(of([{ ...review(), tenantId: 'org-a', organizationName: 'City Cabs', organizationCode: 'CITYCABS',
      franchiseId: 'f-1', franchiseCode: 'HYD1', franchiseName: 'Hyd One', placeName: 'Vijayawada' }]));
    const component = create();

    expect(component.columns.map((c) => c.key)).toEqual(expect.arrayContaining(['organization', 'franchise', 'place']));
    expect(text()).toContain('City Cabs (CITYCABS)');
    expect(text()).toContain('HYD1 · Hyd One');
    expect(text()).toContain('Vijayawada');
  });

  it('shows dashes for a missing reference, name and contact', () => {
    onboarding.listOpenReviews.mockReturnValue(of([review({ reference: null, answers: {}, createdAt: undefined })]));
    create();

    const cells = Array.from(fixture.nativeElement.querySelectorAll('tbody tr:first-child td')).map((c) => (c as HTMLElement).textContent?.trim());
    expect(cells[0]).toBe('—');
    expect(cells[1]).toBe('—');
    expect(cells[2]).toBe('—');
    expect(cells[5]).toBe('—');
  });

  it('shows the empty message when nothing waits, and treats a null response as empty', () => {
    onboarding.listOpenReviews.mockReturnValue(of(null));
    const component = create();

    expect(component.rows).toEqual([]);
    expect(text()).toContain('onboardingReviews.empty');
  });

  it('reports a failed load with a toast and the error state', () => {
    onboarding.listOpenReviews.mockReturnValue(throwError(() => new Error('x')));
    const component = create();

    expect(component.hasError).toBe(true);
    expect(component.rows).toEqual([]);
    expect(component.isLoading).toBe(false);
    expect(notify.error).toHaveBeenCalledWith('onboardingReviews.loadFailed');
    expect(text()).toContain('onboardingReviews.loadFailed');
  });

  it('disables refresh while loading', () => {
    onboarding.listOpenReviews.mockReturnValue(new Subject());
    const component = create();

    expect(component.isLoading).toBe(true);
    expect((fixture.nativeElement.querySelector('.table-create-btn') as HTMLButtonElement).disabled).toBe(true);
  });

  it('opens the submission detail and the workflow history', () => {
    const component = create();

    button('shared.listPage.rowActions.viewDetail').click();
    expect(router.navigate).toHaveBeenCalledWith(['/onboarded-users', 's1']);

    historyDialog.open.mockClear();
    button('onboardingReviews.history').click();
    expect(historyDialog.open).toHaveBeenCalledWith(expect.objectContaining({ instanceId: 'wf-1' }));

    historyDialog.open.mockClear();
    component.history(review({ workflowInstanceId: null }));
    expect(historyDialog.open).not.toHaveBeenCalled();
  });

  it('hides the history action for a review with no workflow instance', () => {
    onboarding.listOpenReviews.mockReturnValue(of([review({ workflowInstanceId: null })]));
    create();

    expect(actionTitles()).not.toContain('onboardingReviews.history');
  });

  describe('decisions', () => {
    it('approves after confirmation, then reloads and confirms with a toast', () => {
      const component = create();
      onboarding.listOpenReviews.mockClear();

      button('onboardingReviews.approve').click();

      expect(dialogs.confirm).toHaveBeenCalledTimes(1);
      expect(dialogs.confirm.mock.calls[0][0].message).toBe('onboardingReviews.approveDialog.message');
      expect(onboarding.approveReview).toHaveBeenCalledWith('s1');
      expect(notify.success).toHaveBeenCalledWith('onboardingReviews.messages.approved');
      expect(onboarding.listOpenReviews).toHaveBeenCalledTimes(1);
      expect(component.busySubmissionId).toBeNull();
    });

    it('does nothing when the approval is not confirmed', () => {
      dialogs.confirm.mockReturnValue(of(false));
      const component = create();

      component.approve(component.rows[0]);

      expect(onboarding.approveReview).not.toHaveBeenCalled();
      expect(component.busySubmissionId).toBeNull();
    });

    it('reports a failed approval and re-enables the row', () => {
      onboarding.approveReview.mockReturnValue(throwError(() => new Error('x')));
      const component = create();

      component.approve(component.rows[0]);

      expect(notify.error).toHaveBeenCalledWith('onboardingReviews.messages.approveFailed');
      expect(component.busySubmissionId).toBeNull();
    });

    it('returns for correction with the trimmed reason', () => {
      const component = create();

      button('onboardingReviews.return').click();

      expect(dialogs.prompt.mock.calls[0][0].field).toMatchObject({ key: 'reason', required: true, type: 'textarea' });
      expect(onboarding.returnReview).toHaveBeenCalledWith('s1', 'not complete');
      expect(notify.success).toHaveBeenCalledWith('onboardingReviews.messages.returned');
      expect(component.busySubmissionId).toBeNull();
    });

    it('rejects with the trimmed reason', () => {
      create();

      button('onboardingReviews.reject').click();

      expect(onboarding.rejectReview).toHaveBeenCalledWith('s1', 'not complete');
      expect(notify.success).toHaveBeenCalledWith('onboardingReviews.messages.rejected');
    });

    it.each([
      ['return', 'returnReview', 'returnForCorrection', 'onboardingReviews.messages.returnFailed'],
      ['reject', 'rejectReview', 'reject', 'onboardingReviews.messages.rejectFailed'],
    ] as const)('reports a failed %s', (_name, api, method, messageKey) => {
      onboarding[api].mockReturnValue(throwError(() => new Error('x')));
      const component = create();

      component[method](component.rows[0]);

      expect(notify.error).toHaveBeenCalledWith(messageKey);
      expect(component.busySubmissionId).toBeNull();
    });

    it.each([
      ['return', 'returnReview', 'returnForCorrection'],
      ['reject', 'rejectReview', 'reject'],
    ] as const)('does not %s without a reason', (_name, api, method) => {
      const component = create();

      for (const answer of [null, '   ']) {
        dialogs.prompt.mockReturnValue(of(answer));
        component[method](component.rows[0]);
      }

      expect(onboarding[api]).not.toHaveBeenCalled();
    });

    it('ignores a second decision while one is in flight and disables that row\'s buttons', () => {
      const inFlight = new Subject<void>();
      onboarding.approveReview.mockReturnValue(inFlight);
      const component = create();

      component.approve(component.rows[0]);
      render();
      expect(component.busySubmissionId).toBe('s1');
      expect(button('onboardingReviews.reject').disabled).toBe(true);

      component.approve(component.rows[0]);
      component.reject(component.rows[0]);
      component.returnForCorrection(component.rows[0]);

      expect(dialogs.confirm).toHaveBeenCalledTimes(1);
      expect(dialogs.prompt).not.toHaveBeenCalled();
    });
  });

  describe('privileges', () => {
    it('shows every action to a reviewer with the admin access privilege', () => {
      granted = new Set(['RIDER_ONBOARDING_ADMIN_ACCESS']);
      create();

      expect(actionTitles()).toEqual([
        'shared.listPage.rowActions.viewDetail',
        'onboardingReviews.history',
        'onboardingReviews.approve',
        'onboardingReviews.return',
        'onboardingReviews.reject',
      ]);
    });

    it('shows only approve to a user who may approve but not reject', () => {
      granted = new Set(['ONBOARDING_ADMIN_REVIEW_APPROVE']);
      create();

      expect(actionTitles()).toContain('onboardingReviews.approve');
      expect(actionTitles()).not.toContain('onboardingReviews.reject');
      expect(actionTitles()).not.toContain('onboardingReviews.return');
    });

    it('shows only reject to a user who may reject but not approve', () => {
      granted = new Set(['ONBOARDING_ADMIN_REVIEW_REJECT']);
      create();

      expect(actionTitles()).toContain('onboardingReviews.reject');
      expect(actionTitles()).not.toContain('onboardingReviews.approve');
    });

    it('shows no decision actions to a read-only user', () => {
      granted = new Set();
      create();

      expect(actionTitles()).toEqual(['shared.listPage.rowActions.viewDetail', 'onboardingReviews.history']);
    });
  });
});
