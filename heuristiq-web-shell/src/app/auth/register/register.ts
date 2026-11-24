import { Component, OnDestroy, inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { UamService } from '../../shared/services/uam-service';
import { AuthService } from '../../shared/services/auth-service';
import { NotificationService } from '../../shared/services/notification-service';

import { TranslateService } from '@ngx-translate/core';
import { BrandingService } from '../../shared/services/branding-service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
type Step = 'details' | 'otp';
type OtpChannel = 'phone' | 'email';

@Component({
  selector: 'app-register',
  templateUrl: './register.html',
  standalone: false,
  styleUrl: './register.scss',
})
export class Register implements OnDestroy {
  /** Per-deployment branding (app name, logo, tagline), loaded at runtime by BrandingService. */
  readonly branding = inject(BrandingService).branding;
  private readonly translate = inject(TranslateService);
  step: Step = 'details';
  channel: OtpChannel = 'phone';
  loading = false;
  otpCooldown = 0;
  private cooldownRef?: ReturnType<typeof setInterval>;

  detailsForm: FormGroup;
  otpForm: FormGroup;

  constructor(
    private fb: FormBuilder,
    private uam: UamService,
    private auth: AuthService,
    private router: Router,
    private notify: NotificationService,
  ) {
    this.detailsForm = this.fb.group({
      name: ['', Validators.required],
      // Validation itself is provided by hq-phone-number-input's own NG_VALIDATORS (required +
      // valid international number); the control value is the E.164 string.
      mobile: [''],
      email: ['', [Validators.required, Validators.email]],
    });
    this.otpForm = this.fb.group({
      otp: ['', [Validators.required, Validators.minLength(4)]],
    });
  }

  ngOnDestroy(): void {
    clearInterval(this.cooldownRef);
  }

  get nameCtrl() { return this.detailsForm.get('name'); }
  get mobileCtrl() { return this.detailsForm.get('mobile'); }
  get emailCtrl() { return this.detailsForm.get('email'); }
  get otpCtrl() { return this.otpForm.get('otp'); }

  submitDetails(): void {
    if (this.detailsForm.invalid || this.loading) {
      this.detailsForm.markAllAsTouched();
      return;
    }
    this.loading = true;
    const { name, mobile, email } = this.detailsForm.value;
    this.uam.registerCustomer({ name, mobile, email }).subscribe({
      next: () => {
        this.loading = false;
        this.notify.success(this.translate.instant('register.messages.accountCreated'));
        this.sendOtp();
      },
      error: (err: any) => {
        this.loading = false;
        this.notify.error(apiErrorMessage(err, this.translate.instant('register.messages.registrationFailed')));
      },
    });
  }

  setChannel(channel: OtpChannel): void {
    this.channel = channel;
  }

  sendOtp(): void {
    if (this.loading) return;
    this.loading = true;
    const { mobile, email } = this.detailsForm.value;
    const request$ = this.channel === 'phone'
      ? this.uam.sendPhoneOtp({ phone: mobile })
      : this.uam.sendEmailOtp({ email });
    request$.subscribe({
      next: () => {
        this.loading = false;
        this.step = 'otp';
        this.notify.success(this.translate.instant('register.messages.otpSent', {
          channel: this.translate.instant(`register.channels.${this.channel}`),
        }));
        this.startCooldown();
      },
      error: (err: any) => {
        this.loading = false;
        this.notify.error(apiErrorMessage(err, this.translate.instant('register.messages.otpSendFailed')));
      },
    });
  }

  verifyOtp(): void {
    if (this.otpForm.invalid || this.loading) {
      this.otpForm.markAllAsTouched();
      return;
    }
    this.loading = true;
    const { mobile, email } = this.detailsForm.value;
    const { otp } = this.otpForm.value;
    const request$ = this.channel === 'phone'
      ? this.uam.verifyPhoneOtp({ phone: mobile, otp })
      : this.uam.verifyEmailOtp({ email, otp });
    request$.subscribe({
      next: (data) => {
        this.loading = false;
        this.auth.applySession(data);
        this.notify.success(this.translate.instant('register.messages.welcome', { appName: this.branding.appName }));
        this.router.navigate(['/today']);
      },
      error: (err: any) => {
        this.loading = false;
        this.notify.error(apiErrorMessage(err, this.translate.instant('register.messages.otpInvalid')));
      },
    });
  }

  resendOtp(): void {
    if (this.otpCooldown > 0) return;
    this.sendOtp();
  }

  backToDetails(): void {
    this.step = 'details';
    clearInterval(this.cooldownRef);
    this.otpCooldown = 0;
  }

  private startCooldown(): void {
    this.otpCooldown = 60;
    clearInterval(this.cooldownRef);
    this.cooldownRef = setInterval(() => {
      this.otpCooldown--;
      if (this.otpCooldown <= 0) clearInterval(this.cooldownRef);
    }, 1000);
  }
}
