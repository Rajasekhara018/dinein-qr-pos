import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiError } from '../../../core/api/api-error';
import { ADMIN_PATHS, WAITER_PATHS } from '../../../core/auth/auth-paths';
import { WAITER_SCREEN_ROLES } from '../../../core/auth/auth.guards';
import { AuthStore } from '../../../core/auth/auth.store';
import { AuthShellBenefit } from '../../../shared/components/auth-shell/auth-shell';

export type WaiterLoginMode = 'pin' | 'password';

export const WAITER_PIN_PATTERN = /^\d{4,6}$/;

/** Maps backend sign-in errors to waiter-friendly messages. */
export function waiterLoginErrorMessage(error: ApiError, mode: WaiterLoginMode): string {
  if (error.status === 429 || error.code === 'RATE_LIMITED') {
    return error.retryAfter
      ? `Too many attempts. Try again in ${error.retryAfter} seconds.`
      : 'Too many attempts. Wait a minute and try again.';
  }
  switch (error.code) {
    case 'INVALID_CREDENTIALS':
      return mode === 'pin'
        ? 'Wrong username or PIN. Check them and try again.'
        : 'Wrong username or password. Check them and try again.';
    case 'ACCOUNT_LOCKED':
      return (
        error.message ||
        'This account is locked after too many failed attempts. Try again in 15 minutes.'
      );
    case 'PIN_LOGIN_NOT_ALLOWED':
      return 'PIN sign-in is only for waiter accounts. Sign in with your password.';
    case 'KITCHEN_ACCOUNT':
      return 'Kitchen accounts sign in on the kitchen screen (/kitchen).';
    case 'CREDENTIALS_REQUIRED':
      return mode === 'pin' ? 'Enter your PIN.' : 'Enter your password.';
    case 'VALIDATION_FAILED':
      return error.message;
    case 'NETWORK_ERROR':
      return 'Cannot reach the server. Check the Wi-Fi and try again.';
    default:
      return error.message || 'Sign-in failed. Please try again.';
  }
}

/** Only waiter-screen URLs are allowed after sign-in (no open redirects). */
export function safeWaiterReturnUrl(url: string | null | undefined): string {
  if (!url || !url.startsWith(WAITER_PATHS.home) || url.startsWith('//')) return WAITER_PATHS.home;
  if (url.startsWith(WAITER_PATHS.login)) return WAITER_PATHS.home;
  return url;
}

/**
 * `/waiter/login`: staff sign-in with username + PIN (waiters) or password (anyone allowed on the waiter screen). A
 * normal staff session (15-minute JWT + refresh cookie), not a device token: waiters are individuals.
 */
@Component({
  selector: 'app-waiter-login',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './waiter-login.html',
})
export class WaiterLogin {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly benefits: AuthShellBenefit[] = [
    { title: 'Take orders at the table', description: 'Add items and send them straight to the kitchen, no counter trip needed.' },
    { title: 'Know the moment food is ready', description: 'A notification lands here as soon as the kitchen marks a ticket ready.' },
    { title: 'Collect payment on the spot', description: 'Cash, UPI or card — close out a table without leaving it.' },
  ];

  /** `?returnUrl=` (bound by the router). */
  readonly returnUrl = input<string | undefined>();
  /** `?reason=role`: the previous account may not use the waiter screen. */
  readonly reason = input<string | undefined>();

  protected readonly mode = signal<WaiterLoginMode>('pin');
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly submitted = signal(false);

  protected readonly form = this.fb.group({
    username: ['', [Validators.required, Validators.maxLength(50)]],
    pin: ['', [Validators.required, Validators.pattern(WAITER_PIN_PATTERN)]],
    password: ['', [Validators.maxLength(72)]],
  });

  setMode(mode: WaiterLoginMode): void {
    if (mode === this.mode()) return;
    this.mode.set(mode);
    this.errorMessage.set(null);
    const { pin, password } = this.form.controls;
    if (mode === 'pin') {
      pin.setValidators([Validators.required, Validators.pattern(WAITER_PIN_PATTERN)]);
      password.setValidators([Validators.maxLength(72)]);
      password.reset('');
    } else {
      pin.setValidators([Validators.pattern(WAITER_PIN_PATTERN)]);
      password.setValidators([Validators.required, Validators.maxLength(72)]);
      pin.reset('');
    }
    pin.updateValueAndValidity();
    password.updateValueAndValidity();
  }

  protected showError(control: 'username' | 'pin' | 'password'): boolean {
    const c = this.form.controls[control];
    return c.invalid && (c.touched || this.submitted());
  }

  async submit(): Promise<void> {
    this.submitted.set(true);
    this.errorMessage.set(null);
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const { username, pin, password } = this.form.getRawValue();
    const mode = this.mode();
    this.submitting.set(true);
    try {
      const user = await this.auth.signIn(
        mode === 'pin' ? { username: username.trim(), pin } : { username: username.trim(), password },
      );
      if (!WAITER_SCREEN_ROLES.includes(user.role)) {
        await this.auth.logout();
        this.errorMessage.set('This account cannot use the waiter screen.');
        return;
      }
      await this.router.navigateByUrl(
        user.mustChangePassword ? ADMIN_PATHS.changePassword : safeWaiterReturnUrl(this.returnUrl()),
      );
    } catch (e) {
      const error = ApiError.from(e);
      this.errorMessage.set(waiterLoginErrorMessage(error, mode));
      if (error.code === 'PIN_LOGIN_NOT_ALLOWED') {
        const message = this.errorMessage();
        this.setMode('password');
        this.errorMessage.set(message);
      } else if (mode === 'pin') {
        this.form.controls.pin.reset('');
      } else {
        this.form.controls.password.reset('');
      }
    } finally {
      this.submitting.set(false);
    }
  }
}
