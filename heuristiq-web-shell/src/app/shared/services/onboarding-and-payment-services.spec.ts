import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, NavigationEnd, NavigationError, Event as RouterEvent } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { environment } from '../../../environments/environment';
import { OnboardingAdminService } from './onboarding-admin-service';
import { OnboardingSchemaService } from './onboarding-schema-service';
import { PaymentService } from './payment-service';
import { StaleBundleRecovery } from './stale-bundle-recovery';

const translate = { provide: TranslateService, useValue: { instant: (k: string) => k } };
const ok = <T>(data: T) => ({ status: true, data });

describe('OnboardingAdminService', () => {
  let svc: OnboardingAdminService;
  let http: HttpTestingController;
  const SUB = `${environment.riderOnboardingApiUrl}/admin/onboarding-submissions`;
  const REV = `${environment.riderOnboardingApiUrl}/admin/onboarding-reviews`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), translate] });
    svc = TestBed.inject(OnboardingAdminService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('lists with paging and only the filters set (query trimmed)', () => {
    let out: unknown;
    svc.list({ type: 'DRIVER', status: '', query: ' abc ' } as never, 1, 20).subscribe((v) => (out = v));
    const req = http.expectOne((r) => r.url === SUB);
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.get('type')).toBe('DRIVER');
    expect(req.request.params.has('status')).toBe(false);
    expect(req.request.params.get('q')).toBe('abc');
    req.flush(ok({ content: [] }));
    expect(out).toEqual({ content: [] });
    svc.list({ type: '', status: 'NEW', query: '  ' } as never, 0, 5).subscribe();
    const r2 = http.expectOne((r) => r.url === SUB);
    expect(r2.request.params.has('q')).toBe(false);
    expect(r2.request.params.get('status')).toBe('NEW');
    r2.flush(ok({}));
  });

  it('raises the server message, or a translated fallback, for a failed envelope', () => {
    const errs: unknown[] = [];
    svc.get('s1').subscribe({ error: (e) => errs.push(e) });
    http.expectOne(`${SUB}/s1`).flush({ status: false, message: 'gone' });
    svc.get('s1').subscribe({ error: (e) => errs.push(e) });
    http.expectOne(`${SUB}/s1`).flush({ status: false });
    expect(errs).toEqual([
      { error: { detail: 'gone', message: 'gone' } },
      { error: { detail: 'shared.errors.requestFailed', message: 'shared.errors.requestFailed' } },
    ]);
  });

  it('review endpoints: list, approve, return and reject with the reason body', () => {
    svc.listOpenReviews().subscribe();
    http.expectOne(REV).flush(ok([]));
    svc.approveReview('a b').subscribe();
    const approve = http.expectOne(`${REV}/a%20b/approve`);
    expect(approve.request.method).toBe('POST');
    approve.flush(ok(null));
    svc.returnReview('s1', 'fix photo').subscribe();
    const ret = http.expectOne(`${REV}/s1/return`);
    expect(ret.request.body).toEqual({ reason: 'fix photo' });
    ret.flush(ok(null));
    svc.rejectReview('s1', 'fake').subscribe();
    const rej = http.expectOne(`${REV}/s1/reject`);
    expect(rej.request.body).toEqual({ reason: 'fake' });
    rej.flush(ok(null));
  });

  it('retries provisioning with a POST', () => {
    svc.retryProvisioning('s1').subscribe();
    const req = http.expectOne(`${SUB}/s1/retry-provisioning`);
    expect(req.request.method).toBe('POST');
    req.flush(ok({}));
  });

  it('re-links the franchise with a POST and unwraps the result', () => {
    let created: boolean | undefined;
    svc.relinkFranchise('s 1').subscribe((r) => (created = r.created));
    const req = http.expectOne(`${SUB}/s%201/relink-franchise`);
    expect(req.request.method).toBe('POST');
    req.flush(ok({ submissionId: 's 1', userId: 'u1', franchiseId: 'f-1', memberId: 'm1', created: false }));
    expect(created).toBe(false);
  });

  it('downloads a document as a blob', () => {
    let blob: Blob | undefined;
    svc.downloadDocument('s1', 'licence/front').subscribe((b) => (blob = b));
    const req = http.expectOne(`${SUB}/s1/documents/licence%2Ffront`);
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['pdf']));
    expect(blob?.size).toBe(3);
  });

  it('reads the server reason out of a blob error body', async () => {
    const result = new Promise<unknown>((resolve) => svc.downloadDocument('s1', 'f').subscribe({ error: resolve }));
    http.expectOne(`${SUB}/s1/documents/f`).flush(new Blob([JSON.stringify({ message: 'Document download was rejected' })]), { status: 502, statusText: 'Bad Gateway' });
    expect(await result).toEqual({ error: { detail: 'Document download was rejected', message: 'Document download was rejected' } });
  });

  it('uses the raw JSON body when it has no message, and a status fallback when the body is not JSON or empty', async () => {
    const raw = new Promise<unknown>((resolve) => svc.downloadDocument('s1', 'f').subscribe({ error: resolve }));
    http.expectOne(`${SUB}/s1/documents/f`).flush(new Blob(['{"code":7}']), { status: 500, statusText: 'x' });
    expect(await raw).toEqual({ error: { detail: '{"code":7}', message: '{"code":7}' } });

    const plain = new Promise<unknown>((resolve) => svc.downloadDocument('s1', 'f').subscribe({ error: resolve }));
    http.expectOne(`${SUB}/s1/documents/f`).flush(new Blob(['not json']), { status: 500, statusText: 'x' });
    expect(await plain).toEqual({ error: { detail: 'shared.errors.requestFailed (HTTP 500)', message: 'shared.errors.requestFailed (HTTP 500)' } });

    const empty = new Promise<unknown>((resolve) => svc.downloadDocument('s1', 'f').subscribe({ error: resolve }));
    http.expectOne(`${SUB}/s1/documents/f`).flush(new Blob([]), { status: 404, statusText: 'x' });
    expect(await empty).toEqual({ error: { detail: 'shared.errors.requestFailed (HTTP 404)', message: 'shared.errors.requestFailed (HTTP 404)' } });
  });
});

describe('OnboardingSchemaService', () => {
  let svc: OnboardingSchemaService;
  let http: HttpTestingController;
  const BASE = `${environment.riderOnboardingApiUrl}/admin/onboarding-types`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), translate] });
    svc = TestBed.inject(OnboardingSchemaService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('lists and creates types', () => {
    svc.listTypes().subscribe();
    http.expectOne(BASE).flush(ok([]));
    svc.createType({ code: 'X' } as never).subscribe();
    const req = http.expectOne(BASE);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ code: 'X' });
    req.flush(ok({}));
  });

  it('toggles requires-review through a query param', () => {
    svc.setRequiresReview('D R', true).subscribe();
    const req = http.expectOne((r) => r.url === `${BASE}/D%20R/requires-review`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.params.get('value')).toBe('true');
    req.flush(ok({}));
  });

  it('sorts schema versions newest first, and copes with a null list', () => {
    let out: Array<{ version: number }> = [];
    svc.listSchemasNewestFirst('D').subscribe((v) => (out = v as never));
    http.expectOne(`${BASE}/D/schemas`).flush(ok([{ version: 1 }, { version: 3 }, { version: 2 }]));
    expect(out.map((v) => v.version)).toEqual([3, 2, 1]);
    svc.listSchemasNewestFirst('D').subscribe((v) => (out = v as never));
    http.expectOne(`${BASE}/D/schemas`).flush(ok(null));
    expect(out).toEqual([]);
  });

  it('drafts, publishes and republishes a version', () => {
    svc.defaultSchema('D').subscribe();
    http.expectOne(`${BASE}/D/schemas/defaults`).flush(ok({}));
    svc.createDraft('D', { f: 1 } as never).subscribe();
    const c = http.expectOne(`${BASE}/D/schemas`);
    expect(c.request.method).toBe('POST');
    c.flush(ok({}));
    svc.updateDraft('D', 4, { f: 2 } as never).subscribe();
    const u = http.expectOne(`${BASE}/D/schemas/4`);
    expect(u.request.method).toBe('PUT');
    u.flush(ok({}));
    svc.publish('D', 4).subscribe();
    http.expectOne(`${BASE}/D/schemas/4/publish`).flush(ok({}));
    svc.republish('D', 4).subscribe();
    http.expectOne(`${BASE}/D/schemas/4/republish`).flush(ok({}));
  });

  it('raises the server message on a failed envelope', () => {
    let err: unknown;
    svc.listTypes().subscribe({ error: (e) => (err = e) });
    http.expectOne(BASE).flush({ status: false, message: 'nope' });
    expect(err).toEqual({ error: { detail: 'nope', message: 'nope' } });
    svc.listTypes().subscribe({ error: (e) => (err = e) });
    http.expectOne(BASE).flush({ status: false });
    expect(err).toEqual({ error: { detail: 'shared.errors.requestFailed', message: 'shared.errors.requestFailed' } });
  });
});

describe('PaymentService', () => {
  let svc: PaymentService;
  let http: HttpTestingController;
  const BASE = environment.paymentApiUrl;
  const pay = (over: Record<string, unknown> = {}) => ({ status: true, object: { paymentId: 'p1', gateway: 'RAZORPAY', status: 'INITIATED', ...over } });

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), translate] });
    svc = TestBed.inject(PaymentService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('creates a payment with the plain body and builds the Razorpay checkout config', () => {
    let out: unknown;
    svc.createPayment({ amount: 10 } as never).subscribe((v) => (out = v));
    const req = http.expectOne(`${BASE}/payments`);
    expect(req.request.body).toEqual({ amount: 10 });
    req.flush(pay({ razorpayOrder: { keyId: 'k', amountInPaise: 1000, currency: 'INR', orderId: 'o1' } }));
    expect(out).toEqual({ paymentId: 'p1', gateway: 'RAZORPAY', status: 'INITIATED', checkoutConfig: { key: 'k', amount: 1000, currency: 'INR', order_id: 'o1' } });
  });

  it('uses the redirect URL, or an auto-submit form config, from a gateway session', () => {
    let out: Record<string, unknown> = {};
    svc.createPayment({} as never).subscribe((v) => (out = v as never));
    http.expectOne(`${BASE}/payments`).flush(pay({ gatewaySession: { redirectUrl: 'https://pay' } }));
    expect(out['redirectUrl']).toBe('https://pay');
    svc.createPayment({} as never).subscribe((v) => (out = v as never));
    http.expectOne(`${BASE}/payments`).flush(pay({ gateway: undefined, gatewaySession: { action: 'https://post', field: 'v' } }));
    expect(out['gateway']).toBe('');
    expect(out['checkoutConfig']).toEqual({ action: 'https://post', field: 'v', autoSubmit: true });
    svc.createPayment({} as never).subscribe((v) => (out = v as never));
    http.expectOne(`${BASE}/payments`).flush(pay());
    expect(out['checkoutConfig']).toBeUndefined();
    expect(out['redirectUrl']).toBeUndefined();
  });

  it.each([
    ['CAPTURED', 'SUCCESS'], ['settled', 'SUCCESS'], ['INITIATED', 'PENDING'], ['AUTHORIZED', 'PENDING'],
    ['REVERSED', 'CANCELLED'], ['REFUNDED', 'CANCELLED'], ['PARTIALLY_REFUNDED', 'CANCELLED'], ['failed', 'FAILED'],
  ])('maps payment status %s to the invoice status %s', (status, expected) => {
    let out: { status: string } | undefined;
    svc.getByPaymentId('p1').subscribe((v) => (out = v as never));
    http.expectOne(`${BASE}/payments/p1`).flush(pay({ status }));
    expect(out?.status).toBe(expected);
  });

  it('maps a payment to the invoice transaction shape, preferring the provider payment id', () => {
    let out: unknown;
    svc.getByPaymentId('p1').subscribe((v) => (out = v));
    http.expectOne(`${BASE}/payments/p1`).flush(pay({
      invoiceNumber: 'I1', receiptNumber: 'R1', rideId: 'ride', customerName: 'Asha', customerEmail: 'a@x', customerMobile: '99',
      providerPaymentId: 'pp', providerOrderId: 'po', method: 'UPI', amount: { code: 'INR', value: 50 }, createdAt: 'c', updatedAt: 'u',
    }));
    expect(out).toMatchObject({ paymentId: 'p1', invoiceNumber: 'I1', email: 'a@x', mobile: '99', transactionId: 'pp', paymentMode: 'UPI', transactionAmount: 50, transactionCurrency: 'INR' });
    svc.getByPaymentId('p1').subscribe((v) => (out = v));
    http.expectOne(`${BASE}/payments/p1`).flush(pay({ providerOrderId: 'po', status: undefined }));
    expect(out).toMatchObject({ transactionId: 'po', status: '' });
  });

  it('still reads a legacy bare-number amount during a rolling deploy', () => {
    let out: unknown;
    svc.getByPaymentId('p1').subscribe((v) => (out = v));
    http.expectOne(`${BASE}/payments/p1`).flush(pay({ amount: 75, currency: 'INR' }));
    expect(out).toMatchObject({ transactionAmount: 75, transactionCurrency: 'INR' });
  });

  it('reads the lifecycle and reconciles by payment id', () => {
    let steps: unknown;
    svc.getTransitions('p 1').subscribe((v) => (steps = v));
    http.expectOne(`${BASE}/payments/p%201/transitions`).flush({ status: true, object: [{ to: 'X' }] });
    expect(steps).toEqual([{ to: 'X' }]);
    svc.checkStatus({ messageID: 'p/2' } as never).subscribe();
    const req = http.expectOne(`${BASE}/payments/p%2F2/reconcile`);
    expect(req.request.method).toBe('POST');
    req.flush(pay());
  });

  it('raises the server errorMsg, or the translated fallback, on failure', () => {
    const errs: unknown[] = [];
    svc.getByPaymentId('p1').subscribe({ error: (e) => errs.push(e) });
    http.expectOne(`${BASE}/payments/p1`).flush({ status: false, errorMsg: 'declined' });
    svc.getByPaymentId('p1').subscribe({ error: (e) => errs.push(e) });
    http.expectOne(`${BASE}/payments/p1`).flush(null);
    expect(errs).toEqual([
      { error: { detail: 'declined', message: 'declined' } },
      { error: { detail: 'shared.errors.requestFailed', message: 'shared.errors.requestFailed' } },
    ]);
  });
});

describe('StaleBundleRecovery', () => {
  let events: Subject<RouterEvent>;
  let assign: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    sessionStorage.clear();
    events = new Subject<RouterEvent>();
    assign = vi.fn();
    vi.stubGlobal('location', { ...window.location, assign });
    TestBed.configureTestingModule({ providers: [{ provide: Router, useValue: { events } }] });
    TestBed.inject(StaleBundleRecovery).start();
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    sessionStorage.clear();
  });

  const stale = new Error('Failed to fetch dynamically imported module: /x.js');

  it('reloads once at the screen being opened when a code chunk is gone', () => {
    events.next(new NavigationError(1, '/orders', stale));
    expect(assign).toHaveBeenCalledWith('/orders');
    events.next(new NavigationError(2, '/orders', stale));
    expect(assign).toHaveBeenCalledTimes(1);
  });

  it('ignores errors that are not stale-chunk errors', () => {
    events.next(new NavigationError(1, '/orders', new Error('Some other failure')));
    expect(assign).not.toHaveBeenCalled();
  });

  it('re-arms the guard after a successful navigation', () => {
    events.next(new NavigationError(1, '/a', stale));
    events.next(new NavigationEnd(2, '/a', '/a'));
    events.next(new NavigationError(3, '/b', stale));
    expect(assign).toHaveBeenCalledTimes(2);
    expect(assign).toHaveBeenLastCalledWith('/b');
  });
});
