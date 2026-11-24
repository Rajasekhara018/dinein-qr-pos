import { ChangeDetectorRef, NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HQ_LANGUAGE_PROVIDER, HqSharedLibModule } from 'hq-shared-lib';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../../shared/services/auth-service';
import { BrandingService } from '../../shared/services/branding-service';
import { NotificationService } from '../../shared/services/notification-service';
import { UamService } from '../../shared/services/uam-service';
import { Register } from './register';

describe('Register', () => {
  /** Re-renders after the test changed state directly. */
  const refresh = (fixture: ComponentFixture<Register>) => fixture.debugElement.injector.get(ChangeDetectorRef).detectChanges();
  const uam = { registerCustomer: vi.fn(), sendPhoneOtp: vi.fn(), sendEmailOtp: vi.fn(), verifyPhoneOtp: vi.fn(), verifyEmailOtp: vi.fn() };
  const auth = { applySession: vi.fn() };
  const router = { navigate: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  const english = { code: 'en', nativeName: 'English', dir: 'ltr' as const };
  const languages = { languages: [english], currentLanguage: 'en', current: english, setLanguage: vi.fn() };
  const branding = { branding: { appName: 'TestApp', tagline: 't', poweredBy: 'p', logoImagePath: 'l.png', favicon: 'f.png' } };

  function create(): ComponentFixture<Register> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [Register],
      imports: [ReactiveFormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: UamService, useValue: uam },
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
        { provide: NotificationService, useValue: notify },
        { provide: BrandingService, useValue: branding },
        { provide: HQ_LANGUAGE_PROVIDER, useValue: languages },
      ],
    });
    const fixture = TestBed.createComponent(Register);
    fixture.detectChanges();
    return fixture;
  }

  const details = { name: 'Asha', mobile: '+919876543210', email: 'a@x.io' };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
    uam.registerCustomer.mockReturnValue(of({}));
    uam.sendPhoneOtp.mockReturnValue(of({}));
    uam.sendEmailOtp.mockReturnValue(of({}));
    uam.verifyPhoneOtp.mockReturnValue(of({ token: 'T' }));
    uam.verifyEmailOtp.mockReturnValue(of({ token: 'E' }));
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('starts on the details step with the phone channel', () => {
    const component = create().componentInstance;

    expect(component.step).toBe('details');
    expect(component.channel).toBe('phone');
  });

  it('does not register an invalid form', () => {
    const component = create().componentInstance;

    component.submitDetails();

    expect(uam.registerCustomer).not.toHaveBeenCalled();
    expect(component.nameCtrl?.touched).toBe(true);
    expect(component.mobileCtrl).not.toBeNull();
  });

  it('registers, then sends a code by phone and starts the resend countdown', () => {
    const component = create().componentInstance;
    component.detailsForm.patchValue(details);

    component.submitDetails();

    expect(uam.registerCustomer).toHaveBeenCalledWith(details);
    expect(notify.success).toHaveBeenCalledWith('register.messages.accountCreated');
    expect(uam.sendPhoneOtp).toHaveBeenCalledWith({ phone: details.mobile });
    expect(component.step).toBe('otp');
    expect(component.otpCooldown).toBe(60);

    vi.advanceTimersByTime(3000);
    expect(component.otpCooldown).toBe(57);
    vi.advanceTimersByTime(60_000);
    expect(component.otpCooldown).toBe(0);
  });

  it('sends the code by email when that channel is chosen', () => {
    const component = create().componentInstance;
    component.detailsForm.patchValue(details);
    component.setChannel('email');

    component.submitDetails();

    expect(uam.sendEmailOtp).toHaveBeenCalledWith({ email: details.email });
    expect(uam.sendPhoneOtp).not.toHaveBeenCalled();
    expect(notify.success).toHaveBeenLastCalledWith('register.messages.otpSent');
  });

  it('reports a registration failure and stays on the details step', () => {
    const component = create().componentInstance;
    component.detailsForm.patchValue(details);

    uam.registerCustomer.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Already registered' } })));
    component.submitDetails();
    expect(notify.error).toHaveBeenLastCalledWith('Already registered');

    uam.registerCustomer.mockReturnValueOnce(throwError(() => ({ error: { message: 'Nope' } })));
    component.submitDetails();
    expect(notify.error).toHaveBeenLastCalledWith('Nope');

    uam.registerCustomer.mockReturnValueOnce(throwError(() => undefined));
    component.submitDetails();
    expect(notify.error).toHaveBeenLastCalledWith('register.messages.registrationFailed');
    expect(component.step).toBe('details');
    expect(component.loading).toBe(false);
  });

  it('reports a failure to send the code', () => {
    const component = create().componentInstance;
    component.detailsForm.patchValue(details);

    uam.sendPhoneOtp.mockReturnValueOnce(throwError(() => ({ error: { detail: 'SMS down' } })));
    component.sendOtp();
    expect(notify.error).toHaveBeenLastCalledWith('SMS down');

    uam.sendPhoneOtp.mockReturnValueOnce(throwError(() => ({ error: {} })));
    component.sendOtp();
    expect(notify.error).toHaveBeenLastCalledWith('register.messages.otpSendFailed');
    expect(component.step).toBe('details');
  });

  it('ignores sending while a request is running', () => {
    const component = create().componentInstance;
    component.loading = true;

    component.sendOtp();

    expect(uam.sendPhoneOtp).not.toHaveBeenCalled();
  });

  describe('verifying the code', () => {
    const toOtpStep = (channel: 'phone' | 'email' = 'phone') => {
      const component = create().componentInstance;
      component.detailsForm.patchValue(details);
      component.setChannel(channel);
      component.sendOtp();
      return component;
    };

    it('does not verify an invalid code', () => {
      const component = toOtpStep();
      component.otpForm.patchValue({ otp: '12' });

      component.verifyOtp();

      expect(uam.verifyPhoneOtp).not.toHaveBeenCalled();
      expect(component.otpCtrl?.touched).toBe(true);
    });

    it('verifies the phone code, signs in and goes to Today', () => {
      const component = toOtpStep();
      component.otpForm.patchValue({ otp: '1234' });

      component.verifyOtp();

      expect(uam.verifyPhoneOtp).toHaveBeenCalledWith({ phone: details.mobile, otp: '1234' });
      expect(auth.applySession).toHaveBeenCalledWith({ token: 'T' });
      expect(notify.success).toHaveBeenLastCalledWith('register.messages.welcome');
      expect(router.navigate).toHaveBeenCalledWith(['/today']);
    });

    it('verifies the email code', () => {
      const component = toOtpStep('email');
      component.otpForm.patchValue({ otp: '9999' });

      component.verifyOtp();

      expect(uam.verifyEmailOtp).toHaveBeenCalledWith({ email: details.email, otp: '9999' });
      expect(auth.applySession).toHaveBeenCalledWith({ token: 'E' });
    });

    it('reports a wrong code and does not sign in', () => {
      const component = toOtpStep();
      component.otpForm.patchValue({ otp: '1234' });

      uam.verifyPhoneOtp.mockReturnValueOnce(throwError(() => ({ error: { message: 'Wrong code' } })));
      component.verifyOtp();
      expect(notify.error).toHaveBeenLastCalledWith('Wrong code');

      uam.verifyPhoneOtp.mockReturnValueOnce(throwError(() => ({})));
      component.verifyOtp();
      expect(notify.error).toHaveBeenLastCalledWith('register.messages.otpInvalid');
      expect(auth.applySession).not.toHaveBeenCalled();
    });

    it('only allows resending after the countdown', () => {
      const component = toOtpStep();
      uam.sendPhoneOtp.mockClear();

      component.resendOtp();
      expect(uam.sendPhoneOtp).not.toHaveBeenCalled();

      vi.advanceTimersByTime(61_000);
      component.resendOtp();
      expect(uam.sendPhoneOtp).toHaveBeenCalledTimes(1);
    });

    it('lets the user go back to edit the details, stopping the countdown', () => {
      const component = toOtpStep();

      component.backToDetails();

      expect(component.step).toBe('details');
      expect(component.otpCooldown).toBe(0);
    });

    it('stops the countdown when the screen is destroyed', () => {
      const fixture = create();
      const component = fixture.componentInstance;
      component.detailsForm.patchValue(details);
      component.sendOtp();

      fixture.destroy();
      vi.advanceTimersByTime(5000);

      expect(component.otpCooldown).toBe(60);
    });
  });

  it('shows the OTP form after a successful registration in the DOM', () => {
    const fixture = create();
    fixture.componentInstance.detailsForm.patchValue(details);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('#otp')).not.toBeNull();
  });
});
