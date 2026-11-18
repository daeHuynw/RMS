package com.rms.modules.discount;

import static org.assertj.core.api.Assertions.assertThat;

import com.rms.modules.discount.entity.Discount;
import com.rms.modules.discount.entity.Discount.Type;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * REVIEW C2 — số tiền giảm và điều kiện áp mã. Tính ở entity, nên test thẳng không cần Spring.
 */
class DiscountAmountTest {

    private static final Instant START = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-11-30T23:59:59Z");

    @Test
    void percentageIsAShareOfTheSubtotal() {
        assertThat(percent("10", null).amount(money("155000"))).isEqualTo(money("15500.00"));
    }

    @Test
    void percentageRoundsHalfUpToTheCent() {
        // 33 333 × 15% = 4 999,95 — giữ đủ hai chữ số, không cắt
        assertThat(percent("15", null).amount(money("33333"))).isEqualTo(money("4999.95"));
        // 10 001 × 12,5% = 1 250,125 → 1 250,13
        assertThat(percent("12.5", null).amount(money("10001"))).isEqualTo(money("1250.13"));
    }

    @Test
    void percentageIsCappedByTheMaximumDiscount() {
        assertThat(percent("50", "30000").amount(money("100000"))).isEqualTo(money("30000.00"));
        // dưới mức trần thì giữ nguyên phần trăm
        assertThat(percent("50", "30000").amount(money("40000"))).isEqualTo(money("20000.00"));
    }

    @Test
    void fixedAmountIsTakenAsIs() {
        assertThat(fixed("20000").amount(money("155000"))).isEqualTo(money("20000.00"));
    }

    @Test
    void theDiscountNeverExceedsTheSubtotal() {
        assertThat(fixed("50000").amount(money("35000"))).isEqualTo(money("35000.00"));
        assertThat(percent("150", null).amount(money("35000"))).isEqualTo(money("35000.00"));
    }

    @Test
    void minimumOrderAmountIsInclusive() {
        Discount discount = new Discount(
                "MIN100", null, Type.FIXED_AMOUNT, money("10000"), money("100000"), null, null, START, END);
        Instant now = START.plusSeconds(60);

        assertThat(discount.canApply(now, money("100000"))).isTrue();
        assertThat(discount.canApply(now, money("99999.99"))).isFalse();
    }

    @Test
    void theValidityWindowIncludesBothEnds() {
        Discount discount = fixed("10000");

        assertThat(discount.canApply(START, money("1"))).isTrue();
        assertThat(discount.canApply(END, money("1"))).isTrue();
        assertThat(discount.canApply(START.minusMillis(1), money("1"))).isFalse();
        assertThat(discount.canApply(END.plusMillis(1), money("1"))).isFalse();
    }

    @Test
    void anExhaustedOrInactiveCodeCannotBeApplied() {
        Discount limited = new Discount(
                "TWICE", null, Type.FIXED_AMOUNT, money("10000"), null, null, 2, START, END);
        Instant now = START.plusSeconds(60);
        limited.consume();
        assertThat(limited.canApply(now, money("50000"))).isTrue();
        limited.consume();
        assertThat(limited.canApply(now, money("50000"))).isFalse();

        Discount inactive = fixed("10000");
        inactive.deactivate();
        assertThat(inactive.canApply(now, money("50000"))).isFalse();
    }

    private static Discount percent(String value, String max) {
        return discount(Type.PERCENTAGE, value, max);
    }

    private static Discount fixed(String value) {
        return discount(Type.FIXED_AMOUNT, value, null);
    }

    private static Discount discount(Type type, String value, String max) {
        Discount discount = new Discount(
                "CODE", null, type, money(value), null, max == null ? null : money(max), null, START, END);
        ReflectionTestUtils.setField(discount, "id", 1L);
        return discount;
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
