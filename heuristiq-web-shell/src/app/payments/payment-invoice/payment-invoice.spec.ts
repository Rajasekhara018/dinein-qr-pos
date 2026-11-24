import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PaymentService } from '../../shared/services/payment-service';
import { PaymentInvoice } from './payment-invoice';

describe('PaymentInvoice', () => {
  const payments = { getByPaymentId: vi.fn(), checkStatus: vi.fn(), getTransitions: vi.fn() };
  let paymentId: string | null;

  function create(): ComponentFixture<PaymentInvoice> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [PaymentInvoice],
      imports: [TranslateModule.forRoot()],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: PaymentService, useValue: payments },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(paymentId ? { paymentId } : {}) } } },
      ],
    });
    const fixture = TestBed.createComponent(PaymentInvoice);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    paymentId = 'pay1';
    payments.getByPaymentId.mockReset();
    payments.getByPaymentId.mockReturnValue(of({ status: 'SUCCESS', gateway: 'RAZORPAY', transactionAmount: 1234.5, receiptNumber: 'R1' }));
    payments.getTransitions.mockReturnValue(of([{ status: 'CAPTURED', source: 'GATEWAY_WEBHOOK', reason: 'ok' }]));
    payments.checkStatus.mockReturnValue(of({}));
  });

  it('shows an error when the link has no payment reference', () => {
    paymentId = null;
    const fixture = create();

    expect(payments.getByPaymentId).not.toHaveBeenCalled();
    expect(fixture.componentInstance.loadError).toBe('payments.invoice.messages.noReference');
    expect(fixture.nativeElement.querySelector('.invoice-error')).not.toBeNull();
  });

  it('loads a successful payment with its lifecycle and renders it', () => {
    const fixture = create();
    const component = fixture.componentInstance;

    expect(payments.getByPaymentId).toHaveBeenCalledWith('pay1');
    expect(payments.getTransitions).toHaveBeenCalledWith('pay1');
    expect(component.isSuccess).toBe(true);
    expect(component.isFailure).toBe(false);
    expect(component.isPending).toBe(false);
    expect(component.statusText).toBe('payments.invoice.status.successful');
    expect(component.statusDetail).toBe('payments.invoice.statusDetail.success');
    expect(component.amountDisplay).toBe('1,234.50');
    expect(component.loading).toBe(false);
    expect(payments.checkStatus).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.hero-note')?.textContent).toContain('noteSuccess');
    expect(fixture.nativeElement.querySelectorAll('.timeline-item').length).toBe(1);
  });

  it('reconciles a pending payment with the gateway and shows the refreshed status', () => {
    payments.getByPaymentId
      .mockReturnValueOnce(of({ status: 'pending', gateway: 'PAYU' }))
      .mockReturnValueOnce(of({ status: 'SUCCESS', gateway: 'PAYU' }));

    const component = create().componentInstance;

    expect(payments.checkStatus).toHaveBeenCalledWith({ messageID: 'pay1', gateway: 'PAYU', object: { invoiceNumber: 'pay1' } });
    expect(component.isSuccess).toBe(true);
    expect(component.loading).toBe(false);
  });

  it('stops loading when the gateway check or the re-read fails', () => {
    payments.getByPaymentId.mockReturnValue(of({ status: 'CREATED', gateway: 'PAYU' }));
    payments.checkStatus.mockReturnValueOnce(throwError(() => new Error('x')));
    expect(create().componentInstance.loading).toBe(false);

    payments.getByPaymentId.mockReset();
    payments.getByPaymentId.mockReturnValueOnce(of({ status: 'CREATED', gateway: 'PAYU' })).mockReturnValueOnce(throwError(() => new Error('x')));
    const component = create().componentInstance;
    expect(component.loading).toBe(false);
    expect(component.isPending).toBe(true);
  });

  it('does not reconcile a pending payment that has no gateway', () => {
    payments.getByPaymentId.mockReturnValue(of({ status: 'PENDING' }));

    const component = create().componentInstance;

    expect(payments.checkStatus).not.toHaveBeenCalled();
    expect(component.loading).toBe(false);
  });

  it('describes a failed payment with the gateway error message', () => {
    payments.getByPaymentId.mockReturnValue(of({ status: 'FAILED', errorMessage: 'Insufficient funds' }));
    const fixture = create();

    expect(fixture.componentInstance.isFailure).toBe(true);
    expect(fixture.componentInstance.statusText).toBe('payments.invoice.status.failed');
    expect(fixture.componentInstance.statusDetail).toBe('Insufficient funds');
    expect(fixture.nativeElement.querySelector('.reason-box')?.textContent).toContain('Insufficient funds');
  });

  it('uses the default failure detail when the gateway gave none', () => {
    payments.getByPaymentId.mockReturnValue(of({ status: 'EXPIRED' }));

    expect(create().componentInstance.statusDetail).toBe('payments.invoice.statusDetail.failure');
  });

  it('treats a missing status as processing', () => {
    payments.getByPaymentId.mockReturnValue(of({}));
    const component = create().componentInstance;

    expect(component.isPending).toBe(true);
    expect(component.statusText).toBe('payments.invoice.status.processing');
    expect(component.statusDetail).toBe('payments.invoice.statusDetail.pending');
  });

  it('reports a load failure with the server detail, message or a default, and can retry', () => {
    payments.getByPaymentId.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Not yours' } })));
    const component = create().componentInstance;
    expect(component.loadError).toBe('Not yours');

    payments.getByPaymentId.mockReturnValueOnce(throwError(() => ({ error: { message: 'Down' } })));
    component.loadStatus();
    expect(component.loadError).toBe('Down');

    payments.getByPaymentId.mockReturnValueOnce(throwError(() => ({})));
    component.loadStatus();
    expect(component.loadError).toBe('payments.invoice.messages.loadFailed');

    payments.getByPaymentId.mockReturnValueOnce(of({ status: 'SUCCESS' }));
    component.loadStatus();
    expect(component.loadError).toBe('');
  });

  it('shows a refresh button only while pending and reloads on click', () => {
    payments.getByPaymentId.mockReturnValue(of({ status: 'PENDING' }));
    const fixture = create();
    payments.getByPaymentId.mockClear();

    (fixture.nativeElement.querySelector('button.btn-outline-primary') as HTMLElement).click();

    expect(payments.getByPaymentId).toHaveBeenCalledTimes(1);

    payments.getByPaymentId.mockReturnValue(of({ status: 'SUCCESS' }));
    expect(create().nativeElement.querySelector('button.btn-outline-primary')).toBeNull();
  });

  it('survives a lifecycle that cannot be loaded', () => {
    payments.getTransitions.mockReturnValue(throwError(() => new Error('x')));
    const fixture = create();

    expect(fixture.componentInstance.lifecycle).toEqual([]);
    expect(fixture.nativeElement.textContent).toContain('payments.invoice.trail.empty');
  });

  it('labels statuses and sources, falling back to the raw code', () => {
    const component = create().componentInstance;

    expect(component.stepStatusLabel(null)).toBe('-');
    expect(component.stepStatusLabel('PARTIALLY_REFUNDED')).toBe('PARTIALLY_REFUNDED');
    expect(component.stepSourceLabel(undefined)).toBe('');
    expect(component.stepSourceLabel('GATEWAY_WEBHOOK')).toBe('GATEWAY_WEBHOOK');
  });

  it('formats the processed time in IST, and falls back for odd values', () => {
    const component = create().componentInstance;

    component.transaction = {} as never;
    expect(component.processedAtDisplay).toBe('-');
    component.transaction = { saleDateTime: '2026-01-15T10:30:00Z' } as never;
    expect(component.processedAtDisplay).toMatch(/15 Jan 2026.* IST$/);
    component.transaction = { updatedAt: 'not a date' } as never;
    expect(component.processedAtDisplay).toBe('not a date');
    component.transaction = { createdAt: '2026-02-01T00:00:00Z' } as never;
    expect(component.processedAtDisplay).toContain('Feb 2026');
  });

  it('shows a dash for a missing amount and for empty values', () => {
    const component = create().componentInstance;
    component.transaction = {} as never;

    expect(component.amountDisplay).toBe('-');
    expect(component.display(undefined)).toBe('-');
    expect(component.display('')).toBe('-');
    expect(component.display(0)).toBe('0');
    expect(component.display('x')).toBe('x');
  });
});
