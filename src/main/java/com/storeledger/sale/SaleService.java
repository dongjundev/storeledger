package com.storeledger.sale;

import com.storeledger.product.Product;
import com.storeledger.product.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class SaleService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

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

        Map<LocalDate, long[]> buckets = new HashMap<>(); // [매출, 이익, 수량]
        for (Sale sale : sales.findBetween(from, to)) {
            long[] acc = buckets.computeIfAbsent(period.bucketStart(sale.getSaleDate()), k -> new long[3]);
            acc[0] += sale.revenue();
            acc[1] += sale.profit();
            acc[2] += sale.getQuantity();
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
        return new SalesSummary(period, from, to, totalRevenue, totalProfit, totalQuantity, points);
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "판매 기록을 찾을 수 없습니다: " + id));
    }
}
