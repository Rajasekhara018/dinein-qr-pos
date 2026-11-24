import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../shared/services/auth-service';
import { GoogleAuthService } from '../../shared/services/google-auth-service';
import { NotificationService } from '../../shared/services/notification-service';

import { TranslateService } from '@ngx-translate/core';
import { BrandingService } from '../../shared/services/branding-service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import { loginErrorCode, loginErrorKey } from './login.helpers';
import { EMAIL_VERIFICATION_REQUIRED_CODE, LOGIN_DEFAULT_STORY_ILLUSTRATION, LOGIN_EMAIL_PATTERN } from './login.constants';
import { MfaEnrollTotpResponse } from '../../shared/models/uam.models';

@Component({
  selector: 'app-login',
  templateUrl: './login.html',
  standalone: false,
  styleUrl: './login.scss',
})
export class Login implements OnInit {
  /** Per-deployment branding (app name, logo, tagline), loaded at runtime by BrandingService. */
  readonly branding = inject(BrandingService).branding;
  /** Picture above the story: the rail-cab scene with the default story, otherwise the deployment's own (none if unset). */
  readonly storyIllustration = this.branding.loginStory ? (this.branding.loginStory.illustration ?? '') : LOGIN_DEFAULT_STORY_ILLUSTRATION;
  private readonly translate = inject(TranslateService);
  form!: FormGroup;
  loading = false;
  showTotp = false;
  hidePassword = true;
  googleLoading = false;
  googleIdToken = '';
  enrollmentToken = '';
  enrollment: MfaEnrollTotpResponse | null = null;
  /** The email being verified (the security policy needs a verified email) and where the code went, shown masked. */
  emailCheck: { email: string; maskedEmail: string } | null = null;

  constructor(
    private fb: FormBuilder,
    private auth: AuthService,
    private googleAuth: GoogleAuthService,
    private router: Router,
    private notify: NotificationService,
  ) {}

  ngOnInit(): void {
    if (this.auth.isAuthenticated()) {
      this.router.navigate(['/today']);
      return;
    }
    this.form = this.fb.group({
      email:      ['', [Validators.required]],
      password:   ['', [Validators.required]],
      totp_code:  [''],
      email_code: [''],
    });
  }

  onSubmit(): void {
    if (this.loading) return;
    if (this.enrollmentToken && this.enrollment) {
      const code = String(this.form.value.totp_code ?? '').trim();
      if (!/^\d{6}$/.test(code)) { this.notify.error(this.translate.instant('login.messages.totpRequired')); return; }
      this.loading = true;
      this.auth.completeMfaEnrollment(this.enrollmentToken, this.enrollment.id, code).subscribe({
        next: data => { this.loading = false; this.handleResult(data); },
        error: error => { this.loading = false; this.notify.error(this.failureMessage(error, 'login.messages.loginFailed')); },
      });
      return;
    }
    if (this.emailCheck) {
      this.verifyEmail();
      return;
    }
    if (!this.googleIdToken && this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.showTotp && !this.form.value.totp_code) {
      this.notify.error(this.translate.instant('login.messages.totpRequired'));
      return;
    }
    this.loading = true;
    const { email, password, totp_code } = this.form.value;
    const credentials: any = { email, password };
    if (this.showTotp && totp_code) credentials.totpCode = totp_code;
    const login = this.googleIdToken ? this.auth.loginWithGoogle(this.googleIdToken, totp_code) : this.auth.login(credentials);
    login.subscribe({
      next: (data: any) => {
        this.loading = false;
        this.handleResult(data);
      },
      error: (err: any) => {
        this.loading = false;
        if (!this.googleIdToken && loginErrorCode(err) === EMAIL_VERIFICATION_REQUIRED_CODE) {
          this.startEmailVerification(String(email ?? '').trim());
          return;
        }
        this.notify.error(this.failureMessage(err, 'login.messages.loginFailed'));
      },
    });
  }

  /**
   * The security policy needs a verified email: a code is emailed and the page asks for it. Signing in with the code verifies the
   * address (UAM marks it) and finishes the sign-in, so nobody is stuck. Needs the email address itself as the sign-in name.
   */
  startEmailVerification(email: string, resend = false): void {
    if (!LOGIN_EMAIL_PATTERN.test(email)) {
      this.notify.error(this.translate.instant('login.emailCheck.needEmail'));
      return;
    }
    this.loading = true;
    this.auth.sendEmailVerificationCode(email).subscribe({
      next: (sent) => {
        this.loading = false;
        this.emailCheck = { email, maskedEmail: sent?.maskedEmail || email };
        this.form.patchValue({ email_code: '' });
        this.notify.success(this.translate.instant(resend ? 'login.emailCheck.resent' : 'login.emailCheck.sent', { email: this.emailCheck.maskedEmail }));
      },
      error: (err) => { this.loading = false; this.notify.error(this.failureMessage(err, 'login.emailCheck.sendFailed')); },
    });
  }

  resendEmailCode(): void {
    if (this.emailCheck && !this.loading) this.startEmailVerification(this.emailCheck.email, true);
  }

  private verifyEmail(): void {
    const check = this.emailCheck;
    if (!check) return;
    const code = String(this.form.value.email_code ?? '').trim();
    if (!/^\d{4,8}$/.test(code)) { this.notify.error(this.translate.instant('login.emailCheck.codeRequired')); return; }
    const totp = String(this.form.value.totp_code ?? '').trim();
    this.loading = true;
    this.auth.verifyEmailAndSignIn(check.email, code, totp || undefined).subscribe({
      next: (data: any) => { this.loading = false; this.handleResult(data); },
      error: (err) => { this.loading = false; this.notify.error(this.failureMessage(err, 'login.messages.loginFailed')); },
    });
  }

  goToForgotPassword(): void {
    this.router.navigate(['/auth/forgot-password']);
  }

  private handleResult(data: any): void {
    if (data.mfaEnrollmentRequired && data.enrollmentToken) {
      this.enrollmentToken = data.enrollmentToken;
      this.loading = true;
      this.auth.beginMfaEnrollment(data.enrollmentToken).subscribe({
        next: enrollment => { this.enrollment = enrollment; this.showTotp = true; this.loading = false; },
        error: error => { this.enrollmentToken = ''; this.loading = false; this.notify.error(this.failureMessage(error, 'login.messages.loginFailed')); },
      });
      return;
    }
    if (data.mfaRequired) {
      this.showTotp = true;
      this.notify.error(data.message || this.translate.instant('login.messages.mfaRequired'));
      return;
    }
    this.enrollment = null;
    this.enrollmentToken = '';
    this.googleIdToken = '';
    this.emailCheck = null;
    if (data.forcePasswordChange) this.router.navigate(['/change-password'], { queryParams: { reason: 'forced' } });
    else this.router.navigate(['/today']);
  }

  restartSignIn(): void {
    this.enrollment = null; this.enrollmentToken = ''; this.googleIdToken = ''; this.showTotp = false; this.emailCheck = null;
    this.form.patchValue({ password: '', totp_code: '', email_code: '' });
  }

  async signInWithGoogle(): Promise<void> {
    if (this.googleLoading) return;
    this.googleLoading = true;
    try {
      const idToken = await this.googleAuth.signIn();
      this.googleIdToken = idToken;
      this.auth.loginWithGoogle(idToken).subscribe({
        next: data => {
          this.googleLoading = false;
          this.handleResult(data);
        },
        error: (err: any) => {
          this.googleLoading = false;
          this.googleIdToken = '';
          this.notify.error(this.failureMessage(err, 'login.messages.googleFailed'));
        },
      });
    } catch (err: any) {
      this.googleLoading = false;
      this.notify.error(err?.message ?? this.translate.instant('login.messages.googleFailed'));
    }
  }

  /** A refusal UAM marks with a known code (e.g. FRANCHISE_INACTIVE) in the user's language; otherwise the server's text or the fallback. */
  private failureMessage(err: unknown, fallbackKey: string): string {
    const key = loginErrorKey(err);
    return key ? this.translate.instant(key) : apiErrorMessage(err, this.translate.instant(fallbackKey));
  }

  get emailCtrl() { return this.form.get('email'); }
  get passwordCtrl() { return this.form.get('password'); }
}
