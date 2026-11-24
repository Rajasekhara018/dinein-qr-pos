import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../core/api/api-error';
import { StaffInfo } from '../../../core/api/models';
import { AuthStore } from '../../../core/auth/auth.store';
import { WaiterModule } from '../waiter-module';
import { safeWaiterReturnUrl, WaiterLogin, waiterLoginErrorMessage } from './waiter-login';

const waiter: StaffInfo = {
  id: 9,
  username: 'ravi',
  displayName: 'Ravi',
  role: 'WAITER',
  mustChangePassword: false,
  platformAdmin: false,
  restaurantId: 1,
};

describe('WaiterLogin', () => {
  let fixture: ComponentFixture<WaiterLogin>;
  let el: HTMLElement;
  let signIn: ReturnType<typeof vi.fn>;
  let logout: ReturnType<typeof vi.fn>;
  let router: Router;

  beforeEach(() => {
    signIn = vi.fn(async () => waiter);
    logout = vi.fn(async () => undefined);
    TestBed.configureTestingModule({
      // Import the declaring NgModule; don't re-declare the component.
      imports: [WaiterModule],
      providers: [provideRouter([]), { provide: AuthStore, useValue: { signIn, logout } }],
    });
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(WaiterLogin);
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
  const errorText = () => el.querySelector('[data-testid="waiter-login-error"]')?.textContent ?? '';

  it('defaults to PIN and signs in with {username, pin}', async () => {
    expect(input('w-pin')).not.toBeNull();
    expect(input('w-password')).toBeNull();
    type('w-username', ' ravi ');
    type('w-pin', '4321');
    await submit();
    expect(signIn).toHaveBeenCalledWith({ username: 'ravi', pin: '4321' });
    expect(router.navigateByUrl).toHaveBeenCalledWith('/waiter');
  });

  it('rejects a malformed PIN without calling the server', async () => {
    type('w-username', 'ravi');
    type('w-pin', '12');
    await submit();
    expect(signIn).not.toHaveBeenCalled();
    expect(el.querySelector('#w-pin-error')).not.toBeNull();
  });

  it('password mode sends {username, password}', async () => {
    modeButton('Password').click();
    fixture.detectChanges();
    type('w-username', 'owner');
    type('w-password', 'S3cret-pass');
    await submit();
    expect(signIn).toHaveBeenCalledWith({ username: 'owner', password: 'S3cret-pass' });
  });

  it('shows "wrong username or PIN" and clears the PIN on INVALID_CREDENTIALS', async () => {
    signIn.mockRejectedValue(new ApiError(401, 'INVALID_CREDENTIALS', 'Invalid'));
    type('w-username', 'ravi');
    type('w-pin', '9999');
    await submit();
    expect(errorText()).toContain('Wrong username or PIN');
    expect(input('w-pin')!.value).toBe('');
  });

  it('switches to password mode on PIN_LOGIN_NOT_ALLOWED and keeps the message', async () => {
    signIn.mockRejectedValue(new ApiError(403, 'PIN_LOGIN_NOT_ALLOWED', 'Sign in with your password'));
    type('w-username', 'manager');
    type('w-pin', '1234');
    await submit();
    expect(input('w-password')).not.toBeNull();
    expect(errorText()).toContain('only for waiter accounts');
  });

  it('points kitchen accounts to the kitchen screen', async () => {
    signIn.mockRejectedValue(new ApiError(403, 'KITCHEN_ACCOUNT', 'Kitchen accounts…'));
    type('w-username', 'cook');
    type('w-pin', '1234');
    await submit();
    expect(errorText()).toContain('kitchen screen');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('signs out an account that may not use the waiter screen', async () => {
    signIn.mockResolvedValue({ ...waiter, role: 'KITCHEN' });
    type('w-username', 'cook');
    type('w-pin', '1234');
    await submit();
    expect(logout).toHaveBeenCalled();
    expect(errorText()).toContain('cannot use the waiter screen');
  });

  it('sends an owner who must change the password to the admin page', async () => {
    signIn.mockResolvedValue({ ...waiter, role: 'OWNER', mustChangePassword: true });
    modeButton('Password').click();
    fixture.detectChanges();
    type('w-username', 'owner');
    type('w-password', 'ChangeMe@123');
    await submit();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/admin/change-password');
  });

  it('maps lock-out, rate limiting and network errors', () => {
    expect(waiterLoginErrorMessage(new ApiError(423, 'ACCOUNT_LOCKED', ''), 'pin')).toContain(
      'locked',
    );
    expect(
      waiterLoginErrorMessage(new ApiError(429, 'RATE_LIMITED', 'x', [], undefined, 30), 'pin'),
    ).toContain('30 seconds');
    expect(waiterLoginErrorMessage(new ApiError(0, 'NETWORK_ERROR', ''), 'pin')).toContain(
      'Wi-Fi',
    );
    expect(waiterLoginErrorMessage(new ApiError(401, 'INVALID_CREDENTIALS', ''), 'password')).toContain(
      'password',
    );
  });

  it('only returns to waiter URLs', () => {
    expect(safeWaiterReturnUrl('/waiter/tables')).toBe('/waiter/tables');
    expect(safeWaiterReturnUrl('/admin/settings')).toBe('/waiter');
    expect(safeWaiterReturnUrl('//evil.example')).toBe('/waiter');
    expect(safeWaiterReturnUrl('/waiter/login')).toBe('/waiter');
    expect(safeWaiterReturnUrl(undefined)).toBe('/waiter');
  });
});
