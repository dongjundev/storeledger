package com.storeledger.product;

import com.storeledger.category.Category;
import com.storeledger.margin.MarginResult;

import java.math.BigDecimal;

/** 상품 정보 + 정가 기준 수수료·마진·마진율. */
public record ProductResponse(
        Long id,
        String name,
        Long categoryId,
        String categoryName,
        int sellingPrice,
        int costPrice,
        int shippingCost,
        int buyerShippingFee,
        int otherCost,
        BigDecimal orderFeeRate,
        BigDecimal salesFeeRate,
        int fee,
        int margin,
        BigDecimal marginRate) {

    public static ProductResponse from(Product product) {
        MarginResult m = product.margin();
        Category category = product.getCategory();
        return new ProductResponse(
                product.getId(), product.getName(),
                category == null ? null : category.getId(), category == null ? null : category.getName(),
                product.getSellingPrice(), product.getCostPrice(),
                product.getShippingCost(), product.getBuyerShippingFee(), product.getOtherCost(),
                product.getOrderFeeRate(), product.getSalesFeeRate(),
                m.fee(), m.margin(), m.marginRate());
    }
}
