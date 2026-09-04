package com.storeledger.sale;

import java.time.LocalDate;

public record SaleResponse(
        Long id,
        Long productId,
        String productName,
        LocalDate saleDate,
        int quantity,
        int unitPrice,
        long revenue,
        long profit) {

    public static SaleResponse from(Sale sale) {
        return new SaleResponse(sale.getId(), sale.getProduct().getId(), sale.getProduct().getName(),
                sale.getSaleDate(), sale.getQuantity(), sale.getUnitPrice(), sale.revenue(), sale.profit());
    }
}
