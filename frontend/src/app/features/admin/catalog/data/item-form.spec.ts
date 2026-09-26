import { describe, expect, it } from 'vitest';
import { ItemResponse } from '../../../../core/api/models';
import {
  addVariant,
  applyHasSizes,
  createItemForm,
  createVariantGroup,
  moneyValidator,
  patchItemForm,
  removeVariant,
  setDefaultVariant,
  toItemRequest,
  toPreviewItem,
} from './item-form';
import { FormControl } from '@angular/forms';

function validBase() {
  const form = createItemForm({ categoryId: 10 });
  form.patchValue({ name: 'Masala Dosa', basePrice: 120, gstPercent: 5 });
  return form;
}

const biryani: ItemResponse = {
  id: 2,
  categoryId: 20,
  categoryName: 'Biryani',
  name: 'Chicken Biryani',
  foodType: 'NON_VEG',
  gstPercent: 5,
  available: true,
  active: true,
  displayOrder: 0,
  version: 7,
  displayPrice: 180,
  hasVariants: true,
  variants: [
    { id: 21, name: 'Half', price: 180, isDefault: true },
    { id: 22, name: 'Full', price: 320, isDefault: false },
  ],
  addons: [{ id: 31, name: 'Extra raita', price: 30 }],
};

describe('item form', () => {
  describe('base price rule', () => {
    it('requires a base price > 0 when the item has no sizes', () => {
      const form = validBase();
      expect(form.valid).toBe(true);

      form.controls.basePrice.setValue(null);
      expect(form.controls.basePrice.hasError('required')).toBe(true);
      form.controls.basePrice.setValue(0);
      expect(form.controls.basePrice.hasError('money')).toBe(true);
      form.controls.basePrice.setValue(-5);
      expect(form.valid).toBe(false);
      form.controls.basePrice.setValue(12.345);
      expect(form.controls.basePrice.getError('money')).toContain('2 decimal');
      form.controls.basePrice.setValue(99.5);
      expect(form.valid).toBe(true);
    });

    it('ignores the base price when "has sizes" is on', () => {
      const form = validBase();
      form.controls.basePrice.setValue(null);
      form.controls.hasSizes.setValue(true);
      applyHasSizes(form, true);
      expect(form.controls.basePrice.disabled).toBe(true);
      const row = form.controls.variants.at(0);
      row.patchValue({ name: 'Regular', price: 100 });
      expect(form.valid).toBe(true);
    });
  });

  describe('sizes (variants FormArray)', () => {
    function withSizes() {
      const form = validBase();
      form.controls.hasSizes.setValue(true);
      applyHasSizes(form, true);
      return form;
    }

    it('starts with one default row prefilled from the base price', () => {
      const form = withSizes();
      expect(form.controls.variants.length).toBe(1);
      expect(form.controls.variants.at(0).getRawValue()).toMatchObject({ price: 120, isDefault: true });
    });

    it('requires at least one size when "has sizes" is on', () => {
      const form = withSizes();
      form.controls.variants.removeAt(0);
      expect(form.controls.variants.hasError('sizesRequired')).toBe(true);
      expect(form.valid).toBe(false);
    });

    it('requires exactly one default', () => {
      const form = withSizes();
      form.controls.variants.at(0).patchValue({ name: 'Half', price: 180 });
      form.controls.variants.push(createVariantGroup({ name: 'Full', price: 320, isDefault: true }));
      expect(form.controls.variants.hasError('multipleDefaults')).toBe(true);

      setDefaultVariant(form, 1);
      expect(form.controls.variants.errors).toBeNull();
      expect(form.controls.variants.controls.map((c) => c.controls.isDefault.value)).toEqual([false, true]);

      form.controls.variants.at(1).controls.isDefault.setValue(false);
      expect(form.controls.variants.hasError('defaultRequired')).toBe(true);
    });

    it('requires every size price > 0 and a name', () => {
      const form = withSizes();
      const row = form.controls.variants.at(0);
      row.patchValue({ name: '', price: 0 });
      expect(row.controls.name.hasError('required')).toBe(true);
      expect(row.controls.price.hasError('money')).toBe(true);
      row.patchValue({ name: 'Half', price: 0.01 });
      expect(form.valid).toBe(true);
    });

    it('rejects duplicate size names (case-insensitive)', () => {
      const form = withSizes();
      form.controls.variants.at(0).patchValue({ name: 'Half', price: 10 });
      form.controls.variants.push(createVariantGroup({ name: ' half ', price: 20 }));
      expect(form.controls.variants.hasError('duplicateNames')).toBe(true);
    });

    it('moves the default to the first row when the default row is removed', () => {
      const form = withSizes();
      form.controls.variants.at(0).patchValue({ name: 'Half', price: 10 });
      addVariant(form);
      form.controls.variants.at(1).patchValue({ name: 'Full', price: 20 });
      removeVariant(form, 0);
      expect(form.controls.variants.at(0).getRawValue()).toMatchObject({ name: 'Full', isDefault: true });
      expect(form.valid).toBe(true);
    });

    it('turning sizes off disables the rows so they cannot block saving', () => {
      const form = withSizes();
      form.controls.variants.at(0).patchValue({ name: '', price: null });
      expect(form.valid).toBe(false);
      form.controls.hasSizes.setValue(false);
      applyHasSizes(form, false);
      expect(form.valid).toBe(true);
    });
  });

  describe('mapping', () => {
    it('round-trips an item with sizes and add-ons into the request (with version)', () => {
      const form = createItemForm();
      patchItemForm(form, biryani);
      expect(form.controls.hasSizes.value).toBe(true);
      expect(form.controls.basePrice.disabled).toBe(true);
      expect(form.valid).toBe(true);
      expect(toItemRequest(form, biryani.version)).toEqual({
        categoryId: 20,
        name: 'Chicken Biryani',
        description: null,
        imageId: null,
        foodType: 'NON_VEG',
        gstPercent: 5,
        available: true,
        basePrice: null,
        variants: [
          { id: 21, name: 'Half', price: 180, isDefault: true },
          { id: 22, name: 'Full', price: 320, isDefault: false },
        ],
        addons: [{ id: 31, name: 'Extra raita', price: 30 }],
        version: 7,
      });
    });

    it('sends no variants and a base price for single-price items', () => {
      const form = validBase();
      const request = toItemRequest(form, null);
      expect(request.basePrice).toBe(120);
      expect(request.variants).toEqual([]);
      expect(request.version).toBeNull();
    });

    it('builds the guest preview from the current values', () => {
      const form = createItemForm();
      patchItemForm(form, biryani);
      form.controls.variants.at(1).patchValue({ price: 350 });
      const preview = toPreviewItem(form, { thumbUrl: '/api/images/5/thumb' });
      expect(preview.displayPrice).toBe(180);
      expect(preview.variants.map((v) => v.price)).toEqual([180, 350]);
      expect(preview.thumbUrl).toBe('/api/images/5/thumb');
    });
  });

  it('moneyValidator allows 0 for add-ons but not for prices', () => {
    expect(new FormControl(0, moneyValidator(0)).valid).toBe(true);
    expect(new FormControl(0, moneyValidator('positive')).valid).toBe(false);
    expect(new FormControl(100000000, moneyValidator('positive')).valid).toBe(false);
  });
});
