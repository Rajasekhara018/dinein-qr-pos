import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService } from 'hq-shared-lib';
import { EMPTY, firstValueFrom, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FranchiseApi } from './franchise-api';
import { PackageTemplate } from './franchise-packages.models';
import { FranchisePlanPreviewService } from './franchise-plan-preview.service';

const api = { namedPlans: vi.fn(), appliedRules: vi.fn() };
let granted: Set<string> | null = null;
const access = { changes$: EMPTY, can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)) };
const template = { id: 't1', name: 'Gold', applicationCode: 'RIDE', active: true, commissionPlanCode: 'GOLD', referralPlanCode: null, version: 1 } as PackageTemplate;
const commissionRow = (eventType: string | null, code = 'GOLD') => ({
  id: `${code}-${eventType}`, scope: { level: 'PLAN', planCode: code, eventType }, planName: 'Gold plan',
  effectiveFrom: '2026-01-01', effectiveTo: null, lines: [{ party: 'OWNER', valueType: 'PERCENTAGE', basisPoints: 1500 }],
});

describe('FranchisePlanPreviewService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [{ provide: FranchiseApi, useValue: api }, { provide: HqPrivilegeAccessService, useValue: access }],
    });
  });

  it("shows the package's named commission plan per event, and the application's rules for the referral it does not pick", async () => {
    api.namedPlans.mockReturnValue(of([commissionRow('RIDE_COMPLETED'), commissionRow(null, 'OTHER')]));
    api.appliedRules.mockReturnValue(of([
      { id: 'r', scope: { level: 'VERTICAL' }, effectiveFrom: '2026-01-01', rate: { valueType: 'PERCENTAGE', basisPoints: 200 } },
    ]));

    const preview = await firstValueFrom(TestBed.inject(FranchisePlanPreviewService).preview('RIDE', template));

    expect(api.namedPlans).toHaveBeenCalledWith('commission', 'RIDE');
    expect(api.appliedRules).toHaveBeenCalledWith('referral', 'RIDE');
    expect(preview.commission).toEqual({
      source: 'plan', planName: 'Gold plan', lines: [{ eventType: 'Ride completed', placeType: '', summary: 'accessManagement.myEarnings.parties.OWNER 15%' }],
    });
    expect(preview.referral).toEqual({ source: 'rules', planName: '', lines: [{ eventType: '', placeType: '', summary: '2%' }] });
  });

  it('reads nothing the caller may not list, and says when a read failed', async () => {
    granted = new Set(['REFERRAL_PLAN_LIST']);
    api.appliedRules.mockReturnValue(throwError(() => ({ status: 500 })));
    const service = TestBed.inject(FranchisePlanPreviewService);

    const preview = await firstValueFrom(service.preview('RIDE', null));

    expect(api.namedPlans).not.toHaveBeenCalled();
    expect(preview.commission).toBeNull();
    expect(preview.referral).toBe('failed');
    expect(await firstValueFrom(service.rules('commission', ''))).toBeNull();
  });
});
