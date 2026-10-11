package com.bozor.order;

import com.bozor.catalog.SaleUnit;
import com.bozor.product.PriceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** 주문 항목. 상품명·단가·단위는 주문 시점 값으로 고정한다(상품을 나중에 고쳐도 주문 내역은 그대로). */
@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, length = 120)
    private String productName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PriceType priceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private SaleUnit unit;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    protected OrderItem() {}

    public OrderItem(Order order, Long productId, String productName, BigDecimal unitPrice, PriceType priceType,
                     SaleUnit unit, BigDecimal quantity) {
        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.priceType = priceType;
        this.unit = unit;
        this.quantity = quantity;
        this.lineTotal = unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isNegotiable() {
        return priceType == PriceType.NEGOTIABLE;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public PriceType getPriceType() { return priceType; }
    public SaleUnit getUnit() { return unit; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
