import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { PaymentService } from '../../shared/services/payment-service';
import { PaymentLifecycleStep, PaymentTransaction } from '../../shared/models/payment.models';
import { TranslateService } from '@ngx-translate/core';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';

const FAILURE_STATES = ['FAILED', 'CANCELLED', 'EXPIRED'];
const PENDING_STATES = ['CREATED', 'PENDING'];

@Component({
  selector: 'app-payment-invoice',
  standalone: false,
  templateUrl: './payment-invoice.html',
  styleUrl: './payment-invoice.scss',
})
export class PaymentInvoice implements OnInit {
  loading = true;
  loadError = '';
  paymentId = '';
  transaction: PaymentTransaction | null = null;
  lifecycle: PaymentLifecycleStep[] = [];

  constructor(private route: ActivatedRoute, private paymentService: PaymentService, private translate: TranslateService) {}

  ngOnInit(): void {
    this.paymentId = this.route.snapshot.paramMap.get('paymentId') || '';
    this.loadStatus();
  }

  loadStatus(): void {
    if (!this.paymentId) {
      this.loading = false;
      this.loadError = this.translate.instant('payments.invoice.messages.noReference');
      return;
    }
    this.loading = true;
    this.loadError = '';
    this.paymentService.getByPaymentId(this.paymentId).subscribe({
      next: (transaction) => {
        this.transaction = transaction;
        this.loadLifecycle();
        const status = (transaction.status || '').toUpperCase();
        if (PENDING_STATES.includes(status) && transaction.gateway) {
          this.reconcileWithGateway(transaction.gateway);
        } else {
          this.loading = false;
        }
      },
      error: (err: any) => {
        this.loading = false;
        this.loadError = apiErrorMessage(err, this.translate.instant('payments.invoice.messages.loadFailed'));
      },
    });
  }

  // The gateway's webhook can't reach localhost in dev, so a PENDING transaction never advances
  // on its own — proactively pull the real status from the gateway instead of waiting on it.
  private reconcileWithGateway(gateway: string): void {
    this.paymentService.checkStatus({
      messageID: this.paymentId,
      gateway,
      object: { invoiceNumber: this.paymentId },
    }).subscribe({
      next: () => this.paymentService.getByPaymentId(this.paymentId).subscribe({
        next: (transaction) => { this.transaction = transaction; this.loading = false; },
        error: () => { this.loading = false; },
      }),
      error: () => { this.loading = false; },
    });
  }

  /** The payment's real status history; best effort -- the invoice is still useful without it. */
  private loadLifecycle(): void {
    this.paymentService.getTransitions(this.paymentId).subscribe({
      next: (steps) => (this.lifecycle = steps),
      error: () => (this.lifecycle = []),
    });
  }

  /** Translated label for a Rider_PaymentService status code (INITIATED, CAPTURED, ...). */
  stepStatusLabel(status: string | null | undefined): string {
    if (!status) return '-';
    const key = 'payments.invoice.trail.status.' + status.toLowerCase().replace(/_([a-z])/g, (_, c: string) => c.toUpperCase());
    const label = this.translate.instant(key);
    return label === key ? status : label;
  }

  stepSourceLabel(source: string | null | undefined): string {
    if (!source) return '';
    const key = 'payments.invoice.trail.source.' + source.toLowerCase().replace(/_([a-z])/g, (_, c: string) => c.toUpperCase());
    const label = this.translate.instant(key);
    return label === key ? source : label;
  }

  get status(): string {
    return (this.transaction?.status || '').toUpperCase();
  }

  get isSuccess(): boolean {
    return this.status === 'SUCCESS';
  }

  get isFailure(): boolean {
    return FAILURE_STATES.includes(this.status);
  }

  get isPending(): boolean {
    return PENDING_STATES.includes(this.status) || !this.status;
  }

  get statusText(): string {
    if (this.isSuccess) return this.translate.instant('payments.invoice.status.successful');
    if (this.isFailure) return this.translate.instant('payments.invoice.status.failed');
    return this.translate.instant('payments.invoice.status.processing');
  }

  get statusDetail(): string {
    if (this.isSuccess) return this.translate.instant('payments.invoice.statusDetail.success');
    if (this.isFailure) return this.transaction?.errorMessage || this.translate.instant('payments.invoice.statusDetail.failure');
    return this.translate.instant('payments.invoice.statusDetail.pending');
  }

  get amountDisplay(): string {
    const amount = this.transaction?.transactionAmount;
    if (amount === undefined || amount === null) {
      return '-';
    }
    return new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(amount);
  }

  get processedAtDisplay(): string {
    const raw = this.transaction?.saleDateTime || this.transaction?.updatedAt || this.transaction?.createdAt;
    if (!raw) {
      return '-';
    }
    const parsed = new Date(raw);
    if (Number.isNaN(parsed.getTime())) {
      return raw;
    }
    return parsed.toLocaleString('en-IN', {
      day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false,
    }) + ' IST';
  }

  display(value: string | number | undefined | null): string {
    if (value === undefined || value === null || value === '') {
      return '-';
    }
    return String(value);
  }
}
