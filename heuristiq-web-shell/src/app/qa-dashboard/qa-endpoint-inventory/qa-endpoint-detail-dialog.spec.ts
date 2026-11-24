import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { NEVER, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { QA_DASHBOARD_PRIVILEGES } from '../qa-dashboard.constants';
import { accessMock, endpoint, endpointDetail } from '../qa-dashboard.fixtures';
import { QaEndpointDetail } from './qa-endpoint-inventory.models';
import { QaEndpointDetailDialog } from './qa-endpoint-detail-dialog';
import { QaEndpointService } from './qa-endpoint-inventory.service';

describe('QaEndpointDetailDialog', () => {
  const svc = { detail: vi.fn((_id: string): Observable<QaEndpointDetail> => of(endpointDetail('a'))) };
  const ref = { close: vi.fn() };
  let granted: Set<string> | null;
  let fixture: ComponentFixture<QaEndpointDetailDialog>;

  function create(): QaEndpointDetailDialog {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [QaEndpointDetailDialog],
      imports: [TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: QaEndpointService, useValue: svc },
        { provide: MatDialogRef, useValue: ref },
        { provide: MAT_DIALOG_DATA, useValue: endpoint('a') },
        { provide: HqPrivilegeAccessService, useValue: accessMock(granted) },
      ],
    });
    fixture = TestBed.createComponent(QaEndpointDetailDialog);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const q = (selector: string): HTMLElement | null => el().querySelector(selector);

  beforeEach(() => {
    vi.clearAllMocks();
    svc.detail.mockImplementation(() => of(endpointDetail('a')));
    granted = null;
  });

  it('shows the method and path at once and then what the server knows about the endpoint', () => {
    create();
    expect(svc.detail).toHaveBeenCalledWith('a');
    expect(q('.qa-dialog__title .qa-method')?.textContent).toBe('POST');
    expect(q('.qa-dialog__path')?.textContent).toBe('/api/v1/uam/users/{userId}/block');
    expect(q('.qa-facts__description')?.textContent).toBe('Allows blocking a user');
    expect(q('.qa-facts__privilege')?.textContent).toBe('UAM_USER_BLOCK_CREATE');
    expect(q('.qa-facts__operation')?.textContent).toBe('CREATE');
  });

  it('lists the test cases that call the endpoint with their status', () => {
    create();
    const rows = el().querySelectorAll('.qa-test-row');
    expect(rows).toHaveLength(2);
    expect(rows[0].querySelector('.qa-pill--success')).not.toBeNull();
    expect(rows[1].querySelector('.qa-pill--danger')).not.toBeNull();
    expect(rows[0].textContent).toContain('uam:users:block');
  });

  it('links each test case to its row in the test case inventory, and closes the dialog on the way', () => {
    const dialog = create();
    const links = el().querySelectorAll('.qa-open-test');
    expect(links).toHaveLength(2);
    expect(dialog.testCaseParams(endpointDetail('a').tests[0])).toEqual({ workspace: 'API', q: 'uam:users:block' });
    expect(dialog.testCasesRoute).toBe('/qa/test-cases');
    (links[0] as HTMLAnchorElement).click();
    expect(ref.close).toHaveBeenCalled();
  });

  it('does not offer the link to a user who cannot list test cases', () => {
    granted = new Set([QA_DASHBOARD_PRIVILEGES.endpointRead]);
    create();
    expect(el().querySelectorAll('.qa-open-test')).toHaveLength(0);
    expect(el().querySelectorAll('.qa-test-row')).toHaveLength(2);
  });

  it('says there is no description, no privilege and no test case when there are none', () => {
    svc.detail.mockImplementation(() => of(endpointDetail('a', { description: '', privilegeName: null, operation: '', tests: [], documented: false })));
    create();
    expect(q('.qa-facts__description')?.textContent).toContain('qaDashboard.endpointInventory.noDescription');
    expect(q('.qa-facts__privilege')?.textContent).toBe('—');
    expect(q('.qa-facts__operation')?.textContent).toBe('—');
    expect(q('.qa-no-tests')).not.toBeNull();
    expect(q('.qa-tests')).toBeNull();
    expect(q('.qa-pill--warning')).not.toBeNull();
  });

  it('shows a loading message while waiting and an error when the details cannot be loaded', () => {
    svc.detail.mockImplementation(() => NEVER);
    const waiting = create();
    expect(waiting.isLoading).toBe(true);
    expect(q('.qa-state')?.textContent).toContain('qaDashboard.loading');
    svc.detail.mockImplementation(() => throwError(() => new Error('down')));
    const failed = create();
    expect(failed.hasError).toBe(true);
    expect(failed.isLoading).toBe(false);
    expect(q('.qa-state--error')).not.toBeNull();
    expect(q('.qa-facts')).toBeNull();
  });

  it('closes with the close button', () => {
    create();
    (q('.qa-dialog__close') as HTMLButtonElement).click();
    expect(ref.close).toHaveBeenCalledTimes(1);
  });

  it('labels statuses and tracks tests', () => {
    const dialog = create();
    expect(dialog.statusLabel('FAILED')).toBe('FAILED');
    expect(dialog.statusTone('FAILED')).toBe('danger');
    expect(dialog.methodClass('DELETE')).toBe('qa-method--delete');
    expect(dialog.trackByTest(0, { id: 'x', tcId: null, testKey: 'k', name: '', status: '', lastRunAt: null })).toBe('x');
    expect(dialog.trackByTest(0, { id: '', tcId: null, testKey: 'k', name: '', status: '', lastRunAt: null })).toBe('k');
  });
});
