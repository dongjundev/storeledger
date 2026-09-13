package com.storeledger.sale;

import com.storeledger.category.Category;
import com.storeledger.product.Product;
import com.storeledger.product.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class SaleService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 한 번에 만드는 구간 수 상한. 연도를 입력하다 만 날짜(예: 0002년)로 수십만 개 구간을 만들지 않게 막는다. */
    static final int MAX_POINTS = 1_000;

    private final SaleRepository sales;
    private final ProductService products;

    public SaleService(SaleRepository sales, ProductService products) {
        this.sales = sales;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<SaleResponse> findBetween(LocalDate from, LocalDate to) {
        LocalDate[] range = resolveRange(Period.DAILY, from, to);
        return sales.findBetween(range[0], range[1]).stream().map(SaleResponse::from).toList();
    }

    public SaleResponse create(SaleRequest request) {
        Product product = products.get(request.productId());
        Sale sale = new Sale(product, request.saleDate(), request.quantity(), unitPrice(request, product));
        return SaleResponse.from(sales.save(sale));
    }

    public SaleResponse update(Long id, SaleRequest request) {
        Sale sale = get(id);
        Product product = products.get(request.productId());
        sale.update(product, request.saleDate(), request.quantity(), unitPrice(request, product));
        return SaleResponse.from(sale);
    }

    public void delete(Long id) {
        sales.delete(get(id));
    }

    /**
     * 기간별 매출 집계. 판매가 없는 구간도 0으로 채워 그래프의 x축이 끊기지 않게 한다.
     * from/to 생략 시 to=오늘(KST), from=단위별 기본 범위.
     */
    @Transactional(readOnly = true)
    public SalesSummary summary(Period period, LocalDate from, LocalDate to) {
        LocalDate[] range = resolveRange(period, from, to);
        from = range[0];
        to = range[1];
        if (period.bucketCount(from, to) > MAX_POINTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 기간이 너무 깁니다. 한 번에 "
                    + MAX_POINTS + "개 구간까지 볼 수 있으니 기간을 줄이거나 주·월·년 단위로 보세요.");
        }

        Map<LocalDate, long[]> buckets = new HashMap<>();     // 구간별 [매출, 이익, 수량]
        Map<Long, long[]> byCategory = new HashMap<>();        // 카테고리별 [매출, 이익, 수량], null 키는 미분류
        Map<Long, String> categoryNames = new HashMap<>();
        for (Sale sale : sales.findBetween(from, to)) {
            long[] acc = buckets.computeIfAbsent(period.bucketStart(sale.getSaleDate()), k -> new long[3]);
            acc[0] += sale.revenue();
            acc[1] += sale.profit();
            acc[2] += sale.getQuantity();

            Category category = sale.getProduct().getCategory();
            Long categoryId = category == null ? null : category.getId();
            categoryNames.put(categoryId, category == null ? null : category.getName());
            long[] byCat = byCategory.computeIfAbsent(categoryId, k -> new long[3]);
            byCat[0] += sale.revenue();
            byCat[1] += sale.profit();
            byCat[2] += sale.getQuantity();
        }

        List<SummaryPoint> points = new ArrayList<>();
        long totalRevenue = 0, totalProfit = 0, totalQuantity = 0;
        for (LocalDate start = period.bucketStart(from); !start.isAfter(to); start = period.next(start)) {
            long[] acc = buckets.getOrDefault(start, new long[3]);
            LocalDate end = period.next(start).minusDays(1);
            points.add(new SummaryPoint(period.label(start),
                    start.isBefore(from) ? from : start,
                    end.isAfter(to) ? to : end,
                    acc[0], acc[1], acc[2]));
            totalRevenue += acc[0];
            totalProfit += acc[1];
            totalQuantity += acc[2];
        }
        List<CategorySales> categories = byCategory.entrySet().stream()
                .map(e -> new CategorySales(e.getKey(), categoryNames.get(e.getKey()),
                        e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .sorted(Comparator.comparingLong(CategorySales::revenue).reversed()
                        .thenComparing(CategorySales::categoryName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return new SalesSummary(period, from, to, totalRevenue, totalProfit, totalQuantity, points, categories);
    }

    private static LocalDate[] resolveRange(Period period, LocalDate from, LocalDate to) {
        if (to == null) to = LocalDate.now(KST);
        if (from == null) from = period.defaultFrom(to);
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "시작일이 종료일보다 늦을 수 없습니다.");
        }
        return new LocalDate[] {from, to};
    }

    private static int unitPrice(SaleRequest request, Product product) {
        return request.unitPrice() != null ? request.unitPrice() : product.getSellingPrice();
    }

    private Sale get(Long id) {
        return sales.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "판매 기록을 찾을 수 없습니다. 이미 지워졌을 수 있습니다."));
    }
}
