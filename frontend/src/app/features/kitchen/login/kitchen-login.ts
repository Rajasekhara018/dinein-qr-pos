import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiError } from '../../../core/api/api-error';
import { KITCHEN_PATHS } from '../../../core/auth/auth-paths';
import { DeviceAuthStore } from '../../../core/auth/device-auth.store';

export type KitchenLoginMode = 'pin' | 'password';

export const PIN_PATTERN = /^\d{4,6}$/;
export const DEFAULT_DEVICE_NAME = 'Kitchen screen';

/** Maps backend auth errors to kitchen-friendly messages. */
export function loginErrorMessage(error: ApiError, mode: KitchenLoginMode): string {
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
    case 'CREDENTIALS_REQUIRED':
    case 'VALIDATION_FAILED':
      return error.message;
    case 'NETWORK_ERROR':
      return 'Cannot reach the server. Check the Wi-Fi and try again.';
    default:
      return error.message || 'Sign-in failed. Please try again.';
  }
}

/** `/kitchen/login`: registers this screen as a kitchen device (username + password or 4–6 digit PIN). */
@Component({
  selector: 'app-kitchen-login',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './kitchen-login.html',
})
export class KitchenLogin {
  private readonly devices = inject(DeviceAuthStore);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly mode = signal<KitchenLoginMode>('pin');
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly submitted = signal(false);

  protected readonly form = this.fb.group({
    username: ['', [Validators.required, Validators.maxLength(50)]],
    pin: ['', [Validators.required, Validators.pattern(PIN_PATTERN)]],
    password: ['', [Validators.maxLength(72)]],
    deviceName: [DEFAULT_DEVICE_NAME, [Validators.maxLength(60)]],
  });

  setMode(mode: KitchenLoginMode): void {
    if (mode === this.mode()) return;
    this.mode.set(mode);
    this.errorMessage.set(null);
    const { pin, password } = this.form.controls;
    if (mode === 'pin') {
      pin.setValidators([Validators.required, Validators.pattern(PIN_PATTERN)]);
      password.setValidators([Validators.maxLength(72)]);
      password.reset('');
    } else {
      pin.setValidators([Validators.pattern(PIN_PATTERN)]);
      password.setValidators([Validators.required, Validators.maxLength(72)]);
      pin.reset('');
    }
    pin.updateValueAndValidity();
    password.updateValueAndValidity();
  }

  protected showError(control: 'username' | 'pin' | 'password' | 'deviceName'): boolean {
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
    const { username, pin, password, deviceName } = this.form.getRawValue();
    const mode = this.mode();
    this.submitting.set(true);
    try {
      await this.devices.registerDevice({
        username: username.trim(),
        pin: mode === 'pin' ? pin : undefined,
        password: mode === 'password' ? password : undefined,
        deviceName: deviceName.trim() || DEFAULT_DEVICE_NAME,
      });
      await this.router.navigateByUrl(KITCHEN_PATHS.home);
    } catch (e) {
      this.errorMessage.set(loginErrorMessage(ApiError.from(e), mode));
      if (mode === 'pin') this.form.controls.pin.reset('');
      else this.form.controls.password.reset('');
    } finally {
      this.submitting.set(false);
    }
  }
}
