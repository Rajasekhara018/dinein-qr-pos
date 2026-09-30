import { describe, expect, it } from 'vitest';
import { KioskUpsellView } from '../../../core/api/models';
import {
  describeUpsell,
  nameLookup,
  toUpsellRequest,
  triggerKind,
  triggerProblem,
  UpsellFormValue,
} from './upsell-logic';

const names = nameLookup(
  [
    { id: 1, name: 'Classic Burger' },
    { id: 2, name: 'Cold Coffee' },
    { id: 3, name: 'Choco Lava Cake' },
  ],
  [{ id: 10, name: 'Burgers' }],
);

const rule = (over: Partial<KioskUpsellView>): KioskUpsellView => ({
  id: 1,
  triggerItemId: null,
  triggerCategoryId: null,
  suggestedItemId: 2,
  placement: 'ITEM_ADDED',
  message: null,
  sortOrder: 0,
  active: true,
  ...over,
});

describe('describeUpsell', () => {
  it('describes an item trigger with a message', () => {
    expect(describeUpsell(rule({ triggerItemId: 1, message: 'Add a drink?' }), names)).toBe(
      "When Classic Burger is added, suggest Cold Coffee: 'Add a drink?'",
    );
  });

  it('describes category and any-order triggers', () => {
    expect(describeUpsell(rule({ triggerCategoryId: 10 }), names)).toBe(
      'When an item from Burgers is added, suggest Cold Coffee',
    );
    expect(describeUpsell(rule({}), names)).toBe('When any item is added, suggest Cold Coffee');
  });

  it('describes checkout rules', () => {
    expect(describeUpsell(rule({ placement: 'CHECKOUT', suggestedItemId: 3 }), names)).toBe(
      'At checkout, suggest Choco Lava Cake',
    );
    expect(
      describeUpsell(
        rule({ placement: 'CHECKOUT', triggerItemId: 1, message: '  Sweet?  ' }),
        names,
      ),
    ).toBe("At checkout, if the order has Classic Burger, suggest Cold Coffee: 'Sweet?'");
    expect(describeUpsell(rule({ placement: 'CHECKOUT', triggerCategoryId: 10 }), names)).toBe(
      'At checkout, if the order has an item from Burgers, suggest Cold Coffee',
    );
  });

  it('falls back to ids for deleted items and categories', () => {
    expect(describeUpsell(rule({ triggerItemId: 99, suggestedItemId: 98 }), names)).toBe(
      'When item #99 is added, suggest item #98',
    );
    expect(describeUpsell(rule({ triggerCategoryId: 77 }), names)).toContain('category #77');
  });
});

describe('trigger handling', () => {
  it('derives the trigger kind', () => {
    expect(triggerKind({ triggerItemId: 1, triggerCategoryId: null })).toBe('ITEM');
    expect(triggerKind({ triggerItemId: null, triggerCategoryId: 2 })).toBe('CATEGORY');
    expect(triggerKind({ triggerItemId: null, triggerCategoryId: null })).toBe('ANY');
  });

  const base: UpsellFormValue = {
    triggerKind: 'ANY',
    triggerCategoryId: 10,
    triggerItemId: 1,
    suggestedItemId: 2,
    placement: 'CHECKOUT',
    message: '  ',
    active: true,
  };

  it('sends only the id that matches the chosen kind', () => {
    const any = toUpsellRequest({ ...base, suggestedItemId: 2 });
    expect(any).toMatchObject({ triggerItemId: null, triggerCategoryId: null, message: null });
    expect(toUpsellRequest({ ...base, triggerKind: 'ITEM', suggestedItemId: 2 })).toMatchObject({
      triggerItemId: 1,
      triggerCategoryId: null,
    });
    expect(toUpsellRequest({ ...base, triggerKind: 'CATEGORY', suggestedItemId: 2 })).toMatchObject(
      {
        triggerItemId: null,
        triggerCategoryId: 10,
      },
    );
  });

  it('keeps sort order on edit and trims the message', () => {
    const req = toUpsellRequest({ ...base, suggestedItemId: 2, message: ' Hi ' }, 4);
    expect(req.sortOrder).toBe(4);
    expect(req.message).toBe('Hi');
  });

  it('requires the picker that goes with the trigger kind', () => {
    expect(triggerProblem({ ...base, triggerKind: 'ITEM', triggerItemId: null })).toBe(
      'Choose an item.',
    );
    expect(triggerProblem({ ...base, triggerKind: 'CATEGORY', triggerCategoryId: null })).toBe(
      'Choose a category.',
    );
    expect(triggerProblem({ ...base, triggerKind: 'ANY', triggerItemId: null })).toBeNull();
  });
});
