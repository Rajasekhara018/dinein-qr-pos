import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { NotificationService } from '../../shared/services/notification-service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import { labelKey } from '../franchises/franchise-earnings.helpers';
import { FLOAT_PRIVILEGES, FLOAT_STATUS_LABEL_KEYS } from './wallet-float.constants';
import { floatErrorKey } from './wallet-float.helpers';
import { WalletFloatApi } from './wallet-float-api';
import { FloatSummary } from './wallet-float.models';

/** The signed-in partner's or agent's own float: balance, credit limit, what is still available, credit in use and the alert state. */
@Component({
  selector: 'app-my-float',
  standalone: false,
  templateUrl: './my-float.html',
  styleUrl: '../franchises/franchises-list.scss',
})
export class MyFloat implements OnInit {
  readonly privileges = FLOAT_PRIVILEGES;
  summary: FloatSummary | null = null;
  isLoading = false;
  hasError = false;

  constructor(
    private api: WalletFloatApi,
    private notify: NotificationService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.isLoading = true;
    this.hasError = false;
    this.api.mine().subscribe({
      next: (summary) => { this.summary = summary; this.isLoading = false; this.cdr.detectChanges(); },
      error: (err: unknown) => {
        this.hasError = true;
        this.isLoading = false;
        const key = floatErrorKey(err);
        this.notify.error(key ? this.translate.instant(key) : apiErrorMessage(err, this.translate.instant('walletFloat.my.errorMessage')));
        this.cdr.detectChanges();
      },
    });
  }

  statusLabel(status: string): string {
    const key = labelKey(FLOAT_STATUS_LABEL_KEYS, status);
    return key ? this.translate.instant(key) : status;
  }
}
