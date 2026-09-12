package com.storeledger.product;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductTest {

    @Test
    void 합계_수수료율이_3_63보다_작은_옛_상품도_판매_수수료율이_음수가_되지_않는다() {
        // 수수료율이 하나였던 시절 2.50%로 저장된 상품: DB 이전 때 주문관리 칸은 기본값 3.63으로 채워진다
        Product legacy = new Product("옛 상품", null, 10_000, 5_000, 0, 0, 0, BigDecimal.ONE, BigDecimal.ONE);
        ReflectionTestUtils.setField(legacy, "feeRate", new BigDecimal("2.50"));
        ReflectionTestUtils.setField(legacy, "orderFeeRate", new BigDecimal("3.63"));

        assertEquals(new BigDecimal("2.50"), legacy.getOrderFeeRate());
        assertEquals(new BigDecimal("0.00"), legacy.getSalesFeeRate());
        assertEquals(250, legacy.margin().fee()); // 합계 2.50%는 그대로
    }
}
