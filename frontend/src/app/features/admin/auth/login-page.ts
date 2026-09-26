import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ADMIN_PATHS } from '../../../core/auth/auth-paths';
import { AuthStore } from '../../../core/auth/auth.store';
import { ApiError } from '../../../core/api/api-error';
import { errorMessage } from '../shared/form-errors';

interface LoginForm {
  username: FormControl<string>;
  password: FormControl<string>;
}

/** Only same-app admin URLs are allowed as post-login destinations (no open redirects). */
export function safeAdminReturnUrl(url: string | null | undefined): string {
  if (!url || !url.startsWith('/admin') || url.startsWith('//')) return ADMIN_PATHS.home;
  if (url.startsWith(ADMIN_PATHS.login) || url.startsWith(ADMIN_PATHS.changePassword)) {
    return ADMIN_PATHS.home;
  }
  return url;
}

@Component({
  selector: 'app-admin-login-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login-page.html',
})
export class LoginPage {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  /** `?returnUrl=` (bound by the router). */
  readonly returnUrl = input<string | undefined>();

  protected readonly form = new FormGroup<LoginForm>({
    username: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(50)],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(72)],
    }),
  });

  protected readonly submitting = signal(false);
  protected readonly error = signal('');
  protected readonly showPassword = signal(false);

  async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set('');
    try {
      const { username, password } = this.form.getRawValue();
      const user = await this.auth.login(username.trim(), password);
      await this.router.navigateByUrl(
        user.mustChangePassword ? ADMIN_PATHS.changePassword : safeAdminReturnUrl(this.returnUrl()),
      );
    } catch (error) {
      this.error.set(this.describe(error));
      this.form.controls.password.reset();
    } finally {
      this.submitting.set(false);
    }
  }

  private describe(error: unknown): string {
    if (error instanceof ApiError) {
      switch (error.code) {
        case 'INVALID_CREDENTIALS':
          return 'Incorrect username or password.';
        case 'KITCHEN_ACCOUNT':
          return 'Kitchen accounts sign in on the kitchen screen (/kitchen).';
        case 'ACCOUNT_LOCKED':
        case 'RATE_LIMITED':
          return error.message;
      }
    }
    return errorMessage(error, 'Could not sign in. Please try again.');
  }
}
