import { ChangeDetectorRef, NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HQ_LANGUAGE_PROVIDER, HqSharedLibModule } from 'hq-shared-lib';
import { of, throwError } from 'rxjs';
import { afterEach, vi } from 'vitest';
import { AuthService } from '../../shared/services/auth-service';
import { BrandingService } from '../../shared/services/branding-service';
import { GoogleAuthService } from '../../shared/services/google-auth-service';
import { NotificationService } from '../../shared/services/notification-service';
import { Login } from './login';
import { LOGIN_DEFAULT_STORY_ILLUSTRATION } from './login.constants';

describe('Login', () => {
  /** Re-renders after the test changed state directly. */
  const refresh = (fixture: ComponentFixture<Login>) => fixture.debugElement.injector.get(ChangeDetectorRef).detectChanges();
  const auth = { isAuthenticated: vi.fn(), login: vi.fn(), loginWithGoogle: vi.fn(), beginMfaEnrollment: vi.fn(), completeMfaEnrollment: vi.fn() };
  const google = { signIn: vi.fn() };
  const router = { navigate: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  const english = { code: 'en', nativeName: 'English', dir: 'ltr' as const };
  const languages = { languages: [english], currentLanguage: 'en', current: english, setLanguage: vi.fn() };
  const branding = { branding: { appName: 'TestApp', tagline: 't', poweredBy: 'p', logoImagePath: 'l.png', favicon: 'f.png' } };

  function create(render = true): ComponentFixture<Login> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [Login],
      imports: [ReactiveFormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: GoogleAuthService, useValue: google },
        { provide: Router, useValue: router },
        { provide: NotificationService, useValue: notify },
        { provide: BrandingService, useValue: branding },
        { provide: HQ_LANGUAGE_PROVIDER, useValue: languages },
      ],
    });
    const fixture = TestBed.createComponent(Login);
    if (render) fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    auth.isAuthenticated.mockReturnValue(false);
    auth.login.mockReturnValue(of({}));
    auth.loginWithGoogle.mockReturnValue(of({}));
  });

  const fill = (login: Login, email = 'a@x.io', password = 'a-long-password-1') => login.form.patchValue({ email, password });

  it('sends a signed-in visitor straight to Today', () => {
    auth.isAuthenticated.mockReturnValue(true);
    // Not rendered: the template needs the form that is never built for a signed-in visitor.
    const component = create(false).componentInstance;

    component.ngOnInit();

    expect(router.navigate).toHaveBeenCalledWith(['/today']);
    expect(component.form).toBeUndefined();
  });

  describe('account sign-in', () => {
    it('does not call the API with an invalid form', () => {
      const component = create().componentInstance;

      component.onSubmit();

      expect(auth.login).not.toHaveBeenCalled();
      expect(component.emailCtrl?.touched).toBe(true);
    });

    it('lets the server validate an existing password instead of assuming a fixed minimum', () => {
      const component = create().componentInstance;
      fill(component, 'a@x.io', 'short');

      expect(component.passwordCtrl?.errors).toBeNull();
      component.onSubmit();
      expect(auth.login).toHaveBeenCalled();
    });

    it('finishes required enrollment before navigating into the application', () => {
      auth.login.mockReturnValueOnce(of({ mfaRequired: true, mfaEnrollmentRequired: true, enrollmentToken: 'proof' }));
      auth.beginMfaEnrollment.mockReturnValueOnce(of({ id: 'mfa', secret: 'secret', qrCodeImageBase64: 'png' }));
      auth.completeMfaEnrollment.mockReturnValueOnce(of({ forcePasswordChange: false }));
      const component = create().componentInstance;
      fill(component);
      component.onSubmit();
      expect(auth.beginMfaEnrollment).toHaveBeenCalledWith('proof');
      expect(router.navigate).not.toHaveBeenCalled();
      expect(component.enrollment?.id).toBe('mfa');
      component.form.patchValue({ totp_code: '123456' });
      component.onSubmit();
      expect(auth.completeMfaEnrollment).toHaveBeenCalledWith('proof', 'mfa', '123456');
      expect(router.navigate).toHaveBeenCalledWith(['/today']);
      expect(component.enrollmentToken).toBe('');
    });

    it('signs in and goes to Today', () => {
      const component = create().componentInstance;
      fill(component);

      component.onSubmit();

      expect(auth.login).toHaveBeenCalledWith({ email: 'a@x.io', password: 'a-long-password-1' });
      expect(router.navigate).toHaveBeenCalledWith(['/today']);
      expect(component.loading).toBe(false);
    });

    it('sends a user with a forced password change to the change-password screen', () => {
      auth.login.mockReturnValue(of({ forcePasswordChange: true }));
      const component = create().componentInstance;
      fill(component);

      component.onSubmit();

      expect(router.navigate).toHaveBeenCalledWith(['/change-password'], { queryParams: { reason: 'forced' } });
    });

    it('asks for the authenticator code when two-factor is required, then sends it', () => {
      auth.login.mockReturnValueOnce(of({ mfaRequired: true, message: 'Enter your code' }));
      const component = create().componentInstance;
      fill(component);

      component.onSubmit();
      expect(component.showTotp).toBe(true);
      expect(notify.error).toHaveBeenCalledWith('Enter your code');
      expect(router.navigate).not.toHaveBeenCalled();

      component.onSubmit();
      expect(notify.error).toHaveBeenLastCalledWith('login.messages.totpRequired');
      expect(auth.login).toHaveBeenCalledTimes(1);

      component.form.patchValue({ totp_code: '123456' });
      component.onSubmit();
      expect(auth.login).toHaveBeenLastCalledWith({ email: 'a@x.io', password: 'a-long-password-1', totpCode: '123456' });
    });

    it('uses a default message when two-factor is required without one', () => {
      auth.login.mockReturnValue(of({ mfaRequired: true }));
      const component = create().componentInstance;
      fill(component);

      component.onSubmit();

      expect(notify.error).toHaveBeenCalledWith('login.messages.mfaRequired');
    });

    it('shows the server detail, message or a default when sign-in fails', () => {
      const component = create().componentInstance;
      fill(component);

      auth.login.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Locked' } })));
      component.onSubmit();
      expect(notify.error).toHaveBeenLastCalledWith('Locked');

      auth.login.mockReturnValueOnce(throwError(() => ({ error: { message: 'Bad credentials' } })));
      component.onSubmit();
      expect(notify.error).toHaveBeenLastCalledWith('Bad credentials');

      auth.login.mockReturnValueOnce(throwError(() => ({ error: {} })));
      component.onSubmit();
      expect(notify.error).toHaveBeenLastCalledWith('login.messages.loginFailed');
      expect(component.loading).toBe(false);
    });

    it('shows the translated franchise-inactive message instead of the server text', () => {
      const component = create().componentInstance;
      fill(component);

      auth.login.mockReturnValueOnce(throwError(() => ({
        status: 403,
        error: { success: false, message: 'Your franchise is not active (English server text)', errorCode: 'FRANCHISE_INACTIVE' },
      })));
      component.onSubmit();

      expect(notify.error).toHaveBeenLastCalledWith('login.messages.franchiseInactive');
      expect(router.navigate).not.toHaveBeenCalled();
      expect(component.loading).toBe(false);
    });

    it('goes to the forgot-password screen', () => {
      create().componentInstance.goToForgotPassword();

      expect(router.navigate).toHaveBeenCalledWith(['/auth/forgot-password']);
    });

    it('submitting the rendered form signs in, and the button stays disabled while it is invalid', () => {
      const fixture = create();
      const el: HTMLElement = fixture.nativeElement;
      const submit = el.querySelector('button.login-submit') as HTMLButtonElement;
      expect(submit.disabled).toBe(true);

      fill(fixture.componentInstance);
      refresh(fixture);
      expect(submit.disabled).toBe(false);
      (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));

      expect(auth.login).toHaveBeenCalled();
    });
  });

  describe('Google sign-in', () => {
    it('signs in with the Google id token', async () => {
      google.signIn.mockResolvedValue('id-token');
      const component = create().componentInstance;

      await component.signInWithGoogle();

      expect(auth.loginWithGoogle).toHaveBeenCalledWith('id-token');
      expect(router.navigate).toHaveBeenCalledWith(['/today']);
      expect(component.googleLoading).toBe(false);
    });

    it('reports a rejected token', async () => {
      google.signIn.mockResolvedValue('id-token');
      auth.loginWithGoogle.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Not allowed' } })));
      const component = create().componentInstance;

      await component.signInWithGoogle();
      expect(notify.error).toHaveBeenLastCalledWith('Not allowed');

      auth.loginWithGoogle.mockReturnValueOnce(throwError(() => ({ error: {} })));
      await component.signInWithGoogle();
      expect(notify.error).toHaveBeenLastCalledWith('login.messages.googleFailed');
      expect(component.googleLoading).toBe(false);
    });

    it('shows the translated franchise-inactive message for a Google sign-in too', async () => {
      google.signIn.mockResolvedValue('id-token');
      auth.loginWithGoogle.mockReturnValueOnce(throwError(() => ({
        status: 403,
        error: { message: 'server text', errorCode: 'FRANCHISE_INACTIVE' },
      })));
      const component = create().componentInstance;

      await component.signInWithGoogle();

      expect(notify.error).toHaveBeenLastCalledWith('login.messages.franchiseInactive');
      expect(component.googleLoading).toBe(false);
    });

    it('reports a failure of the Google dialog itself', async () => {
      const component = create().componentInstance;
      google.signIn.mockRejectedValueOnce(new Error('popup closed'));
      await component.signInWithGoogle();
      expect(notify.error).toHaveBeenLastCalledWith('popup closed');

      google.signIn.mockRejectedValueOnce({});
      await component.signInWithGoogle();
      expect(notify.error).toHaveBeenLastCalledWith('login.messages.googleFailed');
    });

    it('ignores a second click while one is in progress', async () => {
      const component = create().componentInstance;
      component.googleLoading = true;

      await component.signInWithGoogle();

      expect(google.signIn).not.toHaveBeenCalled();
    });
  });
  describe('story picture', () => {
    const story = { badge: 'b', headline: 'h', copy: 'c', features: [], stats: [] };
    const withStory = (loginStory?: object) => {
      const target = branding.branding as { loginStory?: object };
      if (loginStory) target.loginStory = loginStory;
      else delete target.loginStory;
    };
    const art = (fixture: ComponentFixture<Login>) => (fixture.nativeElement as HTMLElement).querySelector('[data-testid="login-story-art"]');

    afterEach(() => withStory());

    it('shows the rail-cab scene with the default story', () => {
      withStory();
      const fixture = create();
      expect(art(fixture)?.getAttribute('src')).toBe(LOGIN_DEFAULT_STORY_ILLUSTRATION);
    });

    it("shows the deployment's own picture with its own story", () => {
      withStory({ ...story, illustration: 'assets/illustrations/platform-hub.svg' });
      expect(art(create())?.getAttribute('src')).toBe('assets/illustrations/platform-hub.svg');
    });

    it('shows no picture when the deployment has its own story but names none', () => {
      withStory(story);
      expect(art(create())).toBeNull();
    });
  });
});
