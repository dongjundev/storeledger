package com.storeledger.margin;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 개당 수수료 · 마진 · 마진율 계산 결과.
 *
 * <pre>
 * 수수료   = 판매가 × (주문관리 수수료율 + 판매 수수료율) + 구매자 배송비 × 주문관리 수수료율 (각각 원 단위 반올림)
 * 마진     = 판매가 + 구매자 배송비 − 원가 − 택배비 − 기타비용 − 수수료
 * 마진율   = 마진 ÷ 판매가 × 100 (소수 첫째 자리)
 * </pre>
 * 네이버는 구매자가 낸 배송비에 주문관리 수수료만 매기고 판매 수수료는 매기지 않는다.
 */
public record MarginResult(int fee, int margin, BigDecimal marginRate) {

    /** 네이버페이 주문관리 수수료 기본값: 영세 등급 1.77% + VAT = 1.947% (2025년 10월 인하 후). 이 스토어의 등급. */
    public static final BigDecimal DEFAULT_ORDER_FEE_RATE = new BigDecimal("1.95");

    /** 스마트스토어 판매 수수료 기본값: 2.73% + VAT. 2025년 6월 네이버쇼핑 매출연동 수수료(2%)를 대체했다. */
    public static final BigDecimal DEFAULT_SALES_FEE_RATE = new BigDecimal("3.00");

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static MarginResult of(int sellingPrice, int costPrice, int shippingCost, int buyerShippingFee, int otherCost,
                                  BigDecimal orderFeeRate, BigDecimal salesFeeRate) {
        int fee = percentOf(sellingPrice, orderFeeRate.add(salesFeeRate)) + percentOf(buyerShippingFee, orderFeeRate);
        int margin = sellingPrice + buyerShippingFee - costPrice - shippingCost - otherCost - fee;
        BigDecimal marginRate = sellingPrice == 0
                ? new BigDecimal("0.0")
                : BigDecimal.valueOf(margin).multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(sellingPrice), 1, RoundingMode.HALF_UP);
        return new MarginResult(fee, margin, marginRate);
    }

    private static int percentOf(int amount, BigDecimal rate) {
        return BigDecimal.valueOf(amount).multiply(rate)
                .divide(HUNDRED, 0, RoundingMode.HALF_UP)
                .intValueExact();
    }
}
