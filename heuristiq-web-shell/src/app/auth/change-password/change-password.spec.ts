import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ApiService } from '../../shared/services/api-service';
import { BrandingService } from '../../shared/services/branding-service';
import { NotificationService } from '../../shared/services/notification-service';
import { ChangePassword } from './change-password';

describe('ChangePassword', () => {
  const api = { post: vi.fn() };
  const router = { navigate: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  const branding = { branding: { appName: 'TestApp', tagline: 't', poweredBy: 'p', logoImagePath: 'l.png', favicon: 'f.png' } };
  const NEW = 'a-Strong-Passw0rd!';

  function create(): ComponentFixture<ChangePassword> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ChangePassword, TranslateModule.forRoot()],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: Router, useValue: router },
        { provide: NotificationService, useValue: notify },
        { provide: BrandingService, useValue: branding },
      ],
    });
    const fixture = TestBed.createComponent(ChangePassword);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    api.post.mockReturnValue(of({}));
  });

  it('does not submit an empty form and marks the fields touched', () => {
    const component = create().componentInstance;

    component.onSubmit();

    expect(api.post).not.toHaveBeenCalled();
    expect(component.currentPasswordCtrl?.touched).toBe(true);
  });

  it('requires a new password of at least 13 characters that matches the confirmation', () => {
    const component = create().componentInstance;

    component.form.patchValue({ current_password: 'old', new_password: 'short', confirm_password: 'short' });
    expect(component.newPasswordCtrl?.errors?.['minlength']).toBeTruthy();

    component.form.patchValue({ new_password: NEW, confirm_password: 'other-Passw0rd!!' });
    component.onSubmit();
    expect(component.form.errors?.['mismatch']).toBeTruthy();
    expect(api.post).not.toHaveBeenCalled();
  });

  it('changes the password and goes to Today', () => {
    const component = create().componentInstance;
    component.form.patchValue({ current_password: 'old-pass', new_password: NEW, confirm_password: NEW });

    component.onSubmit();

    expect(api.post).toHaveBeenCalledWith('/v1/uam/identity/account/change-password', { requestObject: { oldPassword: 'old-pass', newPassword: NEW } });
    expect(notify.success).toHaveBeenCalledWith('changePassword.messages.success');
    expect(router.navigate).toHaveBeenCalledWith(['/today']);
    expect(component.loading).toBe(false);
  });

  it('shows the server detail or a default message when the change fails', () => {
    const component = create().componentInstance;
    component.form.patchValue({ current_password: 'old-pass', new_password: NEW, confirm_password: NEW });

    api.post.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Old password wrong' } })));
    component.onSubmit();
    expect(notify.error).toHaveBeenLastCalledWith('Old password wrong');

    api.post.mockReturnValueOnce(throwError(() => ({})));
    component.onSubmit();
    expect(notify.error).toHaveBeenLastCalledWith('changePassword.messages.failed');
    expect(router.navigate).not.toHaveBeenCalled();
    expect(component.loading).toBe(false);
  });

  it('does not submit twice while a request is running', () => {
    const component = create().componentInstance;
    component.form.patchValue({ current_password: 'old-pass', new_password: NEW, confirm_password: NEW });
    component.loading = true;

    component.onSubmit();

    expect(api.post).not.toHaveBeenCalled();
  });

  it('rates the new password and keeps the checklist in step with what is typed', () => {
    const component = create().componentInstance;

    component.form.patchValue({ new_password: 'a' });
    const weakMet = component.passwordChecks.filter((c) => c.valid).length;
    component.form.patchValue({ new_password: NEW });

    expect(component.passwordChecks.filter((c) => c.valid).length).toBeGreaterThan(weakMet);
    expect(component.passwordChecks.every((c) => c.valid)).toBe(true);
    expect(component.confirmPasswordCtrl).not.toBeNull();
  });

  it('submits through the rendered form', () => {
    const fixture = create();
    fixture.componentInstance.form.patchValue({ current_password: 'old-pass', new_password: NEW, confirm_password: NEW });
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));

    expect(api.post).toHaveBeenCalled();
  });

  it('shows the validation message for a missing current password once touched', () => {
    const fixture = create();
    fixture.componentInstance.onSubmit();
    fixture.componentInstance.form.get('current_password')?.markAsTouched();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.field-error')).not.toBeNull();
  });
});
