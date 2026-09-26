import { estimateBill, lineTaxPaise, splitGst } from './cart-pricing';

describe('cart pricing (mirrors backend PricingService)', () => {
  describe('prices exclusive of GST', () => {
    it('adds GST on top of the subtotal', () => {
      const bill = estimateBill([{ unitPrice: 100, quantity: 2, gstPercent: 5 }], false);
      expect(bill).toMatchObject({ subtotal: 20000, taxTotal: 1000, cgst: 500, sgst: 500, grandTotal: 21000 });
    });

    it('rounds the tax HALF_UP to the paisa per line', () => {
      // 99.99 × 5% = 4.9995 → 5.00
      expect(lineTaxPaise(9999, 5, false)).toBe(500);
      // 10.10 × 5% = 0.505 → 0.51 (half rounds up)
      expect(lineTaxPaise(1010, 5, false)).toBe(51);
      // 10.30 × 5% = 0.515 → 0.52
      expect(lineTaxPaise(1030, 5, false)).toBe(52);
      // 10.20 × 5% = 0.51 exactly
      expect(lineTaxPaise(1020, 5, false)).toBe(51);
    });

    it('sums rounded line taxes (not the rounded total)', () => {
      // Each 0.10 × 5% = 0.005 → 0.01; two lines = 0.02 (whole-order rounding would give 0.01).
      const bill = estimateBill(
        [
          { unitPrice: 0.1, quantity: 1, gstPercent: 5 },
          { unitPrice: 0.1, quantity: 1, gstPercent: 5 },
        ],
        false,
      );
      expect(bill.taxTotal).toBe(2);
      expect(bill.grandTotal).toBe(22);
    });

    it('supports fractional GST rates and zero GST', () => {
      expect(lineTaxPaise(10000, 12.5, false)).toBe(1250);
      expect(lineTaxPaise(10000, 0, false)).toBe(0);
    });

    it('computes line totals exactly in paise', () => {
      // 0.1 + 0.2 style float traps: 3 × 33.33 = 99.99
      const bill = estimateBill([{ unitPrice: 33.33, quantity: 3, gstPercent: 18 }], false);
      expect(bill.lines[0].lineTotal).toBe(9999);
      expect(bill.lines[0].tax).toBe(1800); // 17.9982 → 18.00
    });
  });

  describe('prices inclusive of GST', () => {
    it('extracts GST from the price', () => {
      const bill = estimateBill([{ unitPrice: 105, quantity: 1, gstPercent: 5 }], true);
      expect(bill).toMatchObject({ subtotal: 10000, taxTotal: 500, grandTotal: 10500 });
    });

    it('rounds the extracted tax HALF_UP per line', () => {
      // 100 × 18/118 = 15.2542… → 15.25
      const bill = estimateBill([{ unitPrice: 100, quantity: 1, gstPercent: 18 }], true);
      expect(bill).toMatchObject({ taxTotal: 1525, subtotal: 8475, grandTotal: 10000 });
    });

    it('keeps the grand total equal to the menu prices', () => {
      const bill = estimateBill(
        [
          { unitPrice: 149, quantity: 3, gstPercent: 5 },
          { unitPrice: 59.5, quantity: 1, gstPercent: 18 },
        ],
        true,
      );
      expect(bill.grandTotal).toBe(149 * 3 * 100 + 5950);
      expect(bill.subtotal + bill.taxTotal).toBe(bill.grandTotal);
    });
  });

  describe('CGST / SGST split', () => {
    it('gives CGST the half rounded HALF_UP and SGST the remainder', () => {
      expect(splitGst(1000)).toEqual([500, 500]);
      expect(splitGst(51)).toEqual([26, 25]);
      expect(splitGst(1)).toEqual([1, 0]);
      expect(splitGst(0)).toEqual([0, 0]);
    });

    it('always adds up to the tax total', () => {
      for (const tax of [1, 7, 99, 101, 12345]) {
        const [c, s] = splitGst(tax);
        expect(c + s).toBe(tax);
      }
    });
  });

  it('returns zeros for an empty cart', () => {
    expect(estimateBill([], false)).toMatchObject({ subtotal: 0, taxTotal: 0, cgst: 0, sgst: 0, grandTotal: 0 });
  });
});
