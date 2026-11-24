import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqSharedLibModule } from 'hq-shared-lib';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { NotificationService } from '../../shared/services/notification-service';
import { WorkflowHistoryService } from '../../workflow/history/workflow-history.service';
import { OnboardingAdminService } from '../../shared/services/onboarding-admin-service';
import { OnboardingSubmission } from '../onboarded-users.models';
import { OnboardedUsersList } from './onboarded-users-list';

const historyDialog = { open: vi.fn() };

describe('OnboardedUsersList', () => {
  const onboarding = { list: vi.fn() };
  const router = { navigate: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };

  const submission = (extra: Partial<OnboardingSubmission> = {}): OnboardingSubmission => ({
    id: 's1', reference: 'DRV-1', typeCode: 'DRIVER', schemaVersion: 1, subjectId: null, status: 'SUBMITTED',
    answers: { fullName: 'Asha Rao', mobile: '99999', email: 'a@x.io' }, workflowInstanceId: 'wf1', rejectionReason: null,
    createdAt: '2026-03-01T10:00:00Z', updatedAt: '2026-03-01T10:00:00Z', ...extra,
  });

  function create(): ComponentFixture<OnboardedUsersList> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [OnboardedUsersList],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        { provide: WorkflowHistoryService, useValue: historyDialog },
        { provide: OnboardingAdminService, useValue: onboarding },
        { provide: Router, useValue: router },
        { provide: NotificationService, useValue: notify },
      ],
    });
    const fixture = TestBed.createComponent(OnboardedUsersList);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    onboarding.list.mockReturnValue(of({ items: [submission()], page: 0, size: 10, totalElements: 31, totalPages: 4 }));
  });

  it('loads the first page with empty filters and renders the headline columns', () => {
    const fixture = create();
    const component = fixture.componentInstance;

    expect(onboarding.list).toHaveBeenCalledWith({ type: '', status: '', query: '' }, 0, 10);
    expect([component.totalElements, component.totalPages]).toEqual([31, 4]);
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('DRV-1');
    expect(text).toContain('Asha Rao');
    expect(text).toContain('99999 · a@x.io');
    expect(text).toContain('onboardingSubmissions.types.DRIVER');
    expect(text).toContain('onboardingSubmissions.statuses.SUBMITTED');
    expect(text).toContain('2026');
  });

  it('shows the organization, franchise and place columns, falling back to ids', () => {
    onboarding.list.mockReturnValue(of({ items: [
      submission({ tenantId: 'org-a', organizationCode: 'CITYCABS', organizationName: 'City Cabs',
        franchiseId: 'f-1', franchiseCode: 'HYD1', franchiseName: 'Hyd One', placeId: 'vja', placeName: 'Vijayawada' }),
      submission({ id: 's2', tenantId: 'org-b', franchiseId: 'f-9' }),
    ] }));
    const fixture = create();
    const labels = fixture.componentInstance.columns.map((c) => c.label);
    const text: string = fixture.nativeElement.textContent;

    expect(labels).toEqual(expect.arrayContaining([
      'onboardingSubmissions.columns.organization', 'onboardingSubmissions.columns.franchise', 'onboardingSubmissions.columns.place',
    ]));
    expect(text).toContain('City Cabs (CITYCABS)');
    expect(text).toContain('HYD1 · Hyd One');
    expect(text).toContain('Vijayawada');
    expect(text).toContain('org-b');
    expect(text).toContain('f-9');
  });

  it('shows dashes for a submission with no reference or contact', () => {
    onboarding.list.mockReturnValue(of({ items: [submission({ reference: null, answers: {} })] }));
    const fixture = create();

    expect(fixture.nativeElement.textContent).toContain('—');
    expect(fixture.componentInstance.totalElements).toBe(1);
    expect(fixture.componentInstance.totalPages).toBe(1);
  });

  it('treats a response without items as an empty page', () => {
    onboarding.list.mockReturnValue(of({}));

    expect(create().componentInstance.rows).toEqual([]);
  });

  it('shows the error state with a toast when loading fails', () => {
    onboarding.list.mockReturnValue(throwError(() => new Error('x')));
    const fixture = create();

    expect(fixture.componentInstance.hasError).toBe(true);
    expect(notify.error).toHaveBeenCalledWith('onboardingSubmissions.loadFailed');
    expect(fixture.nativeElement.querySelector('.app-state--error')).not.toBeNull();
  });

  it('filters from page 0 and clears every filter', () => {
    const component = create().componentInstance;
    component.page = 3;
    component.filters = { type: 'DRIVER', status: 'APPROVED', query: 'asha' };

    component.applyFilters();
    expect(onboarding.list).toHaveBeenLastCalledWith({ type: 'DRIVER', status: 'APPROVED', query: 'asha' }, 0, 10);

    component.clearFilters();
    expect(component.filters).toEqual({ type: '', status: '', query: '' });
    expect(onboarding.list).toHaveBeenLastCalledWith({ type: '', status: '', query: '' }, 0, 10);
  });

  it('pages and resizes', () => {
    const component = create().componentInstance;

    component.goToPage(2);
    expect(onboarding.list).toHaveBeenLastCalledWith(expect.any(Object), 2, 10);
    component.onPageSizeChange(50);
    expect([component.page, component.pageSize]).toEqual([0, 50]);
    expect(onboarding.list).toHaveBeenLastCalledWith(expect.any(Object), 0, 50);
  });

  it('opens the submission detail', () => {
    create().componentInstance.viewDetail(submission());

    expect(router.navigate).toHaveBeenCalledWith(['/onboarded-users', 's1']);
  });

  it('shows the workflow history in a dialog, only for a submission that has one, without leaving the list', () => {
    const component = create().componentInstance;
    historyDialog.open.mockClear();

    component.history(submission());
    expect(historyDialog.open).toHaveBeenCalledWith(expect.objectContaining({ instanceId: 'wf1' }));
    expect(router.navigate).not.toHaveBeenCalledWith(['/workflow', 'instances', 'wf1']);

    historyDialog.open.mockClear();
    component.history(submission({ workflowInstanceId: null }));
    expect(historyDialog.open).not.toHaveBeenCalled();
  });

  it('renders the row actions: detail always, history only with a workflow instance', () => {
    onboarding.list.mockReturnValue(of({ items: [submission(), submission({ id: 's2', workflowInstanceId: null })], totalElements: 2, totalPages: 1 }));
    const el: HTMLElement = create().nativeElement;
    const rows = Array.from(el.querySelectorAll('tbody tr'));

    expect(rows[0].querySelectorAll('.row-action-btn').length).toBe(2);
    expect(rows[1].querySelectorAll('.row-action-btn').length).toBe(1);
    (rows[0].querySelector('button[title="shared.listPage.rowActions.viewDetail"]') as HTMLElement).click();
    expect(router.navigate).toHaveBeenCalledWith(['/onboarded-users', 's1']);
  });

  it('the refresh button reloads', () => {
    const fixture = create();
    onboarding.list.mockClear();

    (fixture.nativeElement.querySelector('.table-create-btn') as HTMLElement).click();

    expect(onboarding.list).toHaveBeenCalledTimes(1);
  });
});
