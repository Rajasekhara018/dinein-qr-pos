import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import { amountToSend, floatCurrency, floatErrorKey, isNonNegativeAmount, isPositiveAmount, reasonError } from './wallet-float.helpers';
import { FloatSummary } from './wallet-float.models';
import { WalletFloatApi } from './wallet-float-api';

const base = environment.walletApiUrl;
const ok = (responseObject: unknown) => ({ success: true, message: '', responseObject });

describe('wallet float helpers', () => {
  it('checks { code, value } amounts and never sends an empty one', () => {
    expect(isPositiveAmount({ code: 'INR', value: 0 })).toBe(false);
    expect(isPositiveAmount({ code: 'INR', value: 0.01 })).toBe(true);
    expect(isPositiveAmount(null)).toBe(false);
    expect(isNonNegativeAmount({ code: 'INR', value: 0 })).toBe(true);
    expect(isNonNegativeAmount({ code: 'INR', value: -1 })).toBe(false);
    expect(isNonNegativeAmount({ code: 'INR', value: null })).toBe(false);
    expect(amountToSend({ code: 'inr', value: 500 })).toEqual({ code: 'INR', value: 500 });
    expect(amountToSend({ code: 'INR', value: null })).toBeNull();
    expect(amountToSend(null)).toBeNull();
  });

  it('takes the float currency from its balance, also when the balance is an older string', () => {
    expect(floatCurrency({ balance: { code: 'AED', value: 10 } } as FloatSummary)).toBe('AED');
    expect(floatCurrency({ balance: '10.00' } as unknown as FloatSummary)).toBe('');
    expect(floatCurrency(null)).toBe('');
  });

  it('maps known error codes to a label key and leaves others to the service message', () => {
    expect(floatErrorKey({ error: { errorCode: 'FLOAT_LIMIT_EXCEEDED' } })).toBe('walletFloat.errors.FLOAT_LIMIT_EXCEEDED');
    expect(floatErrorKey({ error: { errorCode: 'OTHER' } })).toBeNull();
    expect(floatErrorKey(null)).toBeNull();
  });

  it('requires a reason', () => {
    expect(reasonError('  ')).toBe('walletFloat.errors.reason');
    expect(reasonError('top up')).toBeNull();
  });
});

describe('WalletFloatApi', () => {
  let api: WalletFloatApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(WalletFloatApi);
    http = TestBed.inject(HttpTestingController);
  });

  it('reads the own float and the alert page', () => {
    let mine: unknown;
    api.mine().subscribe((r) => (mine = r));
    http.expectOne(`${base}/float/me`).flush(ok({ balance: { code: 'INR', value: 10 } }));
    expect(mine).toEqual({ balance: { code: 'INR', value: 10 } });
    api.alerts(1, 20).subscribe();
    http.expectOne(`${base}/admin/partner-float-alerts?page=1&size=20`).flush(ok({ content: [] }));
  });

  it('sends the threshold and a settlement with its idempotency key', () => {
    api.setAlertThreshold('u/1', { threshold: null, reason: 'r' }).subscribe();
    const put = http.expectOne(`${base}/admin/partner-floats/u%2F1/alert-threshold`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ threshold: null, reason: 'r' });
    put.flush(ok({}));
    api.settle('u1', { appCode: 'RIDE', amount: { code: 'INR', value: 5 }, direction: 'DEBIT', reason: 'r' }, 'key-1').subscribe();
    const post = http.expectOne(`${base}/admin/partner-floats/u1/settlements`);
    expect(post.request.body).toEqual({ appCode: 'RIDE', amount: { code: 'INR', value: 5 }, direction: 'DEBIT', reason: 'r' });
    expect(post.request.headers.get('Idempotency-Key')).toBe('key-1');
    post.flush(ok({ journalId: 'j' }));
  });

  it('turns an unsuccessful envelope into an error', () => {
    let error: unknown;
    api.byUser('u1').subscribe({ error: (e) => (error = e) });
    http.expectOne(`${base}/admin/partner-floats/u1`).flush({ success: false, message: 'nope' });
    expect((error as { error: { message: string } }).error.message).toBe('nope');
  });
});
