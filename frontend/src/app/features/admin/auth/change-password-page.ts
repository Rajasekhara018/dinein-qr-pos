import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiError } from '../../../core/api/api-error';
import { ADMIN_PATHS } from '../../../core/auth/auth-paths';
import { AuthStore } from '../../../core/auth/auth.store';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { ToastService } from '../../../core/ui/toast.service';
import { errorMessage, setServerError } from '../shared/form-errors';
import { PASSWORD_MESSAGES, passwordChangeRules, passwordPolicy } from './password-validators';

interface ChangePasswordForm {
  currentPassword: FormControl<string>;
  newPassword: FormControl<string>;
  confirmPassword: FormControl<string>;
}

/** Forced after the first login (see `passwordChangeGuard`), and available from the user menu. */
@Component({
  selector: 'app-admin-change-password-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './change-password-page.html',
})
export class ChangePasswordPage {
  protected readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);
  private readonly realtime = inject(RealtimeService);

  protected readonly messages = PASSWORD_MESSAGES;
  protected readonly forced = this.auth.mustChangePassword;

  protected readonly form = new FormGroup<ChangePasswordForm>(
    {
      currentPassword: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(72)],
      }),
      newPassword: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, passwordPolicy],
      }),
      confirmPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    },
    { validators: passwordChangeRules },
  );

  protected readonly submitting = signal(false);
  protected readonly error = signal('');
  protected readonly attempted = signal(false);

  async submit(): Promise<void> {
    this.attempted.set(true);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set('');
    try {
      const { currentPassword, newPassword } = this.form.getRawValue();
      await this.auth.changePassword(currentPassword, newPassword);
      this.toasts.success('Password changed.');
      await this.router.navigateByUrl(ADMIN_PATHS.home);
    } catch (error) {
      if (error instanceof ApiError && error.code === 'INVALID_CURRENT_PASSWORD') {
        setServerError(this.form.controls.currentPassword, error.message);
      } else if (error instanceof ApiError && error.code === 'WEAK_PASSWORD') {
        setServerError(this.form.controls.newPassword, error.message);
      } else {
        this.error.set(errorMessage(error, 'Could not change the password.'));
      }
    } finally {
      this.submitting.set(false);
    }
  }

  async cancel(): Promise<void> {
    await this.router.navigateByUrl(ADMIN_PATHS.home);
  }

  async logout(): Promise<void> {
    await this.auth.logout();
    await this.realtime.disconnect();
    await this.router.navigateByUrl(ADMIN_PATHS.login);
  }
}
