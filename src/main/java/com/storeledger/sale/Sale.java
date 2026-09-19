package com.storeledger.sale;

import com.storeledger.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** 판매 기록 한 건. 원가는 판매 시점 값을 기록하고, 비어 있으면 상품의 현재 원가를 쓴다. */
@Entity
@Table(name = "sales")
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private LocalDate saleDate;

    @Column(nullable = false)
    private int quantity;

    /** 실제 판매 단가 (할인 판매 시 정가와 다를 수 있음) */
    @Column(nullable = false)
    private int unitPrice;

    /**
     * 그 판매 시점의 개당 원가. 달러로 매입하면 환율에 따라 건마다 달라지므로 따로 기록해 둔다.
     * 이 기능이 생기기 전의 기록은 비어 있고, 그때는 상품의 현재 원가를 쓴다.
     */
    @Column
    private Integer unitCost;

    protected Sale() {
    }

    public Sale(Product product, LocalDate saleDate, int quantity, int unitPrice, Integer unitCost) {
        update(product, saleDate, quantity, unitPrice, unitCost);
    }

    public void update(Product product, LocalDate saleDate, int quantity, int unitPrice, Integer unitCost) {
        this.product = product;
        this.saleDate = saleDate;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.unitCost = unitCost;
    }

    /** 이 판매에 적용할 개당 원가. 기록해 둔 값이 없으면 상품의 현재 원가. */
    public int costPrice() {
        return unitCost != null ? unitCost : product.getCostPrice();
    }

    /** 매출 = 수량 × 판매 단가 */
    public long revenue() {
        return (long) quantity * unitPrice;
    }

    /** 이익 = 수량 × (판매 단가와 그때 원가 기준 개당 마진) */
    public long profit() {
        return (long) quantity * product.marginAt(unitPrice, costPrice()).margin();
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public LocalDate getSaleDate() {
        return saleDate;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getUnitPrice() {
        return unitPrice;
    }

    public Integer getUnitCost() {
        return unitCost;
    }
}
