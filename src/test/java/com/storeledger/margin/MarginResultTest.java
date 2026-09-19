package com.storeledger.margin;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarginResultTest {

    // 주문관리 3.63% + 판매 2.00% = 합계 5.63%
    private static final BigDecimal ORDER = new BigDecimal("3.63");
    private static final BigDecimal SALES = new BigDecimal("2.00");

    @Test
    void 수수료_마진_마진율을_계산한다() {
        // 판매가 19,900 / 원가 8,000 / 판매자 배송비 3,000 / 구매자 배송비 0 / 기타 500
        MarginResult m = MarginResult.of(19_900, 8_000, 3_000, 0, 500, ORDER, SALES);

        assertEquals(1_120, m.fee());                          // 19,900 × 5.63% = 1,120.37 → 1,120
        assertEquals(7_280, m.margin());                       // 19,900 − 8,000 − 3,000 − 500 − 1,120
        assertEquals(new BigDecimal("36.6"), m.marginRate());  // 7,280 ÷ 19,900 = 36.58% → 36.6
    }

    @Test
    void 구매자_배송비는_수입이고_주문관리_수수료만_붙는다() {
        // 위와 같은 상품을 구매자 배송비 3,000원으로 판매
        MarginResult m = MarginResult.of(19_900, 8_000, 3_000, 3_000, 500, ORDER, SALES);

        assertEquals(1_229, m.fee());                          // 1,120 + 3,000 × 3.63% (108.9 → 109)
        assertEquals(10_171, m.margin());                      // 19,900 + 3,000 − 8,000 − 3,000 − 500 − 1,229
        assertEquals(new BigDecimal("51.1"), m.marginRate());  // 10,171 ÷ 19,900 = 51.11% → 51.1
    }

    @Test
    void 수수료는_원_단위로_반올림한다() {
        assertEquals(563, MarginResult.of(10_000, 0, 0, 0, 0, ORDER, SALES).fee()); // 563.0
        assertEquals(56, MarginResult.of(1_000, 0, 0, 0, 0, ORDER, SALES).fee());   // 56.3 → 56
        assertEquals(3, MarginResult.of(50, 0, 0, 0, 0, new BigDecimal("5"), BigDecimal.ZERO).fee()); // 2.5 → 3
    }

    @Test
    void 원가가_판매가보다_크면_마진이_음수다() {
        MarginResult m = MarginResult.of(5_000, 6_000, 0, 0, 0, ORDER, SALES);

        assertEquals(282, m.fee());
        assertEquals(-1_282, m.margin());
        assertEquals(new BigDecimal("-25.6"), m.marginRate());
    }

    @Test
    void 판매가가_0이면_마진율은_0이다() {
        MarginResult m = MarginResult.of(0, 1_000, 0, 0, 0, ORDER, SALES);

        assertEquals(0, m.fee());
        assertEquals(-1_000, m.margin());
        assertEquals(new BigDecimal("0.0"), m.marginRate());
    }
}
