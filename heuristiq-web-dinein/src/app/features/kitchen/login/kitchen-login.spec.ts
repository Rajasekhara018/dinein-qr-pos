import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../core/api/api-error';
import { DeviceAuthStore } from '../../../core/auth/device-auth.store';
import { KitchenModule } from '../kitchen-module';
import { KitchenLogin, loginErrorMessage } from './kitchen-login';

describe('KitchenLogin', () => {
  let fixture: ComponentFixture<KitchenLogin>;
  let el: HTMLElement;
  let registerDevice: ReturnType<typeof vi.fn>;
  let router: Router;

  beforeEach(() => {
    registerDevice = vi.fn(async () => ({}));
    TestBed.configureTestingModule({
      imports: [KitchenModule],
      providers: [provideRouter([]), { provide: DeviceAuthStore, useValue: { registerDevice } }],
    });
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(KitchenLogin);
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  const input = (id: string) => el.querySelector<HTMLInputElement>(`#${id}`);
  function type(id: string, value: string) {
    const i = input(id)!;
    i.value = value;
    i.dispatchEvent(new Event('input'));
  }
  async function submit() {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    fixture.detectChanges();
  }
  const modeButton = (label: string) =>
    Array.from(el.querySelectorAll<HTMLButtonElement>('button[aria-pressed]')).find(
      (b) => b.textContent!.trim() === label,
    )!;

  it('defaults to PIN mode with a device name', () => {
    expect(input('k-pin')).not.toBeNull();
    expect(input('k-password')).toBeNull();
    expect(input('k-device')!.value).toBe('Kitchen screen');
  });

  it.each(['12', '1234567', '12a4', ''])('rejects PIN "%s"', async (pin) => {
    type('k-username', 'cook');
    type('k-pin', pin);
    await submit();
    expect(registerDevice).not.toHaveBeenCalled();
    expect(el.querySelector('#k-pin-error')).not.toBeNull();
  });

  it.each(['1234', '123456'])('accepts PIN "%s" and registers the device', async (pin) => {
    type('k-username', ' cook ');
    type('k-pin', pin);
    await submit();
    expect(registerDevice).toHaveBeenCalledWith({
      username: 'cook',
      pin,
      password: undefined,
      deviceName: 'Kitchen screen',
    });
    expect(router.navigateByUrl).toHaveBeenCalledWith('/kitchen');
  });

  it('password mode requires a password and ignores the PIN rule', async () => {
    modeButton('Password').click();
    fixture.detectChanges();
    expect(input('k-pin')).toBeNull();
    type('k-username', 'cook');
    await submit();
    expect(registerDevice).not.toHaveBeenCalled();
    expect(el.querySelector('#k-password-error')).not.toBeNull();

    type('k-password', 'secret-pass');
    await submit();
    expect(registerDevice).toHaveBeenCalledWith({
      username: 'cook',
      pin: undefined,
      password: 'secret-pass',
      deviceName: 'Kitchen screen',
    });
  });

  it('shows a friendly message for wrong credentials and clears the PIN', async () => {
    registerDevice.mockRejectedValue(new ApiError(401, 'INVALID_CREDENTIALS', 'Invalid'));
    type('k-username', 'cook');
    type('k-pin', '9999');
    await submit();
    expect(el.querySelector('[data-testid="kitchen-login-error"]')!.textContent).toContain(
      'Wrong username or PIN',
    );
    expect(input('k-pin')!.value).toBe('');
  });

  it('maps lock-out and rate limiting', () => {
    expect(loginErrorMessage(new ApiError(423, 'ACCOUNT_LOCKED', ''), 'pin')).toContain('locked');
    expect(
      loginErrorMessage(new ApiError(429, 'RATE_LIMITED', 'x', [], undefined, 42), 'pin'),
    ).toContain('42 seconds');
    expect(loginErrorMessage(new ApiError(429, 'RATE_LIMITED', 'x'), 'password')).toContain(
      'Too many attempts',
    );
  });
});
