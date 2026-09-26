import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { STORAGE_KEYS } from '../../../core/util/storage';
import { CartStore } from './cart.store';
import { biryani, dosa, menu, menuItem } from './test-fixtures';

describe('CartStore', () => {
  let cart: CartStore;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    cart = TestBed.inject(CartStore);
    cart.bindTable(7);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  describe('line merging', () => {
    it('merges identical selections into one line', () => {
      cart.add(dosa, { variantId: null, addonIds: [], notes: '', quantity: 1 });
      cart.add(dosa, { variantId: null, addonIds: [], notes: '', quantity: 2 });
      expect(cart.lines().length).toBe(1);
      expect(cart.lines()[0].quantity).toBe(3);
      expect(cart.itemCount()).toBe(3);
    });

    it('treats addon order and note whitespace/case as the same line', () => {
      cart.add(biryani, { variantId: 21, addonIds: [32, 31], notes: 'Less  Spicy ', quantity: 1 });
      cart.add(biryani, { variantId: 21, addonIds: [31, 32], notes: 'less spicy', quantity: 1 });
      expect(cart.lines().length).toBe(1);
      expect(cart.lines()[0].addonIds).toEqual([31, 32]);
    });

    it('keeps different variants, addons or notes as separate lines', () => {
      cart.add(biryani, { variantId: 21, addonIds: [], notes: '', quantity: 1 });
      cart.add(biryani, { variantId: 22, addonIds: [], notes: '', quantity: 1 });
      cart.add(biryani, { variantId: 22, addonIds: [31], notes: '', quantity: 1 });
      cart.add(biryani, { variantId: 22, addonIds: [31], notes: 'no onion', quantity: 1 });
      expect(cart.lines().length).toBe(4);
      expect(cart.quantityByItem().get(biryani.id)).toBe(4);
    });

    it('computes the unit price from variant + addons', () => {
      cart.add(biryani, { variantId: 22, addonIds: [31, 32], notes: '', quantity: 1 });
      expect(cart.lines()[0].unitPrice).toBe(375.5);
    });

    it('increments, decrements (removing at zero) and caps quantity at 50', () => {
      const key = cart.add(dosa, { variantId: null, addonIds: [], notes: '', quantity: 1 });
      cart.increment(key);
      expect(cart.find(key)?.quantity).toBe(2);
      cart.setQuantity(key, 99);
      expect(cart.find(key)?.quantity).toBe(50);
      cart.setQuantity(key, 1);
      cart.decrement(key);
      expect(cart.isEmpty()).toBe(true);
    });

    it('edits a line in place and merges when it becomes identical to another line', () => {
      const a = cart.add(biryani, { variantId: 21, addonIds: [], notes: '', quantity: 1 });
      const b = cart.add(biryani, { variantId: 22, addonIds: [], notes: '', quantity: 2 });
      cart.replace(a, biryani, { variantId: 22, addonIds: [], notes: '', quantity: 1 });
      expect(cart.lines().length).toBe(1);
      expect(cart.find(b)?.quantity).toBe(3);
    });

    it('decrementLatest removes from the most recently added line of an item', () => {
      vi.spyOn(Date, 'now').mockReturnValueOnce(1000).mockReturnValueOnce(1000).mockReturnValue(2000);
      cart.add(biryani, { variantId: 21, addonIds: [], notes: '', quantity: 1 });
      cart.add(biryani, { variantId: 22, addonIds: [], notes: '', quantity: 1 });
      cart.decrementLatest(biryani.id);
      expect(cart.lines().map((l) => l.variantId)).toEqual([21]);
    });
  });

  describe('totals', () => {
    it('estimates an exclusive-GST bill in paise', () => {
      cart.add(dosa, { variantId: null, addonIds: [], notes: '', quantity: 2 }); // 240.00 @5%
      cart.add(biryani, { variantId: 22, addonIds: [32], notes: '', quantity: 1 }); // 345.50 @5%
      const bill = cart.bill();
      expect(bill.subtotal).toBe(58550);
      // 12.00 + 17.275→17.28
      expect(bill.taxTotal).toBe(2928);
      expect(bill.cgst).toBe(1464);
      expect(bill.sgst).toBe(1464);
      expect(bill.grandTotal).toBe(61478);
    });

    it('estimates an inclusive-GST bill', () => {
      cart.setPricesIncludeGst(true);
      cart.add(menuItem({ id: 5, basePrice: 105 }), { variantId: null, addonIds: [], notes: '', quantity: 1 });
      expect(cart.bill()).toMatchObject({ subtotal: 10000, taxTotal: 500, grandTotal: 10500 });
    });

    it('excludes lines with issues from the totals', () => {
      cart.add(dosa, { variantId: null, addonIds: [], notes: '', quantity: 1 });
      cart.reconcile(menu([menuItem({ available: false })]));
      expect(cart.hasIssues()).toBe(true);
      expect(cart.total()).toBe(0);
    });
  });

  describe('reconcile with a fresh menu', () => {
    it('flags lines whose item, variant or addon disappeared or became unavailable', () => {
      cart.add(dosa, { variantId: null, addonIds: [], notes: '', quantity: 1 });
      cart.add(biryani, { variantId: 22, addonIds: [], notes: '', quantity: 1 });
      cart.add(biryani, { variantId: 21, addonIds: [32], notes: '', quantity: 1 });
      const fresh = menu([
        { ...biryani, variants: [biryani.variants[0]], addons: [biryani.addons[0]] },
      ]);
      const result = cart.reconcile(fresh);
      const reasons = cart.lines().map((l) => l.issue?.reason ?? null);
      expect(reasons).toEqual(['ITEM_NOT_FOUND', 'VARIANT_UNAVAILABLE', 'ADDON_UNAVAILABLE']);
      expect(result.unavailable).toBe(3);
    });

    it('re-prices lines from the fresh menu and clears stale issues', () => {
      cart.add(dosa, { variantId: null, addonIds: [], notes: '', quantity: 2 });
      cart.markProblems([{ lineIndex: 0, itemId: dosa.id, reason: 'ITEM_UNAVAILABLE' }], [cart.lines()[0].key]);
      expect(cart.hasIssues()).toBe(true);
      const result = cart.reconcile(menu([{ ...dosa, basePrice: 130, name: 'Masala Dosa (new)' }], true));
      expect(result.repriced).toBe(1);
      expect(cart.hasIssues()).toBe(false);
      expect(cart.lines()[0]).toMatchObject({ unitPrice: 130, quantity: 2, name: 'Masala Dosa (new)' });
      expect(cart.pricesIncludeGst()).toBe(true);
    });
  });

  describe('server problems', () => {
    it('maps ITEM_UNAVAILABLE lineIndex back to cart lines', () => {
      cart.add(dosa, { variantId: null, addonIds: [], notes: '', quantity: 1 });
      cart.add(biryani, { variantId: 21, addonIds: [], notes: '', quantity: 1 });
      const { keys, request } = cart.toOrderRequest();
      expect(request.items.map((i) => i.itemId)).toEqual([1, 2]);
      cart.markProblems([{ lineIndex: 1, itemId: 2, reason: 'VARIANT_UNAVAILABLE' }], keys);
      expect(cart.lines()[0].issue).toBeNull();
      expect(cart.lines()[1].issue?.reason).toBe('VARIANT_UNAVAILABLE');
      cart.removeUnavailable();
      expect(cart.lines().length).toBe(1);
    });
  });

  describe('order request', () => {
    it('sends only ids, quantities and notes', () => {
      cart.add(biryani, { variantId: 22, addonIds: [31], notes: 'no onion', quantity: 2 });
      cart.setNotes('  window seat ');
      cart.setCustomer('Asha', '98765 43210');
      expect(cart.toOrderRequest().request).toEqual({
        items: [{ itemId: 2, variantId: 22, addonIds: [31], quantity: 2, notes: 'no onion' }],
        notes: 'window seat',
        customerName: 'Asha',
        customerPhone: '9876543210',
      });
    });
  });

  describe('persistence', () => {
    it('persists per table and restores on bind', () => {
      cart.add(dosa, { variantId: null, addonIds: [], notes: '', quantity: 2 });
      expect(localStorage.getItem(STORAGE_KEYS.cart(7))).toContain('Masala Dosa');

      cart.bindTable(8);
      expect(cart.isEmpty()).toBe(true);
      cart.bindTable(7);
      expect(cart.itemCount()).toBe(2);
    });

    it('clear() empties the cart after payment but keeps the guest details', () => {
      cart.setCustomer('Asha', '9876543210');
      cart.add(dosa, { variantId: null, addonIds: [], notes: '', quantity: 1 });
      cart.markCheckout(42);
      cart.clear();
      expect(cart.isEmpty()).toBe(true);
      expect(cart.pendingOrderId()).toBeNull();
      expect(cart.customerName()).toBe('Asha');
    });

    it('keeps working when localStorage throws', () => {
      vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
        throw new DOMException('QuotaExceededError');
      });
      vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
        throw new DOMException('SecurityError');
      });
      expect(() => cart.bindTable(9)).not.toThrow();
      expect(() => cart.add(dosa, { variantId: null, addonIds: [], notes: '', quantity: 1 })).not.toThrow();
      expect(cart.itemCount()).toBe(1);
    });

    it('ignores corrupt stored data', () => {
      localStorage.setItem(STORAGE_KEYS.cart(11), '{not json');
      cart.bindTable(11);
      expect(cart.isEmpty()).toBe(true);
    });
  });
});
