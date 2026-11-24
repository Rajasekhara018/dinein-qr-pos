import { ChangeDetectorRef, NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { BrandingService } from '../../shared/services/branding-service';
import { NotificationService } from '../../shared/services/notification-service';
import { UamService } from '../../shared/services/uam-service';
import { ForgotPassword } from './forgot-password';

describe('ForgotPassword', () => {
  /** Re-renders after the test changed state directly. */
  const refresh = (fixture: ComponentFixture<ForgotPassword>) => fixture.debugElement.injector.get(ChangeDetectorRef).detectChanges();
  const uam = { sendPasswordResetOtp: vi.fn(), resetPassword: vi.fn() };
  const router = { navigate: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  const branding = { branding: { appName: 'TestApp', tagline: 't', poweredBy: 'p', logoImagePath: 'l.png', favicon: 'f.png' } };
  const PASSWORD = 'a-Strong-Passw0rd!';

  function create(): ComponentFixture<ForgotPassword> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [ForgotPassword],
      imports: [ReactiveFormsModule, TranslateModule.forRoot()],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: UamService, useValue: uam },
        { provide: Router, useValue: router },
        { provide: NotificationService, useValue: notify },
        { provide: BrandingService, useValue: branding },
      ],
    });
    const fixture = TestBed.createComponent(ForgotPassword);
    fixture.detectChanges();
    return fixture;
  }

  const toResetStep = () => {
    const fixture = create();
    const component = fixture.componentInstance;
    component.emailForm.patchValue({ email: 'a@x.io' });
    component.sendOtp();
    return { fixture, component };
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
    uam.sendPasswordResetOtp.mockReturnValue(of({}));
    uam.resetPassword.mockReturnValue(of({}));
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('starts on the email step', () => {
    expect(create().componentInstance.step).toBe('email');
  });

  it('does not send a code for a missing or malformed email', () => {
    const component = create().componentInstance;

    component.sendOtp();
    component.emailForm.patchValue({ email: 'nope' });
    component.sendOtp();

    expect(uam.sendPasswordResetOtp).not.toHaveBeenCalled();
    expect(component.emailCtrl?.errors?.['email']).toBeTruthy();
  });

  it('sends the code, moves to the reset step and starts the resend countdown', () => {
    const { component } = toResetStep();

    expect(uam.sendPasswordResetOtp).toHaveBeenCalledWith('a@x.io');
    expect(component.step).toBe('reset');
    expect(component.sentEmail).toBe('a@x.io');
    expect(notify.success).toHaveBeenCalledWith('forgotPassword.messages.otpSent');
    expect(component.otpCooldown).toBe(60);
    vi.advanceTimersByTime(61_000);
    expect(component.otpCooldown).toBe(0);
  });

  it('reports a failure to send the code', () => {
    const component = create().componentInstance;
    component.emailForm.patchValue({ email: 'a@x.io' });

    uam.sendPasswordResetOtp.mockReturnValueOnce(throwError(() => ({ error: { detail: 'No such account' } })));
    component.sendOtp();
    expect(notify.error).toHaveBeenLastCalledWith('No such account');

    uam.sendPasswordResetOtp.mockReturnValueOnce(throwError(() => ({ error: { message: 'Busy' } })));
    component.sendOtp();
    expect(notify.error).toHaveBeenLastCalledWith('Busy');

    uam.sendPasswordResetOtp.mockReturnValueOnce(throwError(() => ({ error: {} })));
    component.sendOtp();
    expect(notify.error).toHaveBeenLastCalledWith('forgotPassword.messages.otpSendFailed');
    expect(component.step).toBe('email');
    expect(component.loading).toBe(false);
  });

  describe('resetting', () => {
    it('refuses a short code, a short password or a mismatch', () => {
      const { component } = toResetStep();

      component.resetForm.patchValue({ otp: '12', new_password: PASSWORD, confirm_password: PASSWORD });
      component.resetPassword();
      component.resetForm.patchValue({ otp: '1234', new_password: 'short', confirm_password: 'short' });
      component.resetPassword();
      component.resetForm.patchValue({ new_password: PASSWORD, confirm_password: 'different-Passw0rd!' });
      component.resetPassword();

      expect(uam.resetPassword).not.toHaveBeenCalled();
      expect(component.resetForm.errors?.['mismatch']).toBeTruthy();
    });

    it('resets the password and goes to the login page', () => {
      const { component } = toResetStep();
      component.resetForm.patchValue({ otp: '1234', new_password: PASSWORD, confirm_password: PASSWORD });

      component.resetPassword();

      expect(uam.resetPassword).toHaveBeenCalledWith('a@x.io', '1234', PASSWORD);
      expect(notify.success).toHaveBeenLastCalledWith('forgotPassword.messages.resetSuccess');
      expect(router.navigate).toHaveBeenCalledWith(['/auth/login']);
    });

    it('reports a failed reset', () => {
      const { component } = toResetStep();
      component.resetForm.patchValue({ otp: '1234', new_password: PASSWORD, confirm_password: PASSWORD });

      uam.resetPassword.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Code expired' } })));
      component.resetPassword();
      expect(notify.error).toHaveBeenLastCalledWith('Code expired');

      uam.resetPassword.mockReturnValueOnce(throwError(() => ({ error: { message: 'Weak' } })));
      component.resetPassword();
      expect(notify.error).toHaveBeenLastCalledWith('Weak');

      uam.resetPassword.mockReturnValueOnce(throwError(() => ({ error: {} })));
      component.resetPassword();
      expect(notify.error).toHaveBeenLastCalledWith('forgotPassword.messages.resetFailed');
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('scores the new password against the checklist', () => {
      const { component } = toResetStep();

      component.resetForm.patchValue({ new_password: '' });
      const weak = component.passwordStrength;
      component.resetForm.patchValue({ new_password: PASSWORD });

      expect(component.passwordChecks.length).toBeGreaterThan(0);
      expect(component.passwordChecks.every((c) => c.valid)).toBe(true);
      expect(component.passwordStrength).not.toEqual(weak);
    });

    it('lets the user ask for a new code only after the countdown', () => {
      const { fixture, component } = toResetStep();

      component.resendOtp();
      expect(component.step).toBe('reset');

      vi.advanceTimersByTime(61_000);
      component.resetForm.patchValue({ otp: '1234' });
      component.resendOtp();
      expect(component.step).toBe('email');
      expect(component.resetForm.get('otp')?.value).toBeNull();
      // Leave before the zoneless scheduler re-renders the state this test changed by hand.
      fixture.destroy();
    });

    it('stops the countdown on destroy', () => {
      const { fixture, component } = toResetStep();

      fixture.destroy();
      vi.advanceTimersByTime(5000);

      expect(component.otpCooldown).toBe(60);
    });

    it('exposes the controls and renders the reset step', () => {
      const { fixture, component } = toResetStep();
      refresh(fixture);

      expect(component.otpCtrl).not.toBeNull();
      expect(component.newPasswordCtrl).not.toBeNull();
      expect(component.confirmPasswordCtrl).not.toBeNull();
      expect(fixture.nativeElement.querySelector('input[formControlName="otp"]')).not.toBeNull();
    });
  });
});
