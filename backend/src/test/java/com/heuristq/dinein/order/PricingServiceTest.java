package com.heuristq.dinein.order;

import com.heuristq.dinein.order.PricingService.Bill;
import com.heuristq.dinein.order.PricingService.LineInput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PricingServiceTest {

    private final PricingService pricing = new PricingService();

    private static LineInput line(String unit, int qty, String gst) {
        return new LineInput(new BigDecimal(unit), qty, new BigDecimal(gst));
    }

    @Test
    void exclusiveGstAddsTaxOnTopPerLine() {
        Bill bill = pricing.calculate(List.of(line("240.00", 2, "5.00"), line("70.00", 1, "18.00")), false);

        assertThat(bill.lines().get(0).lineTotal()).isEqualByComparingTo("480.00");
        assertThat(bill.lines().get(0).taxAmount()).isEqualByComparingTo("24.00");
        assertThat(bill.lines().get(1).taxAmount()).isEqualByComparingTo("12.60");
        assertThat(bill.subtotal()).isEqualByComparingTo("550.00");
        assertThat(bill.taxTotal()).isEqualByComparingTo("36.60");
        assertThat(bill.grandTotal()).isEqualByComparingTo("586.60");
        assertThat(bill.cgst()).isEqualByComparingTo("18.30");
        assertThat(bill.sgst()).isEqualByComparingTo("18.30");
    }

    @Test
    void roundsHalfUpAtLineLevelAndSumsRoundedLines() {
        // 33.33 * 5% = 1.6665 -> 1.67 per line; three lines -> 5.01 (not round(4.9995) = 5.00)
        Bill bill = pricing.calculate(List.of(line("33.33", 1, "5"), line("33.33", 1, "5"), line("33.33", 1, "5")), false);

        assertThat(bill.lines()).allSatisfy(l -> assertThat(l.taxAmount()).isEqualByComparingTo("1.67"));
        assertThat(bill.taxTotal()).isEqualByComparingTo("5.01");
        assertThat(bill.subtotal()).isEqualByComparingTo("99.99");
        assertThat(bill.grandTotal()).isEqualByComparingTo("105.00");
    }

    @Test
    void oddPaisaOfGstGoesToSgst() {
        Bill bill = pricing.calculate(List.of(line("21.00", 1, "5.00")), false); // tax 1.05

        assertThat(bill.cgst()).isEqualByComparingTo("0.53");
        assertThat(bill.sgst()).isEqualByComparingTo("0.52");
        assertThat(bill.cgst().add(bill.sgst())).isEqualByComparingTo(bill.taxTotal());
    }

    @Test
    void inclusiveGstExtractsTaxFromPrice() {
        Bill bill = pricing.calculate(List.of(line("105.00", 1, "5.00"), line("118.00", 2, "18.00")), true);

        assertThat(bill.lines().get(0).taxAmount()).isEqualByComparingTo("5.00");
        assertThat(bill.lines().get(1).taxAmount()).isEqualByComparingTo("36.00");
        assertThat(bill.grandTotal()).isEqualByComparingTo("341.00");
        assertThat(bill.taxTotal()).isEqualByComparingTo("41.00");
        assertThat(bill.subtotal()).isEqualByComparingTo("300.00");
    }

    @Test
    void zeroGstItemsCarryNoTax() {
        Bill bill = pricing.calculate(List.of(line("40.00", 3, "0.00")), false);

        assertThat(bill.taxTotal()).isEqualByComparingTo("0.00");
        assertThat(bill.grandTotal()).isEqualByComparingTo("120.00");
    }
}
