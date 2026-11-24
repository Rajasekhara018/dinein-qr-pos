import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../shared/services/api-service';
import { AuthEndpoints } from '../../shared/api-endpoints';
import { NotificationService } from '../../shared/services/notification-service';
import { matchPasswords } from '../../shared/validators/password.validators';
import { getPasswordChecks, getPasswordStrength, PasswordCheck, PasswordStrength } from '../../shared/utils/password-strength';

import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HqSharedLibModule } from 'hq-shared-lib';
import { BrandingService } from '../../shared/services/branding-service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
@Component({
  selector: 'app-change-password',
  templateUrl: './change-password.html',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, TranslateModule, HqSharedLibModule],
  styleUrl: './change-password.scss',
})
export class ChangePassword implements OnInit {
  /** Per-deployment branding (app name, logo, tagline), loaded at runtime by BrandingService. */
  readonly branding = inject(BrandingService).branding;
  private readonly translate = inject(TranslateService);
  form!: FormGroup;
  loading = false;
  hideCurrentPassword = true;
  hideNewPassword = true;
  hideConfirmPassword = true;

  constructor(
    private fb: FormBuilder,
    private api: ApiService,
    private router: Router,
    private notify: NotificationService,
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group(
      {
        current_password: ['', Validators.required],
        new_password:     ['', [Validators.required, Validators.minLength(13)]],
        confirm_password: ['', Validators.required],
      },
      { validators: matchPasswords('new_password', 'confirm_password') },
    );
  }

  onSubmit(): void {
    if (this.form.invalid || this.loading) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading = true;

    const { current_password, new_password } = this.form.value;
    this.api.post(AuthEndpoints.changePassword(), { requestObject: { oldPassword: current_password, newPassword: new_password } }).subscribe({
      next: () => {
        this.loading = false;
        this.notify.success(this.translate.instant('changePassword.messages.success'));
        this.router.navigate(['/today']);
      },
      error: (err: any) => {
        this.loading = false;
        this.notify.error(apiErrorMessage(err, this.translate.instant('changePassword.messages.failed')));
      },
    });
  }

  get currentPasswordCtrl() { return this.form.get('current_password'); }
  get newPasswordCtrl() { return this.form.get('new_password'); }
  get confirmPasswordCtrl() { return this.form.get('confirm_password'); }

  get passwordChecks(): PasswordCheck[] {
    return getPasswordChecks(this.newPasswordCtrl?.value ?? '');
  }

  get passwordStrength(): PasswordStrength {
    return getPasswordStrength(this.passwordChecks);
  }
}
