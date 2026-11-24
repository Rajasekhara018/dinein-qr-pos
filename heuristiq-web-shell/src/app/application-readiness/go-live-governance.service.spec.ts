import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { environment } from '../../environments/environment';
import { GoLiveBlockedDialog } from './go-live-blocked-dialog/go-live-blocked-dialog';
import { GoLiveGovernanceService } from './go-live-governance.service';

describe('GoLiveGovernanceService', () => {
  const afterClosed = vi.fn();
  const dialog = { open: vi.fn() };
  let service: GoLiveGovernanceService;
  let http: HttpTestingController;
  const url = (path: string): string => `${environment.uamApiUrl}${path}`;
  const state = { applicationCode: 'APP', guardEnabled: true, override: null, alerts: null };

  beforeEach(() => {
    TestBed.resetTestingModule();
    dialog.open.mockReset().mockReturnValue({ afterClosed });
    afterClosed.mockReset().mockReturnValue(of('overridden'));
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: MatDialog, useValue: dialog }],
    });
    service = TestBed.inject(GoLiveGovernanceService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads the governance state and fills in missing lists', () => {
    let result: unknown;
    service.state('APP/1').subscribe((r) => (result = r));
    const req = http.expectOne(url('/applications/APP%2F1/readiness-governance'));
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, responseObject: { applicationCode: 'APP/1', ownerTeam: ['ignored'], override: { by: 'u1', at: null, reason: 'r', blockingSteps: null }, alerts: null } });
    expect(result).toEqual({
      state: 'ready',
      data: { applicationCode: 'APP/1', guardEnabled: true, override: { by: 'u1', at: null, reason: 'r', blockingSteps: [] }, alerts: null },
    });
  });

  it('answers forbidden for 403 and unavailable for other failures, never throwing', () => {
    const results: { state: string }[] = [];
    service.state('A').subscribe((r) => results.push(r));
    http.expectOne(url('/applications/A/readiness-governance')).flush({}, { status: 403, statusText: 'Forbidden' });
    service.state('B').subscribe((r) => results.push(r));
    http.expectOne(url('/applications/B/readiness-governance')).flush({}, { status: 500, statusText: 'Error' });
    service.state('C').subscribe((r) => results.push(r));
    http.expectOne(url('/applications/C/readiness-governance')).flush({ success: false });
    expect(results.map((r) => r.state)).toEqual(['forbidden', 'unavailable', 'unavailable']);
  });

  it('reads the readiness detail an application needs before go-live, and never throws', () => {
    const results: { state: string; data: unknown }[] = [];
    service.steps('APP/1').subscribe((r) => results.push(r));
    const req = http.expectOne(url('/applications/APP%2F1/readiness'));
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, responseObject: { applicationCode: 'APP/1', applicationName: 'App', steps: [] } });
    service.steps('B').subscribe((r) => results.push(r));
    http.expectOne(url('/applications/B/readiness')).flush({}, { status: 403, statusText: 'Forbidden' });
    service.steps('C').subscribe((r) => results.push(r));
    http.expectOne(url('/applications/C/readiness')).flush({ success: false });
    service.steps('D').subscribe((r) => results.push(r));
    http.expectOne(url('/applications/D/readiness')).flush({}, { status: 500, statusText: 'Error' });

    expect(results.map((r) => r.state)).toEqual(['ready', 'forbidden', 'unavailable', 'unavailable']);
    expect(results[0].data).toEqual({ applicationCode: 'APP/1', applicationName: 'App', steps: [] });
  });

  it('has no owner team calls any more', () => {
    expect((service as unknown as Record<string, unknown>)['candidates']).toBeUndefined();
    expect((service as unknown as Record<string, unknown>)['saveOwnerTeam']).toBeUndefined();
  });

  it('sends the trimmed reason of an override with a POST and surfaces a failure as an error', () => {
    const errors: unknown[] = [];
    service.override('APP', '  because we must  ').subscribe({ error: (e) => errors.push(e) });
    const req = http.expectOne(url('/applications/APP/go-live-override'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body.requestObject).toEqual({ reason: 'because we must' });
    req.flush({ message: 'Only a platform administrator' }, { status: 403, statusText: 'Forbidden' });
    expect(errors).toHaveLength(1);
    expect((errors[0] as { status: number }).status).toBe(403);
  });

  it('turns an unsuccessful 200 answer into an error', () => {
    const errors: unknown[] = [];
    service.override('APP', 'long enough reason').subscribe({ error: (e) => errors.push(e) });
    http.expectOne(url('/applications/APP/go-live-override')).flush({ success: false, message: 'no' });
    expect(errors).toHaveLength(1);
  });

  describe('handleRefusal', () => {
    const refusal = {
      status: 409,
      error: { errorCode: 'GO_LIVE_BLOCKED', responseObject: { applicationCode: 'APP', messageKey: 'k', blockers: [] } },
    };

    it('opens the blocked dialog for a go-live refusal and returns what the user did', () => {
      let result: unknown;
      service.handleRefusal(refusal).subscribe((r) => (result = r));
      expect(dialog.open).toHaveBeenCalledWith(GoLiveBlockedDialog, expect.objectContaining({ data: { info: expect.objectContaining({ applicationCode: 'APP' }) } }));
      expect(result).toBe('overridden');
    });

    it('treats a dismissed dialog as closed', () => {
      afterClosed.mockReturnValue(of(undefined));
      let result: unknown;
      service.handleRefusal(refusal).subscribe((r) => (result = r));
      expect(result).toBe('closed');
    });

    it('answers null, opening nothing, for any other failure', () => {
      let result: unknown = 'unset';
      service.handleRefusal({ status: 500 }).subscribe((r) => (result = r));
      expect(result).toBeNull();
      expect(dialog.open).not.toHaveBeenCalled();
    });
  });
});
