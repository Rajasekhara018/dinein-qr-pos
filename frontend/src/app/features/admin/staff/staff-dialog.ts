import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiError } from '../../../core/api/api-error';
import { AdminSettingsApi } from '../../../core/api/admin.api';
import { StaffResponse, StaffRole } from '../../../core/api/models';
import { AuthStore } from '../../../core/auth/auth.store';
import { generateTemporaryPassword, PASSWORD_MESSAGES, passwordPolicy } from '../auth/password-validators';
import { applyServerErrors, errorMessage, setServerError } from '../../../shared/util/form-errors';

export interface StaffDialogData {
  staff: StaffResponse | null;
}

interface StaffForm {
  username: FormControl<string>;
  displayName: FormControl<string>;
  role: FormControl<StaffRole>;
  active: FormControl<boolean>;
  password: FormControl<string>;
  pinMode: FormControl<'keep' | 'set' | 'clear'>;
  pin: FormControl<string>;
  email: FormControl<string>;
  phone: FormControl<string>;
}

export const ROLE_OPTIONS: { value: StaffRole; label: string; hint: string }[] = [
  { value: 'OWNER', label: 'Owner', hint: 'Everything, incl. staff, settings and reports' },
  { value: 'MANAGER', label: 'Manager', hint: 'Menu, tables and orders' },
  { value: 'KITCHEN', label: 'Kitchen', hint: 'Kitchen screen only (password or PIN)' },
  { value: 'WAITER', label: 'Waiter', hint: 'Waiter screen only: take orders, serve (password or PIN)' },
];

/** Create a staff user, or edit one (role, active, contact, password reset, PIN). */
@Component({
  selector: 'app-staff-dialog',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './staff-dialog.html',
})
export class StaffDialog {
  private readonly api = inject(AdminSettingsApi);
  private readonly auth = inject(AuthStore);
  protected readonly ref = inject<DialogRef<StaffResponse, StaffDialog>>(DialogRef);
  protected readonly data = inject<StaffDialogData>(DIALOG_DATA);

  protected readonly roles = ROLE_OPTIONS;
  protected readonly passwordMessages = PASSWORD_MESSAGES;
  protected readonly isEdit = !!this.data.staff;
  /** Editing yourself: role and active are locked (the server rejects self-lockout). */
  protected readonly isSelf = this.data.staff?.id === this.auth.user()?.id;

  protected readonly form = new FormGroup<StaffForm>({
    username: new FormControl(this.data.staff?.username ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(3), Validators.maxLength(50), Validators.pattern(/^[A-Za-z0-9._-]+$/)],
    }),
    displayName: new FormControl(this.data.staff?.displayName ?? '', { nonNullable: true, validators: [Validators.maxLength(80)] }),
    role: new FormControl<StaffRole>(this.data.staff?.role ?? 'MANAGER', { nonNullable: true }),
    active: new FormControl(this.data.staff?.active ?? true, { nonNullable: true }),
    password: new FormControl('', {
      nonNullable: true,
      validators: this.data.staff ? [passwordPolicy] : [Validators.required, passwordPolicy],
    }),
    pinMode: new FormControl<'keep' | 'set' | 'clear'>(this.data.staff ? 'keep' : 'set', { nonNullable: true }),
    pin: new FormControl('', { nonNullable: true, validators: [Validators.pattern(/^\d{4,6}$/)] }),
    email: new FormControl(this.data.staff?.email ?? '', { nonNullable: true, validators: [Validators.email, Validators.maxLength(120)] }),
    phone: new FormControl(this.data.staff?.phone ?? '', { nonNullable: true, validators: [Validators.pattern(/^\d{10}$/)] }),
  });

  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly showPassword = signal(false);
  protected readonly pinMode = signal(this.form.controls.pinMode.value);
  protected readonly title = computed(() => (this.isEdit ? `Edit ${this.data.staff?.username}` : 'New staff member'));

  constructor() {
    if (this.isEdit) this.form.controls.username.disable();
    if (this.isSelf) {
      this.form.controls.role.disable();
      this.form.controls.active.disable();
    }
    this.form.controls.pinMode.valueChanges.pipe(takeUntilDestroyed()).subscribe((mode) => {
      this.pinMode.set(mode);
      if (mode !== 'set') this.form.controls.pin.setValue('');
    });
  }

  protected generatePassword(): void {
    this.form.controls.password.setValue(generateTemporaryPassword());
    this.form.controls.password.markAsTouched();
    this.showPassword.set(true);
  }

  async save(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set('');
    const v = this.form.getRawValue();
    const pin = v.pinMode === 'set' && v.pin ? v.pin : null;
    try {
      const saved = this.data.staff
        ? await firstValueFrom(
            this.api.updateStaff(this.data.staff.id, {
              displayName: v.displayName.trim() || null,
              role: v.role,
              active: v.active,
              newPassword: v.password || null,
              pin,
              clearPin: v.pinMode === 'clear',
              email: v.email.trim() || null,
              phone: v.phone.trim() || null,
            }),
          )
        : await firstValueFrom(
            this.api.createStaff({
              username: v.username.trim(),
              displayName: v.displayName.trim() || null,
              role: v.role,
              password: v.password,
              pin,
              email: v.email.trim() || null,
              phone: v.phone.trim() || null,
            }),
          );
      this.ref.close(saved);
    } catch (error) {
      this.handleError(error);
    } finally {
      this.saving.set(false);
    }
  }

  private handleError(error: unknown): void {
    if (!(error instanceof ApiError)) {
      this.error.set(errorMessage(error));
      return;
    }
    switch (error.code) {
      case 'USERNAME_TAKEN':
        setServerError(this.form.controls.username, error.message);
        return;
      case 'WEAK_PASSWORD':
        setServerError(this.form.controls.password, error.message);
        return;
      case 'SELF_LOCKOUT':
        this.error.set('You cannot deactivate your own account or change your own role. Ask another owner.');
        return;
      case 'LAST_OWNER':
        this.error.set('At least one active owner must remain. Make someone else an owner first.');
        return;
      case 'VALIDATION_FAILED': {
        const unmatched = applyServerErrors(this.form, error, { newPassword: 'password' });
        if (unmatched.length) this.error.set(unmatched.join(' · '));
        return;
      }
      default:
        this.error.set(error.message);
    }
  }
}
