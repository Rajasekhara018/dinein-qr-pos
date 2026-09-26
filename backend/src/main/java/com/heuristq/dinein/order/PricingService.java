package com.heuristq.dinein.order;

import com.heuristq.dinein.shared.util.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-side bill calculation. Tax is computed and rounded (HALF_UP, 2 dp) per line; totals are sums of rounded
 * lines. GST is split equally into CGST and SGST for display (any odd paisa goes to SGST).
 *
 * <ul>
 *   <li>Prices exclusive of GST: {@code tax = lineTotal * gst%}, {@code grand = subtotal + tax}.</li>
 *   <li>Prices inclusive of GST: {@code tax = lineTotal * gst / (100 + gst)}, {@code subtotal = grand - tax}.</li>
 * </ul>
 */
@Component
public class PricingService {

    public record LineInput(BigDecimal unitPrice, int quantity, BigDecimal gstPercent) {
    }

    public record LineResult(BigDecimal lineTotal, BigDecimal taxAmount) {
    }

    public record Bill(List<LineResult> lines, BigDecimal subtotal, BigDecimal taxTotal, BigDecimal cgst,
                       BigDecimal sgst, BigDecimal grandTotal) {
    }

    public Bill calculate(List<LineInput> inputs, boolean pricesIncludeGst) {
        List<LineResult> lines = new ArrayList<>(inputs.size());
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        for (LineInput in : inputs) {
            BigDecimal lineTotal = Money.round(in.unitPrice().multiply(BigDecimal.valueOf(in.quantity())));
            BigDecimal tax = pricesIncludeGst
                    ? lineTotal.multiply(in.gstPercent()).divide(Money.HUNDRED.add(in.gstPercent()), 2, RoundingMode.HALF_UP)
                    : lineTotal.multiply(in.gstPercent()).divide(Money.HUNDRED, 2, RoundingMode.HALF_UP);
            lines.add(new LineResult(lineTotal, tax));
            gross = gross.add(lineTotal);
            taxTotal = taxTotal.add(tax);
        }
        BigDecimal subtotal = pricesIncludeGst ? gross.subtract(taxTotal) : gross;
        BigDecimal grandTotal = pricesIncludeGst ? gross : gross.add(taxTotal);
        BigDecimal[] split = splitGst(taxTotal);
        return new Bill(lines, Money.round(subtotal), Money.round(taxTotal), split[0], split[1], Money.round(grandTotal));
    }

    /** Returns {CGST, SGST}; CGST is half rounded HALF_UP, SGST takes the remainder so they always sum exactly. */
    public static BigDecimal[] splitGst(BigDecimal taxTotal) {
        BigDecimal cgst = taxTotal.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        return new BigDecimal[]{cgst, Money.round(taxTotal.subtract(cgst))};
    }
}
