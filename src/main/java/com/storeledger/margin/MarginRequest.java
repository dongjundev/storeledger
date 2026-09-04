package com.storeledger.margin;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** 저장 없이 마진만 계산해 보는 요청. 배송비·기타비용은 생략 시 0, 수수료율은 생략 시 기본값. */
public record MarginRequest(
        @NotNull(message = "판매가를 입력하세요") @Min(value = 0, message = "판매가는 0 이상이어야 합니다") Integer sellingPrice,
        @NotNull(message = "원가를 입력하세요") @Min(value = 0, message = "원가는 0 이상이어야 합니다") Integer costPrice,
        @Min(value = 0, message = "배송비는 0 이상이어야 합니다") Integer shippingCost,
        @Min(value = 0, message = "기타 비용은 0 이상이어야 합니다") Integer otherCost,
        @DecimalMin(value = "0", message = "수수료율은 0 이상이어야 합니다")
        @DecimalMax(value = "100", message = "수수료율은 100 이하여야 합니다") BigDecimal feeRate) {

    public MarginRequest {
        if (shippingCost == null) shippingCost = 0;
        if (otherCost == null) otherCost = 0;
        if (feeRate == null) feeRate = MarginResult.DEFAULT_FEE_RATE;
    }

    public MarginResult calculate() {
        return MarginResult.of(sellingPrice, costPrice, shippingCost, otherCost, feeRate);
    }
}
