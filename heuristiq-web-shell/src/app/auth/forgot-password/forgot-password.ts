import { Component, OnDestroy, inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { UamService } from '../../shared/services/uam-service';
import { NotificationService } from '../../shared/services/notification-service';
import { matchPasswords } from '../../shared/validators/password.validators';
import { getPasswordChecks, getPasswordStrength, PasswordCheck, PasswordStrength } from '../../shared/utils/password-strength';

import { TranslateService } from '@ngx-translate/core';
import { BrandingService } from '../../shared/services/branding-service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
@Component({
  selector: 'app-forgot-password',
  templateUrl: './forgot-password.html',
  standalone: false,
  styleUrl: './forgot-password.scss',
})
export class ForgotPassword implements OnDestroy {
  /** Per-deployment branding (app name, logo, tagline), loaded at runtime by BrandingService. */
  readonly branding = inject(BrandingService).branding;
  private readonly translate = inject(TranslateService);
  step: 'email' | 'reset' = 'email';
  loading = false;
  sentEmail = '';
  hideNewPassword = true;
  hideConfirmPassword = true;
  otpCooldown = 0;
  private cooldownRef?: ReturnType<typeof setInterval>;

  emailForm: FormGroup;
  resetForm: FormGroup;

  constructor(
    private fb: FormBuilder,
    private uam: UamService,
    private router: Router,
    private notify: NotificationService,
  ) {
    this.emailForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
    });
    this.resetForm = this.fb.group(
      {
        otp:              ['', [Validators.required, Validators.minLength(4)]],
        new_password:     ['', [Validators.required, Validators.minLength(13)]],
        confirm_password: ['', Validators.required],
      },
      { validators: matchPasswords('new_password', 'confirm_password') },
    );
  }

  sendOtp(): void {
    if (this.emailForm.invalid || this.loading) {
      this.emailForm.markAllAsTouched();
      return;
    }
    this.loading = true;
    const { email } = this.emailForm.value;
    this.uam.sendPasswordResetOtp(email).subscribe({
      next: () => {
        this.loading = false;
        this.sentEmail = email;
        this.step = 'reset';
        this.notify.success(this.translate.instant('forgotPassword.messages.otpSent'));
        this.startCooldown();
      },
      error: (err: any) => {
        this.loading = false;
        this.notify.error(apiErrorMessage(err, this.translate.instant('forgotPassword.messages.otpSendFailed')));
      },
    });
  }

  resetPassword(): void {
    if (this.resetForm.invalid || this.loading) {
      this.resetForm.markAllAsTouched();
      return;
    }
    this.loading = true;
    const { otp, new_password } = this.resetForm.value;
    this.uam.resetPassword(this.sentEmail, otp, new_password).subscribe({
      next: () => {
        this.loading = false;
        this.notify.success(this.translate.instant('forgotPassword.messages.resetSuccess'));
        this.router.navigate(['/auth/login']);
      },
      error: (err: any) => {
        this.loading = false;
        this.notify.error(apiErrorMessage(err, this.translate.instant('forgotPassword.messages.resetFailed')));
      },
    });
  }

  resendOtp(): void {
    if (this.otpCooldown > 0) return;
    this.step = 'email';
    this.resetForm.reset();
  }

  private startCooldown(): void {
    this.otpCooldown = 60;
    clearInterval(this.cooldownRef);
    this.cooldownRef = setInterval(() => {
      this.otpCooldown--;
      if (this.otpCooldown <= 0) clearInterval(this.cooldownRef);
    }, 1000);
  }

  ngOnDestroy(): void {
    clearInterval(this.cooldownRef);
  }

  get emailCtrl() { return this.emailForm.get('email'); }
  get otpCtrl() { return this.resetForm.get('otp'); }
  get newPasswordCtrl() { return this.resetForm.get('new_password'); }
  get confirmPasswordCtrl() { return this.resetForm.get('confirm_password'); }

  get passwordChecks(): PasswordCheck[] {
    return getPasswordChecks(this.newPasswordCtrl?.value ?? '');
  }

  get passwordStrength(): PasswordStrength {
    return getPasswordStrength(this.passwordChecks);
  }
}
