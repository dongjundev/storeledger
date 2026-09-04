package com.storeledger.product;

import com.storeledger.margin.MarginResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    /** 판매가 */
    @Column(nullable = false)
    private int sellingPrice;

    /** 원가(매입 단가) */
    @Column(nullable = false)
    private int costPrice;

    /** 개당 배송비 (판매자 부담분) */
    @Column(nullable = false)
    private int shippingCost;

    /** 개당 기타 비용 (포장비 등) */
    @Column(nullable = false)
    private int otherCost;

    /** 판매 수수료율 (%) */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal feeRate;

    protected Product() {
    }

    public Product(String name, int sellingPrice, int costPrice, int shippingCost, int otherCost, BigDecimal feeRate) {
        update(name, sellingPrice, costPrice, shippingCost, otherCost, feeRate);
    }

    public void update(String name, int sellingPrice, int costPrice, int shippingCost, int otherCost, BigDecimal feeRate) {
        this.name = name;
        this.sellingPrice = sellingPrice;
        this.costPrice = costPrice;
        this.shippingCost = shippingCost;
        this.otherCost = otherCost;
        this.feeRate = feeRate;
    }

    /** 정가(판매가) 기준 개당 마진. */
    public MarginResult margin() {
        return marginAt(sellingPrice);
    }

    /** 실제 판매 단가 기준 개당 마진 (할인 판매 반영). */
    public MarginResult marginAt(int unitPrice) {
        return MarginResult.of(unitPrice, costPrice, shippingCost, otherCost, feeRate);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
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

    public int getOtherCost() {
        return otherCost;
    }

    public BigDecimal getFeeRate() {
        return feeRate;
    }
}
