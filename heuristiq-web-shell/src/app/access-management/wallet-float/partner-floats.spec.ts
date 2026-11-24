import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationService } from '../../shared/services/notification-service';
import { PartnerFloats } from './partner-floats';
import { WalletFloatApi } from './wallet-float-api';

const inr = (value: number) => ({ code: 'INR', value });
const row = { userId: 'u1', balance: inr(10), alertThreshold: inr(50), creditLimit: inr(0), available: inr(10) };
const api = { alerts: vi.fn(), byUser: vi.fn(), settle: vi.fn(), setAlertThreshold: vi.fn() };
const notify = { success: vi.fn(), error: vi.fn() };
let granted: Set<string> | null = null;
const access = {
  changes$: EMPTY,
  can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
};

function create(): { fixture: ComponentFixture<PartnerFloats>; component: PartnerFloats; el: HTMLElement } {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    declarations: [PartnerFloats],
    imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
    providers: [
      { provide: WalletFloatApi, useValue: api },
      { provide: NotificationService, useValue: notify },
      { provide: HqPrivilegeAccessService, useValue: access },
    ],
  });
  const fixture = TestBed.createComponent(PartnerFloats);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
}

describe('PartnerFloats alerts grid', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    api.alerts.mockReturnValue(of({ content: [row], totalElements: 45 }));
  });

  it('lists the floats below their threshold in the common table', () => {
    const { component, el } = create();

    expect(api.alerts).toHaveBeenCalledWith(0, 20);
    expect(component.alerts).toEqual([row]);
    expect(el.textContent).toContain('u1');
    expect(el.textContent).toContain('50.00');
    expect(el.textContent).not.toContain('[object Object]');
  });

  it('still shows an older string amount', () => {
    api.alerts.mockReturnValue(of({ content: [{ ...row, alertThreshold: '75.50' }], totalElements: 1 }));

    const { el } = create();

    expect(el.textContent).toContain('75.50');
  });

  it('does not read the alerts without the privilege', () => {
    granted = new Set(['WALLET_ADMIN_PARTNER_FLOAT_READ']);

    create();

    expect(api.alerts).not.toHaveBeenCalled();
  });

  it('pages from the footer and starts again from the first page with another page size', () => {
    const { component } = create();

    component.loadAlerts(1);
    expect(api.alerts).toHaveBeenLastCalledWith(1, 20);

    component.onPageSizeChange(50);
    expect(component.pageSize).toBe(50);
    expect(api.alerts).toHaveBeenLastCalledWith(0, 50);
  });

  it('says so when the alerts cannot be read', () => {
    api.alerts.mockReturnValue(throwError(() => new Error('down')));

    const { component } = create();

    expect(component.hasError).toBe(true);
  });
});

describe('PartnerFloats threshold and settlement', () => {
  const summary = { ...row, accountId: 'a1', status: 'ACTIVE', creditUsed: inr(0), belowAlert: false };

  beforeEach(() => {
    vi.clearAllMocks();
    granted = new Set(['WALLET_ADMIN_PARTNER_FLOAT_READ']);
    api.byUser.mockReturnValue(of(summary));
    api.settle.mockReturnValue(of({ journalId: 'j1' }));
    api.setAlertThreshold.mockReturnValue(of(summary));
  });

  it('loads the threshold of the holder found as an amount in the float currency', () => {
    const { component } = create();

    component.lookup('u1');

    expect(component.currency).toBe('INR');
    expect(component.thresholdForm.threshold).toEqual(inr(50));
  });

  it('sends the settlement amount as { code, value }', () => {
    const { component } = create();
    component.lookup('u1');
    component.settlementForm = { ...component.settlementForm, appCode: 'ride', amount: inr(250), reason: 'cash in' };

    component.settle();

    expect(api.settle).toHaveBeenCalledWith('u1', { appCode: 'RIDE', amount: inr(250), direction: 'DEBIT', reason: 'cash in' }, expect.any(String));
  });

  it('refuses a settlement without an amount above zero', () => {
    const { component } = create();
    component.lookup('u1');
    component.settlementForm = { ...component.settlementForm, appCode: 'RIDE', amount: { code: 'INR', value: null }, reason: 'r' };

    component.settle();

    expect(api.settle).not.toHaveBeenCalled();
    expect(notify.error).toHaveBeenCalledWith('walletFloat.errors.amount');
  });

  it('clears the threshold with null and never sends an empty amount', () => {
    const { component } = create();
    component.lookup('u1');
    component.thresholdForm = { threshold: { code: 'INR', value: null }, reason: 'not needed' };

    component.saveThreshold();

    expect(api.setAlertThreshold).toHaveBeenCalledWith('u1', { threshold: null, reason: 'not needed' });
  });
});
