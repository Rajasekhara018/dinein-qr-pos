import {
  AbstractControl,
  FormControl,
  FormGroup,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { SettingsResponse, UpdateSettingsRequest } from '../../../core/api/models';

export interface SettingsForm {
  name: FormControl<string>;
  address: FormControl<string>;
  phone: FormControl<string>;
  gstin: FormControl<string>;
  fssaiNo: FormControl<string>;
  logoImageId: FormControl<number | null>;
  openingTime: FormControl<string>;
  closingTime: FormControl<string>;
  acceptingOrders: FormControl<boolean>;
  pricesIncludeGst: FormControl<boolean>;
  brandColor: FormControl<string>;
  kitchenWarnMinutes: FormControl<number>;
  kitchenAlertMinutes: FormControl<number>;
  readyAutoHideMinutes: FormControl<number>;
}

/** Patterns mirror `SettingsDtos.UpdateSettingsRequest`. */
export const GSTIN_PATTERN = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/;
export const FSSAI_PATTERN = /^[0-9]{14}$/;
export const PHONE_PATTERN = /^[0-9+\- ]{6,15}$/;
export const COLOR_PATTERN = /^#[0-9A-Fa-f]{6}$/;

const minutes = [Validators.required, Validators.min(1), Validators.max(240)];

/** Kitchen warn threshold must be below the alert threshold; opening/closing time both or neither. */
export const settingsRules: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const warn = Number(group.get('kitchenWarnMinutes')?.value);
  const alert = Number(group.get('kitchenAlertMinutes')?.value);
  const open = group.get('openingTime')?.value as string;
  const close = group.get('closingTime')?.value as string;
  const errors: ValidationErrors = {};
  if (Number.isFinite(warn) && Number.isFinite(alert) && warn >= alert) errors['thresholds'] = true;
  if (!!open !== !!close) errors['hours'] = true;
  return Object.keys(errors).length ? errors : null;
};

export function createSettingsForm(): FormGroup<SettingsForm> {
  return new FormGroup<SettingsForm>(
    {
      name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
      address: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(300)] }),
      phone: new FormControl('', { nonNullable: true, validators: [Validators.pattern(PHONE_PATTERN)] }),
      gstin: new FormControl('', { nonNullable: true, validators: [Validators.pattern(GSTIN_PATTERN)] }),
      fssaiNo: new FormControl('', { nonNullable: true, validators: [Validators.pattern(FSSAI_PATTERN)] }),
      logoImageId: new FormControl<number | null>(null),
      openingTime: new FormControl('', { nonNullable: true }),
      closingTime: new FormControl('', { nonNullable: true }),
      acceptingOrders: new FormControl(true, { nonNullable: true }),
      pricesIncludeGst: new FormControl(false, { nonNullable: true }),
      brandColor: new FormControl('#c2410c', { nonNullable: true, validators: [Validators.pattern(COLOR_PATTERN)] }),
      kitchenWarnMinutes: new FormControl(10, { nonNullable: true, validators: minutes }),
      kitchenAlertMinutes: new FormControl(20, { nonNullable: true, validators: minutes }),
      readyAutoHideMinutes: new FormControl(10, { nonNullable: true, validators: minutes }),
    },
    { validators: settingsRules },
  );
}

/** `HH:mm:ss` → `HH:mm` for `<input type="time">`. */
const toTimeInput = (value?: string) => (value ? value.slice(0, 5) : '');

export function patchSettingsForm(form: FormGroup<SettingsForm>, s: SettingsResponse): void {
  form.reset({
    name: s.name,
    address: s.address ?? '',
    phone: s.phone ?? '',
    gstin: s.gstin ?? '',
    fssaiNo: s.fssaiNo ?? '',
    logoImageId: s.logoImageId ?? null,
    openingTime: toTimeInput(s.openingTime),
    closingTime: toTimeInput(s.closingTime),
    acceptingOrders: s.acceptingOrders,
    pricesIncludeGst: s.pricesIncludeGst,
    brandColor: s.brandColor ?? '#c2410c',
    kitchenWarnMinutes: s.kitchenWarnMinutes,
    kitchenAlertMinutes: s.kitchenAlertMinutes,
    readyAutoHideMinutes: s.readyAutoHideMinutes,
  });
}

export function toSettingsRequest(form: FormGroup<SettingsForm>): UpdateSettingsRequest {
  const v = form.getRawValue();
  const orNull = (s: string) => s.trim() || null;
  return {
    name: v.name.trim(),
    address: orNull(v.address),
    phone: orNull(v.phone),
    gstin: orNull(v.gstin.toUpperCase()),
    fssaiNo: orNull(v.fssaiNo),
    logoImageId: v.logoImageId,
    openingTime: v.openingTime || null,
    closingTime: v.closingTime || null,
    acceptingOrders: v.acceptingOrders,
    pricesIncludeGst: v.pricesIncludeGst,
    brandColor: v.brandColor || null,
    kitchenWarnMinutes: Number(v.kitchenWarnMinutes),
    kitchenAlertMinutes: Number(v.kitchenAlertMinutes),
    readyAutoHideMinutes: Number(v.readyAutoHideMinutes),
  };
}
