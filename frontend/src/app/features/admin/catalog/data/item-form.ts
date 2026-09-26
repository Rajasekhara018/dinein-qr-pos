import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import {
  FoodType,
  ItemRequest,
  ItemResponse,
  MenuItem,
} from '../../../../core/api/models';
import { displayPriceOf } from './inline-price';

/** Typed reactive form for an item, mirroring `MenuAdminDtos.ItemRequest` validation. */

export interface VariantForm {
  id: FormControl<number | null>;
  name: FormControl<string>;
  price: FormControl<number | null>;
  isDefault: FormControl<boolean>;
}

export interface AddonForm {
  id: FormControl<number | null>;
  name: FormControl<string>;
  price: FormControl<number | null>;
}

export interface ItemForm {
  categoryId: FormControl<number | null>;
  name: FormControl<string>;
  description: FormControl<string>;
  foodType: FormControl<FoodType>;
  gstPercent: FormControl<number | null>;
  imageId: FormControl<number | null>;
  basePrice: FormControl<number | null>;
  available: FormControl<boolean>;
  hasSizes: FormControl<boolean>;
  variants: FormArray<FormGroup<VariantForm>>;
  addons: FormArray<FormGroup<AddonForm>>;
}

export type ItemFormGroup = FormGroup<ItemForm>;

export const GST_SLABS: readonly number[] = [0, 5, 12, 18, 28];
export const MAX_VARIANTS = 20;
export const MAX_ADDONS = 30;
const MAX_MONEY = 99_999_999.99;

// ─── Validators ──────────────────────────────────────────────────────────────────────────────────

/**
 * Money amount: a number ≥ `min` (inclusive) — or > 0 when `min` is 'positive' — with ≤ 2 decimals.
 * Empty values are left to `Validators.required`.
 */
export function moneyValidator(min: 'positive' | number): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value as number | null | '';
    if (value === null || value === '' || value === undefined) return null;
    const n = Number(value);
    if (!Number.isFinite(n)) return { money: 'Enter a valid amount.' };
    if (min === 'positive' ? n <= 0 : n < min) {
      return { money: min === 'positive' ? 'Price must be greater than ₹0.' : `Must be at least ₹${min}.` };
    }
    if (n > MAX_MONEY) return { money: 'Amount is too large.' };
    if (Math.round(n * 100) !== Number((n * 100).toFixed(6))) {
      return { money: 'Use at most 2 decimal places.' };
    }
    return null;
  };
}

/** Sizes: at least one, exactly one default, unique names (case-insensitive). */
export const variantsValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const array = control as FormArray<FormGroup<VariantForm>>;
  const rows = array.controls.map((c) => c.getRawValue());
  if (rows.length === 0) return { sizesRequired: true };
  const defaults = rows.filter((r) => r.isDefault).length;
  const errors: ValidationErrors = {};
  if (defaults === 0) errors['defaultRequired'] = true;
  if (defaults > 1) errors['multipleDefaults'] = true;
  if (hasDuplicates(rows.map((r) => r.name))) errors['duplicateNames'] = true;
  return Object.keys(errors).length ? errors : null;
};

/** Add-ons: unique names. */
export const addonsValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const array = control as FormArray<FormGroup<AddonForm>>;
  return hasDuplicates(array.controls.map((c) => c.controls.name.value))
    ? { duplicateNames: true }
    : null;
};

function hasDuplicates(names: string[]): boolean {
  const seen = new Set<string>();
  for (const raw of names) {
    const name = raw.trim().toLowerCase();
    if (!name) continue;
    if (seen.has(name)) return true;
    seen.add(name);
  }
  return false;
}

export const VARIANTS_MESSAGES: Record<string, string> = {
  sizesRequired: 'Add at least one size, or turn off "Has sizes".',
  defaultRequired: 'Choose which size is selected by default.',
  multipleDefaults: 'Only one size can be the default.',
  duplicateNames: 'Each size needs a different name.',
};

export const ADDONS_MESSAGES: Record<string, string> = {
  duplicateNames: 'Each add-on needs a different name.',
};

// ─── Factories ───────────────────────────────────────────────────────────────────────────────────

export function createVariantGroup(
  value: Partial<{ id: number | null; name: string; price: number | null; isDefault: boolean }> = {},
): FormGroup<VariantForm> {
  return new FormGroup<VariantForm>({
    id: new FormControl(value.id ?? null),
    name: new FormControl(value.name ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(50)],
    }),
    price: new FormControl(value.price ?? null, {
      validators: [Validators.required, moneyValidator('positive')],
    }),
    isDefault: new FormControl(value.isDefault ?? false, { nonNullable: true }),
  });
}

export function createAddonGroup(
  value: Partial<{ id: number | null; name: string; price: number | null }> = {},
): FormGroup<AddonForm> {
  return new FormGroup<AddonForm>({
    id: new FormControl(value.id ?? null),
    name: new FormControl(value.name ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(50)],
    }),
    price: new FormControl(value.price ?? 0, {
      validators: [Validators.required, moneyValidator(0)],
    }),
  });
}

export function createItemForm(defaults: { categoryId?: number | null } = {}): ItemFormGroup {
  const form = new FormGroup<ItemForm>({
    categoryId: new FormControl(defaults.categoryId ?? null, {
      validators: [Validators.required],
    }),
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(120)],
    }),
    description: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(500)] }),
    foodType: new FormControl<FoodType>('VEG', { nonNullable: true }),
    gstPercent: new FormControl<number | null>(5, {
      validators: [Validators.required, Validators.min(0), Validators.max(28)],
    }),
    imageId: new FormControl<number | null>(null),
    basePrice: new FormControl<number | null>(null, {
      validators: [Validators.required, moneyValidator('positive')],
    }),
    available: new FormControl(true, { nonNullable: true }),
    hasSizes: new FormControl(false, { nonNullable: true }),
    variants: new FormArray<FormGroup<VariantForm>>([], { validators: variantsValidator }),
    addons: new FormArray<FormGroup<AddonForm>>([], { validators: addonsValidator }),
  });
  applyHasSizes(form, false);
  return form;
}

/**
 * Switches between "one price" and "priced by size": disables the unused part so its validators don't block the
 * form (base price > 0 unless sizes; at least one size when sizes). Adds a first default row when enabling sizes.
 */
export function applyHasSizes(form: ItemFormGroup, hasSizes: boolean): void {
  const { basePrice, variants } = form.controls;
  if (hasSizes) {
    if (variants.length === 0) {
      variants.push(createVariantGroup({ name: '', price: basePrice.value, isDefault: true }));
    }
    variants.enable({ emitEvent: false });
    basePrice.disable({ emitEvent: false });
  } else {
    variants.disable({ emitEvent: false });
    basePrice.enable({ emitEvent: false });
  }
  form.updateValueAndValidity();
}

/** Makes row `index` the only default size. */
export function setDefaultVariant(form: ItemFormGroup, index: number): void {
  form.controls.variants.controls.forEach((row, i) =>
    row.controls.isDefault.setValue(i === index, { emitEvent: false }),
  );
  form.controls.variants.updateValueAndValidity();
}

export function addVariant(form: ItemFormGroup): void {
  const variants = form.controls.variants;
  if (variants.length >= MAX_VARIANTS) return;
  variants.push(createVariantGroup({ isDefault: variants.length === 0 }));
}

/** Removes a size; if it was the default, the first remaining size becomes the default. */
export function removeVariant(form: ItemFormGroup, index: number): void {
  const variants = form.controls.variants;
  const wasDefault = variants.at(index)?.controls.isDefault.value;
  variants.removeAt(index);
  if (wasDefault && variants.length > 0) setDefaultVariant(form, 0);
}

export function addAddon(form: ItemFormGroup): void {
  if (form.controls.addons.length >= MAX_ADDONS) return;
  form.controls.addons.push(createAddonGroup());
}

// ─── Mapping ─────────────────────────────────────────────────────────────────────────────────────

export function patchItemForm(form: ItemFormGroup, item: ItemResponse): void {
  form.controls.variants.clear({ emitEvent: false });
  form.controls.addons.clear({ emitEvent: false });
  for (const v of item.variants) {
    form.controls.variants.push(createVariantGroup(v), { emitEvent: false });
  }
  for (const a of item.addons) {
    form.controls.addons.push(createAddonGroup(a), { emitEvent: false });
  }
  form.patchValue({
    categoryId: item.categoryId,
    name: item.name,
    description: item.description ?? '',
    foodType: item.foodType,
    gstPercent: item.gstPercent,
    imageId: item.imageId ?? null,
    basePrice: item.basePrice ?? null,
    available: item.available,
    hasSizes: item.hasVariants,
  });
  applyHasSizes(form, item.hasVariants);
  form.markAsPristine();
}

const round2 = (n: number) => Math.round(n * 100) / 100;

export function toItemRequest(form: ItemFormGroup, version: number | null): ItemRequest {
  const v = form.getRawValue();
  const hasSizes = v.hasSizes;
  return {
    categoryId: v.categoryId as number,
    name: v.name.trim(),
    description: v.description.trim() || null,
    imageId: v.imageId,
    foodType: v.foodType,
    gstPercent: round2(Number(v.gstPercent ?? 0)),
    available: v.available,
    basePrice: hasSizes ? null : round2(Number(v.basePrice)),
    variants: hasSizes
      ? v.variants.map((row) => ({
          id: row.id,
          name: row.name.trim(),
          price: round2(Number(row.price)),
          isDefault: row.isDefault,
        }))
      : [],
    addons: v.addons.map((row) => ({
      id: row.id,
      name: row.name.trim(),
      price: round2(Number(row.price ?? 0)),
    })),
    version,
  };
}

/** What the guest menu card would show for the current form values (live preview). */
export function toPreviewItem(
  form: ItemFormGroup,
  image: { imageUrl?: string; thumbUrl?: string } = {},
): MenuItem {
  const v = form.getRawValue();
  const variants = v.hasSizes
    ? v.variants
        .filter((row) => Number(row.price) > 0)
        .map((row, i) => ({
          id: row.id ?? -(i + 1),
          name: row.name.trim() || `Size ${i + 1}`,
          price: Number(row.price),
          isDefault: row.isDefault,
        }))
    : [];
  const basePrice = v.hasSizes ? undefined : Number(v.basePrice) || 0;
  return {
    id: 0,
    name: v.name.trim() || 'Item name',
    description: v.description.trim() || undefined,
    foodType: v.foodType,
    basePrice,
    displayPrice: displayPriceOf(basePrice, variants) ?? 0,
    gstPercent: Number(v.gstPercent ?? 0),
    available: v.available,
    imageUrl: image.imageUrl,
    thumbUrl: image.thumbUrl,
    variants,
    addons: v.addons
      .filter((row) => row.name.trim())
      .map((row, i) => ({ id: row.id ?? -(i + 1), name: row.name.trim(), price: Number(row.price ?? 0) })),
  };
}
