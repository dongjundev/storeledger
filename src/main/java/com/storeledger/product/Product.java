package com.storeledger.product;

import com.storeledger.category.Category;
import com.storeledger.margin.MarginResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

/** 판매 상품과 개당 원가 구조. 금액 단위는 원. */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** 카테고리. 없으면 미분류 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /** 판매가 */
    @Column(nullable = false)
    private int sellingPrice;

    /** 원가(매입 단가) */
    @Column(nullable = false)
    private int costPrice;

    /** 개당 택배비: 판매자가 택배사에 내는 실제 배송 비용 */
    @Column(nullable = false)
    private int shippingCost;

    /** 개당 구매자 배송비: 구매자가 결제하는 배송비 (무료배송이면 0). 기존 상품은 0으로 채워진다 */
    @Column(nullable = false)
    @ColumnDefault("0")
    private int buyerShippingFee;

    /** 개당 기타 비용 (포장비 등) */
    @Column(nullable = false)
    private int otherCost;

    /** 판매가에 붙는 수수료율 합계 (%) = 주문관리 수수료율 + 판매 수수료율 */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal feeRate;

    /**
     * 그중 주문관리 수수료율 (%). 구매자 배송비에는 이 수수료만 붙는다.
     * 합계(feeRate)와 따로 저장해, 이 칸이 생기기 전 상품은 3.63 + 나머지로 나뉘고 합계는 그대로 유지된다.
     * 3.63은 이전 기본값 5.63% = 3.63 + 2.00을 나누기 위한 값이라 현재 기본값(MarginResult)과 다르다.
     */
    @Column(nullable = false, precision = 5, scale = 2)
    @ColumnDefault("3.63")
    private BigDecimal orderFeeRate;

    protected Product() {
    }

    public Product(String name, Category category, int sellingPrice, int costPrice, int shippingCost,
                   int buyerShippingFee, int otherCost, BigDecimal orderFeeRate, BigDecimal salesFeeRate) {
        update(name, category, sellingPrice, costPrice, shippingCost, buyerShippingFee, otherCost, orderFeeRate,
                salesFeeRate);
    }

    public void update(String name, Category category, int sellingPrice, int costPrice, int shippingCost,
                       int buyerShippingFee, int otherCost, BigDecimal orderFeeRate, BigDecimal salesFeeRate) {
        this.name = name;
        this.category = category;
        this.sellingPrice = sellingPrice;
        this.costPrice = costPrice;
        this.shippingCost = shippingCost;
        this.buyerShippingFee = buyerShippingFee;
        this.otherCost = otherCost;
        this.orderFeeRate = orderFeeRate;
        this.feeRate = orderFeeRate.add(salesFeeRate);
    }

    /** 정가(판매가) 기준 개당 마진. */
    public MarginResult margin() {
        return marginAt(sellingPrice);
    }

    /** 실제 판매 단가 기준 개당 마진 (할인 판매 반영). */
    public MarginResult marginAt(int unitPrice) {
        return MarginResult.of(unitPrice, costPrice, shippingCost, buyerShippingFee, otherCost, getOrderFeeRate(),
                getSalesFeeRate());
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Category getCategory() {
        return category;
    }

    public int getSellingPrice() {
        return sellingPrice;
    }

    public int getCostPrice() {
        return costPrice;
    }

    public int getShippingCost() {
        return shippingCost;
    }

    public int getBuyerShippingFee() {
        return buyerShippingFee;
    }

    public int getOtherCost() {
        return otherCost;
    }

    /** 적용하는 주문관리 수수료율. 수수료율이 하나였던 옛 상품은 합계가 3.63보다 작을 수 있어 합계를 넘지 않게 한다. */
    public BigDecimal getOrderFeeRate() {
        return orderFeeRate.min(feeRate);
    }

    /** 판매 수수료율 = 합계 − 주문관리. 위 보정 덕분에 음수가 되지 않는다. */
    public BigDecimal getSalesFeeRate() {
        return feeRate.subtract(getOrderFeeRate());
    }
}
