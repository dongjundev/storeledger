package com.storeledger.product;

import com.storeledger.margin.MarginResult;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 상품 등록/수정 요청. categoryId 생략 시 미분류, 택배비·구매자 배송비·기타비용은 생략 시 0, 수수료율은 생략 시 기본값. */
public record ProductRequest(
        @NotBlank(message = "상품명을 입력하세요") @Size(max = 100, message = "상품명은 100자 이하여야 합니다") String name,
        Long categoryId,
        @NotNull(message = "판매가를 입력하세요") @Min(value = 0, message = "판매가는 0 이상이어야 합니다")
        @Max(value = 100_000_000, message = "판매가는 1억 원 이하여야 합니다") Integer sellingPrice,
        @NotNull(message = "원가를 입력하세요") @Min(value = 0, message = "원가는 0 이상이어야 합니다")
        @Max(value = 100_000_000, message = "원가는 1억 원 이하여야 합니다") Integer costPrice,
        @Min(value = 0, message = "택배비는 0 이상이어야 합니다")
        @Max(value = 100_000_000, message = "택배비는 1억 원 이하여야 합니다") Integer shippingCost,
        @Min(value = 0, message = "구매자 배송비는 0 이상이어야 합니다")
        @Max(value = 100_000_000, message = "구매자 배송비는 1억 원 이하여야 합니다") Integer buyerShippingFee,
        @Min(value = 0, message = "기타 비용은 0 이상이어야 합니다")
        @Max(value = 100_000_000, message = "기타 비용은 1억 원 이하여야 합니다") Integer otherCost,
        @DecimalMin(value = "0", message = "주문관리 수수료율은 0 이상이어야 합니다")
        @DecimalMax(value = "100", message = "주문관리 수수료율은 100 이하여야 합니다")
        @Digits(integer = 3, fraction = 2, message = "주문관리 수수료율은 소수 둘째 자리까지 입력하세요") BigDecimal orderFeeRate,
        @DecimalMin(value = "0", message = "판매 수수료율은 0 이상이어야 합니다")
        @DecimalMax(value = "100", message = "판매 수수료율은 100 이하여야 합니다")
        @Digits(integer = 3, fraction = 2, message = "판매 수수료율은 소수 둘째 자리까지 입력하세요") BigDecimal salesFeeRate) {

    public ProductRequest {
        if (shippingCost == null) shippingCost = 0;
        if (buyerShippingFee == null) buyerShippingFee = 0;
        if (otherCost == null) otherCost = 0;
        if (orderFeeRate == null) orderFeeRate = MarginResult.DEFAULT_ORDER_FEE_RATE;
        if (salesFeeRate == null) salesFeeRate = MarginResult.DEFAULT_SALES_FEE_RATE;
    }

    @AssertTrue(message = "주문관리와 판매 수수료율 합계는 100% 이하여야 합니다")
    public boolean isFeeRateTotalValid() {
        return orderFeeRate.add(salesFeeRate).compareTo(BigDecimal.valueOf(100)) <= 0;
    }
}
