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

/** 판매 기록 한 건. 이익은 현재 상품의 원가 구조로 계산한다. */
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

    protected Sale() {
    }

    public Sale(Product product, LocalDate saleDate, int quantity, int unitPrice) {
        update(product, saleDate, quantity, unitPrice);
    }

    public void update(Product product, LocalDate saleDate, int quantity, int unitPrice) {
        this.product = product;
        this.saleDate = saleDate;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    /** 매출 = 수량 × 판매 단가 */
    public long revenue() {
        return (long) quantity * unitPrice;
    }

    /** 이익 = 수량 × (판매 단가 기준 개당 마진) */
    public long profit() {
        return (long) quantity * product.marginAt(unitPrice).margin();
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
}
