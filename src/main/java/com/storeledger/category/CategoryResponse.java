package com.storeledger.category;

/** 카테고리와 그 카테고리에 속한 상품 수. */
public record CategoryResponse(Long id, String name, long productCount) {
}
