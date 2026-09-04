package com.storeledger.margin;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 개당 수수료 · 마진 · 마진율 계산 결과.
 *
 * <pre>
 * 수수료   = 판매가 × 수수료율 (원 단위 반올림)
 * 마진     = 판매가 − 원가 − 배송비 − 기타비용 − 수수료
 * 마진율   = 마진 ÷ 판매가 × 100 (소수 첫째 자리)
 * </pre>
 */
public record MarginResult(int fee, int margin, BigDecimal marginRate) {

    /** 네이버 주문관리 수수료(신용카드 3.63%) + 네이버쇼핑 매출연동 수수료(2%) 기준 기본값. */
    public static final BigDecimal DEFAULT_FEE_RATE = new BigDecimal("5.63");

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static MarginResult of(int sellingPrice, int costPrice, int shippingCost, int otherCost, BigDecimal feeRate) {
        int fee = BigDecimal.valueOf(sellingPrice).multiply(feeRate)
                .divide(HUNDRED, 0, RoundingMode.HALF_UP)
                .intValueExact();
        int margin = sellingPrice - costPrice - shippingCost - otherCost - fee;
        BigDecimal marginRate = sellingPrice == 0
                ? new BigDecimal("0.0")
                : BigDecimal.valueOf(margin).multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(sellingPrice), 1, RoundingMode.HALF_UP);
        return new MarginResult(fee, margin, marginRate);
    }
}
