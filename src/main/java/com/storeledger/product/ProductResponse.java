package com.storeledger.product;

import com.storeledger.margin.MarginResult;

import java.math.BigDecimal;

/** 상품 정보 + 정가 기준 수수료·마진·마진율. */
public record ProductResponse(
        Long id,
        String name,
        int sellingPrice,
        int costPrice,
        int shippingCost,
        int otherCost,
        BigDecimal feeRate,
        int fee,
        int margin,
        BigDecimal marginRate) {

    public static ProductResponse from(Product product) {
        MarginResult m = product.margin();
        return new ProductResponse(
                product.getId(), product.getName(),
                product.getSellingPrice(), product.getCostPrice(),
                product.getShippingCost(), product.getOtherCost(), product.getFeeRate(),
                m.fee(), m.margin(), m.marginRate());
    }
}
