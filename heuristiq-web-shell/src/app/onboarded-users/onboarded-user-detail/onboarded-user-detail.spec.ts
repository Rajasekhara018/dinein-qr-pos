import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatTabsModule } from '@angular/material/tabs';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { OnboardingAdminService } from '../../shared/services/onboarding-admin-service';
import { DocumentPreviewDialog } from '../document-preview-dialog/document-preview-dialog';
import { OnboardingSubmission } from '../onboarded-users.models';
import { OnboardedUserDetail } from './onboarded-user-detail';

describe('OnboardedUserDetail', () => {
  const onboarding = { get: vi.fn(), progress: vi.fn(), retryProvisioning: vi.fn(), downloadDocument: vi.fn(), relinkFranchise: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  const dialogs = { confirm: vi.fn() };
  const matDialog = { open: vi.fn() };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };

  const submission = (extra: Partial<OnboardingSubmission> = {}): OnboardingSubmission => ({
    id: 's1', reference: 'DRV-1', typeCode: 'DRIVER', schemaVersion: 1, subjectId: null, status: 'APPROVED',
    answers: { fullName: 'Asha Rao', mobile: '99999', email: 'a@x.io', extra: { a: 1 }, blank: '' },
    workflowInstanceId: 'wf1', rejectionReason: null, createdAt: '2026-03-01T10:00:00Z', updatedAt: '2026-03-02T10:00:00Z',
    documents: [{ fieldKey: 'aadhaar', label: 'Aadhaar', documentId: 'd1', documentCategory: null, fileName: 'a.png', contentType: 'image/png', sizeBytes: 2048, stored: true }],
    ...extra,
  });

  function create(): ComponentFixture<OnboardedUserDetail> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [OnboardedUserDetail],
      imports: [TranslateModule.forRoot(), HqSharedLibModule, MatTabsModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: OnboardingAdminService, useValue: onboarding },
        { provide: NotificationService, useValue: notify },
        { provide: AppDialogService, useValue: dialogs },
        { provide: MatDialog, useValue: matDialog },
        { provide: HqPrivilegeAccessService, useValue: access },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 's1' }) } } },
      ],
    });
    const fixture = TestBed.createComponent(OnboardedUserDetail);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    onboarding.get.mockReturnValue(of(submission()));
    onboarding.progress.mockReturnValue(of({ workflowName: 'Customer review', overallStatus: 'APPROVED', reviewStatus: 'APPROVED', reviews: [], documentsStatus: 'NOT_REQUIRED', documents: [], externalVerificationStatus: 'NOT_REQUIRED', accountStatus: 'CREATED', blockers: [] }));
    onboarding.retryProvisioning.mockReturnValue(of(submission()));
    onboarding.downloadDocument.mockReturnValue(of(new Blob(['x'])));
    onboarding.relinkFranchise.mockReturnValue(of({ submissionId: 's1', userId: 'u1', franchiseId: 'f-1', memberId: 'm1', created: true }));
    dialogs.confirm.mockReturnValue(of(true));
  });

  it('loads workflow-specific progress for any onboarding type and shows its blockers', () => {
    onboarding.get.mockReturnValue(of(submission({ typeCode: 'PARTNER', status: 'UNDER_REVIEW' })));
    onboarding.progress.mockReturnValue(of({ workflowName: 'Partner approval', overallStatus: 'AWAITING_REVIEWS', reviewStatus: 'PENDING',
      reviews: [{ taskId: 't1', name: 'Partner compliance', status: 'APPROVED', actorId: 'u1', decidedAt: '2026-03-01T10:00:00Z' }],
      documentsStatus: 'WORKFLOW_MANAGED', documents: [], externalVerificationStatus: 'NOT_REQUIRED', accountStatus: 'NOT_STARTED', blockers: ['REVIEW_PENDING'] }));
    const fixture = create();
    expect(onboarding.progress).toHaveBeenCalledWith('s1');
    expect(fixture.nativeElement.textContent).toContain('Partner approval');
    expect(fixture.nativeElement.textContent).toContain('Partner compliance');
    expect(fixture.componentInstance.approvedReviewCount).toBe(1);
  });

  it('keeps the submission visible when progress is unavailable and supports refresh', () => {
    onboarding.progress.mockReturnValue(throwError(() => new Error('Unavailable')));
    const fixture = create();
    expect(fixture.componentInstance.submission?.id).toBe('s1');
    expect(fixture.componentInstance.progressFailed).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('onboardingProgress.loadFailed');
    fixture.componentInstance.refreshProgress();
    expect(onboarding.progress).toHaveBeenCalledTimes(2);
  });

  it('loads the submission by route id and flattens its answers for display', () => {
    const component = create().componentInstance;

    expect(onboarding.get).toHaveBeenCalledWith('s1');
    expect(component.answerRows).toEqual([
      { field: 'fullName', value: 'Asha Rao' },
      { field: 'mobile', value: '99999' },
      { field: 'email', value: 'a@x.io' },
      { field: 'extra', value: '{"a":1}' },
      { field: 'blank', value: '—' },
    ]);
    expect(component.isLoading).toBe(false);
    expect(component.displayName).toBe('Asha Rao');
    expect(component.subtitle).toBe('99999 · a@x.io · onboardingSubmissions.statuses.APPROVED');
    expect(component.documents.length).toBe(1);
  });

  it('has no name or subtitle before the submission arrives, and no answers without any', () => {
    onboarding.get.mockReturnValue(throwError(() => new Error('x')));
    const component = create().componentInstance;

    expect(notify.error).toHaveBeenCalledWith('onboardingSubmissions.detailLoadFailed');
    expect(component.displayName).toBe('—');
    expect(component.subtitle).toBe('');
    expect(component.documents).toEqual([]);

    onboarding.get.mockReturnValue(of(submission({ answers: undefined as never, documents: null })));
    const second = create().componentInstance;
    expect(second.answerRows).toEqual([]);
    expect(second.documents).toEqual([]);
  });

  it('formats dates in the effective timezone and shows a dash for none', () => {
    const component = create().componentInstance;

    expect(component.date('2026-03-01T10:00:00Z')).toContain('2026');
    expect(component.date(null)).toBe('—');
  });

  it('describes the document columns', () => {
    const component = create().componentInstance;
    const render = (key: string, row: object) => component.documentColumns.find((c) => c.key === key)?.render?.(row as never);

    expect(render('fileName', { fileName: 'a.png' })).toBe('a.png');
    expect(render('fileName', { documentId: 'd9' })).toBe('d9');
    expect(render('fileName', {})).toBe('onboardingSubmissions.documents.notUploaded');
    expect(render('contentType', { stored: false })).toBe('onboardingSubmissions.documents.placeholderOnly');
    expect(render('contentType', { contentType: 'image/png' })).toBe('image/png');
    expect(render('contentType', {})).toBe('—');
    expect(render('sizeBytes', { sizeBytes: 2048 })).toBe('2.0 KB');
  });

  it('opens a stored document in the preview dialog', () => {
    const component = create().componentInstance;
    const doc = submission().documents![0];

    component.openDocument(doc);

    const [dialog, config] = matDialog.open.mock.calls[0];
    expect(dialog).toBe(DocumentPreviewDialog);
    expect(config.data.title).toBe('Aadhaar');
    expect(config.data.fileName).toBe('a.png');
    expect(onboarding.downloadDocument).toHaveBeenCalledWith('s1', 'aadhaar');
  });

  describe('retrying provisioning', () => {
    it('is allowed for approved and account-creation-failed submissions only', () => {
      const component = create().componentInstance;

      expect(component.canRetryProvisioning).toBe(true);
      component.submission = submission({ status: 'ACCOUNT_CREATION_FAILED' });
      expect(component.canRetryProvisioning).toBe(true);
      component.submission = submission({ status: 'REJECTED' });
      expect(component.canRetryProvisioning).toBe(false);
      component.submission = null;
      expect(component.canRetryProvisioning).toBe(false);
    });

    it('does nothing before the submission is loaded', () => {
      onboarding.get.mockReturnValue(throwError(() => new Error('x')));
      create().componentInstance.retryProvisioning();

      expect(dialogs.confirm).not.toHaveBeenCalled();
    });

    it('retries after confirmation and reports success when the account is now created', () => {
      const component = create().componentInstance;
      onboarding.get.mockClear();

      component.retryProvisioning();

      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ title: 'onboardingSubmissions.retry.title' }));
      expect(onboarding.retryProvisioning).toHaveBeenCalledWith('s1');
      expect(notify.success).toHaveBeenCalledWith('onboardingSubmissions.retry.success');
      expect(onboarding.get).toHaveBeenCalledTimes(1);
      expect(component.isRetrying).toBe(false);
    });

    it('shows the rejection reason, or a default, when the retry did not succeed', () => {
      const component = create().componentInstance;

      onboarding.retryProvisioning.mockReturnValueOnce(of(submission({ status: 'ACCOUNT_CREATION_FAILED', rejectionReason: 'Mobile in use' })));
      component.retryProvisioning();
      expect(notify.error).toHaveBeenLastCalledWith('Mobile in use');

      onboarding.retryProvisioning.mockReturnValueOnce(of(submission({ status: 'ACCOUNT_CREATION_FAILED' })));
      component.retryProvisioning();
      expect(notify.error).toHaveBeenLastCalledWith('onboardingSubmissions.retry.failed');
    });

    it('is also offered for a submission still under review once every reviewer approved it', () => {
      const component = create().componentInstance;
      component.submission = submission({ status: 'UNDER_REVIEW' });
      component.progress = { ...component.progress!, reviewStatus: 'APPROVED' };
      expect(component.canRetryProvisioning).toBe(true);

      component.progress = { ...component.progress!, reviewStatus: 'PENDING' };
      expect(component.canRetryProvisioning).toBe(false);
      component.progress = null;
      expect(component.canRetryProvisioning).toBe(false);
    });

    it('shows what still blocks the approval when the server names it', () => {
      onboarding.retryProvisioning.mockReturnValue(throwError(() => ({ error: { message: 'vehicleRc: selected provider verification has not passed' } })));
      const component = create().componentInstance;

      component.retryProvisioning();

      expect(notify.error).toHaveBeenCalledWith('vehicleRc: selected provider verification has not passed');
    });

    it('reports a failed call', () => {
      onboarding.retryProvisioning.mockReturnValue(throwError(() => new Error('x')));
      const component = create().componentInstance;

      component.retryProvisioning();

      expect(notify.error).toHaveBeenCalledWith('onboardingSubmissions.retry.failed');
      expect(component.isRetrying).toBe(false);
    });

    it('does nothing when the confirmation is declined', () => {
      dialogs.confirm.mockReturnValue(of(false));

      create().componentInstance.retryProvisioning();

      expect(onboarding.retryProvisioning).not.toHaveBeenCalled();
    });

    it('ignores a second click while a retry is running', () => {
      const component = create().componentInstance;
      component.isRetrying = true;

      component.retryProvisioning();

      expect(dialogs.confirm).not.toHaveBeenCalled();
    });
  });

  describe('placement and re-linking the franchise', () => {
    const placed = (extra: Partial<OnboardingSubmission> = {}) => submission({
      subjectId: 'u1', tenantId: 'org-a', organizationCode: 'CITYCABS', organizationName: 'City Cabs',
      franchiseId: 'f-1', franchiseCode: 'HYD1', franchiseName: 'Hyd One', placeId: 'vja', placeName: 'Vijayawada', ...extra,
    });
    const relinkButton = (fixture: ComponentFixture<OnboardedUserDetail>) =>
      (fixture.nativeElement as HTMLElement).querySelector('.detail-placement button.relink-btn');

    it('shows the organization, franchise and place of the submission', () => {
      onboarding.get.mockReturnValue(of(placed()));
      const fixture = create();
      const text = (fixture.nativeElement as HTMLElement).querySelector('.detail-placement')?.textContent ?? '';

      expect(text).toContain('onboardingSubmissions.placement.title');
      expect(text).toContain('City Cabs (CITYCABS)');
      expect(text).toContain('HYD1 · Hyd One');
      expect(text).toContain('Vijayawada');
    });

    it('hides the section for a submission without placement and shows dashes before loading', () => {
      const fixture = create();
      expect((fixture.nativeElement as HTMLElement).querySelector('.detail-placement')).toBeNull();
      fixture.componentInstance.submission = null;
      expect([fixture.componentInstance.organization, fixture.componentInstance.franchise, fixture.componentInstance.place]).toEqual(['—', '—', '—']);
    });

    it('can be re-linked only for an approved driver with an account and a franchise', () => {
      const component = create().componentInstance;
      component.submission = placed();
      expect(component.canRelinkFranchise).toBe(true);
      for (const extra of [{ status: 'REJECTED' as const }, { subjectId: null }, { franchiseId: null }, { typeCode: 'CUSTOMER' }]) {
        component.submission = placed(extra);
        expect(component.canRelinkFranchise).toBe(false);
      }
      component.submission = null;
      expect(component.canRelinkFranchise).toBe(false);
    });

    it('shows the action only with the onboarding admin privilege', () => {
      onboarding.get.mockReturnValue(of(placed()));
      granted = new Set(['RIDER_ONBOARDING_ADMIN_ACCESS']);
      const fixture = create();
      expect(relinkButton(fixture)).not.toBeNull();
      (relinkButton(fixture) as HTMLElement).click();
      expect(onboarding.relinkFranchise).toHaveBeenCalledWith('s1');

      granted = new Set(['ONBOARDING_ADMIN_REVIEW_LIST']);
      expect(relinkButton(create())).toBeNull();
    });

    it('re-links after confirmation and says whether the link was new', () => {
      onboarding.get.mockReturnValue(of(placed()));
      const component = create().componentInstance;

      component.relinkFranchise();
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ title: 'onboardingSubmissions.relink.title' }));
      expect(notify.success).toHaveBeenLastCalledWith('onboardingSubmissions.relink.success');

      onboarding.relinkFranchise.mockReturnValueOnce(of({ submissionId: 's1', userId: 'u1', franchiseId: 'f-1', memberId: 'm1', created: false }));
      component.relinkFranchise();
      expect(notify.success).toHaveBeenLastCalledWith('onboardingSubmissions.relink.already');
      expect(component.isRelinking).toBe(false);
    });

    it('shows the server reason, or a default, when re-linking fails', () => {
      onboarding.get.mockReturnValue(of(placed()));
      const component = create().componentInstance;

      onboarding.relinkFranchise.mockReturnValueOnce(throwError(() => ({ error: { message: 'Rides for another franchise' } })));
      component.relinkFranchise();
      expect(notify.error).toHaveBeenLastCalledWith('Rides for another franchise');

      onboarding.relinkFranchise.mockReturnValueOnce(throwError(() => new Error('x')));
      component.relinkFranchise();
      expect(notify.error).toHaveBeenLastCalledWith('onboardingSubmissions.relink.failed');
      expect(component.isRelinking).toBe(false);
    });

    it('does nothing when declined, while running, or when not allowed', () => {
      onboarding.get.mockReturnValue(of(placed()));
      const component = create().componentInstance;
      dialogs.confirm.mockReturnValueOnce(of(false));
      component.relinkFranchise();

      component.isRelinking = true;
      component.relinkFranchise();
      component.isRelinking = false;
      component.submission = placed({ status: 'REJECTED' });
      component.relinkFranchise();

      expect(dialogs.confirm).toHaveBeenCalledTimes(1);
      expect(onboarding.relinkFranchise).not.toHaveBeenCalled();
    });
  });

  describe('privilege-gated retry button', () => {
    const retryButton = (fixture: ComponentFixture<OnboardedUserDetail>) =>
      (fixture.nativeElement as HTMLElement).querySelector('.detail-toolbar button[hqButton="accent"]');

    it('is shown to someone holding the onboarding admin privilege', () => {
      granted = new Set(['RIDER_ONBOARDING_ADMIN_ACCESS']);
      const fixture = create();

      expect(retryButton(fixture)).not.toBeNull();
      (retryButton(fixture) as HTMLElement).click();
      expect(onboarding.retryProvisioning).toHaveBeenCalled();
    });

    it('is hidden without it', () => {
      granted = new Set();

      expect(retryButton(create())).toBeNull();
    });

    it('is hidden for a submission that cannot be retried', () => {
      onboarding.get.mockReturnValue(of(submission({ status: 'REJECTED' })));

      expect(retryButton(create())).toBeNull();
    });
  });
});
