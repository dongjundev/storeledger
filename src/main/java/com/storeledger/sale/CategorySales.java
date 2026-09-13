package com.storeledger.sale;

/** 조회 기간 동안 한 카테고리에서 난 매출·이익·수량. categoryId 가 null 이면 미분류 상품들의 합이다. */
public record CategorySales(Long categoryId, String categoryName, long revenue, long profit, long quantity) {
}
