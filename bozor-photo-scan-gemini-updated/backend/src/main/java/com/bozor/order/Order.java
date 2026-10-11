package com.bozor.order;

import com.bozor.order.OrderEnums.FulfillmentType;
import com.bozor.order.OrderEnums.PaymentMethod;
import com.bozor.order.OrderEnums.Status;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** 주문. 한 주문 = 한 상점. 상품명·가격은 주문 시점 스냅샷(OrderItem)으로 보관한다. */
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String orderNumber;

    @Column(nullable = false)
    private Long buyerId;

    @Column(nullable = false)
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FulfillmentType fulfillmentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentMethod paymentMethod;

    @Column(nullable = false, length = 16)
    private String contactPhone;

    private String deliveryAddress;
    private String landmark;

    @Column(length = 500)
    private String buyerNote;

    /** 흥정 가능 상품이 섞여 있으면 예상 금액이다. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    /** 판매자가 접수 때 "재고에서 빼기"를 골랐으면 true. 취소되면 이걸 보고 재고를 되돌린다. */
    @Column(nullable = false)
    private boolean stockDeducted;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    /** id 가 생기기 전에 저장해야 해서 임시 번호로 만들고, 저장 뒤 assignNumber 로 바꾼다. */
    public Order(String temporaryNumber, Long buyerId, Long storeId, FulfillmentType fulfillmentType,
                 PaymentMethod paymentMethod, String contactPhone, String deliveryAddress, String landmark,
                 String buyerNote) {
        this.orderNumber = temporaryNumber;
        this.buyerId = buyerId;
        this.storeId = storeId;
        this.status = Status.REQUESTED;
        this.fulfillmentType = fulfillmentType;
        this.paymentMethod = paymentMethod;
        this.contactPhone = contactPhone;
        this.deliveryAddress = deliveryAddress;
        this.landmark = landmark;
        this.buyerNote = buyerNote;
        this.totalAmount = BigDecimal.ZERO;
        this.currency = "TJS";
    }

    public void addItem(OrderItem item) {
        items.add(item);
        totalAmount = totalAmount.add(item.getLineTotal());
    }

    public void assignNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public void changeStatus(Status status) {
        this.status = status;
    }

    public void markStockDeducted(boolean deducted) {
        this.stockDeducted = deducted;
    }

    public boolean isEstimate() {
        return items.stream().anyMatch(OrderItem::isNegotiable);
    }

    public Long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public Long getBuyerId() { return buyerId; }
    public Long getStoreId() { return storeId; }
    public Status getStatus() { return status; }
    public FulfillmentType getFulfillmentType() { return fulfillmentType; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getContactPhone() { return contactPhone; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public String getLandmark() { return landmark; }
    public String getBuyerNote() { return buyerNote; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public boolean isStockDeducted() { return stockDeducted; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<OrderItem> getItems() { return items; }
}
