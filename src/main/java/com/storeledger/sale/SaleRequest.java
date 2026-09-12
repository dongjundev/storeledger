package com.storeledger.sale;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** 판매 기록 등록/수정 요청. unitPrice 생략 시 상품의 판매가를 사용한다. */
public record SaleRequest(
        @NotNull(message = "상품을 선택하세요") Long productId,
        @NotNull(message = "판매일을 입력하세요") LocalDate saleDate,
        @NotNull(message = "수량을 입력하세요") @Min(value = 1, message = "수량은 1 이상이어야 합니다") Integer quantity,
        @Min(value = 0, message = "판매 단가는 0 이상이어야 합니다")
        @Max(value = 100_000_000, message = "판매 단가는 1억 원 이하여야 합니다") Integer unitPrice) {
}
