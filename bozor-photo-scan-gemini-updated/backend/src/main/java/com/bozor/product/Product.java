package com.bozor.product;

import com.bozor.catalog.SaleUnit;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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

@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private Long categoryId;

    /** AI 후보에서 만든 상품이면 그 후보 id, 직접 등록이면 NULL. */
    @Column(unique = true)
    private Long sourceCandidateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProductStatus status;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PriceType priceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private SaleUnit unit;

    /** NULL = 재고를 관리하지 않음(구매 가능), 0 = 품절. 판매자가 직접 관리한다. */
    @Column(precision = 10, scale = 3)
    private BigDecimal stockQuantity;

    private Instant publishedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "product_translations", joinColumns = @JoinColumn(name = "product_id"))
    @OrderBy("locale")
    private List<ProductTranslation> translations = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @OrderBy("sortOrder")
    private List<ProductImageRef> images = new ArrayList<>();

    protected Product() {}

    public Product(Long storeId, Long sourceCandidateId) {
        this.storeId = storeId;
        this.sourceCandidateId = sourceCandidateId;
        this.status = ProductStatus.DRAFT;
        this.currency = "TJS";
    }

    /** 등록·수정 폼의 값을 반영한다(검증은 ProductService 에서 끝난 상태). */
    public void apply(Long categoryId, BigDecimal price, PriceType priceType, SaleUnit unit, BigDecimal stockQuantity,
                      List<ProductTranslation> translations, List<Long> imageIds) {
        this.categoryId = categoryId;
        this.price = price;
        this.priceType = priceType;
        this.unit = unit;
        this.stockQuantity = stockQuantity;
        this.translations.clear();
        this.translations.addAll(translations);
        this.images.clear();
        for (int i = 0; i < imageIds.size(); i++) {
            this.images.add(new ProductImageRef(imageIds.get(i), i));
        }
    }

    public void changeStatus(ProductStatus next) {
        if (next == ProductStatus.PUBLISHED && publishedAt == null) {
            publishedAt = Instant.now();
        }
        this.status = next;
    }

    public void changeStock(BigDecimal stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public Long getCategoryId() { return categoryId; }
    public Long getSourceCandidateId() { return sourceCandidateId; }
    public ProductStatus getStatus() { return status; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
    public PriceType getPriceType() { return priceType; }
    public SaleUnit getUnit() { return unit; }
    public BigDecimal getStockQuantity() { return stockQuantity; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ProductTranslation> getTranslations() { return translations; }
    public List<ProductImageRef> getImages() { return images; }
}
