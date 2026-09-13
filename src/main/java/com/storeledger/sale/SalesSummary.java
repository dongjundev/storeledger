package com.storeledger.sale;

import java.time.LocalDate;
import java.util.List;

public record SalesSummary(
        Period period,
        LocalDate from,
        LocalDate to,
        long totalRevenue,
        long totalProfit,
        long totalQuantity,
        List<SummaryPoint> points,
        List<CategorySales> categories) {
}
